package com.example.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Represents a text overlay that can be placed on top of the video.
 * Each text belongs to a project and can be positioned, rotated, and styled.
 */
@Entity(
    tableName = "texts",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class TextEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val text: String = "New Text",
    val fontSize: Float = 36f,
    val color: Int = 0xFFFFFFFF.toInt(), // ARGB (white by default)
    val positionX: Float = 0.5f,         // 0..1 (0 = left, 1 = right, 0.5 = center)
    val positionY: Float = 0.5f,         // 0..1 (0 = top, 1 = bottom, 0.5 = center)
    val rotation: Float = 0f,
    val orderIndex: Int = 0,
    val isVisible: Boolean = true
)