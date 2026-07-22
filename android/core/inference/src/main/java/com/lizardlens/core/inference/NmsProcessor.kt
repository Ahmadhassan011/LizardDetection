package com.lizardlens.core.inference

import com.lizardlens.core.model.BoundingBox
import com.lizardlens.core.model.Detection
import com.lizardlens.core.model.DetectionResult

object NmsProcessor {

    fun computeIoU(a: BoundingBox, b: BoundingBox): Float {
        val interX1 = maxOf(a.x1, b.x1)
        val interY1 = maxOf(a.y1, b.y1)
        val interX2 = minOf(a.x2, b.x2)
        val interY2 = minOf(a.y2, b.y2)

        val interArea = maxOf(0f, interX2 - interX1) * maxOf(0f, interY2 - interY1)
        val areaA = (a.x2 - a.x1) * (a.y2 - a.y1)
        val areaB = (b.x2 - b.x1) * (b.y2 - b.y1)
        val unionArea = areaA + areaB - interArea

        return if (unionArea > 0f) interArea / unionArea else 0f
    }

    fun applyNMS(
        detections: List<Detection>,
        iouThreshold: Float
    ): List<Detection> {
        if (detections.isEmpty()) return emptyList()

        val sorted = detections.sortedByDescending { it.confidence }
        val retained = mutableListOf<Detection>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            retained.add(sorted[i])

            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (computeIoU(sorted[i].boundingBox, sorted[j].boundingBox) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }

        return retained
    }

    fun parseOutput(
        output: FloatArray,
        outputShape: IntArray,
        confidenceThreshold: Float,
        iouThreshold: Float
    ): List<Detection> {
        require(outputShape.size == 3) { "Expected 3D output shape [batch, features, anchors], got ${outputShape.size}D" }

        val features = outputShape[1]
        val anchors = outputShape[2]

        require(features == 5) { "Expected 5 features per anchor (x1, y1, x2, y2, confidence), got $features" }

        val candidates = mutableListOf<Detection>()

        for (a in 0 until anchors) {
            val offset = a * features
            if (offset + 4 >= output.size) break

            val x1 = output[offset]
            val y1 = output[offset + 1]
            val x2 = output[offset + 2]
            val y2 = output[offset + 3]
            val confidence = output[offset + 4]

            if (confidence < confidenceThreshold) continue
            if (x2 <= x1 || y2 <= y1) continue

            candidates.add(
                Detection(
                    boundingBox = BoundingBox(x1, y1, x2, y2),
                    confidence = confidence,
                    label = "Lizard"
                )
            )
        }

        return applyNMS(candidates, iouThreshold)
    }

    fun postprocess(
        rawOutput: FloatArray,
        outputShape: IntArray,
        config: InferenceConfig,
        inferenceTimeMs: Long
    ): DetectionResult {
        val detections = parseOutput(
            output = rawOutput,
            outputShape = outputShape,
            confidenceThreshold = config.confidenceThreshold,
            iouThreshold = config.iouThreshold
        )

        return DetectionResult(
            detections = detections,
            inferenceTimeMs = inferenceTimeMs
        )
    }

    fun decodeOutput(
        featureMajorRaw: FloatArray,
        outputShape: IntArray,
        inputSize: Int
    ): FloatArray {
        require(outputShape.size == 3) { "Expected 3D output shape, got ${outputShape.size}D" }

        val features = outputShape[1]
        val anchors = outputShape[2]

        require(features == 5) { "Expected 5 features (cx, cy, w, h, conf), got $features" }
        if (anchors == 0) return floatArrayOf()

        val decoded = FloatArray(anchors * features)

        for (a in 0 until anchors) {
            val cx = featureMajorRaw[0 * anchors + a]
            val cy = featureMajorRaw[1 * anchors + a]
            val w = featureMajorRaw[2 * anchors + a]
            val h = featureMajorRaw[3 * anchors + a]
            val conf = featureMajorRaw[4 * anchors + a]

            decoded[a * features + 0] = (cx - w / 2f) * inputSize
            decoded[a * features + 1] = (cy - h / 2f) * inputSize
            decoded[a * features + 2] = (cx + w / 2f) * inputSize
            decoded[a * features + 3] = (cy + h / 2f) * inputSize
            decoded[a * features + 4] = conf
        }

        return decoded
    }
}
