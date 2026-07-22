"""
download_dataset.py
--------------------
Downloads the 'Lizard' class from Open Images V7 via the AWS Open Data mirror.

WHY NOT fiftyone.zoo?
  The train split annotation CSV is ~4.8 GB. FiftyOne loads it entirely into
  RAM before filtering, which triggers the Linux OOM killer on most machines.

WHY NOT Google Storage (GCS)?
  As of 2024 the openimages GCS bucket is no longer publicly accessible
  (returns HTTP 403 for anonymous requests). Images are served from the AWS
  Open Data mirror instead: s3://open-images-dataset  (unsigned / no-auth).

THIS APPROACH instead:
  1. Downloads only the small per-split annotation CSVs from GCS
     (validation: ~25 MB, test: ~77 MB — manageable on any machine).
  2. Filters rows where LabelName == '/m/04m9y' (Open Images label for Lizard).
  3. Downloads only the matching images via the AWS Open Data S3 bucket
     using boto3 with unsigned (anonymous) requests — no AWS credentials needed.
  4. Writes YOLO-format .txt label files alongside each image.
  5. Writes a dataset.yaml ready for YOLOv8 training on Kaggle.

Usage
-----
    pip install requests pandas tqdm boto3
    python download_dataset.py

Output layout
-------------
ml/data/lizard_yolo/
    dataset.yaml
    train/
        images/   ← JPEG images (from validation split)
        labels/   ← YOLO .txt annotations
    val/
        images/   ← JPEG images (from test split)
        labels/
"""

import io
import json
import shutil
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import boto3
import botocore
import pandas as pd
import requests
from tqdm import tqdm

# ── Configuration ──────────────────────────────────────────────────────────────

# Open Images label code for "Lizard"
# Verify at: https://storage.googleapis.com/openimages/v5/class-descriptions-boxable.csv
LIZARD_LABEL_CODE = "/m/04m9y"
LIZARD_CLASS_NAME = "Lizard"

# We use validation → YOLO train, and test → YOLO val.
# Reason: the train split annotation file is ~4.8 GB (OOM risk).
# The validation and test annotation files are ~43 MB each — safe on any machine.
SPLITS = {
    "validation": "train",  # OI split  →  YOLO split name
    "test":       "val",
}

MAX_SAMPLES = {
    "validation": 500,   # cap for YOLO train set
    "test":       150,   # cap for YOLO val set
}

# Public Google Storage URLs for Open Images V7 metadata
BBOX_CSV_URLS = {
    "validation": "https://storage.googleapis.com/openimages/v5/validation-annotations-bbox.csv",
    "test":       "https://storage.googleapis.com/openimages/v5/test-annotations-bbox.csv",
}

# AWS Open Data mirror for Open Images (no credentials needed — unsigned requests)
# Bucket: s3://open-images-dataset
# Object path: {split}/{image_id}.jpg
S3_BUCKET = "open-images-dataset"

OUTPUT_DIR      = Path(__file__).parent.parent / "data" / "lizard_yolo"
NUM_WORKERS     = 8      # parallel download threads
RETRY_COUNT     = 3      # retries per failed image

# ── Helpers ────────────────────────────────────────────────────────────────────


def download_csv(url: str, label: str) -> pd.DataFrame:
    """Stream-download a CSV from a URL and return it as a DataFrame."""
    print(f"  Downloading {label} annotation CSV …")
    print(f"  URL: {url}")
    resp = requests.get(url, stream=True, timeout=60)
    resp.raise_for_status()

    total = int(resp.headers.get("content-length", 0))
    buf = io.BytesIO()
    with tqdm(total=total, unit="B", unit_scale=True, desc=f"  {label}", ncols=80) as bar:
        for chunk in resp.iter_content(chunk_size=1 << 20):  # 1 MB chunks
            buf.write(chunk)
            bar.update(len(chunk))

    buf.seek(0)
    df = pd.read_csv(buf)
    print(f"  → {len(df):,} total annotations loaded.")
    return df


def filter_lizard_rows(df: pd.DataFrame, max_n: int) -> pd.DataFrame:
    """Keep only Lizard bounding-box rows, then cap at max_n unique images."""
    lizard_df = df[df["LabelName"] == LIZARD_LABEL_CODE].copy()
    print(f"  → {len(lizard_df):,} Lizard annotations across "
          f"{lizard_df['ImageID'].nunique()} images.")

    # Pick the first max_n unique image IDs (stable ordering)
    unique_ids = lizard_df["ImageID"].unique()[:max_n]
    return lizard_df[lizard_df["ImageID"].isin(unique_ids)]


def make_s3_client():
    """Create a boto3 S3 client configured for unsigned (anonymous) access."""
    return boto3.client(
        "s3",
        config=botocore.config.Config(
            signature_version=botocore.UNSIGNED,
            max_pool_connections=max(10, NUM_WORKERS),
        ),
    )


def download_image(image_id: str, oi_split: str, dest_path: Path, s3_client) -> bool:
    """Download a single image to dest_path via S3. Returns True on success."""
    if dest_path.exists():
        return True  # already cached

    s3_key = f"{oi_split}/{image_id}.jpg"
    for attempt in range(1, RETRY_COUNT + 1):
        try:
            s3_client.download_file(S3_BUCKET, s3_key, str(dest_path))
            return True
        except botocore.exceptions.ClientError as e:
            code = e.response["Error"]["Code"]
            if code in ("404", "NoSuchKey"):
                return False  # image not present in this split
            if attempt < RETRY_COUNT:
                time.sleep(1)
        except Exception:
            if attempt < RETRY_COUNT:
                time.sleep(1)
    return False


