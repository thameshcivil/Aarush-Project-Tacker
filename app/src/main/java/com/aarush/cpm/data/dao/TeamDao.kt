package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.ProjectMember
import com.aarush.cpm.data.entity.TeamDevice
import kotlinx.coroutines.flow.Flow

@Dao
interface TeamDao {
    @Query("SELECT * FROM team_devices ORDER BY createdAt")
    fun observeDevices(): Flow<List<TeamDevice>>

    @Query("SELECT * FROM team_devices ORDER BY createdAt")
    suspend fun getDevices(): List<TeamDevice>

    @Query("SELECT * FROM team_devices WHERE deviceId = :deviceId")
    suspend fun getDevice(deviceId: String): TeamDevice?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDevice(device: TeamDevice): Long

    @Update
    suspend fun updateDevice(device: TeamDevice)

    @Query("UPDATE team_devices SET lastSeenAt = :at WHERE deviceId = :deviceId")
    suspend fun touch(deviceId: String, at: Long)

    @Query("DELETE FROM team_devices WHERE deviceId = :deviceId")
    suspend fun deleteDevice(deviceId: String)

    @Query("SELECT * FROM project_members")
    fun observeMembers(): Flow<List<ProjectMember>>

    @Query("SELECT * FROM project_members WHERE projectId = :projectId")
    suspend fun getMembers(projectId: Long): List<ProjectMember>

    @Query("SELECT projectId FROM project_members WHERE deviceId = :deviceId")
    suspend fun getProjectIdsFor(deviceId: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(member: ProjectMember)

    @Query("DELETE FROM project_members WHERE projectId = :projectId AND deviceId = :deviceId")
    suspend fun removeMember(projectId: Long, deviceId: String)

    @Query("UPDATE project_members SET isLead = 0 WHERE projectId = :projectId")
    suspend fun clearLead(projectId: Long)

    @Query("DELETE FROM project_members WHERE deviceId = :deviceId")
    suspend fun removeMembersForDevice(deviceId: String)

    @Query("DELETE FROM project_members WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
