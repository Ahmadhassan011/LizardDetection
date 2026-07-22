# LizardDetection

Offline Lizard Lens — on-device lizard detection using **YOLOv8n** (single class) on **TFLite FP16**, deployed on Android via CameraX.

## Project Structure

- `ml/` — Python pipeline: FiftyOne dataset download + YOLOv8n training notebook (Kaggle)
- `android/` — Kotlin Android app: Jetpack Compose UI, CameraX, TFLite inference, Room history
- `docs/` — Architecture docs and ADRs

## Architecture (Updated)

**Single-stage detection only.** The original two-stage design (YOLO + MobileNetV3 classifier) was dropped in favour of a single YOLOv8n model detecting one class: `lizard`. This reduces APK size, latency, and complexity.

### ML Pipeline
1. Download `Lizard` class from Open Images V7 via FiftyOne → `ml/data/lizard_yolo/`
2. Upload dataset to Kaggle
3. Train YOLOv8n on Kaggle GPU via `ml/notebooks/lizard_detection_training.ipynb`
4. Export → `yolov8n_lizard.tflite` (FP16, 416×416 input)

### Android App Modes
- **Live Camera** — CameraX ImageAnalysis (10 FPS sustained) → real-time bounding boxes (home screen, fills viewport)
- **Image Detection** — Gallery picker → single-image inference (slide-up overlay from top bar)
- **Video Detection** — Frame-by-frame inference on picked video (slide-up overlay from top bar)
- **History Panel** — Room-persisted detection log (slide-up overlay from top bar)

### Inference Strategy
- **Default:** CPU via XNNPACK (deterministic, ~25ms at 416×416)
- **Optional:** GPU delegate for high-end devices, silent fallback to CPU
- **Avoid:** NNAPI — requires per-OEM accelerator strings; auto-selection picks NPU which is 5× slower for YOLO
- **NMS:** On-device in Kotlin (~8400 anchors, IoU threshold 0.45)
- **Thermal:** PowerManager listener — moderate→indicator, severe→drop to 3 FPS, critical→stop camera

## Agent skills

### Issue tracker

Issues live as markdown files under `.scratch/<feature>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Five canonical roles: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context — one `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
