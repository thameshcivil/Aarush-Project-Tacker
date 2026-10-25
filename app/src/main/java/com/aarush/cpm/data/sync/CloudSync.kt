package com.aarush.cpm.data.sync

import android.content.Context
import androidx.room.InvalidationTracker
import androidx.room.withTransaction
import com.aarush.cpm.data.backup.DatabaseBackupHelper
import com.aarush.cpm.data.database.AppDatabase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.Date

object SyncConfig {
    /** The team's shared Google account. The Firestore security rules allow only this one. */
    const val TEAM_EMAIL = "aarushmakesyoucomfort@gmail.com"
}

data class SyncStatus(
    val enabled: Boolean = false,
    val busy: Boolean = false,
    val lastSyncAt: Long? = null,
    val pending: Long = 0,
    val waitingForParents: Long = 0,
    val error: String? = null
)

/** What the cloud holds when a phone turns sync on. */
enum class CloudState { EMPTY, HAS_DATA, RESUME }

/**
 * Cloud sync over Firestore (Phase 2). Layout: sync/v1/{table}/{uid} with fields
 *   d  = the row (links as uids), u = server time of the last change, by = device id,
 *   x  = true for a deleted row (d is kept so phones joining later can still link to it).
 * sync/v1 itself holds lastChange: every phone listens to it and pulls when it moves.
 *
 * Local changes are queued by database triggers (see [SyncEngine]) and pushed a moment
 * after they happen; Firestore keeps writes made offline and sends them when back online.
 */
