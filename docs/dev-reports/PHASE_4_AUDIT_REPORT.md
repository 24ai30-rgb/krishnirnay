# KRISHINIRNAY Phase 4 Audit

Audit only — no production code, tests, dependencies, or configuration were changed to produce this document. Grounded in the actual repository state as of the end of Phase 3B (60/60 tests passing, `assembleDebug` green), re-verified this session via fresh reads/greps rather than carried forward from memory alone.

---

## 1. Executive Summary

Phases 1–3B built a real, tested, offline-safe core: sensor pipeline, Mock/Live switching, honest LIVE/CACHED/MOCK/UNAVAILABLE status, a Decision Engine that genuinely fuses sensors + pest + disease + region rules + rain outlook through one shared `FieldDecisionResolver`, and a Fertilizer rule engine. Weather and Market have complete, honest architecture (never fabricate data) but no real provider is configured, and — newly confirmed this audit — **neither persists its last value across a process restart**, unlike sensor/decision state.

Everything under "Local LLM," "Offline Voice" (beyond basic native STT/TTS I/O), "IVR," and "Farmer Feedback" is **not started** — no abstraction, no interface, no placeholder class exists for any of them. "Government Schemes" has a real but entirely static, non-personalized dataset with zero eligibility logic.

**Biggest blocker for anything Phase-4-shaped to feel "real": none of Local LLM, Weather provider, Market provider, or IVR can be un-blocked from inside this environment** — they each need an external asset/account (a model file, two API keys, a telephony number) this session cannot obtain. The one fully unblocked, highest-leverage next step is **Weather/Market persistence** (§20).

---

## 2. Current Architecture

```
Android UI (Compose)
  ↓
ViewModels (DashboardViewModel, CropAdvisoryViewModel, WeatherViewModel, FarmSetupViewModel, ...)
  ↓
Repositories (FieldStateRepository, WeatherRepository, MarketRepository, ProfileRepository, ...)
  ↓                                                    ↘
Decision layer (FieldDecisionResolver → DecisionEngine)   Fertilizer layer (FertilizerAdvisor) — parallel, not yet fused in
  ↓
Retrofit (NetworkModule) — X-API-Key, BuildConfig.SERVER_BASE_URL, adb reverse
  ↓
FastAPI (server/app/routers/*) — auth-gated except /health, /api/sensor-data, /api/latest-sensor
  ↓
ESP32 (POST /api/sensor-data) — not in this repo
```

Mock/Live switching (`DefaultFieldStateRepository`, `DefaultWeatherRepository`, `DefaultMarketRepository`) all follow one reused pattern: `@MockSource`/`@LiveSource`-qualified `@Binds` in `RepositoryModule.kt`, resolved per `SettingsRepository.appMode`. Confirmed unchanged and consistent (re-read this session).

---

## 3. Phase 4 Readiness Matrix

| System | Status | Existing | Missing | External Dependency |
|---|---|---|---|---|
| Weather | PARTIAL | Full repo/API/DTO stack, Mock Mode fully functional, honest UNAVAILABLE/CACHED | No real provider wired; no cross-restart persistence | Weather provider + API key |
| Market | PARTIAL | Full repo/API/DTO stack, Mock Mode fully functional, honest UNAVAILABLE/CACHED | No real provider wired; no cross-restart persistence | Market-price provider + API key |
| Fertilizer | PARTIAL | Real offline rule engine, wired into Advisory screen, tested | Not fused into `DecisionOutput`/`FieldDecisionResolver`; not on Dashboard | None |
| Local LLM | NOT_STARTED | Nothing — cloud Gemini chat is the only conversational path | Everything: interface, runtime, model | Model file + on-device runtime |
| Offline Voice | PARTIAL | Real native `SpeechRecognizer`/`TextToSpeech` wired to Chatbot | No engine abstraction/interface; underlying reasoning is cloud; offline guarantee unverified | Device-dependent offline STT/TTS language packs |
| IVR | NOT_STARTED | Nothing | Everything: webhook, session/state machine, provider integration | Telephony provider + phone number |
| Government Schemes | PARTIAL | Real `GovtScheme` model, real (non-fabricated) scheme text, static list | Zero eligibility/matching logic against farmer profile | Larger verified scheme dataset (optional) |
| Farmer Feedback | NOT_STARTED | Nothing | Everything: model, repository, UI | None |
| Farmer Profile | REAL_AND_WORKING | Persisted profile + Farm Setup UI covering every checklist item | Sowing-date picker UI; free-text stage/location (no taxonomy validation) | None |
| Offline-first | PARTIAL | Sensor/decision/pest/disease/profile persist and recover correctly | Weather/Market don't survive a restart | None |

