"""Unit tests for app.services.market_provider (data.gov.in AGMARKNET
integration). httpx.AsyncClient is mocked throughout — these tests must never
hit the real network (no flakiness, no dependency on internet access or a real
DATA_GOV_API_KEY in CI), so they exercise the request-shaping/mapping/error
logic against controlled fake responses instead."""

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest

from app.config import get_settings
from app.services.market_provider import MarketProviderError, get_market_price


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


def _record(
    market="Maddipadu APMC",
    district="Prakasam",
    # Matches the state most call sites in this file pass to get_market_price;
    # the one test that needs Andhra Pradesh (parses-real-fields) sets it
    # explicitly. Since the provider now defensively drops records whose state
    # doesn't match the requested one (see _same_state), a mismatched default
    # here would silently turn "no state override" into "no data" everywhere.
    state="Maharashtra",
    commodity="Paddy(Common)",
    variety="B P T",
    grade="FAQ",
    arrival_date="13/09/2026",
    min_price=2700,
    max_price=2900,
    modal_price=2800,
) -> dict:
    return {
        "state": state,
        "district": district,
        "market": market,
        "commodity": commodity,
        "variety": variety,
        "grade": grade,
        "arrival_date": arrival_date,
        "min_price": min_price,
        "max_price": max_price,
        "modal_price": modal_price,
    }


def _body(records: list[dict]) -> dict:
    return {"records": records, "total": len(records), "count": len(records)}


def _mock_client(get_side_effect) -> AsyncMock:
    client = AsyncMock()
    client.get.side_effect = get_side_effect
    client.__aenter__.return_value = client
    client.__aexit__.return_value = False
    return client


def _run(coro):
    return asyncio.run(coro)


def _set_key(monkeypatch, key: str = "test-key"):
    monkeypatch.setattr(get_settings(), "data_gov_api_key", key)


@patch("app.services.market_provider.httpx.AsyncClient")
def test_missing_api_key_is_rejected_without_any_network_call(mock_ctor, monkeypatch):
    _set_key(monkeypatch, "")
    with pytest.raises(MarketProviderError, match="DATA_GOV_API_KEY"):
        _run(get_market_price(crop="Cotton", state="Maharashtra"))
    mock_ctor.assert_not_called()


@patch("app.services.market_provider.httpx.AsyncClient")
def test_blank_crop_is_rejected_without_any_network_call(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    with pytest.raises(MarketProviderError, match="crop/commodity is required"):
        _run(get_market_price(crop="   ", state="Maharashtra"))
    mock_ctor.assert_not_called()


@patch("app.services.market_provider.httpx.AsyncClient")
def test_a_successful_response_parses_real_fields_never_fabricated(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, _body([_record(state="Andhra Pradesh")]))])

    result = _run(get_market_price(crop="Paddy(Common)", state="Andhra Pradesh", district="Prakasam"))

    assert result["source"] == "data.gov.in (AGMARKNET)"
    assert result["market"] == "Maddipadu APMC"
    assert result["location"] == "Prakasam, Andhra Pradesh"
    assert result["current_price_per_quintal"] == 2800.0
    assert result["min_price_per_quintal"] == 2700.0
    assert result["max_price_per_quintal"] == 2900.0
    assert result["arrival_date"] == "13/09/2026"
    assert result["variety"] == "B P T"
    assert result["grade"] == "FAQ"
    assert result["district"] == "Prakasam"
    assert result["state"] == "Andhra Pradesh"
    # average is never computed from a single day's spread — the dataset
    # doesn't supply one, so it stays honestly absent.
    assert result["average_price_per_quintal"] is None


@patch("app.services.market_provider.httpx.AsyncClient")
def test_the_api_key_is_sent_as_a_query_parameter_never_a_bearer_header(mock_ctor, monkeypatch):
    """data.gov.in authenticates via the `api-key` query parameter. A Bearer
    header is what CEDA's separate API wants and is silently ignored here —
    verified live against both services, so this stays pinned by a test."""
    _set_key(monkeypatch, "a-real-looking-key")
    client = _mock_client([_response(200, _body([_record()]))])
    mock_ctor.return_value = client

    _run(get_market_price(crop="Cotton", state="Maharashtra"))

    params = client.get.call_args.kwargs["params"]
    assert params["api-key"] == "a-real-looking-key"
    # No Authorization header is set anywhere on the client or the call.
    assert "headers" not in client.get.call_args.kwargs
    client_headers = mock_ctor.call_args.kwargs.get("headers", {})
    assert "Authorization" not in client_headers


