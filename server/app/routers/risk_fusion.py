from fastapi import APIRouter, Depends, HTTPException

from app.core.security import verify_api_key
from app.config import get_settings
from app.schemas.risk import RiskRequest, RiskResponse
from app.services.risk_model import AgriculturalRiskModel


router = APIRouter(
    prefix="/v1",
    tags=["risk"],
    dependencies=[Depends(verify_api_key)],
)


_model: AgriculturalRiskModel | None = None


def get_risk_model() -> AgriculturalRiskModel:
    global _model

    if _model is None:
        settings = get_settings()

        _model = AgriculturalRiskModel(
            settings.risk_model_path
        )

    return _model


@router.post(
    "/predict/risk-fusion",
    response_model=RiskResponse,
)
async def predict_risk(request: RiskRequest) -> RiskResponse:
    try:
        model = get_risk_model()

        result = model.predict(
            crop_id=request.crop_ID,
            soil_type=request.soil_type,
            seedling_stage=request.Seedling_Stage,
            moi=request.MOI,
            temperature=request.temp,
            humidity=request.humidity,
        )

        return RiskResponse(**result)

    except FileNotFoundError as exc:
        raise HTTPException(
            status_code=500,
            detail={
                "error": "model_not_found",
                "message": str(exc),
            },
        )

    except Exception as exc:
        raise HTTPException(
            status_code=500,
            detail={
                "error": "prediction_failed",
                "message": str(exc),
            },
        )