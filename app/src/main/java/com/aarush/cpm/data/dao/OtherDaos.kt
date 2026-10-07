package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LabourRateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rates: List<LabourRate>)
    @Update
    suspend fun update(rate: LabourRate)
    @Query("SELECT * FROM labour_rates WHERE projectId = :projectId")
    fun observeForProject(projectId: Long): Flow<List<LabourRate>>
    @Query("SELECT * FROM labour_rates WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<LabourRate>
    @Query("DELETE FROM labour_rates WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface LabourEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: LabourEntry): Long
    @Update
    suspend fun update(entry: LabourEntry)
    @Query("SELECT * FROM labour_entries WHERE projectId = :projectId ORDER BY date DESC")
    fun observeForProject(projectId: Long): Flow<List<LabourEntry>>
    @Query("SELECT * FROM labour_entries WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<LabourEntry>
    @Query("DELETE FROM labour_entries WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface ScheduleActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ScheduleActivity): Long
    @Update
    suspend fun update(activity: ScheduleActivity)
    @Update
    suspend fun updateAll(activities: List<ScheduleActivity>)
    @Delete
    suspend fun delete(activity: ScheduleActivity)
    @Query("SELECT * FROM schedule_activities WHERE projectId = :projectId ORDER BY sequence, plannedStart")
    fun observeForProject(projectId: Long): Flow<List<ScheduleActivity>>
    @Query("SELECT * FROM schedule_activities WHERE projectId = :projectId ORDER BY sequence, plannedStart")
    suspend fun getForProject(projectId: Long): List<ScheduleActivity>
    @Query("SELECT * FROM schedule_activities WHERE id = :id")
    suspend fun getById(id: Long): ScheduleActivity?
    @Query("UPDATE schedule_activities SET linkedNotationCode = :newCode WHERE projectId = :projectId AND linkedNotationCode = :oldCode")
    suspend fun renameNotation(projectId: Long, oldCode: String, newCode: String)
    @Query("UPDATE schedule_activities SET vendorId = NULL WHERE vendorId = :vendorId")
    suspend fun clearVendor(vendorId: Long)
    @Query("DELETE FROM schedule_activities WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface ScheduleDependencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dependency: ScheduleDependency): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(dependencies: List<ScheduleDependency>)
    @Query("SELECT * FROM schedule_dependencies WHERE activityId = :activityId")
    suspend fun getForActivity(activityId: Long): List<ScheduleDependency>
    @Query("SELECT * FROM schedule_dependencies WHERE activityId IN (SELECT id FROM schedule_activities WHERE projectId = :projectId)")
    suspend fun getForProject(projectId: Long): List<ScheduleDependency>
    @Query("SELECT * FROM schedule_dependencies WHERE activityId IN (SELECT id FROM schedule_activities WHERE projectId = :projectId)")
    fun observeForProject(projectId: Long): Flow<List<ScheduleDependency>>
    @Query("DELETE FROM schedule_dependencies WHERE activityId = :activityId OR dependsOnActivityId = :activityId")
    suspend fun deleteForActivity(activityId: Long)
    @Query("DELETE FROM schedule_dependencies WHERE activityId = :activityId")
    suspend fun deletePredecessorsOf(activityId: Long)
    @Query("DELETE FROM schedule_dependencies WHERE activityId IN (SELECT id FROM schedule_activities WHERE projectId = :projectId)")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface ActivityProgressLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: ActivityProgressLog): Long
    @Update
    suspend fun update(log: ActivityProgressLog)
    @Delete
    suspend fun delete(log: ActivityProgressLog)
    @Query("SELECT * FROM activity_progress_logs WHERE projectId = :projectId ORDER BY date DESC, id DESC")
    fun observeForProject(projectId: Long): Flow<List<ActivityProgressLog>>
    @Query("SELECT * FROM activity_progress_logs WHERE projectId = :projectId ORDER BY date, id")
    suspend fun getForProject(projectId: Long): List<ActivityProgressLog>
    @Query("SELECT * FROM activity_progress_logs WHERE activityId = :activityId ORDER BY date, id")
    suspend fun getForActivity(activityId: Long): List<ActivityProgressLog>
    @Query("DELETE FROM activity_progress_logs WHERE activityId = :activityId")
    suspend fun deleteForActivity(activityId: Long)
    @Query("DELETE FROM activity_progress_logs WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface ProjectProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: ProjectProgress): Long
    @Query("SELECT * FROM project_progress WHERE projectId = :projectId ORDER BY date DESC")
    fun observeForProject(projectId: Long): Flow<List<ProjectProgress>>
    @Query("SELECT * FROM project_progress WHERE projectId = :projectId ORDER BY date DESC, id DESC LIMIT 1")
    suspend fun getLatest(projectId: Long): ProjectProgress?
    @Query("DELETE FROM project_progress WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: AppNotification): Long
    @Update
    suspend fun update(notification: AppNotification)
    @Query("SELECT * FROM notifications WHERE projectId = :projectId ORDER BY createdAt DESC")
    fun observeForProject(projectId: Long): Flow<List<AppNotification>>
    @Query("DELETE FROM notifications WHERE projectId = :projectId")
    suspend fun clearForProject(projectId: Long)
}

@Dao
interface AppSettingsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun set(setting: AppSettings)
    @Query("SELECT * FROM app_settings WHERE `key` = :key")
    suspend fun get(key: String): AppSettings?
    @Query("SELECT * FROM app_settings")
    fun observeAll(): Flow<List<AppSettings>>
}

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(user: User): Long
    @Update
    suspend fun update(user: User)
    /** Case-insensitive: "Asha@Mail.com" and "asha@mail.com" are the same account. */
    @Query("SELECT * FROM users WHERE email = :email COLLATE NOCASE LIMIT 1")
    suspend fun findByEmail(email: String): User?
    @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE OR email = :username COLLATE NOCASE LIMIT 1")
    suspend fun findByUsernameOrEmail(username: String): User?
}
