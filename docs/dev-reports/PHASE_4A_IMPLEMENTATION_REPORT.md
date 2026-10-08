# KRISHINIRNAY Phase 4A Implementation Report

Scope: persist the last known-good Weather and Market reading so both survive an app restart, reusing the exact DataStore pattern already established by `FieldStateCache`/`FarmerProfileStore`. No external API, key, or provider was added. No Phase 3A/3B decision logic, ESP32 pipeline, or Retrofit/FastAPI contract was touched.

---

## 1. Goal

Make `LiveWeatherRepositoryImpl` and `LiveMarketRepositoryImpl` behave like `LiveFieldStateRepositoryImpl` already does: restore the last known-good value on cold start (marked `CACHED`, never `LIVE`), and never let a failed/skipped fetch erase a value the farmer could already see — whether that value arrived this session or was just restored from disk.

## 2. Existing Persistence Architecture (inspected, reused, not duplicated)

`FieldStateCache` (`core/data/local/FieldStateCache.kt`) and `FarmerProfileStore` (`core/data/local/FarmerProfileStore.kt`) both follow one pattern: a `@Singleton` class injected with the app's single shared `DataStore<Preferences>` (provided once in `DataStoreModule`), a private `@Serializable` DTO, `kotlinx.serialization.json.Json`, one `stringPreferencesKey`, and `save()`/`load()` suspend functions. Neither the domain models (`WeatherState`, `MarketState`) nor `core/decision` were ever annotated `@Serializable` — persistence detail stays out of the domain layer, matching the existing convention exactly.

This phase adds two new classes following that identical pattern — **not** a new cache mechanism, not a new DataStore instance, not a database.

## 3. Weather Persistence

**New**: `core/data/local/WeatherStateCache.kt` — `save(WeatherState)`/`load(): WeatherState?`, backed by the shared `DataStore<Preferences>`, key `cached_weather_state`. `status` is deliberately never serialized; `load()` always returns the domain object with `status = CACHED` — a restored value can never claim to be `LIVE`.

