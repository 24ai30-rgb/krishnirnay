# PHASE MARKET — data.gov.in AGMARKNET Integration (LIVE, verified)

Mandi prices are now genuinely LIVE against the real government API, verified with
actual requests returning real data. This report records exactly what was verified,
including two real bugs found and fixed along the way.

## 1. Important correction to the brief (verified, not assumed)

The brief asked for the **data.gov.in Agmarknet API** but specified CEDA's endpoint
and auth scheme:

```
endpoint: https://api.ceda.ashoka.edu.in/v1/agmarknet/prices
auth:     Authorization: Bearer <DATA_GOV_API_KEY>
```

Those two things belong to different services, so before writing code I tested the
provided key against both:

| Attempt | Result |
|---|---|
| CEDA, `Authorization: Bearer <key>` | **HTTP 401** `{"status":"failure","message":"Api key expired","isAuthenticated":false}` |
| data.gov.in, `?api-key=<key>` | **HTTP 200** with real current mandi records |

The key is a genuine, working **data.gov.in** key; CEDA requires its own separately
issued JWT and rejects it. So the integration was built against data.gov.in's real
REST API with query-parameter auth — the combination that was actually verified to
work. Following the brief's literal endpoint/header would have produced a permanently
401-ing integration.

Because the previous phase had switched this provider to CEDA (with no working
token, so market prices were never actually LIVE), that switch was reverted. Per the
brief's "do not create duplicate Market/Mandi systems", there is exactly **one**
active market provider — CEDA's code path was removed, not left dormant alongside it.

## 2. API endpoint used

```
GET https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070
    ?api-key=<DATA_GOV_API_KEY>
    &format=json
    &limit=100
    &filters[commodity]=<farmer's crop, Title-Cased>
    &filters[state]=<farmer's state>        (omitted when unknown)
    &filters[district]=<farmer's district>  (omitted when unknown)
```

Resource: *"Current Daily Price of Various Commodities from Various Markets (Mandi)"*
— Ministry of Agriculture and Farmers Welfare, the AGMARKNET-derived dataset.

## 3. How authentication is configured

- Sent as the **`api-key` query parameter** (what this API actually accepts — a Bearer
  header is silently ignored by it). Pinned by a test so it can't regress.
- Read from `Settings.data_gov_api_key` (`DATA_GOV_API_KEY`) via the existing
  pydantic-settings mechanism in `server/app/config.py` — no second config system.
- Stored **only** in `server/.env`, which is gitignored and untracked.
- The Android app never sees it: the app calls this server's own `GET /v1/market`,
  and the server calls data.gov.in. The key cannot be extracted from the APK because
  it isn't in it.

Setup for another environment:
```bash
echo "DATA_GOV_API_KEY=<your-key>" >> server/.env   # get one free at https://data.gov.in
```

## 4. Two real bugs found and fixed (both verified live)

**a) data.gov.in silently hangs on httpx's default User-Agent.**
The first live run through the provider timed out, while `curl` worked seconds
earlier. Isolated it: the identical unfiltered request timed out twice at 45s with
`User-Agent: python-httpx/x.y`, and returned HTTP 200 in ~2s with a conventional
UA set. Their WAF appears to drop unknown agents without responding. Fixed by
sending an explicit `User-Agent`, with a comment explaining why it must not be
"cleaned up" — without it the integration fails as a confusing timeout.

**b) The API key leaked into error messages, the 503 body, and Android logcat.**
`str(httpx.HTTPStatusError)` embeds the full request URL — query string included —
so `f"...failed: {exc}"` would publish `?api-key=<real key>` into the router's 503
response and from there into `Log.e("MARKET", ...)`. Confirmed by reproducing the
exception string. Fixed with `_safe_error()`, which reports only the status code (or
exception type) and additionally redacts the key from any text that gets through.
Verified live by forcing a real upstream 403: the message is now
`"Market data request failed (upstream returned HTTP 403)."` — no `api-key`, no key
value. Guarded by a parametrized test.

## 5. What was implemented

- `market_provider.py` rewritten to call data.gov.in, using the farmer's **actual
  saved** crop / state / district (from `ProfileRepository` → `LiveMarketRepositoryImpl`
  → `GET /v1/market`), never hardcoded and never re-asked from the user.
- Deterministic commodity normalization (`cotton`/`COTTON` → `Cotton`) — exact-form
  matching only, never a fuzzy substitution.
- Per-mandi records mapped into the existing `MandiRecord`/`MandiPrice` shape:
  commodity, market, state, district, variety, grade, arrival date, min/max/modal.
- Primary mandi = highest modal price among those returned; all mandis retained for
  the comparison list.
- Deterministic RISING/FALLING/STABLE/UNKNOWN trend from two+ dated records for the
  same market — UNKNOWN when the data can't support a comparison.
- Unit: the dataset carries no unit field, so nothing is invented — prices remain
  labelled "per quintal", the dataset's documented convention, already in the UI.
- Dashboard market card: added the mandi's **district/state** line and
  **variety/grade** line (existing typography/colour tokens, no redesign), shown only
  when the provider actually supplied them.

