from pathlib import Path

import joblib
import pandas as pd


class AgriculturalRiskModel:
    """
    Loads the trained KrishiNirnay agricultural risk model once
    and performs predictions using the original feature names.
    """

    def __init__(self, model_path: str):
        self.model_path = Path(model_path)

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
        temperature: float,
        humidity: float,
    ) -> dict:
        # IMPORTANT:
        # These column names/order match the training dataset.
        input_data = pd.DataFrame(
            [
                {
                    "crop_ID": crop_id,
                    "soil_type": soil_type,
                    "Seedling_Stage": seedling_stage,
                    "MOI": moi,
                    "temp": temperature,
                    "humidity": humidity,
                }
            ]
        )

        prediction = self.model.predict(input_data)
        risk_class = int(prediction[0])

        result = {
            "risk_class": risk_class,
            "model_version": self.model_version,
        }

        # Return probabilities when the trained model supports them.
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

        else:
            result["probabilities"] = {}
            result["confidence"] = None

        return result