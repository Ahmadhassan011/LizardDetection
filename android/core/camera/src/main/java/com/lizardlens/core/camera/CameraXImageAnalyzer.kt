package com.lizardlens.core.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.lizardlens.core.logging.AppLogger

fun interface FrameProcessor {
    fun processFrame(bitmap: Bitmap)
}

class CameraXImageAnalyzer(
    private val frameProcessor: FrameProcessor,
    private val thermalManager: ThermalManager
) : ImageAnalysis.Analyzer {

    @Volatile
    var isActive: Boolean = false

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (!isActive) return

            if (thermalManager.shouldStopCamera()) {
                AppLogger.d("Frame skipped: camera stopped due to critical thermal level")
                return
            }

            if (thermalManager.shouldSkipCurrentFrame()) {
                AppLogger.d("Frame skipped: thermal throttling")
                return
            }

            val bitmap = imageProxyToBitmap(imageProxy)
            if (bitmap != null) {
                val resized = Bitmap.createScaledBitmap(
                    bitmap,
                    INPUT_SIZE,
                    INPUT_SIZE,
                    true
                )
                frameProcessor.processFrame(resized)
                resized.recycle()
                bitmap.recycle()
            }
        } catch (e: Exception) {
            AppLogger.e(e, "Error processing camera frame")
        } finally {
            imageProxy.close()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null

        val nv21 = YuvConverter.imageToNv21(image) ?: return null
        val bitmap = YuvConverter.nv21ToBitmap(nv21, image.width, image.height) ?: return null

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            rotated
        } else {
            bitmap
        }
    }

    companion object {
        const val INPUT_SIZE = 416
    }
}
