package com.aarush.cpm.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.room.withTransaction
import com.aarush.cpm.data.database.AppDatabase
import com.aarush.cpm.data.entity.AppSettings
import com.aarush.cpm.data.entity.ProjectMember
import com.aarush.cpm.data.entity.TeamDevice
import com.aarush.cpm.data.entity.TeamRole
import com.aarush.cpm.domain.access.Access
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID

/** The ID of the phone the app is running on, for stamping "entered by" on new records.
 *  Set once at startup by [DeviceIdentity]; null only in tests. */
object CurrentDevice {
    @Volatile var id: String? = null
}

/**
 * This install's permanent device ID. Kept in the no-backup folder on purpose: Android's
 * cloud backup and the app's own backup file never carry it, so a restored phone gets its
 * own ID instead of impersonating the phone the backup came from.
 */
class DeviceIdentity(context: Context) {
    val id: String
    val defaultName: String

    init {
        val file = File(context.noBackupFilesDir, "device_id")
        id = file.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { file.writeText(it) }
        CurrentDevice.id = id
        // The phone's own name (Settings > About phone) when set, else maker and model.
        val phoneName = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
        defaultName = phoneName?.takeIf { it.isNotBlank() }
            ?: "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}".trim()
    }
}

/** Owner's "View as" preview: the app behaves as this role / project list until stopped.
 *  Held in memory only, never saved, so it can't lock anyone out. */
data class AccessPreview(val name: String, val role: TeamRole, val projectIds: Set<Long>)

/** Everything the screens need about the team, as one snapshot. */
data class TeamState(
    val thisDeviceId: String,
    val devices: List<TeamDevice>,
    val members: List<ProjectMember>,
    val preview: AccessPreview? = null,
    val ownerPinSet: Boolean = false
) {
    val thisDevice: TeamDevice? get() = devices.firstOrNull { it.deviceId == thisDeviceId }
    val previewing: Boolean get() = preview != null

    /** This phone's own permissions, ignoring any preview (used by the Team screen itself). */
    val ownAccess: Access
        get() {
            val d = thisDevice ?: return Access.LOADING
            return Access(d.role, projectsOf(d.deviceId))
        }

    /** What the rest of the app enforces: the preview while one is running. */
    val access: Access
        get() = preview?.let { Access(it.role, it.projectIds) } ?: ownAccess

    fun nameOf(deviceId: String?): String? = deviceId?.let { id -> devices.firstOrNull { it.deviceId == id }?.name }
    fun projectsOf(deviceId: String): Set<Long> = members.filter { it.deviceId == deviceId }.map { it.projectId }.toSet()
    fun membersOf(projectId: Long): List<TeamDevice> =
        members.filter { it.projectId == projectId }.mapNotNull { m -> devices.firstOrNull { it.deviceId == m.deviceId } }
    fun leadFor(projectId: Long): TeamDevice? =
        members.firstOrNull { it.projectId == projectId && it.isLead }?.let { m -> devices.firstOrNull { it.deviceId == m.deviceId } }
}

