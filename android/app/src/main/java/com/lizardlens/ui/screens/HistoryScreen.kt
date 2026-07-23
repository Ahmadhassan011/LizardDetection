package com.lizardlens.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.lizardlens.core.data.Converters
import com.lizardlens.core.data.DetectionEntity
import com.lizardlens.core.model.DetectionSource
import com.lizardlens.ui.theme.DetectionBox
import com.lizardlens.ui.theme.Error
import com.lizardlens.ui.theme.OnSurface
import com.lizardlens.ui.theme.Primary
import com.lizardlens.ui.theme.Success
import com.lizardlens.ui.theme.Surface
import com.lizardlens.ui.theme.SurfaceVariant
import com.lizardlens.ui.theme.Warning
import com.lizardlens.ui.viewmodel.HistoryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val detections by viewModel.filteredDetections.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    var showClearAllDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Surface
    ) { _ ->
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = "History",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface.copy(alpha = 0.9f)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to camera",
                            tint = OnSurface.copy(alpha = 0.9f)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showClearAllDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear all history",
                            tint = OnSurface.copy(alpha = 0.9f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Surface.copy(alpha = 0.95f)
                )
            )

            FilterChips(
                selectedFilter = selectedFilter,
                onFilterClick = { viewModel.setFilter(it) }
            )

            if (detections.isEmpty()) {
                EmptyHistoryState(filter = selectedFilter)
            } else {
                HistoryList(
                    detections = detections,
                    onSwipeDelete = { entity ->
                        viewModel.deleteWithUndo(entity)
                        scope.launch {
                            val result = snackbarHostState.showSnackbar(
                                message = "Detection deleted",
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.undoDelete(entity)
                            } else {
                                viewModel.consumePendingDelete(entity.id)
                            }
                        }
                    }
                )
            }
        }
    }

    if (showClearAllDialog) {
        ClearAllConfirmationDialog(
            onConfirm = {
                viewModel.clearHistory()
                showClearAllDialog = false
            },
            onDismiss = { showClearAllDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChips(
    selectedFilter: DetectionSource?,
    onFilterClick: (DetectionSource?) -> Unit
) {
    val filters = listOf(
        FilterOption(null, "All", Icons.Default.History),
        FilterOption(DetectionSource.CAMERA, "Camera", Icons.Default.Videocam),
        FilterOption(DetectionSource.IMAGE, "Image", Icons.Default.ImageIcon),
        FilterOption(DetectionSource.VIDEO, "Video", Icons.Default.Movie)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { option ->
            FilterChip(
                onClick = { onFilterClick(option.source) },
                selected = selectedFilter == option.source,
                modifier = Modifier.weight(1f, fill = false),
                label = {
                    Text(
                        text = option.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

private data class FilterOption(
    val source: DetectionSource?,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
private fun EmptyHistoryState(filter: DetectionSource?) {
    val message = when (filter) {
        DetectionSource.CAMERA -> "No camera detections yet"
        DetectionSource.IMAGE -> "No image detections yet"
        DetectionSource.VIDEO -> "No video detections yet"
        else -> "No detections recorded"
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = "History",
            tint = OnSurface.copy(alpha = 0.3f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Detection History",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 14.sp,
            color = OnSurface.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryList(
    detections: List<DetectionEntity>,
    onSwipeDelete: (DetectionEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(detections, key = { it.id }) { detection ->
            SwipeToDeleteItem(
                detection = detection,
                onSwipeDelete = { onSwipeDelete(detection) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDeleteItem(
    detection: DetectionEntity,
    onSwipeDelete: () -> Unit
) {
    var isRemoved by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = !isRemoved,
        exit = shrinkVertically(animationSpec = tween(200)) + fadeOut()
    ) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.StartToEnd) {
                    isRemoved = true
                    onSwipeDelete()
                    true
                } else {
                    false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromStartToEnd = true,
            enableDismissFromEndToStart = false,
            backgroundContent = {
                SwipeDismissBackground()
            }
        ) {
            HistoryItem(detection = detection)
        }
    }
}

@Composable
private fun SwipeDismissBackground() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Error, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "Delete",
            tint = Color.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryItem(
    detection: DetectionEntity
) {
    val boundingBox = Converters().toBoundingBox(detection.boundingBox)
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()) }
    val formattedDate = dateFormat.format(Date(detection.timestamp))
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = Surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SourceBadge(source = detection.source)
                Text(
                    text = formattedDate,
                    fontSize = 12.sp,
                    color = OnSurface.copy(alpha = 0.6f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceVariant)
            ) {
                detection.imageUri?.let { uri ->
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(uri))
                            .crossfade(true)
                            .build(),
                        contentDescription = "Detection thumbnail",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        val boxLeft = boundingBox.x1 * size.width
                        val boxTop = boundingBox.y1 * size.height
                        val boxRight = boundingBox.x2 * size.width
                        val boxBottom = boundingBox.y2 * size.height

                        drawRect(
                            color = DetectionBox.copy(alpha = 0.3f),
                            style = Stroke(width = 2.dp.toPx()),
                            topLeft = Offset(boxLeft, boxTop),
                            size = Size(boxRight - boxLeft, boxBottom - boxTop)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ConfidenceBadge(confidence = detection.confidence)
            }
        }
    }
}

@Composable
private fun SourceBadge(source: DetectionSource) {
    val (label, color) = when (source) {
        DetectionSource.CAMERA -> "CAMERA" to DetectionBox
        DetectionSource.IMAGE -> "IMAGE" to Primary
        DetectionSource.VIDEO -> "VIDEO" to Warning
    }

    Text(
        text = label,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = OnSurface,
        modifier = Modifier
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .background(color, RoundedCornerShape(4.dp))
    )
}

@Composable
private fun ConfidenceBadge(confidence: Float) {
    val percent = (confidence * 100).toInt()
    val color = when {
        confidence >= 0.7f -> Success
        confidence >= 0.5f -> Warning
        else -> Error
    }

    Text(
        text = "$percent%",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = color
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearAllConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Clear All History", fontWeight = FontWeight.Medium) },
        text = {
            Text(text = "This will permanently delete all detection records. This action cannot be undone.")
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = Error
                )
            ) {
                Text(text = "Clear All", color = OnSurface)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}