**`LiveWeatherRepositoryImpl` changes**:
- Constructor gained `weatherStateCache: WeatherStateCache`.
- `init{}` now calls `weatherStateCache.load()` and, if present, seeds `_weather` with it **before** the profile-driven fetch loop starts.
- A successful fetch now calls `weatherStateCache.save(next)` immediately after publishing the `LIVE` value.
- The failure-handling branch (previously inline in `fetchWeather`'s `catch` block and the "no usable location" branch) was consolidated into one `preserveAsCachedOrUnavailable()` — see §5 for the actual behavior fix.

## 4. Market Persistence

Structurally identical: new `core/data/local/MarketStateCache.kt` (key `cached_market_state`, `Instant → epoch millis` conversion for `fetchedAt`, same "never persist/trust a serialized `status`" rule), and the same three changes to `LiveMarketRepositoryImpl` (`marketStateCache` constructor param, load-before-fetch in `init{}`, save-on-success, consolidated `preserveAsCachedOrUnavailable(crop)`).

## 5. LIVE/CACHED/UNAVAILABLE Behavior

**Before this phase** (both repositories, identical bug): the failure/no-data branch only preserved the current value when its status was already exactly `LIVE`:
```kotlin
if (current.status == DataSourceStatus.LIVE) { current.copy(status = CACHED) } else { unavailableState(...) }
```
Since neither repository persisted anything, a restart always started from `UNAVAILABLE`. But even *within* a single run, once a value had been marked `CACHED` (by one failed poll), the **next** failed poll would hit the `else` branch and wipe it back to `UNAVAILABLE` — the exact opposite of "never erase valid cached values." With no real provider configured, every live fetch fails, so this was live, reproducible behavior, not a hypothetical.

**After this phase**: the check is `current.status != DataSourceStatus.UNAVAILABLE` — any real value (whether it just arrived `LIVE` this session or was restored from disk as `CACHED`) is preserved and (re-)marked `CACHED` on any subsequent failure. Only a value that was already `UNAVAILABLE` (nothing to preserve) stays `UNAVAILABLE`. Combined with cache restoration in `init{}`, the full required flow now holds for both Weather and Market:

```
Fresh successful fetch          -> LIVE   (+ persisted)
Restart, cache present          -> CACHED (restored, never LIVE)
Any failure after a real value  -> CACHED (value kept, never erased)
No cache, provider unreachable  -> UNAVAILABLE (nothing invented)
Mock repository                 -> MOCK  (untouched, not in scope)
```

No value is ever fabricated — every branch either forwards a real server response, a real restored cache entry, or an explicit "nothing to show" state.

## 6. Files Modified

**New**:
- `app/src/main/java/com/krishinirnay/core/data/local/WeatherStateCache.kt`
- `app/src/main/java/com/krishinirnay/core/data/local/MarketStateCache.kt`

**Modified**:
- `app/src/main/java/com/krishinirnay/core/data/network/LiveWeatherRepositoryImpl.kt`
- `app/src/main/java/com/krishinirnay/core/data/network/LiveMarketRepositoryImpl.kt`
- `app/src/test/java/com/krishinirnay/data/LiveWeatherRepositoryImplTest.kt` (constructor updated; 4 new tests)
- `app/src/test/java/com/krishinirnay/data/LiveMarketRepositoryImplTest.kt` (constructor updated; 4 new tests)

**Not touched**: `RepositoryModule.kt` (Hilt resolves the new cache dependency automatically via its own `@Inject` constructor, exactly like `FieldStateCache` needed no explicit binding either), `WeatherState`/`MarketState`/`DataSourceStatus` domain models, `WeatherApiService`/`MarketApiService`/DTOs, `DecisionEngine`, `FieldDecisionResolver`, `DashboardViewModel`, `DashboardScreen`, `WeatherScreen` (its existing `LIVE`/`CACHED`/`MOCK`/`UNAVAILABLE` label rendering, added in Phase 2, already displays this correctly — no UI change was needed), sensor DTOs, ESP32/FastAPI contracts.

## 7. Tests Added

| # | Test | File |
|---|---|---|
| 1 | A successful fetch persists the reading to `WeatherStateCache` | `LiveWeatherRepositoryImplTest` |
| 2+3 | Persisted weather survives repository recreation *and* reloads as `CACHED` (one test, two assertions, using a real file-backed DataStore to genuinely simulate a restart) | `LiveWeatherRepositoryImplTest` |
| 4 | A fetch failure never erases a value restored from cache (the exact bug) | `LiveWeatherRepositoryImplTest` |
| 5 | No cache + unreachable provider settles to `UNAVAILABLE` | `LiveWeatherRepositoryImplTest` |
| 6 | A successful fetch persists the price to `MarketStateCache` | `LiveMarketRepositoryImplTest` |
| 7+8 | Persisted market data survives repository recreation *and* reloads as `CACHED` | `LiveMarketRepositoryImplTest` |
| 9 | A fetch failure never erases a price restored from cache | `LiveMarketRepositoryImplTest` |
| 10 | No cache + unreachable provider settles to `UNAVAILABLE` | `LiveMarketRepositoryImplTest` |

Tests 2/3 and 7/8 use a **real** `PreferenceDataStoreFactory`-backed cache pointed at a temp file, with two separate repository instances (separate mocked API/profile dependencies each) sharing only that on-disk file — this is a genuine restart simulation, not a mocked shortcut.

**Fix verified, not just asserted**: temporarily reverted `preserveAsCachedOrUnavailable`'s check back to `== LIVE`, reran `LiveWeatherRepositoryImplTest`, and confirmed exactly the two persistence-dependent tests failed (`persisted weather survives repository recreation...`, and the restored-from-cache-survives-failure test) while the other four passed. Restored the fix and reconfirmed all green.

## 8. Test Results

- `LiveWeatherRepositoryImplTest` + `LiveMarketRepositoryImplTest` alone: 12/12 passed.
- Full suite (`./gradlew testDebugUnitTest`): **68/68 passed** (60 pre-existing + 8 new). No pre-existing test needed modification beyond the two repositories' constructor call sites.

## 9. Build Result

`./gradlew assembleDebug`: **BUILD SUCCESSFUL**.

## 10. Phase 3 Regression Verification

All Phase 3A/3B tests pass unmodified: `FieldDecisionResolverTest`, `MockFieldStateRepositoryImplTest`, the pre-existing `LiveFieldStateRepositoryImplTest`, `DecisionEngineTest` (23 tests), `DashboardViewModelTest` (6 tests, including the Phase 3B risk-masking regression tests). `FieldDecisionResolver`, `DecisionEngine`, `RegionCropRuleRegistry`, pest/disease integration, the ESP32→FastAPI→Android sensor pipeline, and every Retrofit/FastAPI contract are byte-for-byte unchanged.

## 11. Remaining Phase 4 Work

- No real Weather or Market provider is configured — by explicit instruction, not attempted this phase. Both still honestly report `UNAVAILABLE` in Live Mode until a provider/key is chosen.
- Fertilizer → `DecisionEngine` fusion, Government Scheme matching, Farmer Feedback, Local LLM, Offline Voice interfaces, and IVR remain exactly as characterized in `PHASE_4_AUDIT_REPORT.md` — untouched.
- `DashboardScreen`'s Weather/Market cards and `WeatherScreen`'s status label were not modified; they already read `DataSourceStatus` correctly (built in Phase 2) and needed no change to display the now-honest `CACHED` state.

## 12. Next Recommended Target

Per the Phase 4 audit's implementation order: **Fertilizer → `DecisionEngine`/`FieldDecisionResolver` fusion** — the next fully-unblocked item (no external key/account/model needed), extending `DecisionOutput` with an optional fertilizer field and having `FieldDecisionResolver` call `FertilizerAdvisor.recommend()` alongside `DecisionEngine.evaluate()`, so the Dashboard can surface it without any UI-layer agriculture logic.

---

**Stopping here.** Not starting Fertilizer fusion, Government Schemes, Farmer Feedback, Local LLM, Offline Voice, or IVR in this turn.
