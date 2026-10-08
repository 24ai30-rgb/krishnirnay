# KRISHINIRNAY — Real Weather Integration Report (Open-Meteo)

Scope: make real weather work using the farmer's actual saved coordinates, extending the existing Open-Meteo provider (Phase 4C) rather than building a second one. No API key — Open-Meteo's public endpoints require none.

---

## 1. Audit (before any code was touched)

Real Open-Meteo weather already existed from Phase 4C: `server/app/services/weather_provider.py` geocoded the farmer's `state`/`district` text into coordinates via Open-Meteo's own geocoding endpoint, then called the forecast endpoint — genuinely real, keyless, tested. What this task specifically required and didn't yet exist: (a) using the farmer's **saved** latitude/longitude directly, skipping geocoding entirely when available — `FarmLocation` had no lat/lon fields at all; (b) a few additional Open-Meteo request parameters (`rain`, `wind_direction_10m`, `rain_sum`, `precipitation_sum`) the brief listed as required; (c) an unhandled invalid-JSON case in the geocoding path (a real, previously-undiscovered bug — see §7).

## 2. Real API Endpoints

- Geocoding (only used when no saved coordinates exist): `https://geocoding-api.open-meteo.com/v1/search`
- Forecast: `https://api.open-meteo.com/v1/forecast`

Both are Open-Meteo's free, public, keyless endpoints — confirmed no key is sent, requested, or required anywhere in the code.

## 3. Request Parameters

```
current = temperature_2m,relative_humidity_2m,precipitation,rain,weather_code,wind_speed_10m,wind_direction_10m
daily   = weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,rain_sum,precipitation_probability_max
timezone = auto
forecast_days = 7
latitude, longitude = the farmer's saved coordinates (or the geocoded result of their state/district text)
```

## 4. Response Mapping

