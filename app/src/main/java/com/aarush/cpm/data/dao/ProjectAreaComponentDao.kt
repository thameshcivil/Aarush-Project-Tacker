package com.aarush.cpm.data.dao

import androidx.room.*
import com.aarush.cpm.data.entity.ProjectAreaComponent
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectAreaComponentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(components: List<ProjectAreaComponent>)

    @Update
    suspend fun update(component: ProjectAreaComponent)

    @Delete
    suspend fun delete(component: ProjectAreaComponent)

    @Query("SELECT * FROM project_area_components WHERE projectId = :projectId ORDER BY id")
    fun observeForProject(projectId: Long): Flow<List<ProjectAreaComponent>>

    @Query("SELECT * FROM project_area_components WHERE projectId = :projectId ORDER BY id")
    suspend fun getForProject(projectId: Long): List<ProjectAreaComponent>

    @Query("DELETE FROM project_area_components WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: Long)
}
