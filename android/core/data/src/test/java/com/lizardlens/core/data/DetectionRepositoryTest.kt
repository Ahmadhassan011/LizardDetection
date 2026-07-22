package com.lizardlens.core.data

import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lizardlens.core.inference.InferenceConfig
import com.lizardlens.core.inference.InferenceEngine
import com.lizardlens.core.model.BoundingBox
import com.lizardlens.core.model.Detection
import com.lizardlens.core.model.DetectionResult
import com.lizardlens.core.model.DetectionSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain

@RunWith(AndroidJUnit4::class)
class DetectionRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: DetectionDao
    private lateinit var mockEngine: InferenceEngine
    private lateinit var mockConfigStore: DetectionConfigStore
    private lateinit var repository: DetectionRepository

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.detectionDao()

        mockEngine = mock()
        mockConfigStore = mock()

        whenever(mockConfigStore.configFlow).thenReturn(
            kotlinx.coroutines.flow.flowOf(DetectionConfig())
        )
        whenever(mockConfigStore.toInferenceConfig(any())).thenReturn(InferenceConfig())

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        repository = DetectionRepository(context, dao, mockConfigStore, mockEngine)
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
        database.close()
    }

    @Test
    fun detectAndPersist_saves_detections_to_database() = runTest {
        val detections = listOf(
            Detection(BoundingBox(10f, 20f, 100f, 200f), 0.85f, "Lizard"),
            Detection(BoundingBox(50f, 60f, 150f, 250f), 0.72f, "Lizard")
        )
        val result = DetectionResult(detections, inferenceTimeMs = 30L)

        whenever(mockEngine.detect(any<Bitmap>())).thenReturn(result)

        val bitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val returned = repository.detectAndPersist(bitmap, DetectionSource.CAMERA)

        assertEquals(2, returned.detections.size)
        assertEquals(30L, returned.inferenceTimeMs)

        val persisted = dao.getAllDetections().first()
        assertEquals(2, persisted.size)
        assertEquals(DetectionSource.CAMERA, persisted[0].source)

        bitmap.recycle()
    }

    @Test
    fun detectAndPersist_persists_with_correct_source() = runTest {
        val result = DetectionResult(
            detections = listOf(Detection(BoundingBox(0f, 0f, 50f, 50f), 0.9f, "Lizard")),
            inferenceTimeMs = 10L
        )
        whenever(mockEngine.detect(any<Bitmap>())).thenReturn(result)

        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        repository.detectAndPersist(bitmap, DetectionSource.IMAGE)

        val persisted = dao.getDetectionsBySource(DetectionSource.IMAGE).first()
        assertEquals(1, persisted.size)

        bitmap.recycle()
    }

    @Test
    fun detectAndPersist_empty_result_persists_nothing() = runTest {
        val result = DetectionResult(emptyList(), inferenceTimeMs = 5L)
        whenever(mockEngine.detect(any<Bitmap>())).thenReturn(result)

        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        repository.detectAndPersist(bitmap, DetectionSource.VIDEO)

        assertEquals(0, dao.count())

        bitmap.recycle()
    }

    @Test
    fun persistDetection_saves_single_detection() = runTest {
        val detection = Detection(BoundingBox(10f, 20f, 100f, 200f), 0.88f, "Lizard")

        repository.persistDetection(detection, DetectionSource.CAMERA)

        val persisted = dao.getAllDetections().first()
        assertEquals(1, persisted.size)
        assertEquals(0.88f, persisted[0].confidence, 0.001f)
        assertEquals(DetectionSource.CAMERA, persisted[0].source)
    }

    @Test
    fun persistResult_saves_multiple_detections() = runTest {
        val result = DetectionResult(
            detections = listOf(
                Detection(BoundingBox(0f, 0f, 10f, 10f), 0.5f, "Lizard"),
                Detection(BoundingBox(20f, 20f, 30f, 30f), 0.6f, "Lizard"),
                Detection(BoundingBox(40f, 40f, 50f, 50f), 0.7f, "Lizard")
            ),
            inferenceTimeMs = 15L
        )

        repository.persistResult(result, DetectionSource.VIDEO)

        assertEquals(3, dao.count())
        val videoDetections = dao.getDetectionsBySource(DetectionSource.VIDEO).first()
        assertEquals(3, videoDetections.size)
    }

    @Test
    fun persistResult_empty_detections_persists_nothing() = runTest {
        val result = DetectionResult(emptyList(), inferenceTimeMs = 2L)
        repository.persistResult(result, DetectionSource.IMAGE)

        assertEquals(0, dao.count())
    }

    @Test
    fun deleteDetection_removes_entry() = runTest {
        repository.persistDetection(
            Detection(BoundingBox(0f, 0f, 10f, 10f), 0.5f, "Lizard"),
            DetectionSource.CAMERA
        )
        repository.persistDetection(
            Detection(BoundingBox(20f, 20f, 30f, 30f), 0.7f, "Lizard"),
            DetectionSource.CAMERA
        )

        val all = dao.getAllDetections().first()
        assertEquals(2, all.size)

        repository.deleteDetection(all[1].id)

        val remaining = dao.getAllDetections().first()
        assertEquals(1, remaining.size)
    }

    @Test
    fun clearHistory_removes_all_entries() = runTest {
        repository.persistDetection(
            Detection(BoundingBox(0f, 0f, 10f, 10f), 0.5f, "Lizard"),
            DetectionSource.CAMERA
        )
        repository.persistDetection(
            Detection(BoundingBox(20f, 20f, 30f, 30f), 0.7f, "Lizard"),
            DetectionSource.IMAGE
        )

        assertEquals(2, dao.count())

        repository.clearHistory()

        assertEquals(0, dao.count())
    }

    @Test
    fun parseBoundingBox_deserializes_json_correctly() {
        val json = """{"x1":10.5,"y1":20.3,"x2":100.7,"y2":200.9}"""
        val bbox = repository.parseBoundingBox(json)

        assertEquals(10.5f, bbox.x1, 0.001f)
        assertEquals(20.3f, bbox.y1, 0.001f)
        assertEquals(100.7f, bbox.x2, 0.001f)
        assertEquals(200.9f, bbox.y2, 0.001f)
    }

    @Test
    fun allDetections_returns_empty_flow_initially() = runTest {
        val detections = repository.allDetections.first()
        assertTrue(detections.isEmpty())
    }

    @Test
    fun allDetections_reflects_inserted_data() = runTest {
        repository.persistDetection(
            Detection(BoundingBox(0f, 0f, 10f, 10f), 0.5f, "Lizard"),
            DetectionSource.CAMERA
        )

        val detections = repository.allDetections.first()
        assertEquals(1, detections.size)
    }

    @Test
    fun detectAndPersist_persists_thumbnail_uri() = runTest {
        val result = DetectionResult(
            detections = listOf(Detection(BoundingBox(0f, 0f, 50f, 50f), 0.9f, "Lizard")),
            inferenceTimeMs = 10L
        )
        whenever(mockEngine.detect(any<Bitmap>())).thenReturn(result)

        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        repository.detectAndPersist(bitmap, DetectionSource.IMAGE, thumbnailUri = "/tmp/thumb.jpg")

        val persisted = dao.getAllDetections().first()
        assertEquals("/tmp/thumb.jpg", persisted[0].imageUri)

        bitmap.recycle()
    }

    @Test
    fun persistResult_persists_bounding_box_json() = runTest {
        val bbox = BoundingBox(15f, 25f, 115f, 215f)
        val result = DetectionResult(
            detections = listOf(Detection(bbox, 0.82f, "Lizard")),
            inferenceTimeMs = 20L
        )

        repository.persistResult(result, DetectionSource.CAMERA)

        val persisted = dao.getAllDetections().first()
        val parsed = repository.parseBoundingBox(persisted[0].boundingBox)
        assertEquals(15f, parsed.x1, 0.001f)
        assertEquals(25f, parsed.y1, 0.001f)
        assertEquals(115f, parsed.x2, 0.001f)
        assertEquals(215f, parsed.y2, 0.001f)
    }
}
