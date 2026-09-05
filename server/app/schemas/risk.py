from pydantic import BaseModel, Field


class RiskRequest(BaseModel):
    crop_ID: str
    soil_type: str
    Seedling_Stage: str

    MOI: float = Field(..., description="Soil moisture index")
    temp: float = Field(..., description="Temperature in Celsius")
    humidity: float = Field(..., description="Relative humidity percentage")


class RiskResponse(BaseModel):
    risk_class: int
    confidence: float | None = None
    probabilities: dict[str, float]
    model_version: str