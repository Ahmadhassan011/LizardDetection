package com.lizardlens.core.inference

data class InferenceConfig(
    val inputSize: Int = 416,
    val confidenceThreshold: Float = 0.45f,
    val iouThreshold: Float = 0.45f,
    val delegate: Delegate = Delegate.CPU
) {
    enum class Delegate {
        CPU,
        GPU,
        AUTO
    }
}
