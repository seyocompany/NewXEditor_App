package com.example.data

import android.util.Log
import com.example.data.room.ClipEntity
import com.example.data.room.ProjectDao
import com.example.data.room.ProjectEntity
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ProjectRepository(
    private val projectDao: ProjectDao
) {
    private val TAG = "ProjectRepository"
    
    // Check if Firebase is initialized
    private val isFirebaseAvailable: Boolean
        get() = try { Firebase.auth; true } catch (e: Exception) { false }

    val allProjects: Flow<List<ProjectEntity>> = projectDao.getAllProjects()

    fun getProject(id: String): Flow<ProjectEntity?> = projectDao.getProjectById(id)

    fun getClipsForProject(projectId: String): Flow<List<ClipEntity>> = projectDao.getClipsForProject(projectId)

    suspend fun createProject(name: String): String {
        val id = UUID.randomUUID().toString()
        val project = ProjectEntity(id = id, name = name)
        projectDao.insertProject(project)
        syncToFirestore(project)
        return id
    }
    
    suspend fun updateProject(project: ProjectEntity) {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        projectDao.updateProject(updated)
        syncToFirestore(updated)
    }

    suspend fun addClip(clip: ClipEntity) {
        projectDao.insertClip(clip)
        // Also update project's updatedAt
        val project = projectDao.getProjectById(clip.projectId).firstOrNull()
        if (project != null) {
            updateProject(project)
        }
    }
    
    suspend fun updateClip(clip: ClipEntity) {
        projectDao.updateClip(clip)
        val project = projectDao.getProjectById(clip.projectId).firstOrNull()
        if (project != null) {
            updateProject(project)
        }
    }

    suspend fun deleteClip(clip: ClipEntity) {
        projectDao.deleteClipById(clip.id)
    }

    suspend fun deleteProject(id: String) {
        projectDao.deleteProjectById(id)
        if (isFirebaseAvailable) {
            try {
                val user = Firebase.auth.currentUser
                if (user != null) {
                    Firebase.firestore.collection("users").document(user.uid)
                        .collection("projects").document(id).delete().await()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting from Firestore", e)
            }
        }
    }

    private suspend fun syncToFirestore(project: ProjectEntity) {
        if (!isFirebaseAvailable) return
        try {
            val user = Firebase.auth.currentUser
            if (user != null) {
                // simple sync
                val data = hashMapOf(
                    "id" to project.id,
                    "name" to project.name,
                    "updatedAt" to project.updatedAt
                )
                Firebase.firestore.collection("users").document(user.uid)
                    .collection("projects").document(project.id)
                    .set(data).await()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing to Firestore", e)
        }
    }
}
