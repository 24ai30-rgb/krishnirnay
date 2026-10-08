# PHASE MARKET — CEDA Agmarknet Integration

Replaces the market provider's upstream (data.gov.in → CEDA) without touching
`MarketState`, the DTOs, the schema, the router, or any Android code — confirmed
compatible before writing a single line, and confirmed unchanged after.

## 1. Exact endpoint(s) used

Base URL: `https://api.ceda.ashoka.edu.in/v1` (confirmed from the live OpenAPI spec
served at `https://api.ceda.ashoka.edu.in/documentation/swagger-ui-init.js` — the
documentation page itself is a JS-rendered Swagger UI with no static content, so the
underlying spec was fetched directly).

Call sequence per farmer lookup (CEDA's schema is ID-based, not name-based, so a
free-text crop/state/district requires resolving IDs first):

1. `GET /agmarknet/commodities` — full commodity name→id list (cached in-process).
2. `GET /agmarknet/geographies` — full state/district name→id list (cached in-process).
3. `POST /agmarknet/markets` — the markets within the resolved district for the
   resolved commodity.
4. `POST /agmarknet/prices` — the actual price records for those markets over a
   30-day lookback window.

Auth: `Authorization: Bearer <CEDA_API_KEY>` — confirmed against the real API with a
safe, harmless probe (no valid token used): an unauthenticated request returns
`401 {"status":"failure","message":"Unauthorised, no api key passed."}`, and a
request with a bearer header but a fake token returns a *different* message,
`401 {"status":"failure","message":"Api key expired"}` — proving `Authorization:
Bearer` is the header CEDA actually reads (a plain `x-api-key` header still returns
the "no api key passed" message). No real token was ever used or created.

## 2. Exact request parameters

`POST /agmarknet/markets`:
```json
{"commodity_id": 3, "state_id": 3, "district_id": 41, "indicator": "price"}
```

`POST /agmarknet/prices`:
```json
{
  "commodity_id": 3,
  "state_id": 3,
  "district_id": [41],
  "market_id": [255, 256],
  "from_date": "2026-08-14",
  "to_date": "2026-09-13"
}
```

All four values (`commodity_id`, `state_id`, `district_id`, `market_id`) are resolved
at request time from the farmer's actual `ProfileRepository`-sourced crop/state/
district text — never hardcoded (verified by
`test_a_different_farmer_with_a_different_crop_and_location_resolves_different_ids`,
which asserts a Punjab/Wheat farmer produces a completely different request payload
than the Maharashtra/Cotton farmer in every other test).

## 3. Response mapping

| CEDA field | Existing `MarketState`/DTO field | Notes |
|---|---|---|
| `/markets` → `market_name` | `market` | per mandi |
| `/prices` → `min_price`/`max_price`/`modal_price` | `minPricePerQuintal`/`maxPricePerQuintal`/`currentPricePerQuintal` (best market) | best = highest latest modal price, same selection rule as before |
| `/prices` → `date` | `arrivalDate` | CEDA's own field name is `date`; it is a reported observation date, never a live timestamp — this is exactly why the result stays labeled "Latest available" (Android's existing `marketLatestAvailable` string, already correct, untouched) rather than "real-time" |
| resolved district/state text | `district`/`state` | echoed back as the farmer's own saved text, not CEDA's |
| n/a | `variety`/`grade` | CEDA's price records carry neither — left `null`, never guessed (both fields were already nullable in the existing `MandiPrice`/`MandiRecord` model, so no schema change was needed) |
| n/a | `average_price_per_quintal` | still always `null` — unchanged from the previous provider, CEDA doesn't supply this either |
| computed from `>=2` dated records per `market_id` | `trend` | same deterministic RISING/FALLING/STABLE/UNKNOWN logic as before, adapted from data.gov.in's `market` (name) grouping key to CEDA's `market_id` |

`source` changed from `"data.gov.in (AGMARKNET)"` to `"CEDA (Agmarknet)"` — the only
literal string change visible to a farmer, and it is exactly accurate.

## 4. Files changed

- `server/app/config.py` — `data_gov_in_api_key` → `ceda_api_key` (only the market
  provider ever read this field; grepped to confirm no other reference existed).
- `server/app/services/market_provider.py` — full upstream rewrite: CEDA client,
  commodity/geography name→id resolution (with an in-process cache for that
  near-static reference data), markets/prices calls, and the same
  mapping/trend/best-market selection shape as before.
- `server/tests/test_market_provider.py` — fully rewritten for CEDA (see §5).
- `server/tests/test_weather_market_endpoints.py` — updated a comment and two fixed
  fixture strings (`"data.gov.in (AGMARKNET)"` → `"CEDA (Agmarknet)"`,
  `"10/09/2026"` → `"2026-09-10"`) to stay accurate; these tests mock
  `app.routers.market.get_market_price` at the router boundary, so they were never
  actually broken by the provider swap — only the fixture text was stale.

