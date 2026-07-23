package com.lizardlens.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lizardlens.core.data.DetectionRepository
import com.lizardlens.core.model.Detection
import com.lizardlens.core.model.DetectionSource
import com.lizardlens.ui.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import javax.inject.Inject

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
class VideoDetectionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: DetectionRepository,
    @DefaultDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoDetectionUiState())
    val uiState: StateFlow<VideoDetectionUiState> = _uiState.asStateFlow()

    fun onVideoPicked(uri: Uri) {
        _uiState.value = VideoDetectionUiState(
            isProcessing = true,
            videoUri = uri
        )

        viewModelScope.launch(ioDispatcher) {
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

                _uiState.value = _uiState.value.copy(totalFrames = totalFrames)

                processVideoFrames(uri, totalFrames, videoWidth, videoHeight)
                retriever.release()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = "Failed to process video: ${e.message}"
                )
            }
        }
    }

    fun pauseVideoProcessing() {
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    fun resumeVideoProcessing() {
        _uiState.value = _uiState.value.copy(isPaused = false)
    }

    fun selectVideoFrame(index: Int) {
        val result = _uiState.value.videoResults.getOrNull(index) ?: return
        val uri = _uiState.value.videoUri ?: return

        viewModelScope.launch(ioDispatcher) {
            try {
                val bitmap = loadFrameFromVideo(uri, result.timestampMs)
                _uiState.value = _uiState.value.copy(
                    selectedFrameIndex = index,
                    selectedFrameBitmap = bitmap
                )
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    selectedFrameIndex = index,
                    selectedFrameBitmap = null
                )
            }
        }
    }

    fun clearSelectedFrame() {
        _uiState.value = _uiState.value.copy(
            selectedFrameIndex = null,
            selectedFrameBitmap = null
        )
    }

    fun clearVideoDetection() {
        _uiState.value = VideoDetectionUiState()
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
                _uiState.value = _uiState.value.copy(
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
                while (_uiState.value.isPaused) {
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

                                _uiState.value = _uiState.value.copy(
                                    processedFrames = frameIndex + 1,
                                    videoResults = _uiState.value.videoResults + frameResult
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

            _uiState.value = _uiState.value.copy(isProcessing = false)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
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
