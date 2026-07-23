package com.lizardlens.ui.viewmodel

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lizardlens.core.camera.ThermalLevel
import com.lizardlens.core.camera.ThermalManager
import com.lizardlens.core.data.DetectionRepository
import com.lizardlens.core.model.Detection
import com.lizardlens.core.model.DetectionResult
import com.lizardlens.core.model.DetectionSource
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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

data class ImageDetectionUiState(
    val isLoading: Boolean = false,
    val bitmap: Bitmap? = null,
    val detections: List<Detection> = emptyList(),
    val inferenceTimeMs: Long = 0L,
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val error: String? = null
)

data class VideoFrameResult(
    val frameIndex: Int,
    val timestampMs: Long,
    val detections: List<Detection>,
    val detectionCount: Int = detections.size,
    val thumbnailUri: String? = null,
    val thumbnailWidth: Int = 0,
    val thumbnailHeight: Int = 0
)

data class VideoDetectionUiState(
    val isProcessing: Boolean = false,
    val isPaused: Boolean = false,
    val videoUri: Uri? = null,
    val totalFrames: Int = 0,
    val processedFrames: Int = 0,
    val videoResults: List<VideoFrameResult> = emptyList(),
    val selectedFrameIndex: Int? = null,
    val selectedFrameBitmap: Bitmap? = null,
    val error: String? = null
)