@patch("app.services.market_provider.httpx.AsyncClient")
def test_an_explicit_user_agent_is_sent(mock_ctor, monkeypatch):
    """Regression guard: data.gov.in silently hangs (read timeout, no
    response at all) for httpx's default `python-httpx/x.y` User-Agent.
    Verified live — the identical request timed out at 45s without this
    header and returned HTTP 200 in ~2s with it. Removing the header breaks
    the integration in a way that looks like a network problem."""
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, _body([_record()]))])

    _run(get_market_price(crop="Cotton", state="Maharashtra"))

    headers = mock_ctor.call_args.kwargs["headers"]
    assert "User-Agent" in headers
    assert "httpx" not in headers["User-Agent"].lower()


@patch("app.services.market_provider.httpx.AsyncClient")
def test_crop_name_is_normalized_and_state_is_sent_as_a_filter_but_district_is_not(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    client = _mock_client([_response(200, _body([_record()]))])
    mock_ctor.return_value = client

    _run(get_market_price(crop="cotton", state="Maharashtra", district="Amravati"))

    params = client.get.call_args.kwargs["params"]
    assert params["filters[commodity]"] == "Cotton"
    assert params["filters[state]"] == "Maharashtra"
    # District ranks results; it must never narrow the query to nothing.
    assert "filters[district]" not in params


@patch("app.services.market_provider.httpx.AsyncClient")
def test_state_only_query_omits_the_district_filter(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    client = _mock_client([_response(200, _body([_record()]))])
    mock_ctor.return_value = client

    _run(get_market_price(crop="Cotton", state="Maharashtra"))

    params = client.get.call_args.kwargs["params"]
    assert params["filters[state]"] == "Maharashtra"
    assert "filters[district]" not in params


@patch("app.services.market_provider.httpx.AsyncClient")
def test_the_farmers_actual_crop_and_location_are_used_never_hardcoded(mock_ctor, monkeypatch):
    """A second farmer with an entirely different crop/state/district must
    produce an entirely different upstream query."""
    _set_key(monkeypatch)
    client = _mock_client([
        _response(200, _body([_record(state="Punjab", district="Ludhiana", market="Khanna", commodity="Wheat")])),
    ])
    mock_ctor.return_value = client

    result = _run(get_market_price(crop="wheat", state="Punjab", district="Ludhiana"))

    params = client.get.call_args.kwargs["params"]
    assert params["filters[commodity]"] == "Wheat"
    assert params["filters[state]"] == "Punjab"
    assert result["market"] == "Khanna"
    assert result["state"] == "Punjab"


@patch("app.services.market_provider.httpx.AsyncClient")
def test_multiple_markets_are_all_returned_and_the_highest_modal_is_primary(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    records = [
        _record(market="Pune", modal_price=3600),
        _record(market="Nashik", modal_price=4100),
        _record(market="Solapur", modal_price=2900),
    ]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    result = _run(get_market_price(crop="Onion", state="Maharashtra"))

    assert len(result["markets"]) == 3
    assert result["market"] == "Nashik"
    assert result["current_price_per_quintal"] == 4100.0


@patch("app.services.market_provider.httpx.AsyncClient")
def test_empty_records_is_honestly_unavailable_never_a_fabricated_price(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, _body([]))])

    with pytest.raises(MarketProviderError, match="No mandi price found"):
        _run(get_market_price(crop="Cotton", state="Maharashtra"))


@patch("app.services.market_provider.httpx.AsyncClient")
def test_timeout_is_honestly_unavailable(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client(httpx.ReadTimeout("timed out"))

    with pytest.raises(MarketProviderError, match="Market data request failed"):
        _run(get_market_price(crop="Cotton", state="Maharashtra"))


@pytest.mark.parametrize("status_code", [401, 403, 429, 500])
@patch("app.services.market_provider.httpx.AsyncClient")
def test_upstream_http_errors_are_honestly_unavailable(mock_ctor, monkeypatch, status_code):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(status_code, {"message": "error"})])

    with pytest.raises(MarketProviderError, match=f"HTTP {status_code}"):
        _run(get_market_price(crop="Cotton", state="Maharashtra"))


