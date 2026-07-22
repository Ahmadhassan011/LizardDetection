# ML Pipeline — Lizard Detection

YOLOv8n single-class detector trained on Open Images V7, exported to TFLite FP32.

---

## Setup

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

## Workflow

**1. Download dataset**
```bash
python scripts/download_dataset.py
# → ml/data/lizard_yolo/  (~103 train / 150 val images)
```

**2. Upload to Kaggle**
```bash
kaggle datasets create -p ml/data/lizard_yolo --dir-mode zip
# Dataset: codecraft01/lizard-yolo
# Mount path on Kaggle: /kaggle/input/datasets/codecraft01/lizard-yolo/
```

**3. Train on Kaggle**

Open `notebooks/lizard_detection_training.ipynb`, set `EPOCHS = 100`, run all.

**4. Download artifacts**
```bash
kaggle kernels output codecraft01/lizard-detection-training -p /tmp/out
cp /tmp/out/yolov8n_lizard.tflite android/app/src/main/assets/
cp /tmp/out/model_metadata.json   android/app/src/main/assets/
cp /tmp/out/yolov8n_lizard_best.pt ml/models/
```

---

## Model

| Property | Value |
|---|---|
| Input | `[1, 416, 416, 3]` float32 |
| Output | `[1, 5, 8400]` raw anchors — NMS done on-device in Kotlin |
| Quantisation | FP32 (~12 MB) |
| Conf / IoU threshold | 0.45 / 0.45 |

---

## Known Issues

**Model not trained** — current checkpoint is 5 epochs (pipeline test), mAP ≈ 0. Set `EPOCHS = 100` and retrain.

**FP32 instead of FP16** — Ultralytics 8.4.83+ dropped FP16 from the `litert` exporter. To get a smaller model use `quantize=8` (INT8, ~3 MB) in the export call.

**GPU unavailable** — Kaggle assigned a P100 (sm_60), incompatible with torch 2.10+cu128 (requires sm_70+). Training runs on CPU (~90 min for 100 epochs, within the 9hr limit). Re-queuing may assign a T4.

**Kernel shows ERROR** — v15 errors only in the post-export TFLite verification cell, not in training or export. The `.tflite` was produced successfully. Fix: add `device='cpu'` to that cell or remove it.
