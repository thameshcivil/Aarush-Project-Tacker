package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.Project
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(project: Project): Long

    @Update
    suspend fun update(project: Project)

    @Delete
    suspend fun delete(project: Project)

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<Project>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeById(id: Long): Flow<Project?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getById(id: Long): Project?

    @Query("""
        SELECT * FROM projects
        WHERE (:query = '' OR name LIKE '%' || :query || '%' OR clientName LIKE '%' || :query || '%' OR location LIKE '%' || :query || '%')
        ORDER BY updatedAt DESC
    """)
    fun search(query: String): Flow<List<Project>>
}
