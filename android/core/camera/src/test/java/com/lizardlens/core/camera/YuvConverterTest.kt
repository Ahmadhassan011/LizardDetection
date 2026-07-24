package com.lizardlens.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class YuvConverterTest {

    private val width = 320
    private val height = 240

    private fun createNv21ByteArray(): ByteArray {
        val nv21 = ByteArray(width * height * 3 / 2)
        for (i in 0 until width * height) {
            nv21[i] = 128.toByte()
        }
        for (i in width * height until nv21.size step 2) {
            nv21[i] = 128.toByte()
            nv21[i + 1] = 128.toByte()
        }
        return nv21
    }

    private data class YuvBuffers(
        val yBuffer: ByteBuffer,
        val uBuffer: ByteBuffer,
        val vBuffer: ByteBuffer
    )

    private fun createYuv420Buffers(
        imgWidth: Int = width,
        imgHeight: Int = height,
        yValue: Byte = 128.toByte(),
        uValue: Byte = 128.toByte(),
        vValue: Byte = 128.toByte()
    ): YuvBuffers {
        val ySize = imgWidth * imgHeight
        val uvSize = imgWidth * imgHeight / 2

        val yBuffer = ByteBuffer.allocateDirect(ySize)
        val uBuffer = ByteBuffer.allocateDirect(uvSize / 2)
        val vBuffer = ByteBuffer.allocateDirect(uvSize / 2)

        for (i in 0 until ySize) yBuffer.put(yValue)
        for (i in 0 until uvSize / 2) {
            uBuffer.put(uValue)
            vBuffer.put(vValue)
        }
        yBuffer.flip()
        uBuffer.flip()
        vBuffer.flip()

        return YuvBuffers(yBuffer, uBuffer, vBuffer)
    }

    @Test
    fun `nv21ToBitmap returns non-null for valid NV21 data`() {
        val nv21 = createNv21ByteArray()
        val bitmap = YuvConverter.nv21ToBitmap(nv21, width, height)
        assertNotNull(bitmap)
        bitmap!!.recycle()
    }

    @Test
    fun `nv21ToBitmap returns null for empty data`() {
        val bitmap = YuvConverter.nv21ToBitmap(byteArrayOf(), width, height)
        assertNull(bitmap)
    }

    @Test
    fun `nv21ToBitmap returns null for undersized data`() {
        val tiny = ByteArray(10)
        val bitmap = YuvConverter.nv21ToBitmap(tiny, width, height)
        assertNull(bitmap)
    }

    @Test
    fun `nv21ToBitmap produces non-null bitmap from uniform NV21`() {
        val nv21 = createNv21ByteArray()
        val bitmap = YuvConverter.nv21ToBitmap(nv21, width, height)
        assertNotNull(bitmap)
        assertNotNull(bitmap!!.config)
        bitmap.recycle()
    }

    @Test
    fun `yuv420ToNv21 returns correct size array`() {
        val buffers = createYuv420Buffers()
        val nv21 = YuvConverter.yuv420ToNv21(
            yBuffer = buffers.yBuffer,
            uBuffer = buffers.uBuffer,
            vBuffer = buffers.vBuffer,
            width = width,
            height = height,
            yRowStride = width,
            uvRowStride = width / 2,
            uvPixelStride = 1
        )!!
        assertEquals(width * height * 3 / 2, nv21.size)
    }

    @Test
    fun `yuv420ToNv21 copies Y plane correctly`() {
        val buffers = createYuv420Buffers()
        val nv21 = YuvConverter.yuv420ToNv21(
            yBuffer = buffers.yBuffer,
            uBuffer = buffers.uBuffer,
            vBuffer = buffers.vBuffer,
            width = width,
            height = height,
            yRowStride = width,
            uvRowStride = width / 2,
            uvPixelStride = 1
        )!!

        for (i in 0 until width * height) {
            assertEquals(128.toByte(), nv21[i])
        }
    }

    @Test
    fun `yuv420ToNv21 interleaves V and U planes correctly`() {
        val buffers = createYuv420Buffers()
        val nv21 = YuvConverter.yuv420ToNv21(
            yBuffer = buffers.yBuffer,
            uBuffer = buffers.uBuffer,
            vBuffer = buffers.vBuffer,
            width = width,
            height = height,
            yRowStride = width,
            uvRowStride = width / 2,
            uvPixelStride = 1
        )!!

        val ySize = width * height
        for (i in ySize until nv21.size step 2) {
            assertEquals(128.toByte(), nv21[i])
            assertEquals(128.toByte(), nv21[i + 1])
        }
    }

    @Test
    fun `yuv420ToNv21 handles different dimensions`() {
        val w = 640
        val h = 480
        val buffers = createYuv420Buffers(imgWidth = w, imgHeight = h)
        val nv21 = YuvConverter.yuv420ToNv21(
            yBuffer = buffers.yBuffer,
            uBuffer = buffers.uBuffer,
            vBuffer = buffers.vBuffer,
            width = w,
            height = h,
            yRowStride = w,
            uvRowStride = w / 2,
            uvPixelStride = 1
        )!!
        assertEquals(w * h * 3 / 2, nv21.size)
    }

    @Test
    fun `yuv420ToNv21 with row padding skips padding bytes`() {
        val paddedWidth = 160
        val paddedHeight = 120
        val rowStride = 200

        val yBuffer = ByteBuffer.allocateDirect(rowStride * paddedHeight)
        val uBuffer = ByteBuffer.allocateDirect(rowStride * paddedHeight / 2)
        val vBuffer = ByteBuffer.allocateDirect(rowStride * paddedHeight / 2)

        for (row in 0 until paddedHeight) {
            val rowStart = row * rowStride
            for (col in 0 until paddedWidth) {
                yBuffer.put(rowStart + col, 100.toByte())
            }
        }
        for (row in 0 until paddedHeight / 2) {
            val rowStart = row * rowStride
            for (col in 0 until paddedWidth / 2) {
                uBuffer.put(rowStart + col, 50.toByte())
                vBuffer.put(rowStart + col, 75.toByte())
            }
        }
        yBuffer.flip()
        yBuffer.limit(yBuffer.capacity())
        yBuffer.position(0)
        uBuffer.flip()
        uBuffer.limit(uBuffer.capacity())
        uBuffer.position(0)
        vBuffer.flip()
        vBuffer.limit(vBuffer.capacity())
        vBuffer.position(0)

        val nv21 = YuvConverter.yuv420ToNv21(
            yBuffer = yBuffer,
            uBuffer = uBuffer,
            vBuffer = vBuffer,
            width = paddedWidth,
            height = paddedHeight,
            yRowStride = rowStride,
            uvRowStride = rowStride,
            uvPixelStride = 1
        )!!

        assertEquals(paddedWidth * paddedHeight * 3 / 2, nv21.size)

        for (i in 0 until paddedWidth) {
            assertEquals(100.toByte(), nv21[i])
        }

        val ySize = paddedWidth * paddedHeight
        for (i in ySize until nv21.size step 2) {
            assertEquals(75.toByte(), nv21[i])
            assertEquals(50.toByte(), nv21[i + 1])
        }
    }

    @Test
    fun `yuv420ToNv21 with pixelStride 2 reads from shared buffer`() {
        val w = 160
        val h = 120

        val yBuffer = ByteBuffer.allocateDirect(w * h)
        val uvBuffer = ByteBuffer.allocateDirect(w * h)

        for (i in 0 until w * h) yBuffer.put(42.toByte())
        for (i in 0 until w * h) uvBuffer.put(i, (i % 256).toByte())
        yBuffer.flip()
        yBuffer.limit(yBuffer.capacity())
        yBuffer.position(0)
        uvBuffer.flip()
        uvBuffer.limit(uvBuffer.capacity())
        uvBuffer.position(0)

        val nv21 = YuvConverter.yuv420ToNv21(
            yBuffer = yBuffer,
            uBuffer = uvBuffer,
            vBuffer = uvBuffer,
            width = w,
            height = h,
            yRowStride = w,
            uvRowStride = w,
            uvPixelStride = 2
        )!!

        assertEquals(w * h * 3 / 2, nv21.size)

        for (i in 0 until w * h) {
            assertEquals(42.toByte(), nv21[i])
        }

        val ySize = w * h
        for (i in ySize until nv21.size step 2) {
            val uvIndex = ((i - ySize) / 2) * 2
            assertEquals(uvBuffer.get(uvIndex), nv21[i])
            assertEquals(uvBuffer.get(uvIndex), nv21[i + 1])
        }
    }

    @Test
    fun `full pipeline yuv420ToNv21 then nv21ToBitmap produces non-null bitmap`() {
        val buffers = createYuv420Buffers()
        val nv21 = YuvConverter.yuv420ToNv21(
            yBuffer = buffers.yBuffer,
            uBuffer = buffers.uBuffer,
            vBuffer = buffers.vBuffer,
            width = width,
            height = height,
            yRowStride = width,
            uvRowStride = width / 2,
            uvPixelStride = 1
        )!!
        val bitmap = YuvConverter.nv21ToBitmap(nv21, width, height)
        assertNotNull(bitmap)
        bitmap!!.recycle()
    }

    @Test
    fun `yuv420ToNv21 zero dimensions returns empty array`() {
        val nv21 = YuvConverter.yuv420ToNv21(
            yBuffer = ByteBuffer.allocateDirect(0),
            uBuffer = ByteBuffer.allocateDirect(0),
            vBuffer = ByteBuffer.allocateDirect(0),
            width = 0,
            height = 0,
            yRowStride = 0,
            uvRowStride = 0,
            uvPixelStride = 1
        )!!
        assertEquals(0, nv21.size)
    }
}
