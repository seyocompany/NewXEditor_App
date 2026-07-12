package com.example.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Main Room database for the XEditor app.
 * Version 4 adds the TextEntity table for text overlays.
 */
@Database(
    entities = [ProjectEntity::class, ClipEntity::class, TextEntity::class],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}