<div align="center">

# LizardLens

**Offline lizard detection for field researchers**

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0+-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![YOLOv8](https://img.shields.io/badge/YOLOv8n-TFLite-00D4AA?style=flat-square)](https://docs.ultralytics.com)
[![CameraX](https://img.shields.io/badge/CameraX-1.4-3DDC84?style=flat-square&logo=android&logoColor=white)](https://developer.android.com/media/camera/camerax)
[![License](https://img.shields.io/badge/License-MIT-blue?style=flat-square)](LICENSE)

LizardLens is a fully offline Android app that detects lizards in real time using a YOLOv8n model running on-device via TensorFlow Lite. Built for field researchers who need reliable detection without network connectivity.

[Features](#features) | [Prerequisites](#prerequisites) | [ML Pipeline](#ml-pipeline) | [Android App](#android-app) | [Model](#model)

</div>

---

## Features

- **Live Camera Detection** — Real-time bounding boxes at 10 FPS via CameraX ImageAnalysis
- **Image Detection** — Pick a photo from gallery, run inference, view results
- **Video Detection** — Frame-by-frame analysis of picked videos with progress tracking
- **Detection History** — Room-persisted log of all detections with source filtering
- **Thermal Management** — Automatic FPS throttling based on device temperature
- **CPU & GPU Inference** — XNNPACK default with optional GPU delegate and silent fallback

---

## Prerequisites

### ML Pipeline (training only)

- Python 3.12+

### Android App

- Android Studio Ladybug (2024.2.1) or later
- JDK 17
- Android SDK 35 (min SDK 26 / Android 8.0)
- Physical Android device (recommended for camera + inference testing)

---

## ML Pipeline

The model is trained on the **Lizard** class from [Open Images V7](https://storage.googleapis.com/openimages/web/index.html) using YOLOv8n.

### 1. Download the dataset

```bash
cd ml
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python scripts/download_dataset.py
```

This downloads ~500 training and ~150 validation images to `ml/data/lizard_yolo/`.

### 2. Upload to Kaggle

```bash
kaggle datasets create -p ml/data/lizard_yolo --dir-mode zip
```

### 3. Train on Kaggle

Open `ml/notebooks/lizard_detection_training.ipynb` on Kaggle, set `EPOCHS = 100`, and run all cells.

### 4. Export the model

The notebook exports `yolov8n_lizard.tflite` (FP32, ~12 MB). Copy it into the Android assets:

```bash
kaggle kernels output codecraft01/lizard-detection-training -p /tmp/out
cp /tmp/out/yolov8n_lizard.tflite android/app/src/main/assets/
cp /tmp/out/model_metadata.json   android/app/src/main/assets/
```

---

## Android App

### Build

Open the `android/` directory in Android Studio and sync Gradle.

Or build from the command line:

```bash
cd android
./gradlew assembleDebug
```

### Run

Install on a connected device:

```bash
./gradlew installDebug
```

Open **LizardLens** from the app drawer. Tap the **DETECT** button to start live inference.

### App Modes

| Mode | Description |
|---|---|
| **Live Camera** | Full-bleed camera preview with real-time bounding boxes |
| **Image Detection** | Pick a photo, run single-image inference |
| **Video Detection** | Pick a video, process frame-by-frame with progress bar |
| **History** | Browse and filter past detections (Camera / Image / Video) |
| **Settings** | Confidence threshold, acceleration mode, thermal warnings |

---

## Model

| Property | Value |
|---|---|
| Architecture | YOLOv8n (nano) |
| Input | `[1, 416, 416, 3]` float32 |
| Output | `[1, 5, 8400]` raw anchors |
| Quantisation | FP32 (~12 MB) |
| Confidence threshold | 0.45 |
| IoU threshold | 0.45 |
| NMS | On-device in Kotlin (~8,400 anchors) |
| Inference (CPU) | ~25 ms via XNNPACK |
| Inference (GPU) | Optional delegate, silent fallback to CPU |

> [!NOTE]
> The current model was trained for 5 epochs as a pipeline test. Retrain with `EPOCHS = 100` for production-quality results.

---

## Known Issues

- **Model undertrained** — Current checkpoint is a 5-epoch pipeline test (mAP ~ 0). Set `EPOCHS = 100` and retrain on Kaggle.
- **FP32 instead of FP16** — Ultralytics 8.4.83+ dropped FP16 from the `litert` exporter. Use `quantize=8` (INT8, ~3 MB) for a smaller model.
- **GPU delegate unavailable on some devices** — The app silently falls back to CPU inference when GPU is not supported.

---

## Learn More

- [YOLOv8 Documentation](https://docs.ultralytics.com)
- [TensorFlow Lite on Android](https://www.tensorflow.org/lite/android)
- [CameraX Guide](https://developer.android.com/media/camera/camerax)
- [Open Images V7 Dataset](https://storage.googleapis.com/openimages/web/index.html)
