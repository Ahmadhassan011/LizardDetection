package com.lizardlens.core.camera

import com.lizardlens.core.model.DetectionResult

fun interface DetectionAnalyzer {
    fun analyze(frame: ByteArray, width: Int, height: Int): DetectionResult
}