def write_yolo_label(
    image_id: str,
    rows: pd.DataFrame,
    img_w: int,
    img_h: int,
    label_path: Path,
) -> None:
    """
    Write a YOLO .txt annotation file for one image.
    Open Images bbox columns: XMin, XMax, YMin, YMax  (all relative [0..1]).
    YOLO format: class_id  cx  cy  w  h  (all relative [0..1]).
    """
    lines = []
    for _, row in rows.iterrows():
        x_min = float(row["XMin"])
        x_max = float(row["XMax"])
        y_min = float(row["YMin"])
        y_max = float(row["YMax"])

        cx = (x_min + x_max) / 2.0
        cy = (y_min + y_max) / 2.0
        w  = x_max - x_min
        h  = y_max - y_min

        # Clamp to [0, 1] to handle any floating-point edge cases
        cx = max(0.0, min(1.0, cx))
        cy = max(0.0, min(1.0, cy))
        w  = max(0.0, min(1.0, w))
        h  = max(0.0, min(1.0, h))

        lines.append(f"0 {cx:.6f} {cy:.6f} {w:.6f} {h:.6f}")

    label_path.write_text("\n".join(lines))


def process_split(
    oi_split: str,
    yolo_split: str,
    max_n: int,
) -> int:
    """
    Download and export one Open Images split to YOLO format.
    Returns the number of images successfully written.
    """
    print(f"\n{'=' * 60}")
    print(f"  Split: {oi_split}  →  YOLO/{yolo_split}  (max {max_n} images)")
    print(f"{'=' * 60}")

    # 1. Download + filter the annotation CSV
    df = download_csv(BBOX_CSV_URLS[oi_split], oi_split)
    lizard_df = filter_lizard_rows(df, max_n)
    del df  # free ~500 MB of RAM immediately

    image_ids = lizard_df["ImageID"].unique().tolist()
    print(f"  Downloading {len(image_ids)} images …")

    # 2. Prepare output directories
    img_dir   = OUTPUT_DIR / yolo_split / "images"
    label_dir = OUTPUT_DIR / yolo_split / "labels"
    img_dir.mkdir(parents=True, exist_ok=True)
    label_dir.mkdir(parents=True, exist_ok=True)

    # 3. Download images in parallel using S3 unsigned client
    success_ids = []
    s3_client = make_s3_client()

    def _download(image_id: str):
        dest = img_dir / f"{image_id}.jpg"
        ok = download_image(image_id, oi_split, dest, s3_client)
        return image_id, ok

    with ThreadPoolExecutor(max_workers=NUM_WORKERS) as pool:
        futures = {pool.submit(_download, iid): iid for iid in image_ids}
        with tqdm(total=len(image_ids), unit="img", ncols=80) as bar:
            for fut in as_completed(futures):
                iid, ok = fut.result()
                if ok:
                    success_ids.append(iid)
                bar.update(1)

    print(f"  ✔  {len(success_ids)} / {len(image_ids)} images downloaded.")

    # 4. Write YOLO label files for successfully downloaded images
    skipped = 0
    for image_id in success_ids:
        rows = lizard_df[lizard_df["ImageID"] == image_id]
        label_path = label_dir / f"{image_id}.txt"

        # We don't need actual pixel dimensions because Open Images bbox
        # coordinates are already normalised to [0..1].
        write_yolo_label(image_id, rows, img_w=1, img_h=1, label_path=label_path)

    if skipped:
        print(f"  ⚠  {skipped} images skipped (no annotations).")

    return len(success_ids)


def write_dataset_yaml(class_names: list) -> None:
    """Write dataset.yaml for YOLOv8."""
    yaml_content = f"""# Auto-generated by download_dataset.py
# Dataset: Open Images V7 — Lizard class only
path: {OUTPUT_DIR.resolve()}
train: train/images
val:   val/images

nc: {len(class_names)}
names: {json.dumps(class_names)}
"""
    yaml_path = OUTPUT_DIR / "dataset.yaml"
    yaml_path.write_text(yaml_content)
    print(f"\n✅  dataset.yaml written → {yaml_path}")


# ── Main ───────────────────────────────────────────────────────────────────────


def main() -> None:
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)

    counts = {}
    for oi_split, yolo_split in SPLITS.items():
        counts[yolo_split] = process_split(
            oi_split=oi_split,
            yolo_split=yolo_split,
            max_n=MAX_SAMPLES[oi_split],
        )

    write_dataset_yaml([LIZARD_CLASS_NAME])

    # ── Summary ────────────────────────────────────────────────────────────────
    print("\n" + "=" * 60)
    print("  Dataset download complete!")
    print(f"  Output directory : {OUTPUT_DIR}")
    print(f"  Class            : {LIZARD_CLASS_NAME}  (label code {LIZARD_LABEL_CODE})")
    for split, n in counts.items():
        print(f"  {split:5s} images    : {n}")
    print("""
Next steps:
  1. Inspect a few images to verify bounding boxes look correct.
  2. Zip the lizard_yolo/ folder:
       cd ml/data && zip -r lizard_yolo.zip lizard_yolo/
  3. Upload lizard_yolo.zip to Kaggle as a dataset.
  4. Open ml/notebooks/lizard_detection_training.ipynb on Kaggle and run.
""")
    print("=" * 60)


if __name__ == "__main__":
    main()
