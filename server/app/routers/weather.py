from fastapi import APIRouter, Depends, HTTPException

from app.core.security import verify_api_key
from app.schemas.weather import WeatherResponse
from app.services.weather_provider import WeatherProviderError, get_weather

router = APIRouter(prefix="/v1", tags=["weather"], dependencies=[Depends(verify_api_key)])


@router.get("/weather", response_model=WeatherResponse)
async def weather(
    state: str = "",
    district: str | None = None,
    latitude: float | None = None,
    longitude: float | None = None,
) -> WeatherResponse:
    try:
        data = await get_weather(state=state, district=district, latitude=latitude, longitude=longitude)
    except WeatherProviderError as exc:
        raise HTTPException(
            status_code=503,
            detail={"error": "weather_unavailable", "message": str(exc)},
        ) from exc
    return WeatherResponse(**data)