---

## 4. Weather Audit

**Existing files**: `core/data/repository/WeatherRepository.kt` (interface, `refresh()`), `core/data/model/WeatherForecast.kt` (`WeatherState`, `DataSourceStatus`), `core/network/WeatherApiService.kt`, `core/network/dto/WeatherDto.kt`, `core/data/mock/MockWeatherRepositoryImpl.kt`, `core/data/network/LiveWeatherRepositoryImpl.kt`, `core/data/composite/DefaultWeatherRepository.kt`, `feature/weather/{WeatherScreen,WeatherViewModel}.kt`; server: `routers/weather.py`, `schemas/weather.py`, `services/weather_provider.py`; `config.py`'s `weather_api_key`.

**Existing flow**: `LiveWeatherRepositoryImpl` observes `ProfileRepository.profile.farmLocation`, calls `GET /v1/weather?state=&district=` through the same Retrofit/OkHttp/API-key stack as everything else, maps the response into `WeatherState(status = LIVE)`. On failure, keeps the last value and flips `status` to `CACHED` if it was `LIVE`, or `UNAVAILABLE` if there was nothing yet. Server-side, `weather_provider.py` raises `WeatherProviderError` whenever `WEATHER_API_KEY` is unset (it is, in the shipped `.env`) or when a provider isn't implemented — the route turns that into a structured `503`, never a fabricated forecast.

**What actually works**: Mock Mode's static demo forecast is fully functional and clearly labeled. The Live path's honesty contract (never claim LIVE for stale/absent data) is real and unit-tested (`LiveWeatherRepositoryImplTest`).

**What is missing**: (a) a real provider — `weather_provider.py` has no actual HTTP call to any third party, by design, since none was chosen/verified; (b) **persistence** — `_weather` is a plain in-memory `MutableStateFlow`; an app restart resets Live Mode's weather straight to `UNAVAILABLE` even if a real reading was showing seconds before the restart, unlike `FieldStateCache`'s DataStore-backed sensor/decision persistence.

**External dependency**: a chosen weather provider (e.g. a government IMD feed, Open-Meteo, OpenWeatherMap) + its API key, configured server-side only (matching the existing "server holds the keys" rule already used for Gemini).

**Risks**: none currently — the honesty contract prevents fake data. The persistence gap is a real, user-visible regression risk (a farmer who briefly loses connectivity sees "not available" instead of "last known, N minutes ago").

