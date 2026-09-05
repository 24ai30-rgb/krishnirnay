from pydantic import BaseModel, ConfigDict


class DiseaseTopK(BaseModel):
    label: str
    confidence: float


class DiseaseResponse(BaseModel):
    model_config = ConfigDict(protected_namespaces=())

    label: str
    display_name: str
    confidence: float
    top_k: list[DiseaseTopK]
    risk_level: str
    model_version: str
