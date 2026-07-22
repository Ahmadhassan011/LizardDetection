package com.lizardlens.core.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThermalLevelTest {

    @Test
    fun `NORMAL level never skips frames`() {
        assertFalse(ThermalLevel.NORMAL.shouldSkipFrame(0))
        assertFalse(ThermalLevel.NORMAL.shouldSkipFrame(1))
        assertFalse(ThermalLevel.NORMAL.shouldSkipFrame(2))
        assertFalse(ThermalLevel.NORMAL.shouldSkipFrame(100))
    }

    @Test
    fun `MODERATE level never skips frames`() {
        assertFalse(ThermalLevel.MODERATE.shouldSkipFrame(0))
        assertFalse(ThermalLevel.MODERATE.shouldSkipFrame(1))
        assertFalse(ThermalLevel.MODERATE.shouldSkipFrame(2))
        assertFalse(ThermalLevel.MODERATE.shouldSkipFrame(100))
    }

    @Test
    fun `SEVERE level skips frames at non-zero mod 3 positions`() {
        assertFalse(ThermalLevel.SEVERE.shouldSkipFrame(0))
        assertTrue(ThermalLevel.SEVERE.shouldSkipFrame(1))
        assertTrue(ThermalLevel.SEVERE.shouldSkipFrame(2))
        assertFalse(ThermalLevel.SEVERE.shouldSkipFrame(3))
        assertTrue(ThermalLevel.SEVERE.shouldSkipFrame(4))
        assertTrue(ThermalLevel.SEVERE.shouldSkipFrame(5))
        assertFalse(ThermalLevel.SEVERE.shouldSkipFrame(6))
    }

    @Test
    fun `CRITICAL level always skips frames`() {
        assertTrue(ThermalLevel.CRITICAL.shouldSkipFrame(0))
        assertTrue(ThermalLevel.CRITICAL.shouldSkipFrame(1))
        assertTrue(ThermalLevel.CRITICAL.shouldSkipFrame(100))
    }

    @Test
    fun `NORMAL level has no banner message`() {
        assertNull(ThermalLevel.NORMAL.bannerMessage)
    }

    @Test
    fun `MODERATE level has warming banner`() {
        assertEquals("Device warming \u2014 reduce usage", ThermalLevel.MODERATE.bannerMessage)
    }

    @Test
    fun `SEVERE level has throttled banner`() {
        assertEquals("Throttled \u2014 3 FPS", ThermalLevel.SEVERE.bannerMessage)
    }

    @Test
    fun `CRITICAL level has cooling banner`() {
        assertEquals("Cooling down", ThermalLevel.CRITICAL.bannerMessage)
    }
}

class ThermalManagerMapStatusTest {

    companion object {
        private const val THERMAL_STATUS_NONE = 0
        private const val THERMAL_STATUS_LIGHT = 1
        private const val THERMAL_STATUS_MODERATE = 2
        private const val THERMAL_STATUS_SEVERE = 3
        private const val THERMAL_STATUS_CRITICAL = 4
        private const val THERMAL_STATUS_EMERGENCY = 5
        private const val THERMAL_STATUS_SHUTDOWN = 6
    }

    @Test
    fun `NONE maps to NORMAL`() {
        assertEquals(ThermalLevel.NORMAL, ThermalManager.mapThermalStatus(THERMAL_STATUS_NONE))
    }

    @Test
    fun `LIGHT maps to MODERATE`() {
        assertEquals(ThermalLevel.MODERATE, ThermalManager.mapThermalStatus(THERMAL_STATUS_LIGHT))
    }

    @Test
    fun `MODERATE maps to MODERATE`() {
        assertEquals(ThermalLevel.MODERATE, ThermalManager.mapThermalStatus(THERMAL_STATUS_MODERATE))
    }

    @Test
    fun `SEVERE maps to SEVERE`() {
        assertEquals(ThermalLevel.SEVERE, ThermalManager.mapThermalStatus(THERMAL_STATUS_SEVERE))
    }

    @Test
    fun `CRITICAL maps to CRITICAL`() {
        assertEquals(ThermalLevel.CRITICAL, ThermalManager.mapThermalStatus(THERMAL_STATUS_CRITICAL))
    }

    @Test
    fun `EMERGENCY maps to CRITICAL`() {
        assertEquals(ThermalLevel.CRITICAL, ThermalManager.mapThermalStatus(THERMAL_STATUS_EMERGENCY))
    }

    @Test
    fun `SHUTDOWN maps to CRITICAL`() {
        assertEquals(ThermalLevel.CRITICAL, ThermalManager.mapThermalStatus(THERMAL_STATUS_SHUTDOWN))
    }

    @Test
    fun `unknown status maps to NORMAL`() {
        assertEquals(ThermalLevel.NORMAL, ThermalManager.mapThermalStatus(-999))
    }
}
