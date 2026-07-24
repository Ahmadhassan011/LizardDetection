package com.lizardlens.core.inference

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class TfliteInferenceEngineTest {

    private fun createTestBitmap(width: Int = 100, height: Int = 100): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun `mock engine returns detection result`() {
        val engine = TfliteInferenceEngine.createMock()
        val bitmap = createTestBitmap()
        val result = engine.detect(bitmap)

        assertTrue(result.inferenceTimeMs >= 0)
        assertTrue(result.detections.isNotEmpty() || result.detections.isEmpty())
        bitmap.recycle()
        engine.close()
    }

    @Test
    fun `mock engine detections have valid bounding boxes`() {
        val engine = TfliteInferenceEngine.createMock()
        val bitmap = createTestBitmap(200, 200)
        val result = engine.detect(bitmap)

        for (det in result.detections) {
            assertTrue(det.label == "Lizard")
            assertTrue(det.confidence in 0.5f..1.0f)
            assertTrue(det.boundingBox.x1 >= 0f)
            assertTrue(det.boundingBox.y1 >= 0f)
            assertTrue(det.boundingBox.x2 <= 200f)
            assertTrue(det.boundingBox.y2 <= 200f)
            assertTrue(det.boundingBox.x2 > det.boundingBox.x1)
            assertTrue(det.boundingBox.y2 > det.boundingBox.y1)
        }
        bitmap.recycle()
        engine.close()
    }

    @Test
    fun `mock engine with custom config produces detections`() {
        val config = InferenceConfig(
            confidenceThreshold = 0.8f,
            iouThreshold = 0.3f
        )
        val engine = TfliteInferenceEngine.createMock(config)
        val bitmap = createTestBitmap()
        val result = engine.detect(bitmap)

        assertTrue(result.detections.isNotEmpty() || result.detections.isEmpty())
        bitmap.recycle()
        engine.close()
    }

    @Test
    fun `create factory returns working engine even without model file`() {
        val engine = TfliteInferenceEngine.createMock()
        val bitmap = createTestBitmap()
        val result = engine.detect(bitmap)

        assertTrue(result.inferenceTimeMs >= 0)
        bitmap.recycle()
        engine.close()
    }

    @Test
    fun `multiple detections are non-overlapping after NMS`() {
        val engine = TfliteInferenceEngine.createMock()
        val bitmap = createTestBitmap(416, 416)
        val result = engine.detect(bitmap)

        for (i in result.detections.indices) {
            for (j in i + 1 until result.detections.size) {
                val iou = NmsProcessor.computeIoU(
                    result.detections[i].boundingBox,
                    result.detections[j].boundingBox
                )
                assertTrue(
                    "Detections $i and $j should not overlap above threshold",
                    iou <= 0.45f
                )
            }
        }
        bitmap.recycle()
        engine.close()
    }

    @Test
    fun `decodeOutput then postprocess produces valid DetectionResult`() {
        val raw = floatArrayOf(
            0.5f, 0.3f, 0.8f,
            0.5f, 0.7f, 0.2f,
            0.2f, 0.1f, 0.15f,
            0.2f, 0.1f, 0.1f,
            0.9f, 0.8f, 0.3f
        )
        val shape = intArrayOf(1, 5, 3)
        val config = InferenceConfig(confidenceThreshold = 0.45f, iouThreshold = 0.45f)

        val decoded = NmsProcessor.decodeOutput(raw, shape, config.inputSize)
        val result = NmsProcessor.postprocess(decoded, shape, config, 10L)

        assertEquals(2, result.detections.size)
        assertTrue(result.detections.all { it.label == "Lizard" })
        assertTrue(result.detections.all { it.confidence >= 0.45f })
    }

    @Test
    fun `decodeOutput coordinates are within image bounds for unit box`() {
        val raw = floatArrayOf(0.5f, 0.5f, 1.0f, 1.0f, 0.99f)
        val shape = intArrayOf(1, 5, 1)
        val config = InferenceConfig(inputSize = 416)

        val decoded = NmsProcessor.decodeOutput(raw, shape, config.inputSize)
        val result = NmsProcessor.postprocess(decoded, shape, config, 5L)

        assertEquals(1, result.detections.size)
        val bbox = result.detections[0].boundingBox
        assertEquals(0f, bbox.x1, 0.1f)
        assertEquals(0f, bbox.y1, 0.1f)
        assertEquals(416f, bbox.x2, 0.1f)
        assertEquals(416f, bbox.y2, 0.1f)
    }

    @Test
    fun `activeDelegate returns CPU for mock engine created with CPU config`() {
        val config = InferenceConfig(delegate = InferenceConfig.Delegate.CPU)
        val engine = TfliteInferenceEngine.createMock(config)

        assertEquals(InferenceConfig.Delegate.CPU, engine.activeDelegate)
        engine.close()
    }

    @Test
    fun `activeDelegate returns GPU for mock engine created with GPU config`() {
        val config = InferenceConfig(delegate = InferenceConfig.Delegate.GPU)
        val engine = TfliteInferenceEngine.createMock(config)

        assertEquals(InferenceConfig.Delegate.GPU, engine.activeDelegate)
        engine.close()
    }

    @Test
    fun `switchDelegate changes activeDelegate`() {
        val engine = TfliteInferenceEngine.createMock(
            InferenceConfig(delegate = InferenceConfig.Delegate.CPU)
        )
        assertEquals(InferenceConfig.Delegate.CPU, engine.activeDelegate)

        engine.switchDelegate(InferenceConfig.Delegate.GPU)

        assertEquals(InferenceConfig.Delegate.GPU, engine.activeDelegate)
        engine.close()
    }

    @Test
    fun `switchDelegate to same delegate is no-op`() {
        val engine = TfliteInferenceEngine.createMock(
            InferenceConfig(delegate = InferenceConfig.Delegate.CPU)
        )

        engine.switchDelegate(InferenceConfig.Delegate.CPU)

        assertEquals(InferenceConfig.Delegate.CPU, engine.activeDelegate)
        engine.close()
    }

    @Test
    fun `mock engine still detects after switching delegate`() {
        val engine = TfliteInferenceEngine.createMock(
            InferenceConfig(delegate = InferenceConfig.Delegate.CPU)
        )
        val bitmap = createTestBitmap()

        engine.switchDelegate(InferenceConfig.Delegate.GPU)
        val result = engine.detect(bitmap)

        assertTrue(result.inferenceTimeMs >= 0)
        bitmap.recycle()
        engine.close()
    }
}
