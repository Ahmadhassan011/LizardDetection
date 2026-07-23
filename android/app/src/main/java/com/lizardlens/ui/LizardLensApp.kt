package com.lizardlens.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lizardlens.ui.screens.CameraScreen
import com.lizardlens.ui.screens.HistoryScreen
import com.lizardlens.ui.screens.ImageScreen
import com.lizardlens.ui.screens.SettingsScreen
import com.lizardlens.ui.screens.VideoScreen
import com.lizardlens.ui.theme.DetectionBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LizardLensApp() {
    var activeOverlay by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera fills the entire screen as the base layer
        CameraScreen()

        // Top bar overlay on camera
        TopAppBar(
            title = {
                Text(
                    text = "LizardLens",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
            },
            actions = {
                IconButton(onClick = { activeOverlay = "image" }) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "Image Detection",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { activeOverlay = "video" }) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = "Video Detection",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { activeOverlay = "history" }) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = Color.White
                    )
                }
                IconButton(onClick = { activeOverlay = "settings" }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = DetectionBackground,
                titleContentColor = Color.White
            )
        )

        // Slide-up overlay screens
        AnimatedVisibility(
            visible = activeOverlay != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            activeOverlay?.let { overlay ->
                when (overlay) {
                    "image" -> ImageScreen(onDismiss = { activeOverlay = null })
                    "video" -> VideoScreen(onDismiss = { activeOverlay = null })
                    "history" -> HistoryScreen(onDismiss = { activeOverlay = null })
                    "settings" -> SettingsScreen(onDismiss = { activeOverlay = null })
                }
            }
        }
    }
}
