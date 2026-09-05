from pathlib import Path

import numpy as np
import tensorflow as tf
from PIL import Image


BASE_DIR = Path(__file__).resolve().parent

MODEL_PATH = (
    BASE_DIR.parent
    / "services"
    / "disease_ai"
    / "disease_model_finetuned_best.keras"
)


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


class DiseaseModel:

    def __init__(self):
        print("Loading KrishiNirnay Disease Model...")

        self.model = tf.keras.models.load_model(
            MODEL_PATH
        )

        self.model_version = "disease-v1"

        print("✅ Disease model loaded successfully.")

    def predict(self, image_path: str):

        image = Image.open(image_path).convert("RGB")
        image = image.resize((224, 224))

        image_array = np.array(
            image,
            dtype=np.float32,
        ) / 255.0

        image_array = np.expand_dims(
            image_array,
            axis=0,
        )

        probabilities = self.model.predict(
            image_array,
            verbose=0,
        )[0]

        index = int(np.argmax(probabilities))

        confidence = float(
            probabilities[index] * 100
        )

        predicted_class = CLASS_NAMES[index]

        parts = predicted_class.split("_", 1)

        if len(parts) == 2:
            crop = parts[0]
            disease = parts[1]
        else:
            crop = "Unknown"
            disease = predicted_class

        status = (
            "HEALTHY"
            if disease.lower() == "healthy"
            else "DISEASE DETECTED"
        )

        return {
            "crop": crop,
            "prediction": disease,
            "confidence": round(confidence, 2),
            "status": status,
        }