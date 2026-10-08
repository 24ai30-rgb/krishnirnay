"""Real government mandi/market prices via data.gov.in's AGMARKNET-derived
"Current Daily Price of Various Commodities from Various Markets (Mandi)"
resource (id 9ef84268-d588-465a-a308-a864a43d0070). Reached only from this
server; the Android app never calls data.gov.in directly and never sees
DATA_GOV_API_KEY.

NOTE on provider choice: an earlier pass of this integration briefly moved to
CEDA's Agmarknet API (api.ceda.ashoka.edu.in), which republishes the same
underlying government dataset through a modern bearer-token JSON API. That
was reverted back to data.gov.in's own REST API after the API key actually
provided for this integration was verified live against both: it was
rejected by CEDA ("Api key expired" — CEDA requires its own separately
issued JWT, not a data.gov.in key) but accepted by data.gov.in's real
endpoint with a genuine, current data response. Using the credential that
was actually verified to work, rather than the one that wasn't, is the only
honest option here — see PHASE_MARKET_DATA_GOV_IN_IMPLEMENTATION_REPORT.md
for the exact verification transcript. CEDA's provider code is not kept
alongside this one — the task's own instruction against duplicate
Market/Mandi systems means there is exactly one active provider.

Every failure (missing key, no matching crop/location, network error,
timeout, malformed response) raises MarketProviderError, which
app/routers/market.py turns into an honest 503 — never a fabricated price.
"""

from datetime import datetime

import httpx

from app.config import get_settings

RESOURCE_URL = "https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070"

# data.gov.in never responds at all (the connection just hangs until the
# client's read timeout) when the request carries httpx's default
# `User-Agent: python-httpx/x.y` — verified live: the identical request timed
# out twice at 45s with the default UA and returned HTTP 200 in ~2s with this
# header set. Presumably their WAF drops unknown agents silently. Do not
# remove this as "unnecessary boilerplate" — the integration stops working
# entirely without it, and it fails as a timeout rather than a clear error.
REQUEST_HEADERS = {"User-Agent": "KrishiNirnay/1.0 (+https://data.gov.in API client)"}


class MarketProviderError(Exception):
    pass


# Spelling variants of the *same* crop, where this dataset uses one spelling and
# the app/farmer commonly uses another. Verified against the live dataset's own
# commodity list, not guessed: querying "Soybean" returns nothing because every
# record is filed under the Indian spelling "Soyabean".
#
# This is deliberately NOT a fuzzy matcher between different crops — only exact,
# documented equivalences for one crop known by two spellings. Anything not in
# this map still has to match the dataset exactly, and honestly reports
# "not found" when it doesn't.
_COMMODITY_SPELLING_ALIASES = {
    "Soybean": "Soyabean",
    "Soya Bean": "Soyabean",
    "Soyabean": "Soyabean",
}


def _normalize_commodity(crop: str) -> str:
    """Deterministic, documented normalization only — 'cotton'/'COTTON'/'Cotton'
    all become 'Cotton', matching this dataset's commodity naming convention,
    plus the verified spelling aliases above. Never a fuzzy/guessed mapping
    between unrelated names; a crop the dataset doesn't recognize under this
    exact form honestly comes back as not found, never silently substituted for
    something else."""
    titled = crop.strip().title()
    return _COMMODITY_SPELLING_ALIASES.get(titled, titled)


def _to_float(value) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def _parse_date(value: str | None) -> datetime | None:
    if not value:
        return None
    try:
        return datetime.strptime(value, "%d/%m/%Y")
    except ValueError:
        return None


def _compute_trend(records: list[dict]) -> str:
    """Deterministic only — never an LLM guess. Needs at least two distinctly
    dated records for the SAME market to compare; otherwise UNKNOWN. This
    "current daily price" dataset usually returns one row per market per day,
    so UNKNOWN is the common, honest outcome absent real historical spread
    within a single response."""
    by_market: dict[str, list[tuple[datetime, float]]] = {}
    for record in records:
        record_date = _parse_date(record.get("arrival_date"))
        modal = _to_float(record.get("modal_price"))
        if record_date is None or modal is None:
            continue
        by_market.setdefault(record.get("market", ""), []).append((record_date, modal))

    for entries in by_market.values():
        if len(entries) >= 2:
            entries.sort(key=lambda e: e[0], reverse=True)
            latest, previous = entries[0][1], entries[1][1]
            if latest > previous:
                return "RISING"
            if latest < previous:
                return "FALLING"
            return "STABLE"
    return "UNKNOWN"


