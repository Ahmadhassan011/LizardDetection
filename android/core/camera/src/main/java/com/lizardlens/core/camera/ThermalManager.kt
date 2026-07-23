package com.lizardlens.core.camera

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.lizardlens.core.logging.AppLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class ThermalLevel {
    NORMAL,
    MODERATE,
    SEVERE,
    CRITICAL;

    fun shouldSkipFrame(frameCount: Int): Boolean = when (this) {
        NORMAL -> false
        MODERATE -> false
        SEVERE -> frameCount % 3 != 0
        CRITICAL -> true
    }

    val bannerMessage: String? get() = when (this) {
        NORMAL -> null
        MODERATE -> "Device warming \u2014 reduce usage"
        SEVERE -> "Throttled \u2014 3 FPS"
        CRITICAL -> "Cooling down"
    }
}

@Singleton
class ThermalManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _thermalLevel = MutableStateFlow(ThermalLevel.NORMAL)
    val thermalLevel: StateFlow<ThermalLevel> = _thermalLevel.asStateFlow()

    private var frameCounter = 0
    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        val level = mapThermalStatus(status)
        val previous = _thermalLevel.value
        _thermalLevel.value = level
        if (level != previous) {
            AppLogger.i("Thermal level changed: $previous -> $level")
        }
    }

    fun start() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.addThermalStatusListener(thermalListener)
            AppLogger.i("Thermal listener registered")
        }
    }

    fun stop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            powerManager?.removeThermalStatusListener(thermalListener)
        }
        _thermalLevel.value = ThermalLevel.NORMAL
        frameCounter = 0
        AppLogger.i("Thermal manager stopped, reset to NORMAL")
    }

    fun shouldSkipCurrentFrame(): Boolean {
        val level = _thermalLevel.value
        val skip = level.shouldSkipFrame(frameCounter)
        frameCounter++
        if (frameCounter > Int.MAX_VALUE - 2) frameCounter = 0
        return skip
    }

    fun shouldStopCamera(): Boolean = _thermalLevel.value == ThermalLevel.CRITICAL

    fun setManualLevel(level: ThermalLevel) {
        _thermalLevel.value = level
    }

    companion object {
        fun mapThermalStatus(status: Int): ThermalLevel = when (status) {
            PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NORMAL
            PowerManager.THERMAL_STATUS_LIGHT,
            PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
            PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalLevel.CRITICAL
            else -> ThermalLevel.NORMAL
        }
    }
}
