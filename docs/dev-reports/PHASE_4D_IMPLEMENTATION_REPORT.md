# KRISHINIRNAY Phase 4D Implementation Report

Scope: real government mandi/market price integration via data.gov.in's AGMARKNET dataset (resource `9ef84268-d588-465a-a308-a864a43d0070`). No Local LLM, offline voice, IVR, Government Schemes, or Farmer Feedback work was touched.

---

## 1. Audit (before any code was touched)

- **What already existed**: the entire Market architecture mirrored Weather exactly — `MarketRepository`/`MarketState`, Mock/Live switching (`DefaultMarketRepository`), `GET /v1/market`, `MarketStateCache` DataStore persistence (Phase 4A), honest LIVE/CACHED/UNAVAILABLE. `market_provider.py` was a stub that always raised, gated on an unused `market_api_key`.
- **What was Mock**: `MockMarketRepositoryImpl` — a single static Cotton/Nagpur snapshot.
- **What was Live**: everything up to the final third-party call, same as Weather pre-4C.
- **What was persisted**: last known-good `MarketState`, same pattern as Weather (Phase 4A) — unchanged, reused as-is.
- **What was UNAVAILABLE**: any real fetch, always, since no provider existed.
- **Reused, not duplicated**: `MarketRepository`/`MarketStateCache`/`DefaultMarketRepository`/`MockMarketRepositoryImpl` interfaces and Mock/Live wiring in `RepositoryModule.kt` — none of it needed to change shape, only the fields `MarketState` carries and the one real provider function.
- **Existing Dashboard Market card**: found already present (`DashboardScreen.kt`), just very minimal — crop + a single price. Extended, not rebuilt.
- **Files needing modification**: `server/app/services/market_provider.py`, `server/app/config.py`, `server/app/schemas/market.py`, `server/app/routers/market.py`, `server/.env.example` (new), plus the Android model/DTO/repository/cache/UI/decision-engine chain listed below.

## 2. Existing Market Architecture (reused unchanged)

`FarmerProfile.primaryCrop` + `farmLocation.{state,district}` → `LiveMarketRepositoryImpl` → `GET /v1/market` → FastAPI → `market_provider.get_market_price()` → `MarketState` → `MarketStateCache` (persistence) → `DefaultMarketRepository` (Mock/Live switch) → `DashboardViewModel`/`FieldDecisionResolver`.

## 3. Official Data Source

Government of India Open Government Data Platform (data.gov.in), dataset **"Current Daily Price of Various Commodities from Various Markets (Mandi)"**, generated from AGMARKNET.

## 4. API Resource ID

`9ef84268-d588-465a-a308-a864a43d0070` — endpoint `https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070`.

## 5. Files Changed

**Server**:
- `server/app/services/market_provider.py` — rewritten: real HTTP call to the resource above with `filters[commodity]`/`filters[state]`/`filters[district]`, crop-name normalization, per-market parsing, deterministic trend, honest error mapping.
- `server/app/config.py` — `market_api_key` replaced with `data_gov_in_api_key` (real config, still empty by default).
- `server/app/schemas/market.py` — added `MandiRecord` + `arrival_date`/`variety`/`grade`/`district`/`state`/`markets`/`trend` on `MarketResponse`.
- `server/app/routers/market.py` — added a `district` query parameter.
- `server/.env.example` — new; documents `DATA_GOV_IN_API_KEY` (and the other existing server env vars) without any real secret.

**Android**:
- `core/data/model/MarketState.kt` — new `MarketTrend` enum, new `MandiPrice` data class, `MarketState` extended (additive, all new fields defaulted).
- `core/network/dto/MarketDto.kt` — new `MandiRecordDto`, `MarketResponseDto` extended to match.
- `core/network/MarketApiService.kt` — added `district` query param.
- `core/data/network/LiveMarketRepositoryImpl.kt` — now keys its fetch on crop **and** the farmer's state/district (previously crop only), maps every new field.
- `core/data/local/MarketStateCache.kt` — persists the new fields (multi-market list, trend, arrival date, variety, grade).
- `core/data/mock/MockMarketRepositoryImpl.kt` — enriched with two demo mandis so Mock Mode exercises the same comparison UI as Live.
- `core/decision/MarketInsight.kt` — new sealed type (Phase 4D's DecisionEngine integration, see §13).
- `core/data/model/DecisionOutput.kt`, `core/decision/DecisionEngine.kt`, `core/data/composite/FieldDecisionResolver.kt` — `marketInsight` threaded through exactly like Phase 4B's `fertilizerRecommendation`.
- `feature/dashboard/DashboardScreen.kt` — Market card extended: "Latest available mandi price" label, min/modal/max, market name, arrival date, trend word, and a comparison list of other mandis when more than one was returned.
- `core/designsystem/strings/AppStrings.kt` — 9 new EN/HI/MR string keys for the above.

**Not touched**: `RepositoryModule.kt` (no new bindings needed), ESP32/sensor pipeline, Phase 3A/3B risk-fusion logic, Weather's Phase 4C provider, Farmer Profile storage (state/district/crop already existed and were reused as-is, per the brief's explicit instruction not to duplicate profile storage).

