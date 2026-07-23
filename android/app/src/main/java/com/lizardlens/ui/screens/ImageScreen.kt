package com.lizardlens.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lizardlens.ui.composables.BoundingBoxOverlay
import com.lizardlens.ui.theme.DetectionBackground
import com.lizardlens.ui.theme.DetectionBox
import com.lizardlens.ui.viewmodel.ImageDetectionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImageDetectionViewModel = hiltViewModel()
) {
    val imageUiState by viewModel.uiState.collectAsState()

    val pickMediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onImagePicked(uri)
        }
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += panChange
    }

    LaunchedEffect(imageUiState.bitmap) {
        scale = 1f
        offset = Offset.Zero
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = "Image Detection",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            },
            navigationIcon = {
                IconButton(onClick = {
                    viewModel.clearImageDetection()
                    onDismiss()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to camera"
                    )
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
            if (imageUiState.bitmap == null && !imageUiState.isLoading) {
                PickPhotoContent(
                    onPickPhoto = {
                        pickMediaLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )
            } else {
                imageUiState.bitmap?.let { bitmap ->
                    ImageWithOverlay(
                        bitmap = bitmap,
                        detections = imageUiState.detections,
                        imageWidth = imageUiState.imageWidth,
                        imageHeight = imageUiState.imageHeight,
                        transformableState = transformableState,
                        scale = scale,
                        offset = offset
                    )
                }
            }

            Overlays(
                isLoading = imageUiState.isLoading,
                detections = imageUiState.detections,
                inferenceTimeMs = imageUiState.inferenceTimeMs,
                error = imageUiState.error,
                onDismissError = { viewModel.clearImageDetection() }
            )
        }
    }
}

@Composable
private fun PickPhotoContent(
    onPickPhoto: () -> Unit,
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
            text = "Image Detection",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Pick a photo from your gallery to detect lizards",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = onPickPhoto,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary
        ) {
            Text(
                text = "Pick Photo",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun ImageWithOverlay(
    bitmap: android.graphics.Bitmap,
    detections: List<com.lizardlens.core.model.Detection>,
    imageWidth: Int,
    imageHeight: Int,
    transformableState: androidx.compose.foundation.gestures.TransformableState,
    scale: Float,
    offset: Offset
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformableState)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(bitmap)
                .crossfade(true)
                .build(),
            contentDescription = "Picked image",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                }
        )

        BoundingBoxOverlay(
            detections = detections,
            imageWidth = imageWidth,
            imageHeight = imageHeight,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun Overlays(
    isLoading: Boolean,
    detections: List<com.lizardlens.core.model.Detection>,
    inferenceTimeMs: Long,
    error: String?,
    onDismissError: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DetectionBackground
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = DetectionBox,
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = "Detecting lizards...",
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        if (detections.isNotEmpty() && !isLoading) {
            ResultInfoBar(
                detectionCount = detections.size,
                inferenceTimeMs = inferenceTimeMs,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (error != null) {
            ErrorContent(
                message = error,
                onDismiss = onDismissError,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun ResultInfoBar(
    detectionCount: Int,
    inferenceTimeMs: Long,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        shape = RoundedCornerShape(8.dp),
        color = DetectionBackground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (detectionCount == 1) "1 lizard detected" else "$detectionCount lizards detected",
                color = DetectionBox,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${inferenceTimeMs}ms",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(32.dp),
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
