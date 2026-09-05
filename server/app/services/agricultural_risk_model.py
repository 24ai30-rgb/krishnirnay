from __future__ import annotations

from pathlib import Path
from typing import Any

import joblib
import pandas as pd


class AgriculturalRiskModel:
    """
    Loads the trained Agricultural Risk model once and performs inference.

    Expected model inputs:
        crop_ID
        soil_type
        Seedling_Stage
        MOI
        temp
        humidity

    Expected target classes:
        0, 1, 2

    IMPORTANT:
    The semantic meaning of classes 0/1/2 has not been verified,
    so this service intentionally returns the numeric class unchanged.
    """

    def __init__(
        self,
        model_path: str = "models/agricultural_risk_final.pkl",
    ) -> None:
        project_root = Path(__file__).resolve().parents[2]
        self.model_path = project_root / model_path

        if not self.model_path.exists():
            raise FileNotFoundError(
                f"Agricultural risk model not found: {self.model_path}"
            )

        self.model = joblib.load(self.model_path)

        self.model_version = "agricultural-risk-v1"

    def predict(
        self,
        crop_id: str,
        soil_type: str,
        seedling_stage: str,
        moi: float,
        temp: float,
        humidity: float,
    ) -> dict[str, Any]:

        input_data = pd.DataFrame(
            [
                {
                    "crop_ID": crop_id,
                    "soil_type": soil_type,
                    "Seedling_Stage": seedling_stage,
                    "MOI": moi,
                    "temp": temp,
                    "humidity": humidity,
                }
            ]
        )

        prediction = self.model.predict(input_data)

        risk_class = int(prediction[0])

        result: dict[str, Any] = {
            "risk_class": risk_class,
            "model_version": self.model_version,
        }

        # Return class probabilities when supported by the saved model.
        if hasattr(self.model, "predict_proba"):
            probabilities = self.model.predict_proba(input_data)[0]

            result["probabilities"] = {
                str(int(class_id)): float(probability)
                for class_id, probability in zip(
                    self.model.classes_,
                    probabilities,
                )
            }

            result["confidence"] = float(max(probabilities))

        return result