@pytest.mark.parametrize("status_code", [401, 403, 429, 500])
@patch("app.services.market_provider.httpx.AsyncClient")
def test_the_api_key_never_leaks_into_an_error_message(mock_ctor, monkeypatch, status_code):
    """httpx embeds the full request URL — `api-key` query parameter included —
    in str(HTTPStatusError). That message reaches the router's 503 body and the
    Android client's logcat, so the provider must never interpolate it raw."""
    secret = "SUPERSECRETKEY123"
    _set_key(monkeypatch, secret)

    real_request = httpx.Request(
        "GET", f"https://api.data.gov.in/resource/abc?api-key={secret}&format=json",
    )
    real_response = httpx.Response(status_code, request=real_request)
    client = AsyncMock()
    client.get.side_effect = httpx.HTTPStatusError(
        f"Client error for url 'https://api.data.gov.in/resource/abc?api-key={secret}'",
        request=real_request,
        response=real_response,
    )
    client.__aenter__.return_value = client
    client.__aexit__.return_value = False
    mock_ctor.return_value = client

    with pytest.raises(MarketProviderError) as caught:
        _run(get_market_price(crop="Cotton", state="Maharashtra"))

    assert secret not in str(caught.value)
    assert "api-key" not in str(caught.value)


@patch("app.services.market_provider.httpx.AsyncClient")
def test_malformed_json_response_is_honestly_unavailable(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    bad = MagicMock()
    bad.status_code = 200
    bad.raise_for_status.return_value = None
    bad.json.side_effect = ValueError("not json")
    mock_ctor.return_value = _mock_client([bad])

    with pytest.raises(MarketProviderError, match="Malformed market response"):
        _run(get_market_price(crop="Cotton", state="Maharashtra"))


@patch("app.services.market_provider.httpx.AsyncClient")
def test_unparseable_price_fields_become_none_never_a_guess(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    records = [_record(min_price="NA", max_price=None, modal_price="")]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    result = _run(get_market_price(crop="Cotton", state="Maharashtra"))

    assert result["min_price_per_quintal"] is None
    assert result["max_price_per_quintal"] is None
    assert result["current_price_per_quintal"] is None


@patch("app.services.market_provider.httpx.AsyncClient")
def test_trend_is_unknown_with_a_single_dated_record_per_market(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, _body([_record()]))])

    result = _run(get_market_price(crop="Cotton", state="Maharashtra"))

    assert result["trend"] == "UNKNOWN"


