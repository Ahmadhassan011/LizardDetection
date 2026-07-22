package com.lizardlens.core.data

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lizardlens.core.inference.InferenceEngine
import com.lizardlens.core.model.BoundingBox
import com.lizardlens.core.model.Detection
import com.lizardlens.core.model.DetectionResult
import com.lizardlens.core.model.DetectionSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DetectionRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: DetectionDao,
    private val configStore: DetectionConfigStore,
    private val inferenceEngine: InferenceEngine
) {

    private val gson: Gson = GsonBuilder().create()

    val allDetections: Flow<List<DetectionEntity>> = dao.getAllDetections()

    fun getDetectionsBySource(source: DetectionSource): Flow<List<DetectionEntity>> =
        dao.getDetectionsBySource(source)

    suspend fun detectAndPersist(
        bitmap: Bitmap,
        source: DetectionSource,
        thumbnailUri: String? = null
    ): DetectionResult = withContext(Dispatchers.Default) {
        val config = configStore.configFlow.first()
        val inferenceConfig = configStore.toInferenceConfig(config)
        val result = inferenceEngine.detect(bitmap)

        val entities = result.detections.map { detection ->
            DetectionEntity(
                timestamp = System.currentTimeMillis(),
                confidence = detection.confidence,
                boundingBox = gson.toJson(detection.boundingBox),
                source = source,
                imageUri = thumbnailUri
            )
        }

        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }

        result
    }

    suspend fun persistDetection(
        detection: Detection,
        source: DetectionSource,
        thumbnailUri: String? = null
    ) {
        val entity = DetectionEntity(
            timestamp = System.currentTimeMillis(),
            confidence = detection.confidence,
            boundingBox = gson.toJson(detection.boundingBox),
            source = source,
            imageUri = thumbnailUri
        )
        dao.insert(entity)
    }

    suspend fun persistResult(
        result: DetectionResult,
        source: DetectionSource,
        thumbnailUri: String? = null
    ) {
        val entities = result.detections.map { detection ->
            DetectionEntity(
                timestamp = System.currentTimeMillis(),
                confidence = detection.confidence,
                boundingBox = gson.toJson(detection.boundingBox),
                source = source,
                imageUri = thumbnailUri
            )
        }
        if (entities.isNotEmpty()) {
            dao.insertAll(entities)
        }
    }

    suspend fun saveThumbnail(bitmap: Bitmap, filename: String): String =
        withContext(Dispatchers.IO) {
            val file = File(context.filesDir, THUMBNAIL_DIR)
            file.mkdirs()
            val imageFile = File(file, filename)
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }
            imageFile.absolutePath
        }

    suspend fun deleteDetection(id: Long) = dao.deleteById(id)

    suspend fun clearHistory() = dao.deleteAll()

    fun parseBoundingBox(json: String): BoundingBox = gson.fromJson(json, BoundingBox::class)

    fun getCurrentConfig(): Flow<DetectionConfig> = configStore.configFlow

    companion object {
        private const val THUMBNAIL_DIR = "thumbnails"
    }
}
