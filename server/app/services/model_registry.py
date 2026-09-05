from app.services.disease_model import DiseaseModel
from app.services.agricultural_risk_model import AgriculturalRiskModel


_disease_model: DiseaseModel | None = None
_agricultural_risk_model: AgriculturalRiskModel | None = None


def set_disease_model(model: DiseaseModel) -> None:
    global _disease_model
    _disease_model = model


def get_disease_model() -> DiseaseModel | None:
    return _disease_model


def set_agricultural_risk_model(model: AgriculturalRiskModel) -> None:
    global _agricultural_risk_model
    _agricultural_risk_model = model


def get_agricultural_risk_model() -> AgriculturalRiskModel | None:
    return _agricultural_risk_model