@HiltViewModel
class CameraViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DetectionRepository,
    val thermalManager: ThermalManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val _imageUiState = MutableStateFlow(ImageDetectionUiState())
    val imageUiState: StateFlow<ImageDetectionUiState> = _imageUiState.asStateFlow()

    private val _videoUiState = MutableStateFlow(VideoDetectionUiState())
    val videoUiState: StateFlow<VideoDetectionUiState> = _videoUiState.asStateFlow()

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

    fun onImagePicked(uri: Uri) {
        val bitmap = loadBitmapFromUri(uri) ?: run {
            _imageUiState.value = _imageUiState.value.copy(error = "Failed to load image")
            return
        }

        _imageUiState.value = ImageDetectionUiState(
            isLoading = true,
            bitmap = bitmap,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        )

        viewModelScope.launch {
            try {
                val result = repository.detectAndPersist(bitmap, DetectionSource.IMAGE)
                _imageUiState.value = _imageUiState.value.copy(
                    isLoading = false,
                    detections = result.detections,
                    inferenceTimeMs = result.inferenceTimeMs,
                    imageWidth = bitmap.width,
                    imageHeight = bitmap.height
                )
            } catch (_: Exception) {
                _imageUiState.value = _imageUiState.value.copy(
                    isLoading = false,
                    error = "Detection failed"
                )
            }
        }
    }

    fun clearImageDetection() {
        _imageUiState.value = ImageDetectionUiState()
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

    fun onVideoPicked(uri: Uri) {
        _videoUiState.value = VideoDetectionUiState(
            isProcessing = true,
            videoUri = uri
        )

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, uri)

                val frameCountStr = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT
                )
                val widthStr = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )
                val heightStr = retriever.extractMetadata(
                    android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )

                val totalFrames = frameCountStr?.toIntOrNull() ?: 0
                val videoWidth = widthStr?.toIntOrNull() ?: 0
                val videoHeight = heightStr?.toIntOrNull() ?: 0

                _videoUiState.value = _videoUiState.value.copy(totalFrames = totalFrames)

                processVideoFrames(uri, totalFrames, videoWidth, videoHeight)
                retriever.release()
            } catch (e: Exception) {
                _videoUiState.value = _videoUiState.value.copy(
                    isProcessing = false,
                    error = "Failed to process video: ${e.message}"
                )
            }
        }
    }

    fun pauseVideoProcessing() {
        _videoUiState.value = _videoUiState.value.copy(isPaused = true)
    }

    fun resumeVideoProcessing() {
        _videoUiState.value = _videoUiState.value.copy(isPaused = false)
    }

    fun selectVideoFrame(index: Int) {
        val result = _videoUiState.value.videoResults.getOrNull(index) ?: return
        val uri = _videoUiState.value.videoUri ?: return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap = loadFrameFromVideo(uri, result.timestampMs)
                _videoUiState.value = _videoUiState.value.copy(
                    selectedFrameIndex = index,
                    selectedFrameBitmap = bitmap
                )
            } catch (_: Exception) {
                _videoUiState.value = _videoUiState.value.copy(
                    selectedFrameIndex = index,
                    selectedFrameBitmap = null
                )
            }
        }
    }

    fun clearSelectedFrame() {
        _videoUiState.value = _videoUiState.value.copy(
            selectedFrameIndex = null,
            selectedFrameBitmap = null
        )
    }

    fun clearVideoDetection() {
        _videoUiState.value = VideoDetectionUiState()
    }

    private suspend fun processVideoFrames(
        uri: Uri,
        totalFrames: Int,
        videoWidth: Int,
        videoHeight: Int
    ) {
        val extractor = android.media.MediaExtractor()
        val retriever = android.media.MediaMetadataRetriever()

        try {
            extractor.setDataSource(context, uri, null)
            retriever.setDataSource(context, uri)

            var videoTrackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(android.media.MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i
                    break
                }
            }
            if (videoTrackIndex < 0) {
                _videoUiState.value = _videoUiState.value.copy(
                    isProcessing = false,
                    error = "No video track found"
                )
                return
            }

            extractor.selectTrack(videoTrackIndex)
            val format = extractor.getTrackFormat(videoTrackIndex)
            val width = format.getInteger(android.media.MediaFormat.KEY_WIDTH)
            val height = format.getInteger(android.media.MediaFormat.KEY_HEIGHT)
            val mime = format.getString(android.media.MediaFormat.KEY_MIME)!!

            val codec = android.media.MediaCodec.createDecoderByType(mime)
            val imageReader = android.media.ImageReader.newInstance(
                width, height, android.graphics.ImageFormat.YUV_420_888, 2
            )

            codec.configure(format, imageReader.surface, null, 0)
            codec.start()

            var inputDone = false
            var outputDone = false
            var frameIndex = 0

            while (!outputDone) {
                while (_videoUiState.value.isPaused) {
                    delay(100)
                    yield()
                }

                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: continue
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex, 0, 0, 0,
                                android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val sampleTime = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val bufferInfo = android.media.MediaCodec.BufferInfo()
                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                while (outputIndex >= 0) {
                    if (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }

                    val image = imageReader.acquireLatestImage()
                    if (image != null) {
                        try {
                            val timestampMs = bufferInfo.presentationTimeUs / 1000
                            val bitmap = yuv420ToBitmap(image, image.width, image.height)

                            if (bitmap != null) {
                                val thumbnailPath = repository.saveThumbnail(
                                    bitmap, "video_frame_$frameIndex.jpg"
                                )
                                val result = repository.detectAndPersist(
                                    bitmap, DetectionSource.VIDEO, thumbnailPath
                                )

                                val frameResult = VideoFrameResult(
                                    frameIndex = frameIndex,
                                    timestampMs = timestampMs,
                                    detections = result.detections,
                                    thumbnailUri = thumbnailPath,
                                    thumbnailWidth = bitmap.width,
                                    thumbnailHeight = bitmap.height
                                )

                                _videoUiState.value = _videoUiState.value.copy(
                                    processedFrames = frameIndex + 1,
                                    videoResults = _videoUiState.value.videoResults + frameResult
                                )

                                bitmap.recycle()
                            }

                            frameIndex++
                        } finally {
                            image.close()
                        }
                    }

                    codec.releaseOutputBuffer(outputIndex, false)
                    if (!outputDone) {
                        outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    }
                }
            }

            codec.stop()
            codec.release()
            imageReader.close()

            _videoUiState.value = _videoUiState.value.copy(isProcessing = false)
        } catch (e: Exception) {
            _videoUiState.value = _videoUiState.value.copy(
                isProcessing = false,
                error = "Processing failed: ${e.message}"
            )
        } finally {
            extractor.release()
            retriever.release()
        }
    }

    private fun loadFrameFromVideo(uri: Uri, timestampMs: Long): Bitmap? {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(
                timestampMs * 1000,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST
            )
            retriever.release()
            bitmap
        } catch (_: Exception) {
            null
        }
    }

    private fun yuv420ToBitmap(image: android.media.Image, width: Int, height: Int): Bitmap? {
        return try {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer

            val yRowStride = image.planes[0].rowStride
            val uvRowStride = image.planes[1].rowStride
            val uvPixelStride = image.planes[1].pixelStride

            val nv21 = ByteArray(width * height * 3 / 2)

            var pos = 0
            for (row in 0 until height) {
                val yOffset = row * yRowStride
                for (col in 0 until width) {
                    nv21[pos++] = yBuffer.get(yOffset + col)
                }
            }

            val uvHeight = height / 2
            val uvWidth = width / 2
            for (row in 0 until uvHeight) {
                for (col in 0 until uvWidth) {
                    val uvOffset = row * uvRowStride + col * uvPixelStride
                    nv21[pos++] = vBuffer.get(uvOffset)
                    nv21[pos++] = uBuffer.get(uvOffset)
                }
            }

            val yuvImage = android.graphics.YuvImage(
                nv21, android.graphics.ImageFormat.NV21, width, height, null
            )
            val out = java.io.ByteArrayOutputStream()
            yuvImage.compressToJpeg(
                android.graphics.Rect(0, 0, width, height), 85, out
            )
            val bytes = out.toByteArray()
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }
}
