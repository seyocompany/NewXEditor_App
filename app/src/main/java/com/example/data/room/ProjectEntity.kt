package com.example.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    val thumbnailUri: String? = null,
    val audioUri: String? = null,
    val audioVolume: Float = 0.5f,
    val audioStartTimeMs: Long = 0L,
    val audioEndTimeMs: Long = -1L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
