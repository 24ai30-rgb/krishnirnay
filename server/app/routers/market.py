from fastapi import APIRouter, Depends, HTTPException

from app.core.security import verify_api_key
from app.schemas.market import MarketResponse
from app.services.market_provider import MarketProviderError, get_market_price

router = APIRouter(prefix="/v1", tags=["market"], dependencies=[Depends(verify_api_key)])


@router.get("/market", response_model=MarketResponse)
async def market(crop: str, state: str | None = None, district: str | None = None) -> MarketResponse:
    try:
        data = await get_market_price(crop=crop, state=state, district=district)
    except MarketProviderError as exc:
        raise HTTPException(
            status_code=503,
            detail={"error": "market_unavailable", "message": str(exc)},
        ) from exc
    return MarketResponse(**data)
