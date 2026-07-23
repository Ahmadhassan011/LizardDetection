# Android App

Offline Lizard Lens — on-device lizard detection using **YOLOv8n** (single class) on **TFLite FP32**.

## Stack

- Kotlin 2.0+
- Jetpack Compose + Material 3
- CameraX (ImageAnalysis @ 10 FPS)
- TensorFlow Lite (FP32, XNNPACK default, optional GPU delegate)
- Hilt (DI)
- Room (detection history)
- Min SDK 26 (Android 8.0) / Target SDK 35

## Module Structure

- `:app` — Application entry point, Hilt setup, navigation, UI screens, ViewModels (`CameraViewModel`, `ImageDetectionViewModel`, `VideoDetectionViewModel`, `SettingsViewModel`, `HistoryViewModel`)
- `:core:model` — Data classes: `Detection`, `DetectionResult`, `DetectionSource`
- `:core:inference` — TFLite inference engine (`InferenceEngine` interface, `TfliteInferenceEngine`), NMS processor, preprocessing
- `:core:camera` — CameraX integration (`CameraXImageAnalyzer`), thermal listener, YUV→Bitmap conversion
- `:core:data` — Room database (`AppDatabase`), DAO, `DetectionRepository`, `DetectionConfigStore`, Hilt DI (`DataModule`)
- `:core:ui` — Shared composables (`DetectionOverlays`, `PermissionCard`), Material 3 theme (`Color.kt`, `Theme.kt`)

## Build

Open `android/` in Android Studio and sync Gradle.

Or from command line:
```bash
cd android
./gradlew assembleDebug
```

## Navigation

Camera-first layout. Camera fills the screen. Image, Video, History, and Settings open as slide-up overlays from top bar icons.

## Tests

Unit tests live alongside each module:

```bash
cd android
./gradlew test
```

- `:app` — ViewModel tests (`CameraViewModelTest`, `SettingsViewModelTest`, `HistoryViewModelTest`)
- `:core:inference` — `TfliteInferenceEngineTest`, `NmsProcessorTest`, `PreprocessorTest`
- `:core:camera` — `ThermalManagerTest`
- `:core:data` — `DetectionDaoTest`, `DetectionRepositoryTest`, `ConvertersTest`
