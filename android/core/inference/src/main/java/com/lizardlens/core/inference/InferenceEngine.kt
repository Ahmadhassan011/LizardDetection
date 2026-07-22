package com.lizardlens.core.inference

import android.graphics.Bitmap
import com.lizardlens.core.model.DetectionResult

interface InferenceEngine {
    fun detect(bitmap: Bitmap): DetectionResult
    fun close()
}
