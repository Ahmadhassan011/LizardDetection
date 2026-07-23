package com.lizardlens.ui.screens

import android.Manifest
import android.view.ViewGroup
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.PestControl
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.lizardlens.core.camera.CameraXImageAnalyzer
import com.lizardlens.core.camera.FrameProcessor
import com.lizardlens.core.camera.ThermalLevel
import com.lizardlens.core.camera.ThermalManager
import com.lizardlens.ui.composables.AcceleratorIndicator
import com.lizardlens.ui.composables.BoundingBoxOverlay
import com.lizardlens.ui.composables.FpsCounter
import com.lizardlens.ui.composables.ThermalBanner
import com.lizardlens.ui.theme.Primary
import com.lizardlens.ui.theme.DetectionBox
import com.lizardlens.ui.theme.Disabled
import com.lizardlens.ui.viewmodel.CameraViewModel
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    viewModel: CameraViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val cameraPermissionState = rememberPermissionState(
        permission = Manifest.permission.CAMERA,
        onPermissionResult = { granted -> viewModel.onPermissionResult(granted) }
    )

    LaunchedEffect(Unit) {
        viewModel.onPermissionResult(cameraPermissionState.status.isGranted)
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (uiState.permissionGranted) {
            CameraPreviewWithAnalysis(
                isDetecting = uiState.isDetecting,
                isFrontCamera = uiState.isFrontCamera,
                onFrameProcessed = { bitmap -> viewModel.processCameraFrame(bitmap) },
                thermalManager = viewModel.thermalManager,
                modifier = Modifier.fillMaxSize()
            )

            BoundingBoxOverlay(
                detections = uiState.detections,
                imageWidth = uiState.imageWidth,
                imageHeight = uiState.imageHeight,
                modifier = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FpsCounter(fps = uiState.fps)
                AcceleratorIndicator(isGpu = uiState.isGpuActive)
            }

            if (uiState.thermalLevel != ThermalLevel.NORMAL) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 56.dp)
                ) {
                    ThermalBanner(
                        message = uiState.thermalLevel.bannerMessage ?: "",
                        isSevere = uiState.thermalLevel == ThermalLevel.SEVERE ||
                                uiState.thermalLevel == ThermalLevel.CRITICAL,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            IconButton(
                onClick = { viewModel.toggleCamera() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 48.dp)
                    .size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Cameraswitch,
                    contentDescription = "Switch Camera",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            PermissionDeniedContent(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                shouldShowRationale = cameraPermissionState.status.shouldShowRationale,
                modifier = Modifier.fillMaxSize()
            )
        }

        DetectButton(
            isDetecting = uiState.isDetecting,
            isThermalCritical = uiState.thermalLevel == ThermalLevel.CRITICAL,
            onClick = { viewModel.toggleDetection() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        )
    }
}

@Composable
private fun CameraPreviewWithAnalysis(
    isDetecting: Boolean,
    isFrontCamera: Boolean,
    onFrameProcessed: (android.graphics.Bitmap) -> Unit,
    thermalManager: ThermalManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val frameProcessor = remember(onFrameProcessed) {
        FrameProcessor { bitmap -> onFrameProcessed(bitmap) }
    }

    val analyzer = remember(frameProcessor, thermalManager) {
        CameraXImageAnalyzer(frameProcessor, thermalManager)
    }

    LaunchedEffect(isDetecting) {
        analyzer.isActive = isDetecting
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(ContextCompat.getMainExecutor(ctx), previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()
                    .also {
                        it.setAnalyzer(ContextCompat.getMainExecutor(ctx), analyzer)
                    }

                val cameraSelector = if (isFrontCamera) {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                } else {
                    CameraSelector.DEFAULT_BACK_CAMERA
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@Composable
private fun DetectButton(
    isDetecting: Boolean,
    isThermalCritical: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val buttonAlpha by animateFloatAsState(
        targetValue = if (isThermalCritical) 0.5f else 1f,
        animationSpec = tween(durationMillis = 300),
        label = "button_alpha"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    val containerColor = when {
        isThermalCritical -> Disabled
        isDetecting -> DetectionBox
        else -> Primary
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (isDetecting && !isThermalCritical) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .scale(ringScale)
                    .alpha(ringAlpha)
                    .background(
                        color = DetectionBox,
                        shape = CircleShape
                    )
            )
        }

        FloatingActionButton(
            onClick = { if (!isThermalCritical) onClick() },
            modifier = Modifier
                .size(72.dp)
                .alpha(buttonAlpha),
            containerColor = containerColor,
            shape = CircleShape,
            elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(
                defaultElevation = 6.dp
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PestControl,
                    contentDescription = "Detect",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "DETECT",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun PermissionDeniedContent(
    onRequestPermission: () -> Unit,
    shouldShowRationale: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color.Black)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (shouldShowRationale) {
                "LizardLens needs camera access to detect lizards in real time. " +
                        "Please grant camera permission to continue."
            } else {
                "Camera permission is required for live lizard detection. " +
                        "Please enable it in your device settings."
            },
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            onClick = onRequestPermission,
            shape = RoundedCornerShape(8.dp),
            color = Primary
        ) {
            Text(
                text = if (shouldShowRationale) "Grant Permission" else "Open Settings",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}
