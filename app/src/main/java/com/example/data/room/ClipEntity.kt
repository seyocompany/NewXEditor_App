package com.example.data.room

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "clips",
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
data class ClipEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val uri: String,
    val startTimeMs: Long = 0,
    val endTimeMs: Long = -1, // -1 means until end of video
    val cropRectString: String? = null, // "left,top,right,bottom"
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val brightness: Float = 0.0f,
    val contrast: Float = 0.0f,
    val saturation: Float = 0.0f,
    val speed: Float = 1.0f,
    val orderIndex: Int = 0
)
