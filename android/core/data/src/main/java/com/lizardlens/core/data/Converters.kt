package com.lizardlens.core.data

import androidx.room.TypeConverter
import com.lizardlens.core.model.BoundingBox
import com.lizardlens.core.model.DetectionSource

class Converters {

    @TypeConverter
    fun fromDetectionSource(value: DetectionSource): String = value.name

    @TypeConverter
    fun toDetectionSource(value: String): DetectionSource = DetectionSource.valueOf(value)

    @TypeConverter
    fun fromBoundingBox(value: BoundingBox): String = GsonProvider.gson.toJson(value)

    @TypeConverter
    fun toBoundingBox(value: String): BoundingBox = GsonProvider.gson.fromJson(value, BoundingBox::class.java)
}
