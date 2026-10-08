# KRISHINIRNAY — Phase 3 Audit Report

Grounded in the actual repository state (nothing from Phase 0–2 has been committed yet — `git status` still shows all of it as pending changes on top of commit `2922ae8`). This audit re-reads the real files for every claim below; it does not carry forward Phase 0/1/2 report text without re-checking it against current code. No code was changed to produce this document.

**Headline finding, not stated in the Phase 3 brief**: several Phase 2 building blocks are real and unit-tested but **not yet wired into the live decision pipeline** — the region rule layer, disease×rain escalation, and pest-stage escalation are all reachable only from tests, not from `MockFieldStateRepositoryImpl`/`LiveFieldStateRepositoryImpl`'s actual calls to `DecisionEngine.evaluate()`. See §4 and §7. This is exactly the "interface exists but isn't connected" trap the brief's final rule warns against, so it's called out first.

---

## 1. Farmer Profile — REAL IMPLEMENTATION (Phase 2), gaps remain

- `FarmerProfile` (`core/data/model/FarmerProfile.kt`) has name, phone, location, farmSizeAcres, crops, soilType, seedlingStage, `FarmLocation` (state/district/taluka/village), `irrigationMethod`, `cropVariety`, `sowingDate`.
- Persisted for real via `FarmerProfileStore` (DataStore/JSON) + `FarmerProfileRepositoryImpl`, editable through `feature/farmsetup/FarmSetupScreen.kt`. Survives restarts, works offline. Unit-tested (`FarmerProfileStoreTest.kt`).
- **Gap**: `farmLocation`/`irrigationMethod`/`cropVariety` feed Weather, Market, and Fertilizer (real) but **not** `DecisionEngine` or the ML risk-fusion call — those still only read `soilType`/`seedlingStage`/`primaryCrop`. Location doesn't yet influence "crop recommendations" or "regional agriculture rules" end-to-end (see §5).
- Sowing date has a model field, no UI picker yet (documented gap from Phase 2 report).

## 2. Soil + Sensor Data — REAL, UNCHANGED (correctly, per instruction)

- ESP32 → FastAPI → Android pipeline (`POST /api/sensor-data` → `GET /api/latest-sensor` → `SensorApiService` → `LiveFieldStateRepositoryImpl`) verified byte-for-byte unchanged from Phase 1. `SensorDataDto`/`LatestSensorResponseDto` untouched.
- Mock/Live switching (`DefaultFieldStateRepository`, `RepositoryModule.kt`) still correctly wired from Phase 1.
- Soil Health Card data: **not implemented, not started** — `SensorReading`'s `nitrogenPpm`/`phosphorusPpm`/`potassiumPpm` are the only NPK inputs anywhere; there's no separate "Soil Health Card" model or import path. Fertilizer (§6) already consumes these three fields.
- No physical sensors required for any of the above — confirmed no code path assumes real hardware presence.

## 3. Weather — REAL ARCHITECTURE, HONESTLY UNAVAILABLE

- Full stack exists: `server/app/routers/weather.py` (`GET /v1/weather`, auth-gated) → `weather_provider.py` (returns `WeatherProviderError` when `WEATHER_API_KEY` is unset, mirroring the existing Gemini-proxy pattern) → Android `WeatherApiService` → `LiveWeatherRepositoryImpl` (keys off `FarmerProfile.farmLocation`, auto-refetches when it changes) → `DefaultWeatherRepository` (Mock/Live switch, same qualifiers as sensors) → `WeatherState` with `DataSourceStatus` (LIVE/CACHED/MOCK/UNAVAILABLE).
- Dashboard and `WeatherScreen` both render the honest status; unit-tested (`LiveWeatherRepositoryImplTest.kt`: no-location → UNAVAILABLE, failed-poll-after-success → CACHED, values preserved).
- **BLOCKED BY EXTERNAL DEPENDENCY**: no real provider is configured (`WEATHER_API_KEY` empty in `.env`) — this is correct, honest behavior, not a bug. Choosing and wiring a real provider is the remaining work.
- **Gap**: `WeatherState` is not yet consumed by `DecisionEngine` — `RainOutlook` exists in the engine's input contract (Phase 2) but nothing computes it from `WeatherRepository.weather` and passes it into a live `DecisionInput`. Rain-aware irrigation/disease logic is real and tested in isolation, not connected.

