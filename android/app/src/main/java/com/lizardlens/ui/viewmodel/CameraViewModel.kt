package com.lizardlens.ui.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lizardlens.core.camera.ThermalLevel
import com.lizardlens.core.camera.ThermalManager
import com.lizardlens.core.data.DetectionRepository
import com.lizardlens.core.model.Detection
import com.lizardlens.core.model.DetectionResult
import com.lizardlens.core.model.DetectionSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

data class CameraUiState(
    val isDetecting: Boolean = false,
    val detections: List<Detection> = emptyList(),
    val fps: Int = 0,
    val thermalLevel: ThermalLevel = ThermalLevel.NORMAL,
    val isFrontCamera: Boolean = false,
    val isGpuActive: Boolean = false,
    val permissionGranted: Boolean = false,
    val lastInferenceTimeMs: Long = 0L,
    val imageWidth: Int = 640,
    val imageHeight: Int = 480
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    private val repository: DetectionRepository,
    val thermalManager: ThermalManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val frameCount = AtomicInteger(0)
    private var lastFpsTimestamp = System.currentTimeMillis()
    private var fpsValue = 0

    init {
        thermalManager.start()

        viewModelScope.launch {
            thermalManager.thermalLevel.collect { level ->
                _uiState.value = _uiState.value.copy(thermalLevel = level)
            }
        }

        viewModelScope.launch {
            val config = repository.getCurrentConfig().first()
            _uiState.value = _uiState.value.copy(
                isGpuActive = config.delegate == com.lizardlens.core.inference.InferenceConfig.Delegate.GPU
            )
        }
    }

    fun toggleDetection() {
        val current = _uiState.value.isDetecting
        _uiState.value = _uiState.value.copy(
            isDetecting = !current,
            detections = if (current) emptyList() else _uiState.value.detections
        )
    }

    fun toggleCamera() {
        _uiState.value = _uiState.value.copy(
            isFrontCamera = !_uiState.value.isFrontCamera
        )
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.value = _uiState.value.copy(permissionGranted = granted)
    }

    fun processCameraFrame(bitmap: Bitmap) {
        if (!_uiState.value.isDetecting) return

        viewModelScope.launch {
            try {
                val result = repository.detectAndPersist(bitmap, DetectionSource.CAMERA)
                updateFps()

                _uiState.value = _uiState.value.copy(
                    detections = result.detections,
                    lastInferenceTimeMs = result.inferenceTimeMs,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
            } catch (_: Exception) {
            }
        }
    }

    private fun updateFps() {
        val count = frameCount.incrementAndGet()
        val now = System.currentTimeMillis()
        val elapsed = now - lastFpsTimestamp

        if (elapsed >= 1000) {
            fpsValue = (count * 1000L / elapsed).toInt()
            frameCount.set(0)
            lastFpsTimestamp = now
            _uiState.value = _uiState.value.copy(fps = fpsValue)
        }
    }

    fun clearDetections() {
        _uiState.value = _uiState.value.copy(detections = emptyList())
    }
}
