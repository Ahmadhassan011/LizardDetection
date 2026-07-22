package com.lizardlens.core.model

data class Detection(
    val boundingBox: BoundingBox,
    val confidence: Float,
    val label: String
)

data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float
)

enum class DetectionSource {
    CAMERA,
    IMAGE,
    VIDEO
}

data class DetectionResult(
    val detections: List<Detection>,
    val inferenceTimeMs: Long
)
