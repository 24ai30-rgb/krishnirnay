from pathlib import Path

import numpy as np
from ai_edge_litert.interpreter import Interpreter
from PIL import Image


# ============================================================
# MODEL PATH
# ============================================================
# TFLite, not the original .keras — full TensorFlow alone costs ~300MB+ RAM
# just to import, which doesn't fit a free-tier host's 512MB budget. This
# runs the identical trained weights (float32, no quantization) through the
# lightweight LiteRT interpreter instead: verified numerically equivalent to
# the original Keras model (max abs diff ~5e-6, identical top-1 class) via a
# one-time conversion script, not re-run at import time.

BASE_DIR = Path(__file__).resolve().parent

MODEL_PATH = BASE_DIR / "disease_model_finetuned_best.tflite"


# ============================================================
# CLASS NAMES
# ============================================================

CLASS_NAMES = [
    "Apple_apple_scab",
    "Apple_black_rot",
    "Apple_cedar_apple_rust",
    "Apple_healthy",

    "Corn_cercospora_leaf_spot_gray_leaf_spot",
    "Corn_common_rust",
    "Corn_healthy",
    "Corn_northern_leaf_blight",

    "Pepper_bacterial_spot",
    "Pepper_healthy",

    "Potato_early_blight",
    "Potato_healthy",
    "Potato_late_blight",

    "Tomato_bacterial_spot",
    "Tomato_early_blight",
    "Tomato_healthy",
    "Tomato_late_blight",
    "Tomato_leaf_mold",
    "Tomato_septoria_leaf_spot",
    "Tomato_spider_mites_two-spotted_spider_mite",
    "Tomato_target_spot",
    "Tomato_tomato_mosaic_virus",
    "Tomato_tomato_yellow_leaf_curl_virus",
]


# ============================================================
# LOAD MODEL
# ============================================================

print("Loading KrishiNirnay Disease Model...")

_interpreter = Interpreter(model_path=str(MODEL_PATH))
_interpreter.allocate_tensors()
_input_details = _interpreter.get_input_details()[0]
_output_details = _interpreter.get_output_details()[0]

print("[OK] Disease model loaded successfully.")


# ============================================================
# IMAGE PREPROCESSING
# ============================================================

IMG_SIZE = (224, 224)


def preprocess_image(image_path: str):
    image = Image.open(image_path).convert("RGB")

    image = image.resize(IMG_SIZE)

    image_array = np.array(
        image,
        dtype=np.float32,
    )

    image_array = image_array / 255.0

    image_array = np.expand_dims(
        image_array,
        axis=0,
    )

    return image_array


# ============================================================
# DISEASE PREDICTION
# ============================================================

def predict_disease(image_path: str):

    image = preprocess_image(image_path)

    _interpreter.set_tensor(_input_details["index"], image)
    _interpreter.invoke()
    probabilities = _interpreter.get_tensor(_output_details["index"])[0]

    predicted_index = int(
        np.argmax(probabilities)
    )

    confidence = float(
        probabilities[predicted_index] * 100
    )

    predicted_class = CLASS_NAMES[
        predicted_index
    ]

    # --------------------------------------------------------
    # Split crop and disease
    # --------------------------------------------------------

    parts = predicted_class.split("_", 1)

    if len(parts) == 2:
        crop = parts[0]
        disease = parts[1]
    else:
        crop = "Unknown"
        disease = predicted_class

    # --------------------------------------------------------
    # Status
    # --------------------------------------------------------

    if disease.lower() == "healthy":
        status = "HEALTHY"
    else:
        status = "DISEASE DETECTED"

    # --------------------------------------------------------
    # Top 3 predictions
    # --------------------------------------------------------

    top_indices = np.argsort(
        probabilities
    )[-3:][::-1]

    top_predictions = []

    for index in top_indices:
        top_predictions.append(
            {
                "class": CLASS_NAMES[index],
                "confidence": round(
                    float(
                        probabilities[index] * 100
                    ),
                    2,
                ),
            }
        )

    # --------------------------------------------------------
    # Final response
    # --------------------------------------------------------

    return {
        "crop": crop,
        "prediction": disease,
        "confidence": round(
            confidence,
            2,
        ),
        "status": status,
        "top_predictions": top_predictions,
    }