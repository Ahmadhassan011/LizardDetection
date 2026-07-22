package com.lizardlens.core.data

import com.lizardlens.core.model.BoundingBox
import com.lizardlens.core.model.DetectionSource
import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun detectionSource_roundtrip() {
        DetectionSource.entries.forEach { source ->
            val stored = converters.fromDetectionSource(source)
            val restored = converters.toDetectionSource(stored)
            assertEquals(source, restored)
        }
    }

    @Test
    fun boundingBox_roundtrip() {
        val bbox = BoundingBox(10.5f, 20.3f, 100.7f, 200.9f)
        val stored = converters.fromBoundingBox(bbox)
        val restored = converters.toBoundingBox(stored)

        assertEquals(bbox.x1, restored.x1, 0.001f)
        assertEquals(bbox.y1, restored.y1, 0.001f)
        assertEquals(bbox.x2, restored.x2, 0.001f)
        assertEquals(bbox.y2, restored.y2, 0.001f)
    }

    @Test
    fun boundingBox_produces_valid_json() {
        val bbox = BoundingBox(0f, 0f, 100f, 200f)
        val json = converters.fromBoundingBox(bbox)

        assert(json.contains("\"x1\""))
        assert(json.contains("\"y1\""))
        assert(json.contains("\"x2\""))
        assert(json.contains("\"y2\""))
    }

    @Test
    fun detectionSource_stores_as_name_string() {
        assertEquals("CAMERA", converters.fromDetectionSource(DetectionSource.CAMERA))
        assertEquals("IMAGE", converters.fromDetectionSource(DetectionSource.IMAGE))
        assertEquals("VIDEO", converters.fromDetectionSource(DetectionSource.VIDEO))
    }
}