## 4. Decision Engine — CORE LOGIC REAL, INTEGRATION INCOMPLETE

- `DecisionEngine.evaluate(input, ruleSet)` genuinely combines sensors, ML model output, disease result, pest result, device-online, crop stage, and rain outlook into `overallRisk`/`waterStressRisk`/`heatRisk`/`cropHealthRisk`/`pestRisk`/`recommendation`/`timing`/`expectedBenefit`/`reasons`. 23 passing unit tests (`DecisionEngineTest.kt`).
- **Verified gap (not previously reported)**: every actual call site — `MockFieldStateRepositoryImpl.kt` (3 call sites) and `LiveFieldStateRepositoryImpl.kt` (3 call sites) — constructs `DecisionInput` **without** `cropStage`, `rainOutlook`, or a resolved `ruleSet`. All three silently default (`cropStage = null`, `rainOutlook = UNKNOWN`, `ruleSet = DefaultRuleSet`). Confirmed by grep — zero references to `RegionCropRuleRegistry` outside its own file and its tests. Net effect: **region-specific rules, pest-stage escalation, and disease×rain escalation are real, correct, and tested, but currently inert in the running app** — they need `ProfileRepository` (for cropStage/region/crop) and `WeatherRepository` (for rainOutlook) threaded into these two repository classes.
- **Verified gap (not previously reported)**: `DashboardViewModel.kt`'s separate "smart overall risk" heuristic (pre-existing, not written in Phase 2) can **mask** a pest-driven `HIGH` from `decision.overallRisk`. Its fallback chain is `criticalWaterRisk → extremeHeat → mlRisk(HIGH,confident) → mediumWaterRisk → mediumHeat → mlRisk(any) → decision.overallRisk`. If the ML risk-fusion call returns a non-null LOW/MEDIUM class while a pest scan says HIGH, the `mlRisk != null -> mlRisk` branch fires first and the correctly-computed pest-driven HIGH in `decision.overallRisk` is never reached. This is a real bug for Phase 3 to fix, not a Phase 2 regression (the heuristic simply predates pest integration and was never updated for it).
- Market and Government Schemes are correctly **not** wired into `DecisionEngine` — no code claims otherwise.

## 5. Vidarbha + Cotton Decision Logic — MODULE REAL, NOT YET ACTIVE

