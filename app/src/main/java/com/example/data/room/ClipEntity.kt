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

    // ---- NEW FIELDS for CapCut-style adjustments ----
    val brightness: Float = 0f,          // -1..1
    val contrast: Float = 0f,            // -1..1
    val saturation: Float = 0f,          // -1..1
    val warmth: Float = 0f,              // -1..1 (tint)
    val fade: Float = 0f,                // 0..1 (fade to black/white)
    val sharpen: Float = 0f,             // 0..1
    val filterName: String? = null,      // "vintage", "vivid", "bw", etc.

    val speed: Float = 1.0f,
    val orderIndex: Int = 0
)