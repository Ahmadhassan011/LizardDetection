package com.lizardlens.core.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lizardlens.core.model.BoundingBox
import com.lizardlens.core.model.DetectionSource

class Converters {

    private val gson: Gson = GsonBuilder().create()

    @TypeConverter
    fun fromDetectionSource(value: DetectionSource): String = value.name

    @TypeConverter
    fun toDetectionSource(value: String): DetectionSource = DetectionSource.valueOf(value)

    @TypeConverter
    fun fromBoundingBox(value: BoundingBox): String = gson.toJson(value)

    @TypeConverter
    fun toBoundingBox(value: String): BoundingBox = gson.fromJson(value, BoundingBox::class.java)
}