- `core/decision/region/RegionCropRuleSet.kt` (interface), `VidarbhaCottonRules.kt`, `RegionCropRuleRegistry.kt` all exist, are generic (support arbitrary region+crop pairs, `DefaultRuleSet` fallback), and are unit-tested (escalation with/without the rule set, case-insensitive lookup, unmodeled-region fallback).
- Per §4, **not called from any live code path** — this is the same gap stated two ways: the abstraction is done, the wiring isn't. Fixing §4's gap and this one is the same piece of work (thread `ProfileRepository` into the two `FieldStateRepository` impls, resolve a `RegionCropRuleSet` from the farmer's `farmLocation.state` + `primaryCrop`, pass it to `DecisionEngine.evaluate`).

## 6. Fertilizer Recommendation — REAL, WIRED, HONEST

- `core/fertilizer/FertilizerAdvisor.kt` (+`FertilizerInput`/`FertilizerRecommendation`) is a real offline rule engine consuming crop, soil type, crop stage, farm area, soil moisture, NPK, irrigation method. Returns `InsufficientData` when no NPK reading exists at all, `NoActionNeeded` when nutrients are sufficient, or `Recommended` with a generic indicative quantity range and a mandatory safety note pointing at a real soil-test card — never a fabricated precise dose or cost. 6 passing unit tests.
- **Actually wired**: `CropAdvisoryViewModel.kt` calls it with the real `FieldState`/`FarmerProfile` and renders the result via `AppStrings.textFor(FertilizerRecommendation)` in EN/HI/MR. This replaced a previously-static hardcoded string (`advisoryFertilizerDetail`, now removed).
- Not yet exposed on the Dashboard itself, only on the Advisory screen.

## 7. Pest + Disease → Decision Engine — REAL FIX LANDED, ONE GAP REMAINS

- **Real bug fixed in Phase 2**: `CropHealthRepositoryImpl.kt`'s disease `riskLevel` was hardcoded `RiskLevel.UNKNOWN` forever (confirmed still fixed — now derived from `status`/confidence). Before this fix, a disease scan could never move `cropHealthRisk` off UNKNOWN, so `DecisionEngine` was structurally incapable of reacting to it regardless of engine logic.
- `PestDetectionViewModel.kt` now derives a real `PestResult.riskLevel` from `detected` + top-detection confidence and calls the newly-added `FieldStateRepository.recordPestResult()` (implemented in both Mock/Live impls, `DefaultFieldStateRepository`, and persisted through `FieldStateCache`). Confirmed present, confirmed exercised by `DecisionEngineTest`'s pest tests.
- `AlertGenerator.kt` now has a real pest-HIGH alert path (mirrors the existing disease one) — previously dormant because disease risk was always UNKNOWN; now both paths are live.
- **Remaining gap**: the pest-stage escalation from §4/§5 (e.g. cotton at flowering) doesn't fire in production because `cropStage` isn't threaded through. Functionally, an un-escalated pest scan still correctly raises `overallRisk` today (subject to the DashboardViewModel masking issue in §4) — it just doesn't get the region-specific severity boost yet.
- "Farmer-friendly explanation" of a pest/disease result: reasons and recommendations are localized (`ReasonOutcome.PestAssessed`/`CropHealthAssessed` → `textFor`), but there is no dedicated "why this action" screen combining photo + risk + action + timing in one place yet — today it's spread across the Pest/Disease screens and the Dashboard's "Today's Decision" card.

## 8. Market Price System — REAL ARCHITECTURE, HONESTLY UNAVAILABLE

- Same shape as Weather: `server/app/routers/market.py` → `market_provider.py` (503 when `MARKET_API_KEY` unset) → `MarketApiService` → `LiveMarketRepositoryImpl` (keys off `FarmerProfile.primaryCrop`) → `DefaultMarketRepository` → `MarketState` with `DataSourceStatus`.
- Dashboard shows a Market card with the honest unavailable message in Live Mode, a static labeled-MOCK sample in Mock Mode. Unit-tested (`LiveMarketRepositoryImplTest.kt`).
- **BLOCKED BY EXTERNAL DEPENDENCY**: no real market-price provider (e.g. Agmarknet) is integrated — correct current behavior, not a bug.
- No dedicated Market screen yet, only the Dashboard card (documented scope cut in the Phase 2 report).
- Not consumed by `DecisionEngine` — correctly not claimed anywhere that it is.

## 9. Government Schemes — STILL MOCK, UNCHANGED SINCE PHASE 0

- `MockSchemesRepositoryImpl.kt` still returns the same static 3-item list (PM-KISAN, Krishi Yantra Anudan, Mridha Swasthya Card) with descriptive `eligibility` **text**, not computed eligibility logic. Confirmed unchanged this session.
- No use of `FarmerProfile.farmLocation`/`farmSizeAcres`/`crops` to filter or rank schemes. **Not implemented** — this entire section is exactly where Phase 0 left it.

## 10. Local LLM — NOT IMPLEMENTED, NO ABSTRACTION EXISTS YET

- Confirmed by repo-wide search: no `LocalLlmEngine`, `LocalAiRepository`, or any on-device inference interface exists anywhere in `/app`. The only conversational path remains `POST /v1/chat` → Gemini (`gemini_proxy.py`), server-side, online-only.
- **Nothing to misrepresent here** — Phase 2 did not touch this area, and no code anywhere claims offline LLM capability.
- This is the largest genuinely unstarted item in the whole brief. Building even the interface (`LocalLlmEngine`, a `LocalAiRepository` that the Chatbot screen could optionally route to) is real, buildable Phase 3 work; the actual on-device model/runtime (e.g. a quantized GGUF model + llama.cpp-Android or MediaPipe LLM Inference) is a genuine external-asset dependency — multi-hundred-MB model file, licensing, and on-device performance validation on real hardware, none of which can be done from this environment.

## 11. Multilingual Support — REAL, SUBSTANTIALLY DONE

- `AppStrings.kt` now has three full parallel string sets — `EnglishStrings`, `HindiStrings`, `MarathiStrings` — dispatched by `appStringsFor(languageTag)` (`"en"|"hi"|"mr"`). Settings screen offers all three (`SettingsScreen.kt`). Confirmed: 2 occurrences of `"mr"`/`MarathiStrings` in the dispatcher, and `MarathiStrings` supplies every field the data class declares (compiles — a missing field would be a compile error, not a silent gap).
- All new Phase 2 decision/fertilizer/farm-setup strings are genuinely translated (not copied from Hindi). The Phase 2 report's one caveat stands: the ~95 pre-Phase-2 UI-chrome strings were translated but not independently proofread by a fluent reviewer.
- Business logic (`DecisionEngine`, `FertilizerAdvisor`) never contains a hardcoded language string — output is structured (`RecommendationOutcome`, `TimingOutcome`, etc.) and rendered only at the UI layer via `textFor`, exactly per the brief's "don't scatter strings in business logic" rule.

## 12. Voice-to-Voice — PARTIAL, UNCHANGED SINCE PHASE 0

- `core/voice/SpeechRecognizerManager.kt` (native Android `SpeechRecognizer`) and `TextToSpeechManager.kt` (native `TextToSpeech`) are real and wired into `ChatbotScreen`'s mic button / per-message "Listen" — confirmed still present, untouched by Phase 2.
- This is voice I/O around the **online, Gemini-backed** chat pipeline, not "local LLM + voice." No offline STT/TTS model asset is bundled — Android's built-in engines are used as-is, which typically still call out to Google's servers for recognition/synthesis quality depending on device/language, so this cannot honestly be called "fully offline voice."
- No dedicated voice architecture for the Decision Engine / farm context beyond what already feeds the chatbot's prompt.

## 13. IVR for Keypad Phones — NOT IMPLEMENTED, NO ABSTRACTION EXISTS

- Confirmed by repo-wide search: zero references to IVR, telephony, or a call-flow interface anywhere in `/app` or `/server`. Unchanged since Phase 0.
- **BLOCKED BY EXTERNAL DEPENDENCY**: requires a telephony/IVR provider (e.g. Exotel, Twilio Voice, Knowlarity) and a phone number — cannot be demonstrated without one. Building the backend interface (a webhook contract + language-menu state machine) is legitimate Phase 3-scoped work; making it "live" is not possible without provisioning a real number and provider account.

## 14. Farmer Feedback — NOT IMPLEMENTED

- Confirmed by repo-wide search: no feedback model, repository, or screen anywhere. Unchanged since Phase 0.
- Straightforward to add in the existing pattern (a `FeedbackRepository` + DataStore-or-server persistence, mirroring `FarmerProfileStore`) — no external dependency blocks this one; it's simply not built yet.

## 15. Offline-First Design — MOSTLY REAL

- Persisted/cached offline: farmer profile (`FarmerProfileStore`), last sensor reading + full decision + disease + pest result (`FieldStateCache`, extended in Phase 2 to include pest/timing/benefit), Settings/language (`AppPreferences`).
- **Not cached offline**: Weather and Market currently hold their last value only in an in-memory `StateFlow` (`LiveWeatherRepositoryImpl`/`LiveMarketRepositoryImpl`) — a process restart loses the last-known weather/market reading and resets to UNAVAILABLE until the next successful poll, unlike sensor/decision state which survives restarts via DataStore. This is a real, scoped gap: "cached recommendations" and "previously downloaded data" from the brief aren't fully met for Weather/Market specifically.
- LIVE/CACHED/MOCK/UNAVAILABLE statuses are honestly represented everywhere they exist (sensors, weather, market) — confirmed no code path labels cached or mock data as LIVE.
- Local LLM assets / offline language resources for voice: not applicable, since neither exists yet (§10, §12).

## 16. UI/UX — DASHBOARD EXTENDED, NOT REDESIGNED

- Phase 2 added Farm Status, Weather, Market, and "latest scan results" cards to the existing Dashboard `LazyColumn`, and enriched the risk card with WHEN/BENEFIT text — appended, not restructured, per the Phase 1/2 instructions.
- No dedicated visual-design pass has been done (no use of the design-system UI/UX skills mentioned in the brief) — the new cards reuse the existing `KnCard` component and typography scale as-is. If "next-level" visual polish is in scope for Phase 3, that's separate, not-yet-started work distinct from the functional wiring above.
- Mixed-i18n issue from Phase 0 (B12: hardcoded English literals like "Live Sensor Data" alongside localized strings in the same file) **still present** — Phase 2 added more hardcoded literals in the same file (e.g. "Latest scan results", "Pest Detection" card text) rather than fixing the pre-existing pattern, for consistency with the surrounding code at the time. Worth a dedicated i18n pass.

---

## Cross-cutting: Architecture Rules (§17), Server Config (§21), Security (§22)

- Kotlin/Compose/Hilt/Retrofit/Coroutines patterns followed throughout Phase 2 — every new repository follows the existing interface/impl/DI-binding shape, no new framework introduced.
- `SERVER_BASE_URL`/`SERVER_API_KEY`/`adb reverse` flow unchanged — Weather/Market ride the same Retrofit/OkHttp instance and API-key interceptor, no second network stack.
- `WEATHER_API_KEY`/`MARKET_API_KEY` added to `server/app/config.py` with empty defaults — same pattern as the pre-existing `GEMINI_API_KEY`/`api_key`, no secret hardcoded.
- Pre-existing, still-open Phase 0 findings B4/B5 (server `requirements.txt` missing `tensorflow`/`Pillow`/`ultralytics`; Dockerfile not copying `models/`) remain unfixed — out of scope for Phases 1–2, not touched.

---

## Summary table

| Area | Status |
|---|---|
| Farmer Profile | Real, persisted, gaps: not fed into DecisionEngine/region rules |
| Sensor pipeline | Real, unchanged, correctly preserved |
| Weather | Real architecture, blocked on provider key; not fed into DecisionEngine |
| Decision Engine core logic | Real, tested | 
| Decision Engine live integration | **Partial — region rules/rain/crop-stage built but not wired; ML-heuristic can mask pest risk** |
| Vidarbha+Cotton rules | Real module, tested, **not yet invoked in production** |
| Fertilizer | Real, wired, honest |
| Pest→Decision | Real, wired (Phase 2 fix) |
| Disease→Decision | Real, wired (Phase 2 fix) |
| Market | Real architecture, blocked on provider key |
| Government Schemes | Mock only, no real matching — unstarted |
| Local LLM | Not implemented, no abstraction yet — largest unstarted item |
| Marathi | Real, substantially complete |
| Voice-to-voice | Partial — real STT/TTS I/O, online-only underlying chat, no offline claim |
| IVR | Not implemented — blocked on telephony provider |
| Farmer Feedback | Not implemented, no external blocker |
| Offline-first | Mostly real; Weather/Market don't survive restart |
| UI/UX polish | Functional additions only, no design pass yet |

---

**Stopping here per the Phase 3 execution rule.** No code has been changed. Awaiting direction on which of the above to implement first — given the findings, the highest-leverage, fully-unblocked next steps (no external key/hardware/model required) are: (a) wire `RegionCropRuleRegistry`/`cropStage`/`rainOutlook` into the two `FieldStateRepository` impls so the already-built region/rain/pest-stage logic actually runs, (b) fix the `DashboardViewModel` ML-heuristic masking gap, (c) persist Weather/Market's last value like sensor state already is, and (d) real Government Schemes eligibility matching against the farmer profile. Local LLM, IVR, and real Weather/Market providers are the items genuinely blocked on external dependencies (model asset, telephony account, API key respectively).
