from pathlib import Path

from PIL import Image
from io import BytesIO

from ultralytics import YOLO


# =========================================================
# MODEL PATH
# =========================================================

BASE_DIR = Path(__file__).resolve().parents[2]

MODEL_PATH = BASE_DIR / "models" / "best.pt"


# =========================================================
# LOAD YOLO MODEL
# =========================================================

print("Loading KrishiNirnay Pest Detection model...")

if not MODEL_PATH.exists():
    raise FileNotFoundError(
        f"Pest model not found: {MODEL_PATH}"
    )

model = YOLO(str(MODEL_PATH))

print(
    f"Pest model loaded successfully: {MODEL_PATH}"
)


# =========================================================
# PREDICT PESTS
# =========================================================

def predict_pests(image_bytes: bytes) -> dict:

    # -----------------------------------------------------
    # Convert uploaded bytes → PIL image
    # -----------------------------------------------------

    image = Image.open(
        BytesIO(image_bytes)
    ).convert("RGB")


    # -----------------------------------------------------
    # YOLO INFERENCE
    # -----------------------------------------------------

    results = model.predict(
        source=image,
        conf=0.25,
        verbose=False,
    )


    detections = []


    # -----------------------------------------------------
    # PROCESS DETECTIONS
    # -----------------------------------------------------

    for result in results:

        names = result.names

        if result.boxes is None:
            continue


        for box in result.boxes:

            class_id = int(
                box.cls[0].item()
            )

            confidence = float(
                box.conf[0].item()
            )

            coordinates = (
                box.xyxy[0]
                .tolist()
            )

            x1, y1, x2, y2 = coordinates


            # ---------------------------------------------
            # CLASS NAME
            # ---------------------------------------------

            class_name = names.get(
                class_id,
                str(class_id),
            )


            detections.append(
                {
                    "class_id": class_id,
                    "class_name": class_name,
                    "confidence": round(
                        confidence,
                        4,
                    ),
                    "bounding_box": {
                        "x1": round(x1, 2),
                        "y1": round(y1, 2),
                        "x2": round(x2, 2),
                        "y2": round(y2, 2),
                    },
                }
            )


    # -----------------------------------------------------
    # SORT BY CONFIDENCE
    # -----------------------------------------------------

    detections.sort(
        key=lambda item: item["confidence"],
        reverse=True,
    )


    # -----------------------------------------------------
    # TOP PEST
    # -----------------------------------------------------

    top_detection = (
        detections[0]
        if detections
        else None
    )


    # -----------------------------------------------------
    # RESPONSE
    # -----------------------------------------------------

    return {
        "model": "YOLOv8",
        "model_version": "pest-v1",
        "detected": len(detections) > 0,
        "count": len(detections),
        "top_detection": top_detection,
        "detections": detections,
    }