**Not changed** (confirmed compatible without any edit): `server/app/schemas/market.py`,
`server/app/routers/market.py`, `app/src/main/java/com/krishinirnay/core/network/
dto/MarketDto.kt`, `app/src/main/java/com/krishinirnay/core/data/model/MarketState.kt`,
`app/src/main/java/com/krishinirnay/core/data/network/LiveMarketRepositoryImpl.kt`,
`app/src/main/java/com/krishinirnay/core/data/mock/MockMarketRepositoryImpl.kt`,
and every Market-consuming screen (Dashboard's market card, etc.) — the existing
architecture already carried every field CEDA can supply, exactly as the task asked
to confirm before writing code.

## 5. Tests added

`server/tests/test_market_provider.py` (18 tests, full rewrite):

- Missing API key / missing state / missing district → rejected with **no network
  call** (`mock_ctor.assert_not_called()`).
- A successful lookup maps every real field (never fabricated).
- **Crop/state/district are the farmer's actual values, never hardcoded** — one test
  asserts the exact request payload for a Maharashtra/Cotton farmer; a second,
  independent test proves a Punjab/Wheat farmer produces an entirely different
  payload and result.
- Unrecognized commodity / unrecognized state / unrecognized district → each
  honestly `MarketProviderError`, never a fuzzy/guessed match.
- No markets for the commodity+district → honestly unavailable.
- No price data in the 30-day lookback window → honestly unavailable, never
  invented.
- Unauthorized (expired/invalid) token → honestly unavailable.
- Network timeout → honestly unavailable.
- Malformed JSON response → honestly unavailable.
- Trend RISING (two dated records, latest higher) and UNKNOWN (single record).
- Multiple markets are all returned; the highest latest-modal market is primary.
- Reference-data caching: a second lookup re-calls `/markets` and `/prices` but
  **not** `/commodities`/`/geographies` again (asserted via exact call count and URL
  matching).

`server/tests/test_weather_market_endpoints.py`: no new tests (router-level tests
were already provider-agnostic via mocking); two stale fixture strings corrected.

Cache-fallback (CACHED-after-a-failed-poll) behavior lives entirely on the Android
side (`LiveMarketRepositoryImpl.preserveAsCachedOrUnavailable`) and was already
covered by `LiveMarketRepositoryImplTest.kt` before this session — untouched, since
nothing about that logic depends on which upstream the server calls.

## 6. Final test counts

```
Focused (market-related):  25 passed  (18 provider + 7 endpoint)
Full server suite:        104 passed, 6 failed
Full Android suite:       125 passed, 0 failed
assembleDebug:             BUILD SUCCESSFUL
```

The 6 server failures are the same pre-existing, unrelated failures already
documented before this session (`test_disease_endpoint.py` ×4, `test_stub_
endpoints.py` ×2 — disease/pest/risk-fusion stub behavior, untouched by this market
change). No new failures were introduced; no existing test was weakened, skipped, or
deleted.

## 7. Whether a real network smoke test succeeded

**Partially — honestly, not fully.** `CEDA_API_KEY` is not configured anywhere in
this environment (checked the process environment and `server/.env` directly, both
absent), so the actual authenticated data flow (commodities → geographies → markets
→ prices) could not be exercised against real data, and **this is not claimed as
LIVE**.

What *was* verified against the real, live CEDA API (safe, no token created or
exposed):
- The base URL and endpoint paths are real and reachable — `GET /v1/agmarknet/
  commodities` returns a real HTTP response (401, as expected without a token).
- The exact auth mechanism (`Authorization: Bearer`, not an API-key header) was
  confirmed by comparing the *error message* returned with no header at all
  ("Unauthorised, no api key passed") against a request with a bearer header
  carrying an obviously-fake token ("Api key expired") — proving CEDA reads that
  specific header and validates its contents, not just its presence.

Everything else (the full four-call resolution chain, response field mapping, error
handling) is verified only against realistic mocked HTTP responses shaped exactly
like CEDA's documented OpenAPI schema — the same honesty discipline already used for
Weather (Open-Meteo) and the previous Market provider (data.gov.in).

## 8. Remaining blocker

A real `CEDA_API_KEY` (obtained from https://ceda.ashoka.edu.in) is the only thing
standing between the current, honestly-UNAVAILABLE state and real LIVE market
prices. Once set:

```bash
echo "CEDA_API_KEY=<your-token>" >> server/.env
```

No code change is required — `get_settings()` picks it up automatically, and the
first real request will populate the in-process commodity/geography caches from
CEDA's real reference data.

**A known, honest limitation surfaced by this integration** (not invented, not
worked around — per the task's own instruction): CEDA's `/agmarknet/markets`
endpoint requires `district_id`, so unlike the previous data.gov.in provider (which
could return a state-wide price with no district set), a farmer whose saved profile
has a state but no district can no longer get a CEDA market price at all — the
provider correctly and honestly raises `MarketProviderError` ("A farm district is
required...") rather than guessing a district or falling back to a different query
shape. This is a real behavior difference between the two upstreams, not a bug.
