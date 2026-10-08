"""Unit tests for app.services.weather_provider (WeatherAPI.com integration).
httpx.AsyncClient is mocked throughout — these tests must never hit the real
network (no flakiness, no dependency on a real WEATHER_API_KEY in CI), so
they exercise the mapping/error logic against controlled fake responses
shaped exactly like WeatherAPI.com's documented forecast.json schema
instead."""

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest

from app.services.weather_provider import WeatherProviderError, _condition_for, get_weather


def _response(status_code: int, json_body: dict) -> MagicMock:
    resp = MagicMock()
    resp.status_code = status_code
    resp.json.return_value = json_body
    if status_code >= 400:
        resp.raise_for_status.side_effect = httpx.HTTPStatusError(
            "error", request=MagicMock(), response=resp,
        )
    else:
        resp.raise_for_status.return_value = None
    return resp


def _forecast_body(
    temp_c=31.2,
    feelslike_c=33.5,
    condition_code=1000,
    wind_dir="SW",
    humidity=55,
    precip_mm=0.0,
) -> dict:
    return {
        "location": {"name": "Nagpur", "region": "Maharashtra", "country": "India", "lat": 21.15, "lon": 79.09},
        "current": {
            "temp_c": temp_c,
            "feelslike_c": feelslike_c,
            "condition": {"text": "Sunny", "icon": "//cdn.weatherapi.com/weather/64x64/day/113.png", "code": condition_code},
            "wind_kph": 14.0,
            "wind_dir": wind_dir,
            "humidity": humidity,
            "cloud": 10,
            "pressure_mb": 1008.0,
            "vis_km": 10.0,
            "uv": 6.0,
            "precip_mm": precip_mm,
        },
        "forecast": {
            "forecastday": [
                {
                    "date": "2026-09-12",
                    "day": {
                        "maxtemp_c": 34.0, "mintemp_c": 24.0,
                        "condition": {"text": "Sunny", "icon": "//cdn.weatherapi.com/weather/64x64/day/113.png", "code": 1000},
                        "daily_chance_of_rain": 15,
                        "totalprecip_mm": 0.0,
                    },
                },
                {
                    "date": "2026-09-13",
                    "day": {
                        "maxtemp_c": 30.0, "mintemp_c": 23.0,
                        "condition": {"text": "Moderate rain", "icon": "//cdn.weatherapi.com/weather/64x64/day/302.png", "code": 1189},
                        "daily_chance_of_rain": 70,
                        "totalprecip_mm": 4.2,
                    },
                },
            ],
        },
    }


def _mock_client(get_side_effect) -> AsyncMock:
    client = AsyncMock()
    client.get.side_effect = get_side_effect
    client.__aenter__.return_value = client
    client.__aexit__.return_value = False
    return client


def _run(coro):
    return asyncio.run(coro)


def _set_key(monkeypatch, key: str = "test-key"):
    from app.services.weather_provider import get_settings
    monkeypatch.setattr(get_settings(), "weather_api_key", key)


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_missing_api_key_is_rejected_without_any_network_call(mock_ctor, monkeypatch):
    _set_key(monkeypatch, "")
    with pytest.raises(WeatherProviderError, match="WEATHER_API_KEY"):
        _run(get_weather(state="Maharashtra", district="Nagpur"))
    mock_ctor.assert_not_called()


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_blank_state_and_no_coordinates_is_rejected_without_any_network_call(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    with pytest.raises(WeatherProviderError):
        _run(get_weather(state=""))
    mock_ctor.assert_not_called()


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_a_successful_lookup_maps_real_provider_data_never_fabricated(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, _forecast_body())])

    result = _run(get_weather(state="Maharashtra", district="Nagpur"))

    assert result["source"] == "weatherapi.com"
    assert result["current_temp_c"] == 31.2
    assert result["feelslike_c"] == 33.5
    assert result["condition"] == "SUNNY"
    assert result["condition_code"] == 1000
    assert result["condition_icon_url"] == "https://cdn.weatherapi.com/weather/64x64/day/113.png"
    assert result["wind_kph"] == 14.0
    assert result["humidity_pct"] == 55
    assert result["cloud_pct"] == 10
    assert result["pressure_mb"] == 1008.0
    assert result["visibility_km"] == 10.0
    assert result["uv_index"] == 6.0


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_coordinates_are_sent_as_lat_lon_query_and_skip_text_geocoding(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    client = _mock_client([_response(200, _forecast_body())])
    mock_ctor.return_value = client

    result = _run(get_weather(state="Maharashtra", district="Nagpur", latitude=21.15, longitude=79.09))

    call_kwargs = client.get.call_args.kwargs
    assert call_kwargs["params"]["q"] == "21.15,79.09"
    # The farmer's own saved district/state text stays the displayed label for a
    # coordinate lookup — not WeatherAPI's own resolved place name.
    assert result["location_label"] == "Nagpur, Maharashtra"


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_state_district_text_is_sent_as_the_query_when_no_coordinates(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    client = _mock_client([_response(200, _forecast_body())])
    mock_ctor.return_value = client

    result = _run(get_weather(state="Maharashtra", district="Nagpur"))

    call_kwargs = client.get.call_args.kwargs
    assert call_kwargs["params"]["q"] == "Nagpur, Maharashtra"
    # No coordinates were given, so the label is resolved from the response itself.
    assert result["location_label"] == "Nagpur, Maharashtra"


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_request_includes_the_documented_query_parameters(mock_ctor, monkeypatch):
    _set_key(monkeypatch, "a-real-looking-key")
    client = _mock_client([_response(200, _forecast_body())])
    mock_ctor.return_value = client

    _run(get_weather(state="Maharashtra", district="Nagpur"))

    call_kwargs = client.get.call_args.kwargs
    assert call_kwargs["params"]["key"] == "a-real-looking-key"
    assert call_kwargs["params"]["days"] == 7
    assert call_kwargs["params"]["aqi"] == "no"
    assert call_kwargs["params"]["alerts"] == "yes"


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_seven_day_forecast_is_mapped_with_daily_condition_high_low_rain_chance_and_amount(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, _forecast_body())])

    result = _run(get_weather(state="Maharashtra", district="Nagpur"))

    assert len(result["daily"]) == 2
    first, second = result["daily"]
    assert first["condition"] == "SUNNY"
    assert first["high_c"] == 34.0
    assert first["low_c"] == 24.0
    assert first["rain_chance_pct"] == 15
    assert first["rainfall_mm"] == 0.0
    assert second["condition"] == "RAIN"
    assert second["rain_chance_pct"] == 70
    assert second["rainfall_mm"] == 4.2


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_current_rainfall_and_todays_rain_chance_are_mapped(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, _forecast_body(precip_mm=2.5))])

    result = _run(get_weather(state="Maharashtra", district="Nagpur"))

    assert result["rainfall_mm"] == 2.5
    # today's chance-of-rain comes from the first forecast day, per WeatherAPI's schema.
    assert result["rain_chance_pct"] == 15


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_wind_direction_is_passed_through_directly_from_weatherapi(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, _forecast_body(wind_dir="ENE"))])

    result = _run(get_weather(state="Maharashtra", district="Nagpur"))

    assert result["wind_direction"] == "ENE"


