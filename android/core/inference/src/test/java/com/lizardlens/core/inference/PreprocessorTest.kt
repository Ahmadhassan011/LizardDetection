package com.lizardlens.core.inference

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class PreprocessorTest {

    private fun createTestBitmap(width: Int, height: Int, color: Int = 0xFFFF0000.toInt()): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply { this.color = color }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        return bitmap
    }

    @Test
    fun `preprocessNCHW returns correct flat size`() {
        val bitmap = createTestBitmap(200, 100)
        val inputSize = 416
        val result = Preprocessor.preprocessNCHW(bitmap, inputSize)
        assertEquals(inputSize * inputSize * 3, result.size)
        bitmap.recycle()
    }

    @Test
    fun `preprocessNCHW values are normalised to 0_1`() {
        val bitmap = createTestBitmap(100, 100)
        val result = Preprocessor.preprocessNCHW(bitmap, 416)
        assertTrue(result.all { it in 0.0f..1.0f })
        bitmap.recycle()
    }

    @Test
    fun `preprocessNCHW red pixel has correct channel values in planar layout`() {
        val bitmap = createTestBitmap(10, 10, 0xFFFF0000.toInt())
        val result = Preprocessor.preprocessNCHW(bitmap, 10)
        val planeSize = 10 * 10

        assertEquals(1.0f, result[0], 1e-5f)
        assertEquals(0.0f, result[planeSize], 1e-5f)
        assertEquals(0.0f, result[2 * planeSize], 1e-5f)
        bitmap.recycle()
    }

    @Test
    fun `preprocessNCHW white pixel has all channels at 1_0 in planar layout`() {
        val bitmap = createTestBitmap(10, 10, 0xFFFFFFFF.toInt())
        val result = Preprocessor.preprocessNCHW(bitmap, 10)
        val planeSize = 10 * 10

        assertEquals(1.0f, result[0], 1e-5f)
        assertEquals(1.0f, result[planeSize], 1e-5f)
        assertEquals(1.0f, result[2 * planeSize], 1e-5f)
        bitmap.recycle()
    }

    @Test
    fun `preprocessNCHW black pixel has all channels at 0_0 in planar layout`() {
        val bitmap = createTestBitmap(10, 10, 0xFF000000.toInt())
        val result = Preprocessor.preprocessNCHW(bitmap, 10)
        val planeSize = 10 * 10

        assertEquals(0.0f, result[0], 1e-5f)
        assertEquals(0.0f, result[planeSize], 1e-5f)
        assertEquals(0.0f, result[2 * planeSize], 1e-5f)
        bitmap.recycle()
    }

    @Test
    fun `getInputShapeNCHW returns NCHW format`() {
        val shape = Preprocessor.getInputShapeNCHW(416)
        assertEquals(4, shape.size)
        assertEquals(1, shape[0])
        assertEquals(3, shape[1])
        assertEquals(416, shape[2])
        assertEquals(416, shape[3])
    }

    @Test
    fun `preprocessNCHW non-square bitmap resizes correctly`() {
        val bitmap = createTestBitmap(320, 240)
        val result = Preprocessor.preprocessNCHW(bitmap, 416)
        assertEquals(416 * 416 * 3, result.size)
        bitmap.recycle()
    }
}