class AccessRepository(
    private val db: AppDatabase,
    private val identity: DeviceIdentity,
    private val auth: AuthRepository
) {
    private val dao get() = db.teamDao()
    private val preview = MutableStateFlow<AccessPreview?>(null)
    private val pinChanged = MutableStateFlow(0)

    val thisDeviceId: String get() = identity.id

    val team: Flow<TeamState> = combine(
        dao.observeDevices(), dao.observeMembers(), preview,
        // Watched (not just re-read after a local change) so a PIN set on another phone shows.
        db.appSettingsDao().observeAll().map { all -> all.any { it.key == OWNER_PIN_KEY } }
    ) { devices, members, previewing, pinSet ->
        TeamState(identity.id, devices, members, previewing, pinSet)
    }

    /** Called at every app start. A new phone joins as Viewer with no projects. The very
     *  first phone (no Owner yet, e.g. right after updating to v10) becomes the Owner. */
    suspend fun registerThisDevice() = db.withTransaction {
        val now = System.currentTimeMillis()
        if (dao.getDevice(identity.id) == null) {
            val hasOwner = dao.getDevices().any { it.role == TeamRole.OWNER }
            dao.insertDevice(TeamDevice(identity.id, identity.defaultName, if (hasOwner) TeamRole.VIEWER else TeamRole.OWNER, now, now))
        } else {
            dao.touch(identity.id, now)
        }
    }

    suspend fun rename(deviceId: String, name: String): Result<Unit> {
        val clean = name.trim()
        if (clean.isEmpty()) return Result.failure(IllegalArgumentException("Enter a name."))
        val d = dao.getDevice(deviceId) ?: return Result.failure(IllegalStateException("Device not found."))
        dao.updateDevice(d.copy(name = clean.take(40)))
        return Result.success(Unit)
    }

    suspend fun setRole(deviceId: String, role: TeamRole): Result<Unit> = db.withTransaction {
        val d = dao.getDevice(deviceId) ?: return@withTransaction Result.failure(IllegalStateException("Device not found."))
        if (d.role == TeamRole.OWNER && role != TeamRole.OWNER && dao.getDevices().count { it.role == TeamRole.OWNER } <= 1)
            return@withTransaction Result.failure(IllegalStateException("Keep at least one Owner. Make another phone Owner first."))
        dao.updateDevice(d.copy(role = role))
        Result.success(Unit)
    }

    /** Replaces the device's project list with [projectIds] (leads it loses go with them). */
    suspend fun setProjects(deviceId: String, projectIds: Set<Long>) = db.withTransaction {
        if (dao.getDevice(deviceId) == null) return@withTransaction
        val existing = dao.getProjectIdsFor(deviceId).toSet()
        (existing - projectIds).forEach { dao.removeMember(it, deviceId) }
        (projectIds - existing).forEach { dao.upsertMember(ProjectMember(it, deviceId, isLead = false)) }
    }

    /** Makes [deviceId] the project's lead coordinator (assigning it if needed); null clears. */
    suspend fun setLead(projectId: Long, deviceId: String?) = db.withTransaction {
        dao.clearLead(projectId)
        if (deviceId != null) dao.upsertMember(ProjectMember(projectId, deviceId, isLead = true))
    }

    suspend fun removeDevice(deviceId: String): Result<Unit> = db.withTransaction {
        if (deviceId == identity.id) return@withTransaction Result.failure(IllegalStateException("You can't remove this phone."))
        val d = dao.getDevice(deviceId) ?: return@withTransaction Result.success(Unit)
        if (d.role == TeamRole.OWNER && dao.getDevices().count { it.role == TeamRole.OWNER } <= 1)
            return@withTransaction Result.failure(IllegalStateException("Keep at least one Owner."))
        dao.removeMembersForDevice(deviceId)
        dao.deleteDevice(deviceId)
        Result.success(Unit)
    }

    fun startPreview(p: AccessPreview) { preview.value = p }
    fun stopPreview() { preview.value = null }

    // ---------------------------------------------------------------- owner PIN / recovery

    suspend fun setOwnerPin(pin: String): Result<Unit> {
        if (!pin.matches(Regex("\\d{4,8}"))) return Result.failure(IllegalArgumentException("Use 4 to 8 digits."))
        db.appSettingsDao().set(AppSettings(OWNER_PIN_KEY, auth.hash(pin)))
        pinChanged.value += 1
        return Result.success(Unit)
    }

    /**
     * Makes this phone an Owner, e.g. the owner's new phone after restoring a backup. Needs
     * the Owner PIN; if no PIN was ever set, the app account's password instead. If the
     * team has no Owner at all, no secret is needed.
     */
    suspend fun claimOwner(secret: String): Result<Unit> = db.withTransaction {
        registerThisDevice()
        val devices = dao.getDevices()
        val pinHash = db.appSettingsDao().get(OWNER_PIN_KEY)?.value
        val ok = when {
            devices.none { it.role == TeamRole.OWNER } -> true
            pinHash != null -> auth.verify(secret, pinHash)
            else -> db.userDao().getAll().any { it.passwordHash.isNotBlank() && auth.verify(secret, it.passwordHash) }
        }
        if (!ok) return@withTransaction Result.failure(IllegalArgumentException(if (pinHash != null) "Wrong Owner PIN." else "Wrong password."))
        dao.getDevice(identity.id)?.let { dao.updateDevice(it.copy(role = TeamRole.OWNER)) }
        Result.success(Unit)
    }

    companion object {
        const val OWNER_PIN_KEY = "owner_pin_hash"
    }
}
