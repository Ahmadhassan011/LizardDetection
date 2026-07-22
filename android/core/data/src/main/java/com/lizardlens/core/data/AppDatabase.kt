package com.lizardlens.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.lizardlens.core.model.DetectionSource

@Database(
    entities = [DetectionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun detectionDao(): DetectionDao
}
