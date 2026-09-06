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
}

@Dao
interface ScheduleActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(activity: ScheduleActivity): Long
    @Update
    suspend fun update(activity: ScheduleActivity)
    @Delete
    suspend fun delete(activity: ScheduleActivity)
    @Query("SELECT * FROM schedule_activities WHERE projectId = :projectId ORDER BY plannedStart")
    fun observeForProject(projectId: Long): Flow<List<ScheduleActivity>>
    @Query("SELECT * FROM schedule_activities WHERE projectId = :projectId")
    suspend fun getForProject(projectId: Long): List<ScheduleActivity>
}

@Dao
interface ScheduleDependencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dependency: ScheduleDependency): Long
    @Query("SELECT * FROM schedule_dependencies WHERE activityId = :activityId")
    suspend fun getForActivity(activityId: Long): List<ScheduleDependency>
}

@Dao
interface ProjectProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(progress: ProjectProgress): Long
    @Query("SELECT * FROM project_progress WHERE projectId = :projectId ORDER BY date DESC")
    fun observeForProject(projectId: Long): Flow<List<ProjectProgress>>
    @Query("SELECT * FROM project_progress WHERE projectId = :projectId ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(projectId: Long): ProjectProgress?
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
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun findByEmail(email: String): User?
    @Query("SELECT * FROM users WHERE username = :username OR email = :username LIMIT 1")
    suspend fun findByUsernameOrEmail(username: String): User?
}