@pytest.mark.parametrize(
    "code,expected",
    [(1000, "SUNNY"), (1003, "PARTLY_CLOUDY"), (1009, "CLOUDY"), (1189, "RAIN"), (1276, "STORM"), (9999, "CLOUDY")],
)
def test_condition_code_mapping(code, expected):
    assert _condition_for(code) == expected


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_invalid_api_key_401_is_honestly_unavailable(mock_ctor, monkeypatch):
    _set_key(monkeypatch, "wrong-key")
    mock_ctor.return_value = _mock_client([
        _response(401, {"error": {"code": 2006, "message": "API key provided is invalid"}}),
    ])

    with pytest.raises(WeatherProviderError, match="invalid or missing API key"):
        _run(get_weather(state="Maharashtra", district="Nagpur"))


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_quota_exceeded_403_is_honestly_unavailable(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([
        _response(403, {"error": {"code": 2007, "message": "API key has exceeded calls per month quota."}}),
    ])

    with pytest.raises(WeatherProviderError, match="quota or plan restriction"):
        _run(get_weather(state="Maharashtra", district="Nagpur"))


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_bad_query_400_is_honestly_unavailable(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([
        _response(400, {"error": {"code": 1003, "message": "Parameter 'q' is missing."}}),
    ])

    with pytest.raises(WeatherProviderError, match="rejected the query"):
        _run(get_weather(state="Maharashtra", district="Nagpur"))


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_location_not_found_404_is_honestly_unavailable(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([
        _response(404, {"error": {"code": 1006, "message": "No matching location found."}}),
    ])

    with pytest.raises(WeatherProviderError, match="no matching location"):
        _run(get_weather(state="Nowhereistan"))


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_network_timeout_is_honestly_unavailable_not_fabricated(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client(httpx.ConnectTimeout("timed out"))

    with pytest.raises(WeatherProviderError):
        _run(get_weather(state="Maharashtra", district="Nagpur"))


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_malformed_json_response_is_honestly_unavailable(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    bad_response = MagicMock()
    bad_response.status_code = 200
    bad_response.raise_for_status.return_value = None
    bad_response.json.side_effect = ValueError("not json")
    mock_ctor.return_value = _mock_client([bad_response])

    with pytest.raises(WeatherProviderError, match="Malformed"):
        _run(get_weather(state="Maharashtra", district="Nagpur"))


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_missing_optional_fields_become_none_never_guessed(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    body = _forecast_body()
    del body["current"]["feelslike_c"]
    del body["current"]["cloud"]
    del body["current"]["pressure_mb"]
    del body["current"]["vis_km"]
    del body["current"]["uv"]
    mock_ctor.return_value = _mock_client([_response(200, body)])

    result = _run(get_weather(state="Maharashtra", district="Nagpur"))

    assert result["feelslike_c"] is None
    assert result["cloud_pct"] is None
    assert result["pressure_mb"] is None
    assert result["visibility_km"] is None
    assert result["uv_index"] is None


@patch("app.services.weather_provider.httpx.AsyncClient")
def test_malformed_forecast_missing_required_keys_is_honestly_unavailable(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, {"current": {}, "forecast": {"forecastday": []}})])

    with pytest.raises(WeatherProviderError, match="Malformed"):
        _run(get_weather(state="Maharashtra", district="Nagpur"))