Unchanged by design: `MarketState`, `MandiPrice`, `MarketResponse`/`MandiRecord`
schemas, `MarketApiService`, `LiveMarketRepositoryImpl`, `MarketStateCache`,
`MockMarketRepositoryImpl`, `routers/market.py`, and every other module (Weather,
Farmer Profile, Registration, Dashboard decision card, AI, IVR, schemes, feedback).
The existing contract already carried every field this API provides.

## 6. LIVE / CACHED / UNAVAILABLE — honest, and verified with real calls

| Scenario | Real observed behaviour |
|---|---|
| Onion / Maharashtra | **LIVE** — Pune(Pimpri), modal ₹3600, min ₹2500, max ₹4700, 6 mandis, trend RISING, date 13/09/2026 |
| Paddy(Common) / Andhra Pradesh / Prakasam | **LIVE** — Maddipadu APMC, modal ₹2800, variety "B P T", grade "FAQ", 3 mandis |
| Cotton / Maharashtra | **UNAVAILABLE** — genuinely no cotton rows in today's snapshot; no price invented |
| Unknown commodity | **UNAVAILABLE** — "No mandi price found for 'Dragonfruitxyz'" |
| Invalid key | **UNAVAILABLE** — "upstream returned HTTP 403", key redacted |
| No key configured | **UNAVAILABLE** — rejected before any network call |

CACHED behaviour is unchanged and still handled by the untouched
`LiveMarketRepositoryImpl.preserveAsCachedOrUnavailable` + `MarketStateCache`: a
failed poll keeps the last real reading and downgrades it to CACHED; only with
nothing ever cached does it become UNAVAILABLE. No fabricated prices anywhere.

## 7. Files changed

Modified:
- `server/app/services/market_provider.py` — rewritten for data.gov.in; added
  `REQUEST_HEADERS` (User-Agent fix) and `_safe_error()` (key-redaction fix).
- `server/app/config.py` — `ceda_api_key` → `data_gov_api_key`, comment updated.
- `server/.env.example` — `CEDA_API_KEY` → `DATA_GOV_API_KEY=your_data_gov_in_api_key_here`
  (placeholder only).
- `server/.env` *(gitignored, untracked)* — real key stored as `DATA_GOV_API_KEY`;
  stale `CEDA_API_KEY` line removed.
- `server/tests/test_market_provider.py` — rewritten for data.gov.in (24 tests).
- `server/tests/test_weather_market_endpoints.py` — source/date fixtures updated; the
  "unavailable without a provider" test now forces the key empty via monkeypatch
  instead of depending on the ambient environment (it would otherwise have started
  failing now that a real key is configured locally — fixed properly, not weakened).
- `app/src/main/java/com/krishinirnay/feature/dashboard/DashboardScreen.kt` — market
  card now also shows district/state and variety/grade.

Created:
- `PHASE_MARKET_DATA_GOV_IN_IMPLEMENTATION_REPORT.md` (this file).

## 8. Tests result

```
server/tests/test_market_provider.py   24 passed
full server suite                     107 passed, 6 failed
full Android suite                    126 passed, 0 failed
```

The 6 server failures are the same pre-existing, unrelated ones documented in earlier
phases (`test_disease_endpoint.py` ×4, `test_stub_endpoints.py` ×2 — disease/pest/
risk-fusion stubs). Nothing in this change touches them; no test was weakened,
skipped, or deleted.

New test coverage: api-key sent as query param never a Bearer header; explicit
User-Agent present (regression guard for bug 4a); key never leaks into error messages
for 401/403/429/500 (regression guard for bug 4b); crop normalization; state-only vs
state+district filters; a second farmer's different crop/location producing a
different query (nothing hardcoded); multi-mandi selection; empty result; timeout;
upstream 401/403/429/500; malformed JSON; unparseable prices → null; trend
UNKNOWN/RISING/FALLING.

## 9. Build result

```
./gradlew compileDebugKotlin  → BUILD SUCCESSFUL
./gradlew testDebugUnitTest   → BUILD SUCCESSFUL (126/126)
./gradlew assembleDebug       → BUILD SUCCESSFUL
```

## 10. Security status

- Real key present in exactly **one** file in the whole working tree: `server/.env` —
  confirmed gitignored (`git check-ignore`) and untracked (`git ls-files`).
- `.env.example` contains a placeholder only.
- **Zero** references to the key or to `api.data.gov.in` anywhere in `app/src` or
  `app/build.gradle.kts` — it is not in the APK and cannot be extracted from it.
- Not in logs: the leak path through httpx error strings was found and closed
  (§4b), verified against a real 403.
- Not in API responses: the 503 body carries only a status code.
- Not in source: every code reference is to the variable *name*.
- Not printed in this session's tool output or in this report.

## 11. Remaining blockers

None for this integration — it is LIVE and verified end to end.

Two honest notes:
- Coverage is whatever the government publishes that day. "Cotton in Maharashtra"
  returned nothing at verification time; that is real absence of data, surfaced as
  UNAVAILABLE rather than filled in. Commodity names must match the dataset's own
  spelling (e.g. `Paddy(Common)`), which is why unmatched crops report honestly
  instead of being fuzzily remapped.
- The pre-existing 6 server test failures remain open — out of scope here, untouched.
