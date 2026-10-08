"""Real weather via WeatherAPI.com's forecast endpoint. Reached only from
this server; Android never calls WeatherAPI.com directly and never sees
WEATHER_API_KEY. It still comes back through the exact same `GET /v1/weather`
contract (`app/schemas/weather.py`). Every failure (missing/invalid key,
quota exceeded, no matching location, network error, timeout, malformed
response) raises WeatherProviderError, which `app/routers/weather.py` turns
into an honest 503 — never a fabricated reading.

WeatherAPI.com's `q` query parameter accepts either "latitude,longitude" or
free-text location, and resolves/geocodes it server-side in the same call —
so, unlike the previous Open-Meteo integration, there is no separate
geocoding request:
- The farmer's saved latitude/longitude (Farm Setup, manual entry) is sent
  directly as "lat,lon" when both are present, and the farmer's own saved
  district/state text is kept as the displayed location label (WeatherAPI's
  own resolved place name for a raw coordinate pair, e.g. a specific
  neighbourhood, is not necessarily what the farmer expects to see).
- Otherwise the farmer's saved state/district text is sent as the `q` value
  directly, and the *response's* resolved location name is used as the
  label, exactly mirroring the old geocoding behaviour.
"""

from datetime import datetime

import httpx

from app.config import get_settings

FORECAST_URL = "https://api.weatherapi.com/v1/forecast.json"

# WeatherAPI.com's documented condition codes
# (https://www.weatherapi.com/docs/weather_conditions.json), collapsed onto
# the app's coarse WeatherCondition enum. Grouped by the code's own English
# description, not guessed: anything naming thunder is STORM, anything
# naming rain/drizzle/sleet/snow/ice is RAIN, anything naming
# haze/dust/smoke/fog/mist/overcast is CLOUDY, and the two clear-sky codes
# map to SUNNY/PARTLY_CLOUDY directly. An unrecognized code (WeatherAPI adds
# one after this list was written) falls back to CLOUDY, matching the old
# Open-Meteo provider's default.
_STORM_CODES = {1087, 1273, 1276, 1279, 1282}
_CLOUDY_CODES = {
    1006, 1009, 1012, 1015, 1018, 1021, 1024, 1027, 1030, 1033, 1036, 1039,
    1042, 1045, 1048, 1135, 1147,
}
_RAIN_CODES = {
    1063, 1066, 1069, 1072, 1114, 1117, 1150, 1153, 1168, 1171, 1180, 1183,
    1186, 1189, 1192, 1195, 1198, 1201, 1204, 1207, 1210, 1213, 1216, 1219,
    1222, 1225, 1237, 1240, 1243, 1246, 1249, 1252, 1255, 1258, 1261, 1264,
}


class WeatherProviderError(Exception):
    pass


def _condition_for(code: int) -> str:
    if code == 1000:
        return "SUNNY"
    if code == 1003:
        return "PARTLY_CLOUDY"
    if code in _STORM_CODES:
        return "STORM"
    if code in _RAIN_CODES:
        return "RAIN"
    return "CLOUDY"


def _error_message_for(response: httpx.Response) -> str:
    """Builds a safe, user-readable message — the WeatherAPI error body never
    contains the API key, but this still never echoes request internals
    beyond that body's own `message` field."""
    try:
        message = response.json().get("error", {}).get("message")
    except ValueError:
        message = None

    if response.status_code == 401:
        return f"WeatherAPI rejected the request (invalid or missing API key): {message or 'unauthorized'}"
    if response.status_code == 403:
        return f"WeatherAPI access denied (quota or plan restriction): {message or 'forbidden'}"
    if response.status_code == 400:
        return f"WeatherAPI rejected the query: {message or 'invalid request'}"
    if response.status_code == 404:
        return f"WeatherAPI found no matching location: {message or 'not found'}"
    return f"WeatherAPI request failed with HTTP {response.status_code}: {message or 'unknown error'}"


async def _fetch_forecast(client: httpx.AsyncClient, api_key: str, query: str) -> dict:
    try:
        response = await client.get(
            FORECAST_URL,
            params={"key": api_key, "q": query, "days": 7, "aqi": "no", "alerts": "yes"},
        )
        response.raise_for_status()
    except httpx.HTTPStatusError as exc:
        raise WeatherProviderError(_error_message_for(exc.response)) from exc
    except httpx.HTTPError as exc:
        raise WeatherProviderError(f"Weather request failed: {exc}") from exc

    try:
        return response.json()
    except ValueError as exc:
        raise WeatherProviderError(f"Malformed weather response: {exc}") from exc


def _to_weather_response(payload: dict, location_label: str) -> dict:
    try:
        current = payload["current"]
        forecast_days = payload["forecast"]["forecastday"]
        current_condition = current["condition"]

        daily_items = [
            {
                "day_label": datetime.fromisoformat(day["date"]).strftime("%a"),
                "condition": _condition_for(day["day"]["condition"]["code"]),
                "high_c": day["day"]["maxtemp_c"],
                "low_c": day["day"]["mintemp_c"],
                "rain_chance_pct": day["day"].get("daily_chance_of_rain"),
                "rainfall_mm": day["day"].get("totalprecip_mm"),
            }
            for day in forecast_days
        ]
        today_rain_chance = forecast_days[0]["day"].get("daily_chance_of_rain") if forecast_days else None

        icon = current_condition.get("icon")
        return {
            "location_label": location_label,
            "current_temp_c": current["temp_c"],
            "feelslike_c": current.get("feelslike_c"),
            "condition": _condition_for(current_condition["code"]),
            "condition_code": current_condition.get("code"),
            "condition_icon_url": f"https:{icon}" if icon else None,
            "wind_kph": current["wind_kph"],
            "wind_direction": current.get("wind_dir"),
            "humidity_pct": current["humidity"],
            "cloud_pct": current.get("cloud"),
            "pressure_mb": current.get("pressure_mb"),
            "visibility_km": current.get("vis_km"),
            "uv_index": current.get("uv"),
            "rain_chance_pct": today_rain_chance if today_rain_chance is not None else 0,
            "rainfall_mm": current.get("precip_mm"),
            "daily": daily_items,
            "source": "weatherapi.com",
        }
    except (KeyError, IndexError, TypeError) as exc:
        raise WeatherProviderError(f"Malformed weather response: {exc}") from exc


async def get_weather(
    state: str,
    district: str | None = None,
    latitude: float | None = None,
    longitude: float | None = None,
) -> dict:
    settings = get_settings()
    if not settings.weather_api_key:
        raise WeatherProviderError("No weather provider is configured on the server (WEATHER_API_KEY is unset).")

    has_coordinates = latitude is not None and longitude is not None
    if not has_coordinates and not state.strip():
        raise WeatherProviderError("A farm location (state, or latitude/longitude) is required to look up weather.")

    if has_coordinates:
        query = f"{latitude},{longitude}"
        location_label = ", ".join(p for p in (district, state) if p) or f"{latitude}, {longitude}"
    else:
        query = f"{district}, {state}" if district else state
        location_label = None  # resolved from WeatherAPI's own response below

    async with httpx.AsyncClient(timeout=10.0) as client:
        payload = await _fetch_forecast(client, settings.weather_api_key, query)

    if location_label is None:
        location = payload.get("location", {})
        location_label = ", ".join(p for p in (location.get("name"), location.get("region")) if p) or query

    return _to_weather_response(payload, location_label)
