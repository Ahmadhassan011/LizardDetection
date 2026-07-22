package com.lizardlens.core.inference

import android.graphics.Bitmap

object Preprocessor {

    fun preprocessNCHW(bitmap: Bitmap, inputSize: Int): FloatArray {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        val planeSize = inputSize * inputSize
        val floatArray = FloatArray(planeSize * 3)

        for (i in pixels.indices) {
            val pixel = pixels[i]
            floatArray[i] = ((pixel shr 16) and 0xFF) / 255.0f                      // R (plane 0)
            floatArray[planeSize + i] = ((pixel shr 8) and 0xFF) / 255.0f            // G (plane 1)
            floatArray[2 * planeSize + i] = (pixel and 0xFF) / 255.0f                // B (plane 2)
        }

        if (resized !== bitmap) {
            resized.recycle()
        }

        return floatArray
    }

    fun getInputShapeNCHW(inputSize: Int): IntArray = intArrayOf(1, 3, inputSize, inputSize)
}