| Open-Meteo field | Android-facing field | Notes |
|---|---|---|
| `current.temperature_2m` | `current_temp_c` | |
| `current.relative_humidity_2m` | `humidity_pct` | |
| `current.rain` (fallback: `current.precipitation`) | `rainfall_mm` | `rain` is liquid-only and more accurate for "Rainfall" than `precipitation` (which also covers snow — irrelevant here but present in Open-Meteo's general schema) |
| `current.weather_code` | `condition` | mapped through the existing WMO-code table |
| `current.wind_speed_10m` | `wind_kph` | |
| `daily.precipitation_probability_max[0]` | `rain_chance_pct` | |
| `daily.temperature_2m_max/min[i]`, `daily.weather_code[i]` | `daily[i].high_c/low_c/condition` | 7 entries |
| `current.wind_direction_10m`, `daily.rain_sum`, `daily.precipitation_sum` | *(not yet surfaced)* | requested and available in the raw response for correctness/completeness, but not mapped into `WeatherResponse` — the existing Android contract and Dashboard don't ask for per-day rainfall totals or wind direction yet; adding them is a one-line change in `_to_weather_response`/the DTOs whenever a real UI need for them appears |

## 5. Farmer Location Flow (the actual change)

```
FarmerProfile.farmLocation
    ├─ latitude/longitude set (Farm Setup, manual entry) → used directly, no geocoding call
    └─ not set → state/district text geocoded via Open-Meteo (unchanged from Phase 4C)
    ↓
GET /v1/weather?state=&district=&latitude=&longitude=  (Android → this app's own server, unchanged contract shape, 2 new optional params)
    ↓
weather_provider.get_weather() → real Open-Meteo call → WeatherResponse
    ↓
WeatherState → RainOutlook → FieldDecisionResolver → DecisionEngine → Dashboard
```

`FarmLocation.latitude`/`longitude` (`Double?`, both `null` by default) were added — a farmer who has never entered them sees no behavior change (still geocodes by text, exactly as before). Farm Setup gained two optional numeric fields ("Latitude"/"Longitude") with a plain-language hint (search the village on Google Maps, long-press the pin) — manual entry, not device GPS; no location permission was added, keeping this a small, additive change rather than a new permissions/GPS subsystem. `FarmLocation.isUsable()` now also returns `true` when only coordinates are set (previously required non-blank `state`), so a farmer who only entered coordinates still gets real weather.

**No location is ever hardcoded** — Vidarbha/Punjab/Maharashtra/Mumbai never appear in the provider code; every request uses whatever the farmer actually saved.

## 6. LIVE / CACHED / UNAVAILABLE Behavior

Unchanged from Phase 4A/4C (not touched, still correct): a successful fetch persists via `WeatherStateCache` and marks `LIVE`; any failure after a real value (live or restored) preserves it as `CACHED`; no cache and an unreachable provider settles to `UNAVAILABLE`; `RainOutlook` stays `UNKNOWN` (never assumed dry) whenever weather is `UNAVAILABLE`. No fabricated fallback value exists anywhere in the provider.

## 7. Files Changed

**Server**:
- `app/services/weather_provider.py` — `get_weather()` gained optional `latitude`/`longitude` params (skips geocoding when both present); added `rain`/`wind_direction_10m`/`rain_sum`/`precipitation_sum` to the request; `rainfall_mm` now prefers `rain` over `precipitation`; **fixed a real bug** — `_geocode()`'s `response.json()` call was never wrapped in error handling, so a malformed geocoding response would raise an uncaught `ValueError` (a 500) instead of the intended honest 503.
- `app/routers/weather.py` — `state` is now optional (default `""`, since coordinates alone are sufficient); added optional `latitude`/`longitude` query params, passed straight through.

**Android**:
- `core/data/model/FarmerProfile.kt` — `FarmLocation` gained `latitude`/`longitude: Double? = null` and `hasCoordinates()`; `isUsable()` now accounts for coordinates too.
- `core/data/local/FarmerProfileStore.kt` — persists the two new fields (DataStore/JSON, same pattern as every other field).
- `feature/farmsetup/{FarmSetupViewModel, FarmSetupScreen}.kt` — two new optional numeric fields.
- `core/network/WeatherApiService.kt` — two new optional query params.
- `core/data/network/LiveWeatherRepositoryImpl.kt` — passes `farmLocation.latitude`/`longitude` on every fetch.
- `core/designsystem/strings/AppStrings.kt` — 4 new EN/HI/MR keys for the Farm Setup coordinates section.

**Not touched**: `WeatherState`/`DayForecast`/`WeatherResponse` schemas (additive-only elsewhere, no breaking change), `RainOutlook`, `FieldDecisionResolver`, `DecisionEngine`, `WeatherStateCache`, `MockWeatherRepositoryImpl`, `DefaultWeatherRepository`, the Dashboard's Weather card, Market, Fertilizer, Pest/Disease, Local LLM, Voice, IVR, Government Schemes, Feedback, the ESP32 pipeline, and every existing Retrofit/FastAPI contract shape (only additive optional parameters were introduced).

## 8. Tests Added

**Server** (`test_weather_provider.py`, +9 tests): real coordinates skip geocoding entirely (asserted via call count), coordinates are sent to the forecast endpoint exactly as given, coordinates alone with no state text still work, no state and no coordinates is rejected before any network call, rainfall prefers `rain` over `precipitation`, rainfall falls back to `precipitation` when `rain` is absent, invalid JSON from the forecast endpoint is honest, invalid JSON from the geocoding endpoint is honest (the bug fix from §7).

**Android** (+3 tests): `LiveWeatherRepositoryImplTest` — the farmer's saved coordinates are sent to the server; coordinates alone (blank state) still trigger a real `LIVE` fetch. `FarmerProfileStoreTest` — a profile with no saved coordinates round-trips them as `null`, never a fabricated default; the existing full-round-trip test now also covers real coordinates.

**Fix verified, not just asserted**: reverted the geocoding JSON-error guard and confirmed the exact targeted test failed with an uncaught `ValueError` (proving the bug was real); reverted the Android lat/lon pass-through and confirmed exactly the 2 targeted tests failed. Both restored and reconfirmed green.

## 9. Test Results

- **Server**: `python -m pytest tests/` → **90 passed** (81 pre-existing + 9 new); 6 pre-existing failures unrelated to weather (disease/pest/risk-fusion endpoint stubs, unchanged since before any weather work in this project).
- **Android**: `./gradlew testDebugUnitTest` → **121 / 121 passed** (118 pre-existing + 3 new).

## 10. Build Result

`./gradlew testDebugUnitTest assembleDebug` → **BUILD SUCCESSFUL**.

## 11. Limitations

- Coordinate entry is **manual** (typed in Farm Setup), not device GPS — no location permission was added; a farmer without coordinates still gets real weather via the existing geocode-by-address path.
- Wind direction and per-day rainfall totals are fetched from Open-Meteo (for completeness against the brief's exact parameter list) but not yet surfaced in the Android contract or UI — no current screen asks for them; adding them later is a small, additive change, not a redesign.
- Live network access to Open-Meteo was not exercised in this session (sandboxed environment) — every test mocks `httpx.AsyncClient`; the request/response shapes match Open-Meteo's documented, stable public contract, but a first real run against the live internet has not been observed here.

---

**Stopping here.** No other subsystem (Market, Fertilizer, Pest/Disease, Local LLM, Voice, IVR, Government Schemes, Feedback, ESP32 pipeline) was touched.
