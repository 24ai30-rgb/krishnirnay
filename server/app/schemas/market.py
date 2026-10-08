from pydantic import BaseModel


class MandiRecord(BaseModel):
    """One market's raw row from the government dataset — kept separate from
    the flattened top-level fields so a farmer can compare mandis instead of
    only ever seeing the single best one."""

    market: str
    district: str
    state: str
    commodity: str
    variety: str | None = None
    grade: str | None = None
    arrival_date: str | None = None
    min_price: float | None = None
    max_price: float | None = None
    modal_price: float | None = None


class MarketResponse(BaseModel):
    crop: str
    market: str | None = None
    location: str | None = None
    current_price_per_quintal: float | None = None
    min_price_per_quintal: float | None = None
    max_price_per_quintal: float | None = None
    average_price_per_quintal: float | None = None
    source: str
    arrival_date: str | None = None
    variety: str | None = None
    grade: str | None = None
    district: str | None = None
    state: str | None = None
    markets: list[MandiRecord] = []
    trend: str = "UNKNOWN"
