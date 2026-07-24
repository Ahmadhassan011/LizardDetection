package com.lizardlens.core.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import com.lizardlens.core.logging.AppLogger
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object YuvConverter {

    private const val JPEG_QUALITY = 95

    fun imageToNv21(image: Image): ByteArray? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            yuv420ToNv21(
                yBuffer = yBuffer,
                uBuffer = uBuffer,
                vBuffer = vBuffer,
                width = image.width,
                height = image.height,
                yRowStride = image.planes[0].rowStride,
                uvRowStride = image.planes[1].rowStride,
                uvPixelStride = image.planes[1].pixelStride
            )
        } catch (e: Exception) {
            AppLogger.e(e, "Failed to convert Image to NV21")
            null
        }
    }

    fun yuv420ToNv21(
        yBuffer: ByteBuffer,
        uBuffer: ByteBuffer,
        vBuffer: ByteBuffer,
        width: Int,
        height: Int,
        yRowStride: Int,
        uvRowStride: Int,
        uvPixelStride: Int
    ): ByteArray? {
        return try {
            val nv21 = ByteArray(width * height * 3 / 2)
            var pos = 0

            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, pos, width)
                pos += width
            }

            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val uvIndex = row * uvRowStride + col * uvPixelStride
                    nv21[pos++] = vBuffer.get(uvIndex)
                    nv21[pos++] = uBuffer.get(uvIndex)
                }
            }

            nv21
        } catch (e: Exception) {
            AppLogger.e(e, "Failed to convert YUV420 to NV21")
            null
        }
    }

    fun nv21ToBitmap(nv21: ByteArray, width: Int, height: Int): Bitmap? {
        if (nv21.isEmpty() || nv21.size < width * height * 3 / 2) return null

        return try {
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, width, height), JPEG_QUALITY, out)
            val jpegBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
        } catch (e: Exception) {
            AppLogger.e(e, "Failed to decode NV21 to Bitmap")
            null
        }
    }
}
