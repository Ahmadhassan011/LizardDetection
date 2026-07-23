package com.lizardlens.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lizardlens.core.data.DetectionRepository
import com.lizardlens.core.model.Detection
import com.lizardlens.core.model.DetectionSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageDetectionUiState(
    val isLoading: Boolean = false,
    val bitmap: Bitmap? = null,
    val detections: List<Detection> = emptyList(),
    val inferenceTimeMs: Long = 0L,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val error: String? = null
)

@HiltViewModel
class ImageDetectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DetectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageDetectionUiState())
    val uiState: StateFlow<ImageDetectionUiState> = _uiState.asStateFlow()

    fun onImagePicked(uri: Uri) {
        val bitmap = loadBitmapFromUri(uri) ?: run {
            _uiState.value = _uiState.value.copy(error = "Failed to load image")
            return
        }

        _uiState.value = ImageDetectionUiState(
            isLoading = true,
            bitmap = bitmap,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )

        viewModelScope.launch {
            try {
                val result = repository.detectAndPersist(bitmap, DetectionSource.IMAGE)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    detections = result.detections,
                    inferenceTimeMs = result.inferenceTimeMs,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Detection failed"
                )
            }
        }
    }

    fun clearImageDetection() {
        _uiState.value = ImageDetectionUiState()
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
            }
        } catch (_: Exception) {
            null
        }
    }
}