class CloudSync(
    private val context: Context,
    private val db: AppDatabase,
    private val deviceId: String,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("cloud_sync", Context.MODE_PRIVATE)
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val firestore by lazy { FirebaseFirestore.getInstance() }
    private val root get() = firestore.collection("sync").document("v1")
    private val engine by lazy { SyncEngine(RoomSyncSql(db.openHelper.writableDatabase)) }
    private val mutex = Mutex()

    private val _status = MutableStateFlow(SyncStatus(enabled = prefs.getBoolean(KEY_ENABLED, false), lastSyncAt = lastSync()))
    val status: StateFlow<SyncStatus> = _status

    private var listener: ListenerRegistration? = null
    private var observer: InvalidationTracker.Observer? = null
    private var pushJob: Job? = null

    private fun lastSync() = prefs.getLong(KEY_LAST, 0L).takeIf { it > 0 }
    private fun signedInToTeam() = auth.currentUser?.email.equals(SyncConfig.TEAM_EMAIL, ignoreCase = true)

    /** Called at app start: resumes sync if it was on. */
    fun start() {
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        if (!signedInToTeam()) {
            _status.value = _status.value.copy(error = "Signed out of the team account. Turn cloud sync on again.")
            return
        }
        attach()
        scope.launch { syncNow() }
    }

    /** Signs in to Firebase with the Google ID token and reports what the cloud holds. */
    suspend fun connect(idToken: String): Result<CloudState> = runCatching {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        if (!signedInToTeam()) {
            val who = auth.currentUser?.email
            auth.signOut()
            error("That's $who. Cloud sync uses the team account, ${SyncConfig.TEAM_EMAIL}.")
        }
        val initialized = root.get(Source.SERVER).await().exists()
        val syncedBefore = hasMappings()
        when {
            !initialized -> CloudState.EMPTY
            syncedBefore -> CloudState.RESUME
            else -> CloudState.HAS_DATA
        }
    }

    private suspend fun hasMappings(): Boolean = db.withTransaction {
        RoomSyncSql(db.openHelper.writableDatabase).query("SELECT COUNT(*) AS n FROM sync_ids").first()["n"] as Long > 0
    }

    /** Owner's phone, empty cloud: this phone's data becomes the team's starting copy. */
    suspend fun uploadFirstCopy(): Result<Unit> = runCatching {
        db.withTransaction { engine.enqueueAll() }
        root.set(mapOf("initializedBy" to deviceId, "at" to FieldValue.serverTimestamp(), "lastChange" to FieldValue.serverTimestamp()),
            SetOptions.merge()).await()
        enable()
        syncNow()
    }

    /** Another phone, cloud has data: keep a safety copy, empty this phone, take the cloud copy. */
    suspend fun replaceWithCloudCopy(afterReplace: suspend () -> Unit): Result<File> = runCatching {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd_HHmm", java.util.Locale.US).format(Date())
        val safety = File(context.getExternalFilesDir(null) ?: context.filesDir, "Before_cloud_sync_$stamp.db")
        DatabaseBackupHelper.backupToFile(context, safety, db).getOrThrow()
        db.withTransaction { engine.wipeLocal() }
        enable()
        pull()
        afterReplace()   // re-register this phone in the team (as a Viewer, since an Owner exists)
        syncNow()
        safety
    }

    /** Resume on a phone that synced before: just carry on. */
    suspend fun resume(): Result<Unit> = runCatching { enable(); syncNow() }

    fun turnOff() {
        prefs.edit().putBoolean(KEY_ENABLED, false).apply()
        detach()
        auth.signOut()
        _status.value = SyncStatus(enabled = false, lastSyncAt = lastSync())
    }

    private fun enable() {
        prefs.edit().putBoolean(KEY_ENABLED, true).apply()
        _status.value = _status.value.copy(enabled = true, error = null)
        attach()
    }

    private fun attach() {
        if (listener == null) {
            // Another phone changed something: pull. (Our own writes show as pending; skip them.)
            listener = root.addSnapshotListener { snap, _ ->
                if (snap != null && !snap.metadata.hasPendingWrites()) scope.launch { syncNow() }
            }
        }
        if (observer == null) {
            observer = object : InvalidationTracker.Observer(SyncSchema.tables.map { it.name }.toTypedArray()) {
                override fun onInvalidated(tables: Set<String>) = schedulePush()
            }.also { db.invalidationTracker.addObserver(it) }
        }
    }

    private fun detach() {
        listener?.remove(); listener = null
        observer?.let { db.invalidationTracker.removeObserver(it) }; observer = null
        pushJob?.cancel()
    }

    /** Local edits arrive in bursts (an expense touches several tables): push once it settles. */
    private fun schedulePush() {
        pushJob?.cancel()
        pushJob = scope.launch {
            delay(2_000)
            runCatching { mutex.withLock { push() } }
            refreshCounts()
        }
    }

    /** Push local changes, pull everyone else's, push anything the pull deferred. */
    suspend fun syncNow() {
        if (!prefs.getBoolean(KEY_ENABLED, false) || !signedInToTeam()) return
        _status.value = _status.value.copy(busy = true)
        val error = try {
            mutex.withLock {
                push()
                pull()
                push()
            }
            prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
            null
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.TimeoutCancellationException || e.message?.contains("offline", true) == true ||
                e.message?.contains("UNAVAILABLE") == true
            ) "Offline. Changes are saved on this phone and will sync when you're back online."
            else "Sync problem: ${e.message}"
        }
        _status.value = _status.value.copy(busy = false, error = error, lastSyncAt = lastSync())
        refreshCounts()
    }

    private suspend fun refreshCounts() {
        val (pending, parked) = withContext(Dispatchers.IO) {
            db.withTransaction { engine.pendingCount() to engine.parkedCount() }
        }
        _status.value = _status.value.copy(pending = pending, waitingForParents = parked)
    }

    private suspend fun push() {
        while (true) {
            val batch = db.withTransaction { engine.collectOutgoing(BATCH) }
            if (batch.isEmpty()) return
            val write = firestore.batch()
            batch.forEach { (_, row) ->
                if (row == null) return@forEach
                val ref = root.collection(row.table).document(row.uid)
                if (row.deleted) {
                    write.set(ref, mapOf("u" to FieldValue.serverTimestamp(), "by" to deviceId, "x" to true), SetOptions.merge())
                } else {
                    write.set(ref, mapOf("d" to row.data, "u" to FieldValue.serverTimestamp(), "by" to deviceId, "x" to false))
                }
            }
            write.set(root, mapOf("lastChange" to FieldValue.serverTimestamp()), SetOptions.merge())
            withTimeout(30_000) { write.commit().await() }
            db.withTransaction { engine.markSent(batch.map { it.first }) }
        }
    }

    private suspend fun pull() {
        for (t in SyncSchema.tables) {
            // Re-read a few seconds back: a write committed just before the last pull may
            // only have become visible after it. Applying a row twice changes nothing.
            val since = (db.withTransaction { engine.cursor(t.name) } - OVERLAP_MS).coerceAtLeast(0)
            var last: DocumentSnapshot? = null
            while (true) {
                var q: Query = root.collection(t.name)
                    .whereGreaterThanOrEqualTo("u", Timestamp(Date(since)))
                    .orderBy("u")
                    .limit(BATCH.toLong())
                last?.let { q = q.startAfter(it) }
                val docs = withTimeout(30_000) { q.get(Source.SERVER).await() }.documents
                if (docs.isEmpty()) break
                val rows = docs.map { d ->
                    @Suppress("UNCHECKED_CAST")
                    CloudRow(
                        table = t.name, uid = d.id, data = d.get("d") as Map<String, Any?>?,
                        deleted = d.getBoolean("x") == true, by = d.getString("by") ?: "",
                        updatedAt = d.getTimestamp("u")?.toDate()?.time ?: 0L
                    )
                }
                db.withTransaction {
                    engine.applyIncoming(rows)
                    engine.setCursor(t.name, maxOf(engine.cursor(t.name), rows.maxOf { it.updatedAt }))
                }
                last = docs.last()
                if (docs.size < BATCH) break
            }
        }
    }

    companion object {
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST = "last_sync"
        private const val BATCH = 400
        private const val OVERLAP_MS = 10_000L
    }
}
