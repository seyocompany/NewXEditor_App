package com.example.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    // ============================================================
    //  PROJECT OPERATIONS
    // ============================================================

    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun getProjectById(id: String): Flow<ProjectEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    // ============================================================
    //  CLIP OPERATIONS
    // ============================================================

    @Query("SELECT * FROM clips WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getClipsForProject(projectId: String): Flow<List<ClipEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClip(clip: ClipEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClips(clips: List<ClipEntity>)

    @Update
    suspend fun updateClip(clip: ClipEntity)

    @Query("DELETE FROM clips WHERE id = :id")
    suspend fun deleteClipById(id: String)

    @Query("DELETE FROM clips WHERE projectId = :projectId")
    suspend fun deleteClipsForProject(projectId: String)

    // ============================================================
    //  TEXT OVERLAY OPERATIONS  (NEW for CapCut-style text)
    // ============================================================

    @Query("SELECT * FROM texts WHERE projectId = :projectId ORDER BY orderIndex ASC")
    fun getTextsForProject(projectId: String): Flow<List<TextEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertText(text: TextEntity)

    @Update
    suspend fun updateText(text: TextEntity)

    @Query("DELETE FROM texts WHERE id = :id")
    suspend fun deleteTextById(id: String)

    @Query("DELETE FROM texts WHERE projectId = :projectId")
    suspend fun deleteTextsForProject(projectId: String)
}