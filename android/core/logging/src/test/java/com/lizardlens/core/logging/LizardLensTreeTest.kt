package com.lizardlens.core.logging

import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26])
class LizardLensTreeTest {

    @Test
    fun `debug tree logs with custom tag prefix`() {
        val tree = LizardLensTree(isDebug = true)
        ShadowLog.clear()

        tree.d("Test message")

        val logs = ShadowLog.getLogs()
        assertTrue(logs.isNotEmpty())
        val lastLog = logs.last()
        assertEquals(Log.DEBUG, lastLog.type)
        assertTrue(lastLog.tag.startsWith("LL/"))
        assertEquals("Test message", lastLog.msg)
    }

    @Test
    fun `debug tree includes caller info in tag`() {
        val tree = LizardLensTree(isDebug = true)
        ShadowLog.clear()

        tree.i("Info message")

        val logs = ShadowLog.getLogs()
        val lastLog = logs.last()
        assertEquals(Log.INFO, lastLog.type)
        assertTrue(lastLog.tag.startsWith("LL/"))
    }

    @Test
    fun `release tree logs warning and above`() {
        val tree = LizardLensTree(isDebug = false)
        ShadowLog.clear()

        tree.w("Warning message")
        tree.e("Error message")

        val logs = ShadowLog.getLogs()
        assertEquals(2, logs.size)
        assertEquals(Log.WARN, logs[0].type)
        assertEquals(Log.ERROR, logs[1].type)
    }

    @Test
    fun `release tree suppresses debug and verbose`() {
        val tree = LizardLensTree(isDebug = false)
        ShadowLog.clear()

        tree.v("Verbose")
        tree.d("Debug")

        val logs = ShadowLog.getLogs()
        assertTrue(logs.isEmpty())
    }

    @Test
    fun `release tree strips tag prefix`() {
        val tree = LizardLensTree(isDebug = false)
        ShadowLog.clear()

        tree.w("Warn message")

        val logs = ShadowLog.getLogs()
        val lastLog = logs.last()
        assertTrue(!lastLog.tag.startsWith("LL/"))
    }

    @Test
    fun `debug tree with throwable logs stack trace`() {
        val tree = LizardLensTree(isDebug = true)
        ShadowLog.clear()

        val exception = RuntimeException("test error")
        tree.e(exception, "Error occurred")

        val logs = ShadowLog.getLogs()
        val lastLog = logs.last()
        assertEquals(Log.ERROR, lastLog.type)
        assertTrue(lastLog.msg.contains("Error occurred"))
        assertTrue(lastLog.msg.contains("test error"))
    }
}
