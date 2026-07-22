package com.lizardlens.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.lizardlens.core.model.DetectionSource

@Entity(tableName = "detections")
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val confidence: Float,
    val boundingBox: String,
    val source: DetectionSource,
    val imageUri: String? = null
)
