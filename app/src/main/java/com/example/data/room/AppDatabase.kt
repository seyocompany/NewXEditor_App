package com.example.data.room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ProjectEntity::class, ClipEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao
}