## 6. API Configuration

`DATA_GOV_IN_API_KEY` — read from server environment/`.env` via `Settings.data_gov_in_api_key` (pydantic-settings), same convention as `GEMINI_API_KEY`. Empty by default. **Not present in this environment's `.env`** — confirmed by inspection. No key was inserted, faked, or hardcoded anywhere; the Android app never references this key at all (server-only, per the "server holds the keys" pattern already used for Gemini/Weather).

## 7. Real Provider Implementation

`get_market_price(crop, state, district)`:
1. Rejects immediately (no network call) if the key is missing or crop is blank.
2. Normalizes the crop name via `.strip().title()` — deterministic, documented, never a fuzzy/guessed mapping (§8).
3. Calls the resource API with `filters[commodity]` (always) and `filters[state]`/`filters[district]` (when the farmer has them set).
4. Parses every returned record into a `MandiRecord` (market, district, state, commodity, variety, grade, arrival_date, min/max/modal price — numeric fields safely `float()`-converted, `None` on anything unparseable, never a guess).
5. Picks the mandi with the **highest modal price** as the "primary" one (the brief's "highest available modal mandi price" framing) — never labeled as highest *profit*.
6. Computes trend deterministically (§9).

## 8. Filtering / Normalization Logic

- **Crop**: `"cotton"`/`"COTTON"`/`"Cotton"` → `"Cotton"` (title-case). A crop the dataset's commodity field doesn't recognize under this exact form returns "no mandi price found" (honest unavailable), never a silently substituted crop.
- **State/District**: passed through verbatim from the farmer's own `FarmLocation` as exact-match dataset filters — no hardcoded location anywhere; works for any state/district the dataset covers.

## 9. Trend Logic (deterministic, documented — never an LLM)

For records sharing the same `market` name with two or more distinct, parseable `arrival_date`s: compare the two most recent modal prices → `RISING` (latest higher), `FALLING` (latest lower), `STABLE` (equal). Any market with fewer than two dated records → `UNKNOWN`. Since this "current daily price" dataset typically returns one row per market per day, `UNKNOWN` is the common, honest result for a single fetch — not a defect.

## 10. Cache Behavior

Unchanged mechanism from Phase 4A (`MarketStateCache`, DataStore-backed): a successful live fetch persists; a restart restores it marked `CACHED`; any failure after a real value (live or restored) preserves it as `CACHED`; no cache and an unreachable/unconfigured provider settles to `UNAVAILABLE`. The cached DTO was extended (multi-market list, trend, arrival date, variety, grade) using the same "never persist `status`" rule already established.

## 11. Mock/Live Behavior

Unchanged switch (`DefaultMarketRepository`/`SettingsRepository.appMode`). `MockMarketRepositoryImpl` now returns two demo mandis + `STABLE` trend so Mock Mode can exercise the new comparison UI without a real key.

## 12. Error Handling

| Case | Behavior |
|---|---|
| Missing API key | Rejected before any network call — verified by test (`mock_ctor.assert_not_called()`) |
| HTTP 401/403/404/429/500 | `response.raise_for_status()` → `httpx.HTTPStatusError` → `MarketProviderError` → 503 |
| Timeout / network failure | `httpx.HTTPError` → `MarketProviderError` → 503 |
| Malformed JSON | `ValueError`/`AttributeError` → `MarketProviderError` → 503 |
| Empty records | `MarketProviderError` ("no mandi price found for X") → 503 |
| Crop/location not found | Same as empty records (the filtered query legitimately returns nothing) |
| Unparseable price fields | That field becomes `None`, never a guessed number — verified by test |
| Stale data | Not hidden — `arrival_date` is always surfaced to the farmer (server and UI); no separate staleness flag was invented since showing the real date already satisfies "don't hide it" |

All of the above degrade to the existing `CACHED`/`UNAVAILABLE` state machine on the Android side — never a fabricated fallback.

## 13. UI Changes

Dashboard's existing Market card (previously crop + one price) now shows, only when real data exists: min/modal/max price, market name, arrival date, a deterministic trend word (omitted entirely when `UNKNOWN` — never guessed), the LIVE/CACHED/MOCK/UNAVAILABLE status label, and — when more than one mandi was returned — a short "Other mandis" comparison list (name + modal price only, no invented profit/transport-cost comparison). Labeled "Latest available mandi price," never "real-time," per the brief. No raw API JSON is ever shown.

## 14. DecisionEngine Integration

New `MarketInsight` sealed type (`PriceAvailable(crop, market, modalPricePerQuintal, trend, arrivalDate)` / `Unavailable`), computed by `FieldDecisionResolver` from `MarketRepository.market.value` and threaded through `DecisionEngine.evaluate()` into `DecisionOutput.marketInsight` — structurally identical to Phase 4B's `fertilizerRecommendation` pass-through. It plays **no part** in `overallRisk`/`recommendation` (verified by direct `DecisionEngineTest` equality checks and by a `FieldDecisionResolverTest` proving a HIGH disease risk survives alongside a real market insight).

**Deliberately not implemented**: the brief's example farmer-facing sentence ("Current mandi price is favorable compared with recent available prices") was not built. "Favorable" requires a real baseline (transport cost, the farmer's own cost basis, or a genuine historical price distribution) that no part of this system has real data for — inventing that comparison would violate the same "never fabricate" rule the whole system is built around. `MarketInsight` carries only real numbers (price, trend, date) for a future phase to render once such a baseline genuinely exists.

