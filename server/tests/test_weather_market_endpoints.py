from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import app
from app.services.market_provider import MarketProviderError
from app.services.weather_provider import WeatherProviderError

client = TestClient(app)


def _headers():
    return {"X-API-Key": get_settings().api_key}


def test_weather_requires_api_key():
    response = client.get("/v1/weather", params={"state": "Maharashtra"})
    assert response.status_code == 401


def test_weather_returns_real_provider_data_when_the_location_resolves():
    # A real provider (WeatherAPI.com) is wired up — the route must pass its
    # data straight through, never substitute mock data.
    fake_result = {
        "location_label": "Nagpur, Maharashtra",
        "current_temp_c": 31.0,
        "condition": "SUNNY",
        "wind_kph": 10.0,
        "humidity_pct": 50.0,
        "rain_chance_pct": 10.0,
        "rainfall_mm": None,
        "daily": [],
        "source": "weatherapi.com",
    }
    with patch("app.routers.weather.get_weather", new=AsyncMock(return_value=fake_result)):
        response = client.get("/v1/weather", params={"state": "Maharashtra"}, headers=_headers())
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "weatherapi.com"
    assert body["current_temp_c"] == 31.0


def test_weather_is_honestly_unavailable_when_the_provider_fails():
    # A real failure (no location match, network error, malformed response)
    # must still surface as a structured 503 — never a fabricated reading.
    with patch(
        "app.routers.weather.get_weather",
        new=AsyncMock(side_effect=WeatherProviderError("no location found")),
    ):
        response = client.get("/v1/weather", params={"state": "Nowhereistan"}, headers=_headers())
    assert response.status_code == 503
    assert response.json()["detail"]["error"] == "weather_unavailable"


def test_market_requires_api_key():
    response = client.get("/v1/market", params={"crop": "cotton"})
    assert response.status_code == 401


def test_market_is_honestly_unavailable_without_a_configured_provider(monkeypatch):
    # With no DATA_GOV_API_KEY the route must say so with a structured 503,
    # never fabricate a price. The key is forced empty here rather than
    # relying on the ambient environment, so this stays deterministic whether
    # or not the developer running it has a real key in server/.env.
    monkeypatch.setattr(get_settings(), "data_gov_api_key", "")
    response = client.get("/v1/market", params={"crop": "cotton"}, headers=_headers())
    assert response.status_code == 503
    assert response.json()["detail"]["error"] == "market_unavailable"


def test_market_passes_state_and_district_through_to_the_provider():
    fake_result = {
        "crop": "Cotton",
        "market": "Amravati",
        "location": "Amravati, Maharashtra",
        "current_price_per_quintal": 7200.0,
        "min_price_per_quintal": 6800.0,
        "max_price_per_quintal": 7600.0,
        "average_price_per_quintal": None,
        "source": "data.gov.in (AGMARKNET)",
        "arrival_date": "10/09/2026",
        "variety": "H-4",
        "grade": "FAQ",
        "district": "Amravati",
        "state": "Maharashtra",
        "markets": [],
        "trend": "UNKNOWN",
    }
    mock_call = AsyncMock(return_value=fake_result)
    with patch("app.routers.market.get_market_price", new=mock_call):
        response = client.get(
            "/v1/market",
            params={"crop": "Cotton", "state": "Maharashtra", "district": "Amravati"},
            headers=_headers(),
        )
    assert response.status_code == 200
    body = response.json()
    assert body["source"] == "data.gov.in (AGMARKNET)"
    assert body["district"] == "Amravati"
    mock_call.assert_called_once_with(crop="Cotton", state="Maharashtra", district="Amravati")


def test_market_is_honestly_unavailable_when_the_provider_fails():
    with patch(
        "app.routers.market.get_market_price",
        new=AsyncMock(side_effect=MarketProviderError("no mandi price found")),
    ):
        response = client.get("/v1/market", params={"crop": "Unobtainium"}, headers=_headers())
    assert response.status_code == 503
    assert response.json()["detail"]["error"] == "market_unavailable"