@patch("app.services.market_provider.httpx.AsyncClient")
def test_trend_is_rising_when_the_latest_dated_record_is_higher(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    records = [
        _record(market="Pune", arrival_date="12/09/2026", modal_price=3000),
        _record(market="Pune", arrival_date="13/09/2026", modal_price=3600),
    ]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    result = _run(get_market_price(crop="Onion", state="Maharashtra"))

    assert result["trend"] == "RISING"


@patch("app.services.market_provider.httpx.AsyncClient")
def test_trend_is_falling_when_the_latest_dated_record_is_lower(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    records = [
        _record(market="Pune", arrival_date="12/09/2026", modal_price=3600),
        _record(market="Pune", arrival_date="13/09/2026", modal_price=3000),
    ]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    result = _run(get_market_price(crop="Onion", state="Maharashtra"))

    assert result["trend"] == "FALLING"


# The daily snapshot rarely has every crop in every district. Verified live: a
# Kolhapur farmer's Soyabean query with a district filter returned nothing while
# two Maharashtra mandis had reported. So district ranks, it never filters.
@patch("app.services.market_provider.httpx.AsyncClient")
def test_a_farmers_own_district_mandi_is_the_headline_when_it_reported(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    records = [
        _record(market="APMC Udgir", district="Latur", state="Maharashtra", modal_price=5850),
        _record(market="Kolhapur APMC", district="Kolhapur", state="Maharashtra", modal_price=5600),
        _record(market="Sangli APMC", district="Sangli", state="Maharashtra", modal_price=5900),
    ]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    result = _run(get_market_price(crop="Soyabean", state="Maharashtra", district="Kolhapur"))

    # Local mandi wins even though another district posted a higher price...
    assert result["market"] == "Kolhapur APMC"
    assert result["current_price_per_quintal"] == 5600.0
    # ...and every mandi in the state is still returned for comparison.
    assert len(result["markets"]) == 3


@patch("app.services.market_provider.httpx.AsyncClient")
def test_when_the_farmers_district_did_not_report_the_best_same_state_mandi_is_shown(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    records = [
        _record(market="APMC Udgir", district="Latur", state="Maharashtra", modal_price=5850),
        _record(market="Sangli APMC", district="Sangli", state="Maharashtra", modal_price=5900),
    ]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    result = _run(get_market_price(crop="Soyabean", state="Maharashtra", district="Kolhapur"))

    assert result["market"] == "Sangli APMC"
    assert result["state"] == "Maharashtra"  # never another state's price
    assert result["district"] == "Sangli"      # the real district is shown, not the farmer's


@patch("app.services.market_provider.httpx.AsyncClient")
def test_district_ranking_is_case_insensitive(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    records = [
        _record(market="APMC Udgir", district="Latur", state="Maharashtra", modal_price=5850),
        _record(market="Kolhapur APMC", district="KOLHAPUR", state="Maharashtra", modal_price=5600),
    ]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    result = _run(get_market_price(crop="Soyabean", state="Maharashtra", district="kolhapur"))

    assert result["market"] == "Kolhapur APMC"


@patch("app.services.market_provider.httpx.AsyncClient")
def test_a_genuinely_empty_state_is_still_honestly_no_data(mock_ctor, monkeypatch):
    """Dropping the district filter must not turn into fabricating a price."""
    _set_key(monkeypatch)
    mock_ctor.return_value = _mock_client([_response(200, _body([]))])

    with pytest.raises(MarketProviderError, match="No mandi price found for 'Cotton' in Maharashtra"):
        _run(get_market_price(crop="Cotton", state="Maharashtra", district="Kolhapur"))


# The hard rule "never show another state's price" must not depend solely on
# trusting data.gov.in's own filters[state] — verified live that a farmer's
# "Delhi" is filed there under "NCT of Delhi", so an exact match would be
# wrong; this defends against the opposite failure, a genuinely different
# state slipping through.
@patch("app.services.market_provider.httpx.AsyncClient")
def test_records_from_a_different_state_are_dropped_even_if_upstream_returned_them(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    records = [
        _record(market="Kolhapur APMC", district="Kolhapur", state="Maharashtra", modal_price=5600),
        _record(market="Ludhiana Mandi", district="Ludhiana", state="Punjab", modal_price=9999),
    ]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    result = _run(get_market_price(crop="Soyabean", state="Maharashtra"))

    assert result["state"] == "Maharashtra"
    assert all(m["state"] == "Maharashtra" for m in result["markets"])
    assert len(result["markets"]) == 1


@patch("app.services.market_provider.httpx.AsyncClient")
def test_a_requested_state_matches_the_datasets_official_longer_name(mock_ctor, monkeypatch):
    """"Delhi" must match a record filed as "NCT of Delhi" — verified live."""
    _set_key(monkeypatch)
    records = [_record(market="APMC Keshopur", district="Delhi", state="NCT of Delhi", commodity="Tomato", modal_price=2400)]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    result = _run(get_market_price(crop="Tomato", state="Delhi", district="Delhi"))

    assert result["state"] == "NCT of Delhi"
    assert result["market"] == "APMC Keshopur"


@patch("app.services.market_provider.httpx.AsyncClient")
def test_if_every_record_belongs_to_a_different_state_it_is_honestly_no_data(mock_ctor, monkeypatch):
    _set_key(monkeypatch)
    records = [_record(market="Ludhiana Mandi", district="Ludhiana", state="Punjab", modal_price=9999)]
    mock_ctor.return_value = _mock_client([_response(200, _body(records))])

    with pytest.raises(MarketProviderError, match="No mandi price found"):
        _run(get_market_price(crop="Wheat", state="Maharashtra"))