**Recommended implementation order**: persistence first (reuses `FieldStateCache`'s exact pattern, zero new libraries), then provider selection/integration.

## 5. Market Audit

Structurally identical to Weather — `MarketRepository.kt`, `MarketState.kt`, `MarketApiService.kt`, `MarketDto.kt`, `MockMarketRepositoryImpl.kt`, `LiveMarketRepositoryImpl.kt`, `DefaultMarketRepository.kt`; server `routers/market.py`, `schemas/market.py`, `services/market_provider.py`, `config.py`'s `market_api_key`. Keyed on `ProfileRepository.profile.primaryCrop` rather than location. Same honesty contract, same missing real provider, same missing cross-restart persistence, same tests (`LiveMarketRepositoryImplTest`). No dedicated Market screen exists yet — only a Dashboard card.

**External dependency**: a real market-price source (e.g. Agmarknet) + API key/access, server-side only.

## 6. Fertilizer Audit

**Existing files**: `core/fertilizer/{FertilizerAdvisor,FertilizerInput,FertilizerRecommendation}.kt`, `core/designsystem/strings/FertilizerText.kt`. Consumed by `feature/advisory/CropAdvisoryViewModel.kt`.

**Existing flow**: `CropAdvisoryViewModel` builds `FertilizerInput` from `FieldState.sensors` (NPK, moisture) + `FarmerProfile` (crop, soil type, stage, irrigation) and calls `FertilizerAdvisor.recommend()` directly — a second, independent pure-Kotlin rule engine, not routed through `FieldDecisionResolver`/`DecisionEngine`.

**What actually works**: real N→P→K deficiency check against generic indicative thresholds, `InsufficientData` when no NPK reading exists, `NoActionNeeded` when sufficient, mandatory safety note, no invented quantity/cost claims. 6 passing unit tests.

**What is missing, relative to the Phase 4 ask** ("must integrate with the existing DecisionEngine architecture"): `DecisionOutput` has no fertilizer field, and `FieldDecisionResolver` never calls `FertilizerAdvisor`. Today Fertilizer and DecisionEngine are two parallel, independent pure-Kotlin engines rather than one fused pipeline — not a duplication (they don't recompute each other's logic), but not the "one decision path" shape the Phase 4 brief describes either. Not surfaced on the Dashboard (Advisory screen only).

**External dependency**: none — this system is fully unblocked or additional work.

**Risk**: if fused into `DecisionOutput` carelessly, easy to accidentally start branching agriculture logic in the UI — the correct shape is `FieldDecisionResolver` calling both `DecisionEngine.evaluate()` and `FertilizerAdvisor.recommend()` and attaching the result to (an extended) `DecisionOutput`, never computing fertilizer logic in `DashboardViewModel`/`CropAdvisoryViewModel`.

## 7. Local LLM Audit

**Confirmed by fresh repo-wide search this session**: zero matches for `LocalLlm`, `LocalAi`, `gguf`, `llama`, `tflite`, `mediapipe`, `gemma` anywhere in `/app` or `/server` source. No local model asset, no inference runtime, no tokenizer, no offline prompt/context pipeline, no interface of any kind.

**Is the current chatbot cloud-dependent?** Yes, entirely. `ChatbotViewModel` → `core/llm/ChatApiService` → server `POST /v1/chat` → `gemini_proxy.py` → Google's Gemini API over the internet. There is no code path that could serve a farmer's question without connectivity.

**What would need to be added**: (a) a `LocalLlmEngine` interface (input: farmer/farm/sensor/decision context + question + language; output: answer + optional structured action) that `ChatbotViewModel` could route to instead of/alongside the network call; (b) an actual on-device runtime + model.

**What can realistically run on Android, and what would the existing architecture support?** The project already depends on **ONNX Runtime Mobile** (`onnxruntime-android:1.19.2`, `app/build.gradle.kts:147`) — currently unused, since Model 1 (irrigation risk) has no `.onnx` file. ONNX Runtime *can* run small quantized transformer models (with the right export + ORT GenAI extensions), but the more common, better-supported Android paths for an LLM specifically are MediaPipe's LLM Inference API or llama.cpp via JNI (GGUF format) — **neither is present in this repo**, and adding either is a new native/JNI dependency, not a drop-in. Realistically, any locally-runnable model would need to be small (≤2–4B params, heavily quantized) to fit device memory/latency budgets, and would need real on-device benchmarking this environment cannot perform.

**External dependency**: a concrete model file (multi-hundred-MB to multi-GB) and a runtime decision (ONNX Runtime GenAI vs MediaPipe vs llama.cpp) — both irreducibly require the user to choose/obtain them; this session cannot download, license, or benchmark a model.

## 8. Offline Voice Audit

**Existing files**: `core/voice/SpeechRecognizerManager.kt` (wraps Android's native `SpeechRecognizer`), `core/voice/TextToSpeechManager.kt` (wraps native `TextToSpeech`). Both `@Inject`ed into `ChatbotViewModel`, wired to the mic button and per-message "Listen" action. Confirmed unchanged since Phase 0.

**What actually works**: real voice I/O around the existing (cloud) chat pipeline — a farmer can speak a question and hear a reply read aloud, today.

**What is missing**: (a) no `SpeechToTextEngine`/`TextToSpeechEngine` *interface* — `ChatbotViewModel` depends on the two concrete manager classes directly, so there's no seam to later swap in a verified-offline engine without touching the ViewModel; (b) no local reasoning to feed — voice is I/O around a network call, not around a `LocalLlmEngine` (§7 doesn't exist); (c) **the offline claim is unverified**: Android's on-device `SpeechRecognizer`/`TextToSpeech` availability and language coverage (especially Marathi) vary by device/OEM and Google app version — some devices fall back to network-based recognition transparently. Nothing in this codebase checks or reports which mode is active.

**Multi-language**: `startListening(languageTag = "en-IN")`/`speak(text, languageTag = "en-IN")` both accept a language tag parameter already — Hindi/Marathi are pluggable today via the tag, but not yet wired to `SettingsRepository.language`.

**External dependency**: a verified fully-offline STT/TTS path (e.g. bundling Vosk for STT) if the "completely offline" requirement must be guaranteed rather than device-dependent.

## 9. IVR Audit

**Confirmed by fresh repo-wide search this session**: zero matches for IVR/telephony/call-webhook anywhere. No backend, no provider integration, no session/state machine, no language-selection menu logic, no farmer-identification-by-phone-number lookup.

**What exists that could be reused**: `FarmerProfile`/`ProfileRepository` (for farmer lookup, if a phone number were matched to a profile), `AppStrings`'s EN/HI/MR structure (for menu prompts), and — once built — a `LocalLlmEngine` (§7) or the existing server's `/v1/chat` (for answering).

**What inherently requires telecom infrastructure (cannot be built from this repo alone)**: the phone number itself, all call signaling/routing, DTMF (keypad) input capture, and any speech recognition/synthesis that happens *during a live phone call* — none of that runs on a farmer's Android device or in this codebase; it requires a telephony/IVR provider (e.g. Exotel, Twilio Voice, Knowlarity) whose webhook would call into a new FastAPI endpoint. **A normal Android phone cannot itself serve as a public telephone number** — this must be stated explicitly per the audit brief's own instruction.

**What could run without a live call, for later reuse**: language-menu logic, farmer lookup, and question-answering logic could all be unit-tested as plain Kotlin/Python state machines before ever wiring a real provider.

**External dependency**: a telephony/IVR provider account + a provisioned phone number.

## 10. Government Scheme Audit

**Existing files**: `core/data/model/GovtScheme.kt`, `core/data/repository/SchemesRepository.kt` (`val schemes: StateFlow<List<GovtScheme>>` — no matching method), `core/data/mock/MockSchemesRepositoryImpl.kt`.

**Existing flow**: `SchemesScreen` reads the same static list for every farmer, regardless of profile.

**What actually works**: the three listed schemes (PM-KISAN Samman Nidhi, Krishi Yantra Anudan Yojana, Mridha Swasthya Card Yojana) are real, named government schemes with real benefit/eligibility *descriptions* — not fabricated scheme names, just not personalized or sourced from a live/traceable dataset.

**What is missing**: any `SchemeEligibilityRule`/`SchemeMatcher` abstraction, any use of `FarmerProfile.farmLocation`/`farmSizeAcres`/`crops` to filter, and a traceable data source (today the three entries are hardcoded Kotlin string literals, not sourced from any file/API with a citation).

**External dependency**: none strictly required to build the *matching engine* — that can run against the existing static list. A traceable, larger, state-aware dataset (e.g. a government open-data API or a maintained JSON file) would need to be sourced and would need a citation trail (which this session cannot fabricate).

## 11. Farmer Feedback Audit

**Confirmed by fresh repo-wide search this session**: zero matches for a feedback model, repository, or screen. Nothing exists.

**Whether feedback could later train/evaluate the system**: architecturally yes — the same DataStore-backed pattern already used for `FarmerProfileStore`/`FieldStateCache` could persist feedback records locally, and (later) sync them to the server for aggregate analysis. No blocker prevents building this; it's simply not started.

## 12. Farmer Profile Audit

Every item on the Phase 4 checklist has real, working support, confirmed by re-reading `FarmerProfile.kt`/`FarmSetupScreen.kt`/`FarmSetupViewModel.kt` this session:

| Checklist item | Support |
|---|---|
| Farmer login | Real — Firebase email/password (`FirebaseAuthRepositoryImpl`) |
| Farmer information | Real — `name`, `phone` |
| Farm location | Real — `FarmLocation(state, district, taluka, village)`, editable in Farm Setup |
| State | Real — `FarmLocation.state`, the exact field `FieldDecisionResolver`/Weather/Market key off |
| Farm size/acres | Real — `farmSizeAcres`, editable |
| Crop | Real — `crops`/`primaryCrop`, editable |
| Crop stage | Real — `seedlingStage`, free text, editable (feeds `FieldDecisionResolver`'s `cropStage` since Phase 3A) |
| Farming method | Real — `IrrigationMethod` enum, editable |
| Soil information | Real — `soilType`, editable |
| Soil moisture | Real — `SensorReading.soilMoisturePct` (sensor pipeline, unrelated to profile) |
| Sensor information | Real — full `SensorReading` (temp/humidity/moisture/NPK/pH) |

**Gaps (non-blocking)**: no sowing-date picker UI (field exists, unedited); `seedlingStage`/location are free text with no validated taxonomy (a farmer could type anything), so `RegionCropRuleRegistry`'s exact-match lookup (case/whitespace-insensitive only) can silently miss a real region+crop pairing due to spelling variance.

## 13. Offline-first Audit

**Works correctly today**: `FieldStateCache` (DataStore/JSON) persists sensors + full `DecisionOutput` (including `pestRisk`/`timing`/`expectedBenefit` since Phase 2) + disease + pest results across restarts; `FarmerProfileStore` persists the farmer/farm profile; both Mock and Live `FieldStateRepository` implementations restore from cache on cold start before any network call. `LiveFieldStateRepositoryImpl` correctly recovers — a failed poll flips `isOnline`/status to `CACHED`/offline (Phase 1 fix) and a subsequent successful poll flips it back to `LIVE`, never silently presenting stale data as live (re-verified: the fix from Phase 1 is intact).

**Gap, newly confirmed this audit**: `LiveWeatherRepositoryImpl`/`LiveMarketRepositoryImpl` hold their state in a plain in-memory `MutableStateFlow` with no `FieldStateCache`-equivalent persistence. A process restart resets both straight to `UNAVAILABLE`, discarding a possibly-still-valid last reading. This is the one concrete place where "show last known X" (explicitly required by the Phase 4 brief for both Weather and Market) does not hold today.

**LIVE/CACHED/MOCK/UNAVAILABLE distinction**: honestly enforced everywhere `DataSourceStatus` is used (sensors, Weather, Market) — confirmed no code path mislabels one as another.

---

## 14. Data Flow Audit

Confirmed end to end, unchanged since Phase 3B: `ESP32 → POST /api/sensor-data (unauthenticated, in-memory) → GET /api/latest-sensor (unauthenticated) → SensorApiService → LiveFieldStateRepositoryImpl → FieldDecisionResolver → DecisionEngine → FieldState → FieldStateCache + StateFlow → DashboardViewModel → DashboardScreen`. Weather/Market run a structurally identical but independent flow, keyed off `ProfileRepository` rather than a device poll, and do **not** feed into `FieldDecisionResolver` (Market by design; Weather does, via `rainOutlook`, since Phase 3A).

## 15. Security / API Key Audit

- `WEATHER_API_KEY`/`MARKET_API_KEY` follow the exact same pattern as `GEMINI_API_KEY`/`api_key` in `config.py` — empty default, server-side only, never in the Android `BuildConfig`. Confirmed still true.
- `SERVER_API_KEY` remains the dev-only `"dev-only-change-me"` baked into `BuildConfig` — unchanged, still fine for the local `adb reverse` loop, still not production-ready (pre-existing, documented finding, not a Phase 4 regression).
- No new secrets were introduced by anything audited this phase.

## 16. Dependency Audit

- **Android**: no new Gradle dependency has been added since Phase 2. `onnxruntime-android:1.19.2` is present and unused (relevant to §7). No speech/LLM-specific library (MediaPipe, llama.cpp bindings, Vosk) is present.
- **Server**: `requirements.txt` still lists only `fastapi`, `uvicorn`, `pydantic(-settings)`, `python-multipart`, `httpx`, `scikit-learn`, `joblib`, `pandas`, `pytest` — no LLM/ML-serving package (`transformers`, `torch`, `onnxruntime`) and, per the still-open Phase 0 finding B4, not even `tensorflow`/`Pillow`/`ultralytics` despite `main.py` importing them at startup. Unrelated to Phase 4, not touched.

## 17. Architecture Reuse Opportunities

- **Weather/Market persistence**: copy `FieldStateCache`'s exact DataStore/JSON pattern — no new library, no schema redesign.
- **Fertilizer → DecisionEngine fusion**: extend `DecisionOutput` with a `fertilizerRecommendation: FertilizerRecommendation? = null` field (default-safe, non-breaking, same pattern every Phase 2/3A field addition already used) and have `FieldDecisionResolver` call `FertilizerAdvisor.recommend()` alongside `DecisionEngine.evaluate()`.
- **Government Scheme matching**: add one `fun matchFor(profile: FarmerProfile): List<GovtScheme>` to the existing `SchemesRepository` interface and a pure-Kotlin `SchemeMatcher` object (same "pure, offline, testable" shape as `DecisionEngine`/`FertilizerAdvisor`) rather than a new architecture family.
- **Farmer Feedback**: reuse `FarmerProfileStore`'s exact DataStore pattern for a new `FeedbackStore`.
- **Local LLM / IVR**: both should be designed as a `LocalLlmEngine` interface consumed by `ChatbotViewModel` *and*, later, an IVR webhook handler — one interface, two callers — rather than two separate ad hoc integrations.

## 18. Risks and Blockers

- **External-dependency risk** (Weather, Market, Local LLM, IVR): each requires an asset/account this session cannot obtain. Do not schedule these as if they were pure engineering tasks.
- **DashboardViewModel's ML-fusion heuristic**: still present post-Phase-3B (deliberately — 3B fixed the masking bug without removing the heuristic, per that phase's explicit scope). It remains a second, small risk-blending layer sitting on top of `DecisionOutput` rather than purely consuming it. Not a new issue, but worth naming here since Phase 4 explicitly asks "do not move logic into DashboardViewModel" — the *existing* heuristic already predates that rule and hasn't been asked to be removed.
- **Scheme dataset traceability**: any real scheme data added later must cite a real source — this session must not fabricate eligibility rules, matching the brief's explicit rule.
- **Region/crop free-text matching**: `RegionCropRuleRegistry`'s exact-match lookup can silently miss due to spelling variance in free-text profile fields (§12) — low severity today (only one region+crop pair modeled), worth a validated picker before more regions are added.

## 19. Recommended Phase 4 Implementation Order

1. Weather/Market persistence (fully unblocked, reuses existing pattern, closes the one confirmed Offline-first gap).
2. Fertilizer → `DecisionEngine`/`FieldDecisionResolver` fusion (fully unblocked, real integration the brief explicitly asks for).
3. Government Scheme matching engine against the existing static list (fully unblocked; swap in a larger traceable dataset later without changing the engine).
4. Farmer Feedback model/repository/UI (fully unblocked, no dependency).
5. `LocalLlmEngine` interface + `SpeechToTextEngine`/`TextToSpeechEngine` interfaces (unblocked to *design*; the real model/runtime behind them is not).
6. IVR backend interface/state machine (unblocked to *design*; the real phone number/provider is not).
7. Real Weather provider, Market provider, Local LLM model, IVR provider — each gated on the user obtaining/choosing an external asset or account.

## 20. Exact First Implementation Target

**Weather and Market last-known-value persistence** (`LiveWeatherRepositoryImpl`/`LiveMarketRepositoryImpl` gaining a `FieldStateCache`-equivalent DataStore cache, restored on init exactly like `FieldStateRepository` already does).

Rationale: it is the one concrete, fully-unblocked gap this audit newly confirmed against a requirement explicitly stated in this phase's brief ("Persist last known valid weather/market data for offline viewing"); it requires no external key, no new dependency, and no architectural decision beyond copying a pattern that already exists and is already tested in this codebase.

Not implemented in this turn, per the audit-only instruction.

---

**Stopping here.** No production code, tests, dependencies, or configuration were modified to produce this report.
