# Android App

Offline Lizard Lens — on-device lizard detection using **YOLOv8n** (single class) on **TFLite FP16**.

## Stack

- Kotlin 2.0+
- Jetpack Compose + Material 3
- CameraX (ImageAnalysis @ 10 FPS)
- TensorFlow Lite (FP16, XNNPACK default)
- Hilt (DI)
- Room (detection history)
- Min SDK 26 (Android 8.0) / Target SDK 35

## Module Structure

- `:app` — Application entry point, Hilt setup, navigation, UI screens
- `:core:model` — Data classes: `Detection`, `DetectionResult`, `DetectionSource`
- `:core:inference` — TFLite inference engine, NMS processor, preprocessing
- `:core:camera` — CameraX integration, thermal listener
- `:core:data` — Room database, DAO, repository
- `:core:ui` — Shared composables, theme

## Build

Open `android/` in Android Studio and sync Gradle.

Or from command line:
```bash
cd android
./gradlew assembleDebug
```

## Navigation

Camera-first layout. Camera fills the screen. Image, Video, History, and Settings open as slide-up overlays from top bar icons.
