from pydantic import BaseModel


class DailyForecast(BaseModel):
    day_label: str
    condition: str
    high_c: float
    low_c: float
    rain_chance_pct: int | None = None
    rainfall_mm: float | None = None


class WeatherResponse(BaseModel):
    location_label: str
    current_temp_c: float
    feelslike_c: float | None = None
    condition: str
    condition_code: int | None = None
    condition_icon_url: str | None = None
    wind_kph: float
    wind_direction: str | None = None
    humidity_pct: float
    cloud_pct: int | None = None
    pressure_mb: float | None = None
    visibility_km: float | None = None
    uv_index: float | None = None
    rain_chance_pct: float
    rainfall_mm: float | None = None
    daily: list[DailyForecast] = []
    source: str
