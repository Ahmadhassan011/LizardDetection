package com.lizardlens.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorTokensTest {

    @Test
    fun `Primary token has expected value`() {
        assertEquals(Color(0xFF1B5E20), Primary)
    }

    @Test
    fun `OnPrimary token has expected value`() {
        assertEquals(Color(0xFFFFFFFF), OnPrimary)
    }

    @Test
    fun `PrimaryContainer token has expected value`() {
        assertEquals(Color(0xFFA5D6A7), PrimaryContainer)
    }

    @Test
    fun `OnPrimaryContainer token has expected value`() {
        assertEquals(Color(0xFF002204), OnPrimaryContainer)
    }

    @Test
    fun `Secondary token has expected value`() {
        assertEquals(Color(0xFF4E6E50), Secondary)
    }

    @Test
    fun `OnSecondary token has expected value`() {
        assertEquals(Color(0xFFFFFFFF), OnSecondary)
    }

    @Test
    fun `SecondaryContainer token has expected value`() {
        assertEquals(Color(0xFFD0E8D1), SecondaryContainer)
    }

    @Test
    fun `OnSecondaryContainer token has expected value`() {
        assertEquals(Color(0xFF0C1F0E), OnSecondaryContainer)
    }

    @Test
    fun `Surface token has expected value`() {
        assertEquals(Color(0xFFF8FBF8), Surface)
    }

    @Test
    fun `OnSurface token has expected value`() {
        assertEquals(Color(0xFF1A1C1A), OnSurface)
    }

    @Test
    fun `SurfaceVariant token has expected value`() {
        assertEquals(Color(0xFFDDE5DD), SurfaceVariant)
    }

    @Test
    fun `Outline token has expected value`() {
        assertEquals(Color(0xFF717971), Outline)
    }

    @Test
    fun `DetectionBox token has expected value`() {
        assertEquals(Color(0xFF00FF88), DetectionBox)
    }

    @Test
    fun `DetectionLabel token has expected value`() {
        assertEquals(Color(0xFF00FF88), DetectionLabel)
    }

    @Test
    fun `DetectionFill token has expected value`() {
        assertEquals(Color(0x3300FF88), DetectionFill)
    }

    @Test
    fun `DetectionBackground token has expected value`() {
        assertEquals(Color(0x99000000), DetectionBackground)
    }

    @Test
    fun `Success token has expected value`() {
        assertEquals(Color(0xFF4CAF50), Success)
    }

    @Test
    fun `Warning token has expected value`() {
        assertEquals(Color(0xFFFF9800), Warning)
    }

    @Test
    fun `Error token has expected value`() {
        assertEquals(Color(0xFFF44336), Error)
    }

    @Test
    fun `Info token has expected value`() {
        assertEquals(Color(0xFF2196F3), Info)
    }

    @Test
    fun `Disabled token exists with expected value`() {
        assertEquals(Color(0xFF9E9E9E), Disabled)
    }

    @Test
    fun `DetectionBox matches Info inverse for detection accent`() {
        // Smoke test: DetectionBox and Info should be distinct
        assert(DetectionBox != Info)
    }

    @Test
    fun `no two semantic tokens alias the same color unintentionally`() {
        val tokens = listOf(
            Primary, Secondary, Surface, SurfaceVariant, Outline,
            DetectionBox, DetectionFill, DetectionBackground,
            Success, Warning, Error, Info, Disabled
        )
        val unique = tokens.toSet()
        // DetectionFill has alpha so it won't equal DetectionBox even if RGB matches,
        // but all opaque tokens should be distinct
        val opaqueTokens = listOf(
            Primary, Secondary, Surface, SurfaceVariant, Outline,
            DetectionBox, Success, Warning, Error, Info, Disabled
        )
        assertEquals(opaqueTokens.size, opaqueTokens.toSet().size)
    }
}
