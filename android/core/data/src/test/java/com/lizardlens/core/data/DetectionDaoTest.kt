package com.lizardlens.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
class DetectionDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: DetectionDao

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.detectionDao()
    }

    @After
    fun teardown() {
        database.close()
    }

    private fun createEntity(
        confidence: Float = 0.8f,
        source: DetectionSource = DetectionSource.CAMERA,
        timestamp: Long = System.currentTimeMillis(),
        boundingBox: String = """{"x1":10.0,"y1":20.0,"x2":100.0,"y2":200.0}"""
    ) = DetectionEntity(
        timestamp = timestamp,
        confidence = confidence,
        boundingBox = boundingBox,
        source = source
    )

    @Test
    fun insert_and_query_single_detection() = runTest {
        val entity = createEntity(confidence = 0.9f)
        val id = dao.insert(entity)

        assertTrue(id > 0)

        val all = dao.getAllDetections().first()
        assertEquals(1, all.size)
        assertEquals(0.9f, all[0].confidence, 0.001f)
    }

    @Test
    fun insert_multiple_detections_sorted_by_timestamp_desc() = runTest {
        val older = createEntity(timestamp = 1000L, confidence = 0.7f)
        val newer = createEntity(timestamp = 2000L, confidence = 0.9f)

        dao.insert(older)
        dao.insert(newer)

        val all = dao.getAllDetections().first()
        assertEquals(2, all.size)
        assertEquals(2000L, all[0].timestamp)
        assertEquals(1000L, all[1].timestamp)
    }

    @Test
    fun get_detections_by_source() = runTest {
        dao.insert(createEntity(source = DetectionSource.CAMERA))
        dao.insert(createEntity(source = DetectionSource.IMAGE))
        dao.insert(createEntity(source = DetectionSource.CAMERA))

        val cameraDetections = dao.getDetectionsBySource(DetectionSource.CAMERA).first()
        assertEquals(2, cameraDetections.size)
        assertTrue(cameraDetections.all { it.source == DetectionSource.CAMERA })

        val imageDetections = dao.getDetectionsBySource(DetectionSource.IMAGE).first()
        assertEquals(1, imageDetections.size)
        assertEquals(DetectionSource.IMAGE, imageDetections[0].source)
    }

    @Test
    fun deleteById_removes_correct_entry() = runTest {
        val entity1 = createEntity(confidence = 0.5f)
        val entity2 = createEntity(confidence = 0.9f)
        val id1 = dao.insert(entity1)
        val id2 = dao.insert(entity2)

        dao.deleteById(id1)

        val remaining = dao.getAllDetections().first()
        assertEquals(1, remaining.size)
        assertEquals(id2, remaining[0].id)
    }

    @Test
    fun deleteAll_clears_all_entries() = runTest {
        dao.insert(createEntity())
        dao.insert(createEntity())
        dao.insert(createEntity())

        assertEquals(3, dao.count())

        dao.deleteAll()

        assertEquals(0, dao.count())
        val all = dao.getAllDetections().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun insert_returns_auto_generated_id() = runTest {
        val id1 = dao.insert(createEntity(confidence = 0.5f))
        val id2 = dao.insert(createEntity(confidence = 0.7f))

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)
        assertTrue(id1 != id2)
    }

    @Test
    fun count_reflects_current_entries() = runTest {
        assertEquals(0, dao.count())

        dao.insert(createEntity())
        assertEquals(1, dao.count())

        dao.insert(createEntity())
        assertEquals(2, dao.count())

        dao.deleteAll()
        assertEquals(0, dao.count())
    }

    @Test
    fun insertAll_batch_inserts_multiple() = runTest {
        val entities = listOf(
            createEntity(confidence = 0.5f),
            createEntity(confidence = 0.7f),
            createEntity(confidence = 0.9f)
        )

        dao.insertAll(entities)

        assertEquals(3, dao.count())
    }

    @Test
    fun entity_preserves_source_type() = runTest {
        DetectionSource.entries.forEach { source ->
            dao.insert(createEntity(source = source))
        }

        val all = dao.getAllDetections().first()
        assertEquals(DetectionSource.entries.size, all.size)
        assertTrue(all.map { it.source }.toSet() == DetectionSource.entries.toSet())
    }

    @Test
    fun entity_preserves_bounding_box_json() = runTest {
        val bbox = """{"x1":5.5,"y1":10.2,"x2":99.9,"y2":200.1}"""
        dao.insert(createEntity(boundingBox = bbox))

        val all = dao.getAllDetections().first()
        assertEquals(bbox, all[0].boundingBox)
    }

    @Test
    fun entity_preserves_image_uri() = runTest {
        val entity = createEntity().copy(imageUri = "/data/thumbnails/test.jpg")
        dao.insert(entity)

        val all = dao.getAllDetections().first()
        assertEquals("/data/thumbnails/test.jpg", all[0].imageUri)
    }

    @Test
    fun entity_default_image_uri_is_null() = runTest {
        dao.insert(createEntity())

        val all = dao.getAllDetections().first()
        assertEquals(null, all[0].imageUri)
    }

    @Test
    fun insert_replaces_on_id_conflict() = runTest {
        val entity = createEntity(confidence = 0.5f)
        val id = dao.insert(entity)

        val updated = entity.copy(id = id, confidence = 0.99f)
        dao.insert(updated)

        val all = dao.getAllDetections().first()
        assertEquals(1, all.size)
        assertEquals(0.99f, all[0].confidence, 0.001f)
    }

    @Test
    fun getDetectionsBySource_returns_empty_for_nonexistent_source() = runTest {
        dao.insert(createEntity(source = DetectionSource.CAMERA))

        val videoDetections = dao.getDetectionsBySource(DetectionSource.VIDEO).first()
        assertTrue(videoDetections.isEmpty())
    }
}
