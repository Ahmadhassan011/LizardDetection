package com.lizardlens.ui.composables

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lizardlens.core.model.Detection
import com.lizardlens.ui.theme.DetectionBackground
import com.lizardlens.ui.theme.DetectionBox
import com.lizardlens.ui.theme.DetectionFill
import com.lizardlens.ui.theme.Error
import com.lizardlens.ui.theme.Success
import com.lizardlens.ui.theme.Warning

@Composable
fun BoundingBoxOverlay(
    detections: List<Detection>,
    modifier: Modifier = Modifier,
    imageWidth: Int = 1,
    imageHeight: Int = 1
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (imageWidth <= 0 || imageHeight <= 0) return@Canvas

        val scaleX = size.width / imageWidth
        val scaleY = size.height / imageHeight

        detections.forEach { detection ->
            val bbox = detection.boundingBox
            val left = bbox.x1 * scaleX
            val top = bbox.y1 * scaleY
            val right = bbox.x2 * scaleX
            val bottom = bbox.y2 * scaleY
            val width = right - left
            val height = bottom - top

            drawRect(
                color = DetectionFill,
                topLeft = Offset(left, top),
                size = Size(width, height)
            )

            drawRect(
                color = DetectionBox,
                topLeft = Offset(left, top),
                size = Size(width, height),
                style = Stroke(width = 3.dp.toPx())
            )

            val label = "Lizard %.2f".format(detection.confidence)
            val labelTextSize = 10.sp.toPx()
            val textWidth = labelTextSize * label.length * 0.6f
            val pillHeight = labelTextSize * 1.8f
            val pillPadding = 4.dp.toPx()

            drawRect(
                color = DetectionBackground,
                topLeft = Offset(left, top - pillHeight - pillPadding),
                size = Size(textWidth + pillPadding * 2, pillHeight + pillPadding)
            )

            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#00FF88")
                    textSize = labelTextSize
                    isAntiAlias = true
                }
                drawText(
                    label,
                    left + pillPadding,
                    top - pillPadding,
                    paint
                )
            }
        }
    }
}

@Composable
fun ConfidenceBadge(
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val color = when {
        confidence >= 0.70f -> Success
        confidence >= 0.45f -> Warning
        else -> Error
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = color
    ) {
        Text(
            text = "%.0f%%".format(confidence * 100),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun FpsCounter(
    fps: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = DetectionBackground
    ) {
        Text(
            text = "$fps FPS",
            color = Color(0xFF2196F3),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun AcceleratorIndicator(
    isGpu: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = if (isGpu) Success else Color(0xFF2196F3)
    ) {
        Text(
            text = if (isGpu) "GPU" else "CPU",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun ThermalBanner(
    message: String,
    isSevere: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSevere) Error else Warning

    Surface(
        modifier = modifier,
        color = backgroundColor
    ) {
        Text(
            text = message,
            color = Color.White,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}
