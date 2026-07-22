package com.lizardlens.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.lizardlens.core.model.DetectionSource
import kotlinx.coroutines.flow.Flow

@Dao
interface DetectionDao {
    @Query("SELECT * FROM detections ORDER BY timestamp DESC")
    fun getAllDetections(): Flow<List<DetectionEntity>>

    @Query("SELECT * FROM detections WHERE source = :source ORDER BY timestamp DESC")
    fun getDetectionsBySource(source: DetectionSource): Flow<List<DetectionEntity>>

    @Insert
    suspend fun insert(detection: DetectionEntity)

    @Query("DELETE FROM detections WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM detections")
    suspend fun deleteAll()
}
