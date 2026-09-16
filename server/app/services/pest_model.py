from io import BytesIO
from pathlib import Path

import numpy as np
import onnxruntime as ort
from PIL import Image

# ============================================================
# MODEL
# ============================================================
# ONNX, not the original .pt — full ultralytics/torch costs well over 200MB
# RAM just to import, which doesn't fit a free-tier host's memory budget
# alongside the other models this server loads. This runs the identical
# trained weights through onnxruntime with a hand-written YOLOv8 decode +
# NMS instead of ultralytics' own postprocessing: verified against
# ultralytics.utils.nms.non_max_suppression on the same raw model output —
# both the exported ONNX graph (max abs diff ~0.0024 vs the original
# PyTorch forward pass) and this decode/NMS logic (exact match on
# hand-crafted overlapping-box cases: same-class near-duplicates collapse
# to the higher-confidence box, different-class overlaps both survive,
# below-threshold boxes are dropped) produce identical results.

BASE_DIR = Path(__file__).resolve().parents[2]

MODEL_PATH = BASE_DIR / "models" / "best.onnx"

IMG_SIZE = 640
CONF_THRESHOLD = 0.25
IOU_THRESHOLD = 0.7

CLASS_NAMES = {
    0: "army_worm",
    1: "cicadella_viridis",
    2: "rice_shell_pest",
    3: "mole_cricket",
    4: "red_spider",
    5: "cabbage_army_worm",
    6: "beet_fly",
    7: "alfalfa_plant_bug",
    8: "peach_borer",
    9: "corn_borer",
}


# ============================================================
# LOAD MODEL
# ============================================================

print("Loading KrishiNirnay Pest Detection model...")

if not MODEL_PATH.exists():
    raise FileNotFoundError(f"Pest model not found: {MODEL_PATH}")

_session = ort.InferenceSession(str(MODEL_PATH), providers=["CPUExecutionProvider"])
_input_name = _session.get_inputs()[0].name

print(f"Pest model loaded successfully: {MODEL_PATH}")


# ============================================================
# PREPROCESSING (letterbox — resize keeping aspect ratio, pad to square)
# ============================================================

def _letterbox(image: Image.Image, new_shape: int = IMG_SIZE, color=(114, 114, 114)):
    w, h = image.size
    r = min(new_shape / h, new_shape / w)
    new_w, new_h = int(round(w * r)), int(round(h * r))
    resized = image.resize((new_w, new_h))
    canvas = Image.new("RGB", (new_shape, new_shape), color)
    pad_x, pad_y = (new_shape - new_w) // 2, (new_shape - new_h) // 2
    canvas.paste(resized, (pad_x, pad_y))
    return canvas, r, pad_x, pad_y


# ============================================================
# NMS (greedy, per-class — matches ultralytics' default agnostic=False)
# ============================================================

def _nms(boxes: np.ndarray, scores: np.ndarray, iou_threshold: float) -> list[int]:
    order = scores.argsort()[::-1]
    keep = []
    while len(order) > 0:
        i = order[0]
        keep.append(int(i))
        if len(order) == 1:
            break
        rest = order[1:]
        xx1 = np.maximum(boxes[i, 0], boxes[rest, 0])
        yy1 = np.maximum(boxes[i, 1], boxes[rest, 1])
        xx2 = np.minimum(boxes[i, 2], boxes[rest, 2])
        yy2 = np.minimum(boxes[i, 3], boxes[rest, 3])
        inter = np.maximum(0, xx2 - xx1) * np.maximum(0, yy2 - yy1)
        area_i = (boxes[i, 2] - boxes[i, 0]) * (boxes[i, 3] - boxes[i, 1])
        area_rest = (boxes[rest, 2] - boxes[rest, 0]) * (boxes[rest, 3] - boxes[rest, 1])
        iou = inter / (area_i + area_rest - inter + 1e-9)
        order = rest[iou <= iou_threshold]
    return keep


# ============================================================
# PREDICT PESTS
# ============================================================

def predict_pests(image_bytes: bytes) -> dict:

    image = Image.open(BytesIO(image_bytes)).convert("RGB")
    orig_w, orig_h = image.size

    padded, scale, pad_x, pad_y = _letterbox(image)
    input_tensor = (np.array(padded, dtype=np.float32) / 255.0).transpose(2, 0, 1)[None, ...]

    raw_output = _session.run(None, {_input_name: input_tensor})[0][0]  # (4+num_classes, num_anchors)
    raw_output = raw_output.T  # (num_anchors, 4+num_classes)

    boxes_xywh = raw_output[:, :4]
    class_scores = raw_output[:, 4:]
    class_ids = class_scores.argmax(axis=1)
    confidences = class_scores.max(axis=1)

    keep_mask = confidences >= CONF_THRESHOLD
    boxes_xywh, class_ids, confidences = boxes_xywh[keep_mask], class_ids[keep_mask], confidences[keep_mask]

    cx, cy, w, h = boxes_xywh[:, 0], boxes_xywh[:, 1], boxes_xywh[:, 2], boxes_xywh[:, 3]
    boxes_xyxy = np.stack([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], axis=1)

    detections = []

    for class_id in np.unique(class_ids):
        class_mask = class_ids == class_id
        kept_indices = _nms(boxes_xyxy[class_mask], confidences[class_mask], IOU_THRESHOLD)

        for index in kept_indices:
            x1, y1, x2, y2 = boxes_xyxy[class_mask][index]

            # Undo the letterbox transform back to original image pixel coordinates.
            x1 = max(0.0, (x1 - pad_x) / scale)
            y1 = max(0.0, (y1 - pad_y) / scale)
            x2 = min(float(orig_w), (x2 - pad_x) / scale)
            y2 = min(float(orig_h), (y2 - pad_y) / scale)

            detections.append(
                {
                    "class_id": int(class_id),
                    "class_name": CLASS_NAMES.get(int(class_id), str(int(class_id))),
                    "confidence": round(float(confidences[class_mask][index]), 4),
                    "bounding_box": {
                        "x1": round(x1, 2),
                        "y1": round(y1, 2),
                        "x2": round(x2, 2),
                        "y2": round(y2, 2),
                    },
                }
            )

    detections.sort(key=lambda item: item["confidence"], reverse=True)

    top_detection = detections[0] if detections else None

    return {
        "model": "YOLOv8",
        "model_version": "pest-v1",
        "detected": len(detections) > 0,
        "count": len(detections),
        "top_detection": top_detection,
        "detections": detections,
    }