def _safe_error(exc: Exception, api_key: str) -> str:
    """data.gov.in authenticates via an `api-key` *query parameter*, and
    httpx puts the full request URL — query string included — into
    str(HTTPStatusError). That message flows into the router's 503 body and
    from there into the Android client's logcat, so interpolating it raw
    would publish the key. Verified: str(exc) for a 401 contains
    "...?api-key=<the real key>&format=json".

    So: report the status code (or exception type) only, and belt-and-braces
    redact the key from whatever text does get through.
    """
    if isinstance(exc, httpx.HTTPStatusError):
        detail = f"upstream returned HTTP {exc.response.status_code}"
    else:
        detail = type(exc).__name__
    if api_key:
        detail = detail.replace(api_key, "<redacted>")
    return detail


def _same_state(record_state: str, requested_state: str) -> bool:
    a, b = record_state.strip().lower(), requested_state.strip().lower()
    if not a or not b:
        return False
    return a == b or a in b or b in a


def _pick_primary_mandi(priced: list[dict], district: str | None) -> dict:
    """The mandi shown as the headline price. Prefers a mandi in the farmer's
    own district when one reported today; among candidates, the highest modal
    price (the existing rule). Deterministic — never a guess."""
    wanted = (district or "").strip().lower()
    if wanted:
        local = [m for m in priced if (m.get("district") or "").strip().lower() == wanted]
        if local:
            return max(local, key=lambda m: m["modal_price"])
    return max(priced, key=lambda m: m["modal_price"])


def _record_to_mandi(record: dict) -> dict:
    return {
        "market": record.get("market", ""),
        "district": record.get("district", ""),
        "state": record.get("state", ""),
        "commodity": record.get("commodity", ""),
        "variety": record.get("variety") or None,
        "grade": record.get("grade") or None,
        "arrival_date": record.get("arrival_date") or None,
        "min_price": _to_float(record.get("min_price")),
        "max_price": _to_float(record.get("max_price")),
        "modal_price": _to_float(record.get("modal_price")),
    }


async def get_market_price(crop: str, state: str | None = None, district: str | None = None) -> dict:
    settings = get_settings()
    if not settings.data_gov_api_key:
        raise MarketProviderError(
            "No market-price provider is configured on the server (DATA_GOV_API_KEY is unset)."
        )
    if not crop.strip():
        raise MarketProviderError("A crop/commodity is required to look up market prices.")

    commodity = _normalize_commodity(crop)
    params = {
        "api-key": settings.data_gov_api_key,
        "format": "json",
        "limit": "100",
        "filters[commodity]": commodity,
    }
    if state:
        params["filters[state]"] = state
    # The district is deliberately NOT sent as a filter. AGMARKNET's daily
    # snapshot rarely has every crop reported in every district, so filtering on
    # it turned "2 real Soyabean mandis in Maharashtra today" into "no data" for
    # a Kolhapur farmer (verified live). The farmer's own state is the honest
    # boundary — a price from a neighbouring district in the same state is still
    # their market, and every record already shows its own district. The
    # district is used below only to RANK: the farmer's own district wins when
    # it reported, otherwise the best mandi in the state.

    try:
        async with httpx.AsyncClient(timeout=15.0, headers=REQUEST_HEADERS) as client:
            response = await client.get(RESOURCE_URL, params=params)
            response.raise_for_status()
    except httpx.HTTPError as exc:
        raise MarketProviderError(
            f"Market data request failed ({_safe_error(exc, settings.data_gov_api_key)})."
        ) from exc

    try:
        payload = response.json()
        raw_records = payload.get("records", [])
    except (ValueError, AttributeError) as exc:
        raise MarketProviderError(f"Malformed market response: {exc}") from exc

    if state:
        # Defense in depth for the hard rule "never show another state's price":
        # don't just trust data.gov.in's own filters[state] to have worked. The
        # dataset's official state names don't always match common usage (a
        # farmer's "Delhi" is filed as "NCT of Delhi" — verified live), so this
        # is a substring match in either direction rather than an exact one,
        # but it still rejects a genuinely different state (e.g. "Maharashtra"
        # is not a substring of "Madhya Pradesh" or vice versa).
        raw_records = [r for r in raw_records if _same_state(r.get("state", ""), state)]

    if not raw_records:
        where = f" in {state}" if state else ""
        raise MarketProviderError(f"No mandi price found for '{commodity}'{where} in today's data.")

    mandis = [_record_to_mandi(r) for r in raw_records]
    trend = _compute_trend(raw_records)

    priced = [m for m in mandis if m["modal_price"] is not None]
    best = _pick_primary_mandi(priced, district) if priced else mandis[0]

    return {
        "crop": crop,
        "market": best["market"] or None,
        "location": ", ".join(p for p in (best["district"], best["state"]) if p) or None,
        "current_price_per_quintal": best["modal_price"],
        "min_price_per_quintal": best["min_price"],
        "max_price_per_quintal": best["max_price"],
        "average_price_per_quintal": None,
        "source": "data.gov.in (AGMARKNET)",
        "arrival_date": best["arrival_date"],
        "variety": best["variety"],
        "grade": best["grade"],
        "district": best["district"] or None,
        "state": best["state"] or None,
        "markets": mandis,
        "trend": trend,
    }
