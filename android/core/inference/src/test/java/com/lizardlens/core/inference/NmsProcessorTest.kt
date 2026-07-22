package com.lizardlens.core.inference

import com.lizardlens.core.model.BoundingBox
import com.lizardlens.core.model.Detection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NmsProcessorTest {

    // ── IoU Tests ──────────────────────────────────────────────

    @Test
    fun `IoU of identical boxes is 1_0`() {
        val box = BoundingBox(10f, 10f, 50f, 50f)
        assertEquals(1.0f, NmsProcessor.computeIoU(box, box), 1e-5f)
    }

    @Test
    fun `IoU of non-overlapping boxes is 0_0`() {
        val a = BoundingBox(0f, 0f, 10f, 10f)
        val b = BoundingBox(20f, 20f, 30f, 30f)
        assertEquals(0.0f, NmsProcessor.computeIoU(a, b), 1e-5f)
    }

    @Test
    fun `IoU of half-overlapping boxes is correct`() {
        //  A: [0,0]-[10,10], B: [5,0]-[15,10]
        //  intersection = 5*10 = 50, union = 100+100-50 = 150
        val a = BoundingBox(0f, 0f, 10f, 10f)
        val b = BoundingBox(5f, 0f, 15f, 10f)
        assertEquals(50f / 150f, NmsProcessor.computeIoU(a, b), 1e-5f)
    }

    @Test
    fun `IoU of touching boxes is 0_0`() {
        val a = BoundingBox(0f, 0f, 10f, 10f)
        val b = BoundingBox(10f, 0f, 20f, 10f)
        assertEquals(0.0f, NmsProcessor.computeIoU(a, b), 1e-5f)
    }

    @Test
    fun `IoU of fully contained box is less than 1`() {
        // outer: [0,0]-[20,20], inner: [5,5]-[15,15]
        // intersection = 10*10=100, union = 400+100-100=400
        val outer = BoundingBox(0f, 0f, 20f, 20f)
        val inner = BoundingBox(5f, 5f, 15f, 15f)
        assertEquals(100f / 400f, NmsProcessor.computeIoU(outer, inner), 1e-5f)
    }

    // ── NMS Tests ──────────────────────────────────────────────

    @Test
    fun `NMS with empty list returns empty`() {
        val result = NmsProcessor.applyNMS(emptyList(), 0.5f)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `NMS with single detection returns it`() {
        val det = Detection(BoundingBox(10f, 10f, 50f, 50f), 0.9f, "Lizard")
        val result = NmsProcessor.applyNMS(listOf(det), 0.5f)
        assertEquals(1, result.size)
        assertEquals(0.9f, result[0].confidence, 1e-5f)
    }

    @Test
    fun `NMS suppresses high-overlap lower-confidence box`() {
        val high = Detection(BoundingBox(10f, 10f, 50f, 50f), 0.9f, "Lizard")
        val low = Detection(BoundingBox(12f, 12f, 52f, 52f), 0.7f, "Lizard")
        val result = NmsProcessor.applyNMS(listOf(low, high), 0.5f)
        assertEquals(1, result.size)
        assertEquals(0.9f, result[0].confidence, 1e-5f)
    }

    @Test
    fun `NMS keeps non-overlapping detections`() {
        val a = Detection(BoundingBox(0f, 0f, 30f, 30f), 0.9f, "Lizard")
        val b = Detection(BoundingBox(100f, 100f, 130f, 130f), 0.8f, "Lizard")
        val result = NmsProcessor.applyNMS(listOf(a, b), 0.5f)
        assertEquals(2, result.size)
    }

    @Test
    fun `NMS with golden fixture — three overlapping boxes keeps highest`() {
        // Three boxes with high overlap, threshold 0.45
        val boxes = listOf(
            Detection(BoundingBox(10f, 10f, 60f, 60f), 0.95f, "Lizard"),
            Detection(BoundingBox(12f, 12f, 62f, 62f), 0.85f, "Lizard"),
            Detection(BoundingBox(14f, 14f, 64f, 64f), 0.75f, "Lizard")
        )
        val result = NmsProcessor.applyNMS(boxes, 0.45f)
        assertEquals(1, result.size)
        assertEquals(0.95f, result[0].confidence, 1e-5f)
    }

    @Test
    fun `NMS with golden fixture — two clusters keeps one per cluster`() {
        // Cluster 1: high overlap around (0,0)
        // Cluster 2: high overlap around (200,200)
        val boxes = listOf(
            Detection(BoundingBox(0f, 0f, 50f, 50f), 0.9f, "Lizard"),
            Detection(BoundingBox(5f, 5f, 55f, 55f), 0.8f, "Lizard"),
            Detection(BoundingBox(200f, 200f, 250f, 250f), 0.85f, "Lizard"),
            Detection(BoundingBox(205f, 205f, 255f, 255f), 0.7f, "Lizard")
        )
        val result = NmsProcessor.applyNMS(boxes, 0.45f)
        assertEquals(2, result.size)
        assertTrue(result.any { it.confidence == 0.9f })
        assertTrue(result.any { it.confidence == 0.85f })
    }

    // ── Output Parsing Tests ───────────────────────────────────

    @Test
    fun `parseOutput returns empty for all-below-threshold`() {
        // 3 anchors, all with confidence 0.1 (below 0.45 threshold)
        val output = floatArrayOf(
            10f, 10f, 50f, 50f, 0.1f,
            20f, 20f, 60f, 60f, 0.1f,
            30f, 30f, 70f, 70f, 0.1f
        )
        val shape = intArrayOf(1, 5, 3)
        val result = NmsProcessor.parseOutput(output, shape, 0.45f, 0.45f)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `parseOutput returns detections above threshold`() {
        val output = floatArrayOf(
            10f, 10f, 50f, 50f, 0.9f,
            20f, 20f, 60f, 60f, 0.3f,
            30f, 30f, 70f, 70f, 0.8f
        )
        val shape = intArrayOf(1, 5, 3)
        val result = NmsProcessor.parseOutput(output, shape, 0.45f, 0.45f)
        assertEquals(2, result.size)
        assertTrue(result.any { it.confidence == 0.9f })
        assertTrue(result.any { it.confidence == 0.8f })
    }

    @Test
    fun `parseOutput golden fixture — 8400 anchors with 2 true detections`() {
        // Simulate a realistic output: 8400 anchors, 2 with high confidence
        val anchors = 8400
        val output = FloatArray(5 * anchors)

        // Anchor 100: high confidence detection
        val idx1 = 100 * 5
        output[idx1] = 50f     // x1
        output[idx1 + 1] = 60f // y1
        output[idx1 + 2] = 150f // x2
        output[idx1 + 3] = 160f // y2
        output[idx1 + 4] = 0.92f // confidence

        // Anchor 5000: high confidence detection (non-overlapping with 100)
        val idx2 = 5000 * 5
        output[idx2] = 300f
        output[idx2 + 1] = 300f
        output[idx2 + 2] = 400f
        output[idx2 + 3] = 400f
        output[idx2 + 4] = 0.87f

        val shape = intArrayOf(1, 5, anchors)
        val result = NmsProcessor.parseOutput(output, shape, 0.45f, 0.45f)

        assertEquals(2, result.size)
        assertTrue(result.any { it.confidence == 0.92f && it.label == "Lizard" })
        assertTrue(result.any { it.confidence == 0.87f && it.label == "Lizard" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseOutput rejects wrong feature count`() {
        val output = FloatArray(4 * 3) // 4 features instead of 5
        val shape = intArrayOf(1, 4, 3)
        NmsProcessor.parseOutput(output, shape, 0.45f, 0.45f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseOutput rejects wrong dimensions`() {
        val output = FloatArray(15)
        val shape = intArrayOf(1, 5) // 2D instead of 3D
        NmsProcessor.parseOutput(output, shape, 0.45f, 0.45f)
    }

    // ── decodeOutput Tests (feature-major → anchor-major + cx,cy,w,h → x1,y1,x2,y2) ──

    @Test
    fun `decodeOutput transposes feature-major to anchor-major`() {
        // 2 anchors, 5 features. Feature-major layout:
        // [cx0, cx1, cy0, cy1, w0, w1, h0, h1, conf0, conf1]
        val raw = floatArrayOf(
            0.5f, 0.3f,   // cx: anchor0=0.5, anchor1=0.3
            0.5f, 0.7f,   // cy: anchor0=0.5, anchor1=0.7
            0.2f, 0.1f,   // w:  anchor0=0.2, anchor1=0.1
            0.2f, 0.1f,   // h:  anchor0=0.2, anchor1=0.1
            0.9f, 0.8f    // conf: anchor0=0.9, anchor1=0.8
        )
        val shape = intArrayOf(1, 5, 2)
        val inputSize = 416

        // After decode: anchor-major [cx0,cy0,w0,h0,conf0, cx1,cy1,w1,h1,conf1]
        // then converted to x1,y1,x2,y2,conf
        val decoded = NmsProcessor.decodeOutput(raw, shape, inputSize)

        // anchor0: cx=0.5, cy=0.5, w=0.2, h=0.2, conf=0.9
        //   x1 = (0.5-0.1)*416 = 0.4*416 = 166.4
        //   y1 = (0.5-0.1)*416 = 166.4
        //   x2 = (0.5+0.1)*416 = 0.6*416 = 249.6
        //   y2 = (0.5+0.1)*416 = 249.6
        assertEquals(10, decoded.size) // 2 anchors * 5 features
        assertEquals(166.4f, decoded[0], 0.1f) // x1
        assertEquals(166.4f, decoded[1], 0.1f) // y1
        assertEquals(249.6f, decoded[2], 0.1f) // x2
        assertEquals(249.6f, decoded[3], 0.1f) // y2
        assertEquals(0.9f, decoded[4], 1e-5f)  // conf

        // anchor1: cx=0.3, cy=0.7, w=0.1, h=0.1, conf=0.8
        assertEquals(104.0f, decoded[5], 0.1f) // x1 = (0.3-0.05)*416
        assertEquals(270.4f, decoded[6], 0.1f) // y1 = (0.7-0.05)*416
        assertEquals(145.6f, decoded[7], 0.1f) // x2 = (0.3+0.05)*416
        assertEquals(312.0f, decoded[8], 0.1f) // y2 = (0.7+0.05)*416
        assertEquals(0.8f, decoded[9], 1e-5f)  // conf
    }

    @Test
    fun `decodeOutput with golden fixture — real model shape 3549 anchors`() {
        val anchors = 3549
        val features = 5
        val raw = FloatArray(features * anchors)

        // Set anchor 100: cx=0.2, cy=0.3, w=0.1, h=0.15, conf=0.92
        raw[0 * anchors + 100] = 0.2f   // cx
        raw[1 * anchors + 100] = 0.3f   // cy
        raw[2 * anchors + 100] = 0.1f   // w
        raw[3 * anchors + 100] = 0.15f  // h
        raw[4 * anchors + 100] = 0.92f  // conf

        // Set anchor 2000: cx=0.8, cy=0.7, w=0.05, h=0.08, conf=0.87
        raw[0 * anchors + 2000] = 0.8f
        raw[1 * anchors + 2000] = 0.7f
        raw[2 * anchors + 2000] = 0.05f
        raw[3 * anchors + 2000] = 0.08f
        raw[4 * anchors + 2000] = 0.87f

        val shape = intArrayOf(1, features, anchors)
        val inputSize = 416
        val decoded = NmsProcessor.decodeOutput(raw, shape, inputSize)

        // anchor 100: cx=0.2, cy=0.3, w=0.1, h=0.15
        //   x1 = (0.2-0.05)*416 = 62.4
        //   y1 = (0.3-0.075)*416 = 93.6
        //   x2 = (0.2+0.05)*416 = 104.0
        //   y2 = (0.3+0.075)*416 = 156.0
        val offset100 = 100 * 5
        assertEquals(62.4f, decoded[offset100], 0.1f)     // x1
        assertEquals(93.6f, decoded[offset100 + 1], 0.1f) // y1
        assertEquals(104.0f, decoded[offset100 + 2], 0.1f) // x2
        assertEquals(156.0f, decoded[offset100 + 3], 0.1f) // y2
        assertEquals(0.92f, decoded[offset100 + 4], 1e-5f) // conf

        // anchor 2000: cx=0.8, cy=0.7, w=0.05, h=0.08
        val offset2000 = 2000 * 5
        assertEquals(0.8f - 0.025f, decoded[offset2000] / 416f, 1e-3f)
        assertEquals(0.87f, decoded[offset2000 + 4], 1e-5f)
    }

    @Test
    fun `decodeOutput returns empty for zero anchors`() {
        val raw = floatArrayOf()
        val shape = intArrayOf(1, 5, 0)
        val decoded = NmsProcessor.decodeOutput(raw, shape, 416)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `decodeOutput produces valid boxes for full-image detection`() {
        // Anchor covering nearly the full image: cx=0.5, cy=0.5, w=1.0, h=1.0
        val raw = floatArrayOf(
            0.5f,  // cx
            0.5f,  // cy
            1.0f,  // w
            1.0f,  // h
            0.95f  // conf
        )
        val shape = intArrayOf(1, 5, 1)
        val decoded = NmsProcessor.decodeOutput(raw, shape, 416)

        // x1 = (0.5-0.5)*416 = 0, y1 = 0, x2 = (0.5+0.5)*416 = 416, y2 = 416
        assertEquals(0f, decoded[0], 0.1f)
        assertEquals(0f, decoded[1], 0.1f)
        assertEquals(416f, decoded[2], 0.1f)
        assertEquals(416f, decoded[3], 0.1f)
        assertEquals(0.95f, decoded[4], 1e-5f)
    }

    @Test
    fun `decodeOutput then postprocess filters low confidence and applies NMS`() {
        // 3 anchors: two high-confidence overlapping + one low-confidence
        val raw = floatArrayOf(
            // anchor 0: high conf, center
            0.5f, 0.5f, 0.2f, 0.2f, 0.9f,
            // anchor 1: high conf, nearly same position (will be NMS'd)
            0.51f, 0.51f, 0.2f, 0.2f, 0.85f,
            // anchor 2: low conf (below threshold)
            0.8f, 0.8f, 0.1f, 0.1f, 0.2f
        ).let { flat ->
            // Rearrange from anchor-major to feature-major
            val result = FloatArray(15)
            for (a in 0 until 3) {
                for (f in 0 until 5) {
                    result[f * 3 + a] = flat[a * 5 + f]
                }
            }
            result
        }
        val shape = intArrayOf(1, 5, 3)
        val config = InferenceConfig(confidenceThreshold = 0.45f, iouThreshold = 0.45f)
        val decoded = NmsProcessor.decodeOutput(raw, shape, 416)
        val result = NmsProcessor.postprocess(decoded, shape, config, 10L)

        // Should keep only anchor 0 (highest conf), suppress anchor 1 (high overlap), drop anchor 2 (low conf)
        assertEquals(1, result.detections.size)
        assertEquals(0.9f, result.detections[0].confidence, 1e-5f)
    }
}
