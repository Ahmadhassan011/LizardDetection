package com.lizardlens.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lizardlens.ui.composables.BoundingBoxOverlay
import com.lizardlens.ui.viewmodel.CameraViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val videoUiState by viewModel.videoUiState.collectAsState()

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onVideoPicked(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Video Detection",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    viewModel.clearVideoDetection()
                    onDismiss()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to camera"
                    )
                }
            },
            actions = {
                if (videoUiState.isProcessing) {
                    IconButton(onClick = {
                        if (videoUiState.isPaused) {
                            viewModel.resumeVideoProcessing()
                        } else {
                            viewModel.pauseVideoProcessing()
                        }
                    }) {
                        Icon(
                            imageVector = if (videoUiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (videoUiState.isPaused) "Resume" else "Pause",
                            tint = Color.White
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                videoUiState.selectedFrameIndex != null -> {
                    val frameResult = videoUiState.videoResults
                        .getOrNull(videoUiState.selectedFrameIndex!!)
                    VideoFrameDetail(
                        frameResult = frameResult,
                        bitmap = videoUiState.selectedFrameBitmap,
                        onBack = { viewModel.clearSelectedFrame() }
                    )
                }
                videoUiState.isProcessing || videoUiState.videoResults.isNotEmpty() -> {
                    VideoProcessingContent(
                        isProcessing = videoUiState.isProcessing,
                        isPaused = videoUiState.isPaused,
                        processedFrames = videoUiState.processedFrames,
                        totalFrames = videoUiState.totalFrames,
                        videoResults = videoUiState.videoResults,
                        onFrameSelected = { index -> viewModel.selectVideoFrame(index) }
                    )
                }
                videoUiState.error != null -> {
                    VideoErrorContent(
                        message = videoUiState.error!!,
                        onDismiss = { viewModel.clearVideoDetection() }
                    )
                }
                else -> {
                    PickVideoContent(
                        onPickVideo = {
                            pickVideoLauncher.launch(arrayOf("video/*"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PickVideoContent(
    onPickVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Video Detection",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick a video to detect lizards frame-by-frame",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = onPickVideo,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "Pick Video",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun VideoProcessingContent(
    isProcessing: Boolean,
    isPaused: Boolean,
    processedFrames: Int,
    totalFrames: Int,
    videoResults: List<com.lizardlens.ui.viewmodel.VideoFrameResult>,
    onFrameSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
    ) {
        if (totalFrames > 0) {
            val progress = if (totalFrames > 0) processedFrames.toFloat() / totalFrames else 0f
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF00FF88),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (isProcessing) {
                            if (isPaused) "Paused — $processedFrames / $totalFrames frames"
                            else "Processing... $processedFrames / $totalFrames frames"
                        } else {
                            "Complete — $processedFrames frames processed"
                        },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    if (videoResults.isNotEmpty()) {
                        val totalDetections = videoResults.sumOf { it.detectionCount }
                        Text(
                            text = "$totalDetections lizard${if (totalDetections != 1) "s" else ""} found",
                            fontSize = 12.sp,
                            color = Color(0xFF00FF88),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        if (isProcessing && processedFrames == 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFF00FF88),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Starting video analysis...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        } else if (videoResults.isNotEmpty()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(videoResults) { index, result ->
                    VideoResultRow(
                        frameIndex = result.frameIndex,
                        timestampMs = result.timestampMs,
                        detectionCount = result.detectionCount,
                        onClick = { onFrameSelected(index) }
                    )
                }
            }
        } else if (!isProcessing && processedFrames > 0) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No lizards detected in any frame",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun VideoResultRow(
    frameIndex: Int,
    timestampMs: Long,
    detectionCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Frame $frameIndex",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatTimestamp(timestampMs),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Text(
                text = if (detectionCount == 1) "1 lizard" else "$detectionCount lizards",
                fontSize = 13.sp,
                color = if (detectionCount > 0) Color(0xFF00FF88) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontWeight = if (detectionCount > 0) FontWeight.Medium else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun VideoFrameDetail(
    frameResult: com.lizardlens.ui.viewmodel.VideoFrameResult?,
    bitmap: android.graphics.Bitmap?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (bitmap != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(bitmap)
                    .crossfade(true)
                    .build(),
                contentDescription = "Frame ${frameResult?.frameIndex}",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            BoundingBoxOverlay(
                detections = frameResult?.detections ?: emptyList(),
                imageWidth = frameResult?.thumbnailWidth ?: 1,
                imageHeight = frameResult?.thumbnailHeight ?: 1,
                modifier = Modifier.fillMaxSize()
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0x99000000),
                onClick = onBack
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to results",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Back",
                        color = Color.White,
                        fontSize = 12.sp
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0x99000000)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Frame ${frameResult?.frameIndex} — ${formatTimestamp(frameResult?.timestampMs ?: 0)}",
                        color = Color.White,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    val count = frameResult?.detectionCount ?: 0
                    Text(
                        text = if (count == 1) "1 lizard" else "$count lizards",
                        color = Color(0xFF00FF88),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFF00FF88),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Loading frame...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoErrorContent(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.padding(32.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.error
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = "Dismiss",
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestampMs: Long): String {
    val totalSeconds = timestampMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val millis = timestampMs % 1000
    return "%02d:%02d.%03d".format(minutes, seconds, millis)
}