## 15. Tests Added

**Server** (`server/tests/test_market_provider.py`, 15 tests): successful parsing, crop normalization + state/district passed as filters, state-only query, multiple markets → highest-modal selected as primary, empty records, timeout, HTTP 401/429/500, malformed JSON, missing key (no network call), unparseable prices → `None` never a guess, trend UNKNOWN/RISING/FALLING.

**Server** (`server/tests/test_weather_market_endpoints.py`, +2 tests): router passes real provider data through unchanged; router maps a provider failure to honest 503. The pre-existing "unavailable without a configured provider" test needed **no change** — `DATA_GOV_IN_API_KEY` is genuinely still unset in this environment, so that test's premise remains true.

**Android** (8 new tests): `FieldDecisionResolverTest` (+3: real price → `MarketInsight` in the final output; unavailable market never fabricates a price; HIGH disease risk survives alongside a real market insight), `DecisionEngineTest` (+3: pass-through unchanged, null when absent, never influences risk/recommendation — mirroring the Phase 4B fertilizer tests exactly), `LiveMarketRepositoryImplTest` (+2: the farmer's actual state/district are sent to the server; multiple mandis + a real trend map correctly into `MarketState`).

**Fix verified, not just asserted**:
- Reverted `FieldDecisionResolver`'s market pass-through — reran the suite, confirmed exactly the 3 new `FieldDecisionResolverTest` market tests failed (all 10 others green), then restored and reconfirmed a full fresh green run (85/85).
- Broke the server's `_compute_trend` RISING/FALLING branches — reran, confirmed exactly the 2 targeted trend tests failed (13/15 others green), then restored and reconfirmed the full server suite.

## 16. Complete Test Results

- **Android**: `./gradlew testDebugUnitTest --rerun-tasks` (fully fresh, no cache) → **BUILD SUCCESSFUL**, 34/34 tasks executed. Total tests: **85 / 85 passed** (77 pre-existing + 8 new).
- **Server**: `python -m pytest tests/` → **39 passed**, 6 pre-existing failures unrelated to this phase (disease/pest/risk-fusion endpoint stubs — same 6 that failed before any Phase 4C/4D work, confirmed via `git stash` in Phase 4C and unchanged since).

## 17. Build Result

- `./gradlew assembleDebug` → **BUILD SUCCESSFUL**.
- Server: no separate build step; `uvicorn app.main:app` starts from the same unchanged `main.py` import graph.

## 18. Live API Verification Result

**Not performed.** `DATA_GOV_IN_API_KEY` is not present in `server/.env` in this environment (confirmed by direct inspection). Per the brief's explicit instruction, no real request was attempted and no live functionality is claimed.

**Implementation complete; live verification requires DATA_GOV_IN_API_KEY.**

## 19. Known Limitations

- Live correctness against the real data.gov.in API is unverified (no key available here) — the implementation is tested against realistic mocked payloads matching the documented resource schema, not a real response.
- Crop normalization is intentionally simple (case/whitespace only); a crop whose commodity name differs structurally in the dataset (e.g. a multi-word or region-specific name) will honestly report "not found" rather than guess — a larger normalization table would need real, sourced data-gov.in commodity names, which this session did not fabricate.
- Trend is usually `UNKNOWN` for a single day's query, by design — the "current daily price" dataset doesn't naturally return multi-day history in one call.
- No dedicated Market screen exists (Dashboard card only) — matches the pre-existing Phase 4 audit finding; not rebuilt since the brief said to improve, not replace, existing UI.
- `MarketInsight` is not yet rendered anywhere farmer-facing (Advisory screen, etc.) — it exists in `DecisionOutput` for a future phase, deliberately not surfaced with any invented "favorable" judgement (§14).

## 20. Next Recommended Phase

Per `PHASE_4_AUDIT_REPORT.md`'s original order: Government Scheme matching engine (fully unblocked, no external dependency) or Farmer Feedback (fully unblocked, no dependency). Local LLM and IVR remain blocked on an external model/telephony asset; live Market verification remains blocked on a real `DATA_GOV_IN_API_KEY`.

---

**Stopping here.** Not starting Local LLM, offline voice, IVR, Government Schemes, or Farmer Feedback in this turn.
