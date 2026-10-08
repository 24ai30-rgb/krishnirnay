# KRISHINIRNAY — Implementation Plan (Phase 0 Audit)

Grounded in the actual repository at `D:\Downloads\krishinirnay-master\krishinirnay-master` as of commit `2922ae8`. Every claim below cites a real `file:line`. Where the vision doc assumes something that isn't in the code, that's called out explicitly instead of guessed at. No code was changed to produce this document.

**Top-line correction to the brief's own assumptions** (so Phase 1 planning starts from truth, not from the prompt's premise):
- **Disease Detection IS visible on the Dashboard** (`DashboardScreen.kt:301-365`, a dedicated card → `onNavigateToCropHealth`). **Pest Detection is the one that's invisible** — it's the opposite of what the brief describes. See §3/§11.
- Android "Live Mode" is a **Retrofit poll of a local FastAPI relay** (`adb reverse`), not Firebase RTDB. No `FirebaseFieldStateRepositoryImpl` exists. See §1/§2.
- The 100%-vs-27% soil-moisture bug **cannot be reproduced from any code in this repo** — see §7's full trace.

---

## 1. Current Architecture

**Monorepo**: `/app` (Android, package `com.krishinirnay`), `/server` (FastAPI), `/docs` (3 markdown files, all stale relative to current code — see §14). No ESP32 firmware source anywhere in the repo (confirmed no `.ino`/Arduino/ESP32 files).

**Android** — MVVM, package-by-feature under `feature/*`, shared code under `core/*`. Hilt DI via 6 modules: `core/data/di/{DataModule,DataStoreModule,FirebaseModule,RepositoryModule}.kt` + `core/network/di/NetworkModule.kt`. No separate top-level `di` package — DI modules live inside `core/data/di` and `core/network/di`. Navigation Compose, two-level `NavHost` in `navigation/KrishiNavGraph.kt` (outer: login/main + 11 drill-down routes; inner `MainScaffold`: 5 bottom tabs). Retrofit + kotlinx-serialization, OkHttp with a hardcoded dev `X-API-Key`. DataStore(Preferences) for cache + settings. ONNX Runtime Android present as a dependency but the model file is absent by design (documented placeholder). Firebase Auth wired; Firebase RTDB is a declared, **unused** dependency.

**Backend** — FastAPI, routers/schemas/services split (`server/app/{routers,schemas,services}`), plus a raw UDP socket server (`server/app/main.py:37-274`) for ESP32 discovery running independently of FastAPI's own request lifecycle. Two real ML artifacts (`server/models/agricultural_risk_final.pkl`, `server/models/best.pt`) plus a real Keras model (`server/app/services/disease_ai/disease_model_finetuned_best.keras`), all git-tracked (not LFS). One proxied LLM call (Gemini) for chat/"explain".

**Data flow that actually runs today**: ESP32 (not in this repo) → `POST /api/sensor-data` (inline in `main.py:448-491`, unauthenticated) → in-memory global `_latest_sensor` → `GET /api/latest-sensor` (`main.py:498-509`, unauthenticated) → Android `SensorApiService.getLatestSensor()` polled every 5s (`LiveFieldStateRepositoryImpl.kt:71-74`) → `SensorReading` → `DecisionEngine.evaluate()` (pure Kotlin) → `FieldState` → DataStore cache + StateFlow → Dashboard/Alerts/Insights all read the same `FieldStateRepository`.

---

## 2. Working Features (verified real, not stub)

| Feature | Evidence |
|---|---|
| Decision Engine (rule-based irrigation/heat/crop-health risk) | `core/decision/DecisionEngine.kt`, `DecisionRules.kt` — pure Kotlin, no Android/network imports, 13 passing unit tests (`app/src/test/.../DecisionEngineTest.kt`) |
| Disease detection (real Keras CNN, 23 classes, Apple/Corn/Pepper/Potato/Tomato) | `server/app/services/disease_ai/disease_model.py`, model file confirmed on disk (21.2MB), wired to `POST /v1/predict/disease` |
| Pest detection (real YOLOv8) | `server/app/services/pest_model.py` + `server/models/best.pt` (6.2MB), wired to `POST /v1/predict/pest`, degrades to `503` if `ultralytics`/model missing |
| Tabular agricultural-risk classifier (real sklearn model) | `server/app/services/risk_model.py` + `server/models/agricultural_risk_final.pkl` (92.7MB), wired to `POST /v1/predict/risk-fusion` |
| ESP32 UDP auto-discovery | `server/app/main.py:37-274` — real socket server on port 4210, listens for `KRISHINIRNAY_DISCOVER`, replies with server IP. **Not consumed by the Android app at all** — no UDP client exists in `/app` (§6) |
| Gemini-backed chatbot + "explain" mode | `server/app/services/gemini_proxy.py`, `POST /v1/chat`, real HTTP integration (currently inert only because `.env`'s `GEMINI_API_KEY` is empty) |
| Voice I/O on the Chatbot screen | `core/voice/SpeechRecognizerManager.kt` (native `SpeechRecognizer`) + `core/voice/TextToSpeechManager.kt` (native `TextToSpeech`), both `@Inject`ed into `ChatbotViewModel.kt:33-34` and wired to the mic button / per-message "Listen" |
| On-device ONNX fallback logic | `core/ml/OnnxModelRunner.kt` correctly returns `null` when the (intentionally absent) `.onnx` file is missing; `DecisionEngine` falls back to the moisture-threshold rule. This is a working fallback, not a broken feature |
| Offline resilience on sensor fetch failure | `LiveFieldStateRepositoryImpl.kt:180-211` — deliberately keeps the last-known `FieldState` instead of blanking the UI on a failed poll |
| Firebase email/password auth | `core/data/firebase/FirebaseAuthRepositoryImpl.kt`, bound in DI, used by `LoginViewModel` |
| Basic EN/HI string switching | `core/designsystem/strings/AppStrings.kt` (custom Compose-local i18n layer, not Android resources — deliberate, per its own doc comment) + `AppStringsProvider.kt` |
| Auth-gated API surface | `server/app/core/security.py` — constant-time `X-API-Key` check on `chat`, `disease`, `pest`, `risk_fusion` routers |

---

## 3. Broken / Misleading Features

| # | Issue | Evidence |
|---|---|---|
| B1 | **Pest Detection is unreachable in the running app.** `Destination.PestDetection` route + `PestDetectionScreen()` composable exist (`KrishiNavGraph.kt:134-136`) but the string `Destination.PestDetection` appears **exactly once** in the entire app module — its own registration. No bottom tab, no Dashboard card, no quick-access item, no other screen navigates to it. Confirmed by full-repo grep. | `KrishiNavGraph.kt:134`, repo-wide grep |
| B2 | **Settings' Mock/Live toggle does nothing.** `DefaultFieldStateRepository` (the only class that reads the stored `AppMode` preference and picks an implementation) is never bound in `RepositoryModule.kt` — `FieldStateRepository` is bound straight to `LiveFieldStateRepositoryImpl` (`RepositoryModule.kt:35-37`). `MockFieldStateRepositoryImpl` (fully built, `NarrativeEngine`-driven, has its own unit-adjacent test) is **dead code from the running app's perspective**. Toggling the Settings switch changes a stored preference nobody reads for this decision. | `RepositoryModule.kt:35-37`, `core/data/composite/DefaultFieldStateRepository.kt` |
| B3 | **The stale doc/comment trail actively misleads.** `README.md:51`, `AppMode.kt:3`, `SensorReading.kt:6-9`, and `DefaultFieldStateRepository.kt`'s own doc comment all claim Live Mode is Firebase-backed and/or unwired, defaulting to Mock. None of that is true of the current code — Live Mode is a REST poll of FastAPI, and it's the one that's wired. | see files above |
| B4 | **Server cannot boot from a clean `pip install -r requirements.txt`.** `requirements.txt` lists only `fastapi`, `uvicorn`, `pydantic(-settings)`, `python-multipart`, `httpx`, `scikit-learn`, `joblib`, `pandas`, `pytest`. It's missing `tensorflow`, `Pillow`, `ultralytics`, and (transitively-fragile) `numpy` — all of which are hard-imported at module load by `disease_model.py`, `disease_ai/disease_model.py`, and `pest_model.py`, which `main.py` imports eagerly at startup. | `server/requirements.txt`, `server/app/main.py:13`, `server/app/services/pest_model.py:3,6` |
| B5 | **Deployment (Dockerfile/Render) would crash on missing pest model.** `Dockerfile` only `COPY app ./app` — never copies `server/models/`. `pest_model.py:24-27` raises a bare `FileNotFoundError` at import time if `best.pt` is absent, but `pest.py:12-15` only catches `ImportError`, not `FileNotFoundError` — so a model-less deploy crashes the whole app at startup instead of degrading to `503`. | `server/Dockerfile`, `server/app/services/pest_model.py:24-29`, `server/app/routers/pest.py:12-15` |
| B6 | **`/v1/predict/disease`'s actual response shape doesn't match its own declared schema, the docs, or the tests.** Router returns `{crop, prediction, confidence(0-100), status, top_predictions:[{class,confidence}]}` (`disease_ai/disease_model.py:164-173`) but `schemas/disease.py`'s `DiseaseResponse` (`label, display_name, confidence(0-1), top_k, risk_level, model_version`) is declared nowhere as the route's `response_model` and is simply unused. `docs/api-contract.md` and `server/tests/test_disease_endpoint.py` both describe the schema-shaped contract, not the real one. | `server/app/routers/disease.py:17-20`, `server/app/schemas/disease.py`, `server/tests/test_disease_endpoint.py` |
| B7 | **`risk_fusion` doesn't fuse anything.** Despite the name and route (`/v1/predict/risk-fusion`), it's a single tabular sklearn classifier over `crop_ID, soil_type, Seedling_Stage, MOI, temp, humidity` (`server/app/schemas/risk.py:4-11`). Zero references to the disease or pest models anywhere in `risk_fusion.py`/`risk_model.py`. | `server/app/routers/risk_fusion.py`, repo-wide grep for `disease`/`pest` inside it (zero hits) |
| B8 | **Risk-class semantics (0/1/2) are explicitly unverified.** `risk_model.py` and its dead duplicate `agricultural_risk_model.py` both return the raw numeric class from the `.pkl` unchanged, with an in-code comment admitting the semantic meaning was never confirmed against the training labels. Android's `WhatIfViewModel.kt:98-101` has the same caveat on its own mapping. | `server/app/services/agricultural_risk_model.py:25-28`, `feature/whatif/WhatIfViewModel.kt:98-101` |
| B9 | **Stale/failing test suites.** `server/tests/test_disease_endpoint.py` and `test_stub_endpoints.py` assert a contract (`risk_level`, `top_k`, `413` size cap, `501 not_implemented` for pest/risk-fusion) that the current implementation does not produce. Running pytest today would fail most of these two files. Do not treat them as ground truth. | `server/tests/test_disease_endpoint.py`, `test_stub_endpoints.py` |
| B10 | **Two independent copies of the Keras disease model get loaded into memory.** Once via `DiseaseModel()` in `main.py:287` (used only to populate the `/health` model list, never called for real inference), once via `disease_ai/disease_model.py`'s module-level `tf.keras.models.load_model(...)` (the one actually serving `/v1/predict/disease`). Wasted RAM on a free-tier deploy. | `server/app/services/disease_model.py:50-52` vs `disease_ai/disease_model.py:58` |
| B11 | **Dead duplicate repository/service files.** `agricultural_risk_model.py` (server) duplicates `risk_model.py` but is never instantiated by any live code path — only `model_registry.py`'s unused setter/getter reference it. On Android, `core/data/repository/RiskRepositoryImpl.kt` and `core/data/network/RiskRepositoryImpl.kt` are two same-named classes; only the `network` one is bound in DI. `sensor.py` router (server) is defined but never mounted in `main.py` — `main.py` reimplements the same two routes inline instead. | `server/app/services/agricultural_risk_model.py`, both `RiskRepositoryImpl.kt` files, `server/app/routers/sensor.py` |
| B12 | **Mixed i18n discipline within a single screen.** `DashboardScreen.kt` uses `strings.dashboardGreeting`/`strings.dashboardOverallRisk` etc. from `AppStrings` in some places, but hardcodes literal English strings ("Live Sensor Data", "Temperature", "Humidity", "Soil Moisture", "ESP32 • Live readings", "Disease Detection", "Scan crop leaves using AI", "Detect crop diseases & check leaf health") elsewhere in the same file. Switching to Hindi only half-translates this screen. | `DashboardScreen.kt:244,259,268,278,291,334,343,351` |

---

## 4. Missing Features (not started, per the vision doc's ask)

None of these exist anywhere in the current codebase (verified by targeted repo-wide search, not inferred):

- **Farm Setup flow**: no fields for crop variety, crop stage (general), irrigation method, or farming method anywhere in the data model. `core/data/model/FarmerProfile.kt` (explicitly commented "Mock-only demo profile for Phase 1") has only `name, phone, location, farmSizeAcres, crops, soilType, seedlingStage` — no irrigation/farming-method field exists to even attach UI to.
- **Real Weather integration**: `WeatherRepository` is permanently bound to `MockWeatherRepositoryImpl` (`RepositoryModule.kt:77-79`) — no real weather API client exists.
- **Real Government Schemes matching**: `MockSchemesRepositoryImpl` returns a hardcoded 3-item list (PM-KISAN, Krishi Yantra Anudan, Mridha Swasthya Card) to every user regardless of profile/location/land area — no eligibility logic exists.
- **Market prices**: no `MarketRepository`, no market model, no market screen/feature package anywhere in `/app` or `/server`. Zero matches for "market price" as a feature (only the string "market" inside `feature/advisory` copy).
- **Fertilizer recommendation**: no dedicated repository/model. "Fertilizer" only appears as static advisory copy inside `feature/advisory` (`CropAdvisoryScreen.kt`/`CropAdvisoryViewModel.kt`) — not a computed recommendation driven by crop/soil/NPK/weather/disease/pest as the vision describes.
- **Local/offline LLM**: does not exist. The only LLM path is server-proxied Gemini (`gemini_proxy.py`), which requires internet and a configured `GEMINI_API_KEY` (currently empty in `.env`). There is no on-device model, no `LocalAiRepository`, no offline-capable knowledge base.
- **Farmer feedback loop**: no feedback model, repository, or screen anywhere.
- **IVR / keypad-phone telephony**: zero references to IVR, telephony, or any call-flow abstraction anywhere in the repo.
- **Marathi language**: `AppStrings`/`SettingsRepository.language` supports exactly `"en"`/`"hi"` (`AppStrings.kt:6-7` doc comment, confirmed no `"mr"` anywhere) — Marathi is not implemented, only English and Hindi.
- **Alerts persistence**: `AlertGenerator` is explicitly documented as in-memory only, not yet DataStore-persisted (`core/data/composite/AlertGenerator.kt:42`) — alerts don't survive process death.
- **On-device irrigation ONNX model**: `app/src/main/assets/models/` contains only `model1_metadata.json` (`"model_file_present": false`) and a `README.md` stating the `.onnx` file is deliberately absent. This is a documented placeholder, not an oversight — the rule-based fallback is the real Phase-1 behavior.

---

## 5. Duplicate / Dead Code (candidates for cleanup, not touched yet)

- `server/app/services/agricultural_risk_model.py` — orphaned duplicate of `risk_model.py`; never instantiated on any live request path.
- `server/app/routers/sensor.py` — defined, never mounted in `main.py`; its route is reimplemented inline in `main.py:448-509` instead.
- `app/src/main/java/com/krishinirnay/core/data/repository/RiskRepositoryImpl.kt` vs `core/data/network/RiskRepositoryImpl.kt` — same class name, two files; only the `network` copy is DI-bound.
- `core/data/composite/DefaultFieldStateRepository.kt` and `core/data/mock/MockFieldStateRepositoryImpl.kt` — fully built, unit-tested (indirectly), currently unreachable in the compiled app since nothing binds them (this is "dead" only in the DI-wiring sense — it's the natural reconnection point for restoring Mock/Live switching, see Phase 1).
- Empty package `app/src/main/java/com/krishinirnay/ml` (directory exists, zero files) — leftover, not to be confused with the real `core/ml`.

---

## 6. Existing APIs (server routes actually mounted, with real auth/behavior)

| Route | Method | Auth | Real or stub | Notes |
|---|---|---|---|---|
| `/health` | GET | none | Real, but `models_loaded` includes a hardcoded `"agricultural-risk-v1"` literal not tied to an actual load check (`main.py:293-296`) |
| `/api/sensor-data` | POST | **none** | Real (in-memory global), inline in `main.py`, not the unmounted `sensor.py` router |
| `/api/latest-sensor` | GET | **none** | Real (returns the same in-memory global), inline in `main.py` — this is what the Android app polls |
| `/v1/predict/disease` | POST (multipart, field `image`) | `X-API-Key` | Real Keras inference; response shape doesn't match `schemas/disease.py` or `docs/api-contract.md` (B6); no file-size limit enforced despite docs claiming 5MB |
| `/v1/predict/pest` | POST (multipart, field `file`) | `X-API-Key` | Real YOLOv8 inference; degrades to `503` if model/`ultralytics` missing, **except** the specific missing-model-file case is uncaught (B5) |
| `/v1/predict/risk-fusion` | POST (JSON, `RiskRequest`) | `X-API-Key` | Real sklearn inference on tabular fields only; not multi-signal fusion (B7); risk-class ints unverified (B8) |
| `/v1/chat` | POST (JSON, `mode: chat\|explain`) | `X-API-Key` | Real Gemini proxy; currently returns `502` because `.env`'s `GEMINI_API_KEY` is empty |
| UDP `4210` | discovery | n/a | Real, standalone thread, unrelated to FastAPI routing — Android has no client for it |

Android-side, the app calls exactly: `GET api/latest-sensor`, `POST v1/predict/disease`, `POST v1/predict/pest`, `POST v1/predict/risk-fusion`, `POST v1/chat` (`core/network/*ApiService.kt`, `core/data/network/SensorApiService.kt`). It never calls `/health`.

---

## 7. Sensor Pipeline — full trace and the 100%-vs-27% bug

Traced every hop that exists **in this repository**:

1. **ESP32 raw ADC → percentage**: not present in this repo at all. No firmware source exists here (`docs/architecture.md:10` explicitly states ESP32 firmware is out of scope for this repo). This is a **genuine missing input** — I cannot inspect or fix a conversion formula that isn't checked in anywhere.
2. **ESP32 → FastAPI**: `POST /api/sensor-data` accepts `SensorData(temperature: float, humidity: float, soil_moisture: float)` (`main.py:20-23`) and stores it **verbatim**, no clamping, no range check, no unit conversion (`main.py:451-457`).
3. **FastAPI → Android**: `GET /api/latest-sensor` returns that same stored value **verbatim** (`main.py:498-509`).
4. **Android DTO → domain model**: `LiveFieldStateRepositoryImpl.fetchLatestSensor()` maps `data.soil_moisture` straight into `SensorReading.soilMoisturePct` with **zero transformation** (`LiveFieldStateRepositoryImpl.kt:128`).
5. **Dashboard rendering**: `String.format("%.1f%%", uiState.soilMoisturePct)` (`DashboardScreen.kt:280-283`) — a plain format call, no clamping to 100, no rounding logic that could produce a spurious 100.

**Conclusion**: there is no conversion, clamping, or transformation logic anywhere in this repository's FastAPI or Android code that could turn a ~27% reading into 100%. The bug cannot originate here. Two remaining explanations, both outside this repo's code:

- **Most likely: the ESP32 firmware's raw-ADC-to-percentage calibration/mapping** (dry/wet calibration constants, `map()`/`constrain()` logic) — not in this repository. **This is the missing file** per the audit brief's own instruction: firmware source needs to be supplied (or written, under Phase 1's Mock/Real sensor abstraction) before this can be fixed rather than guessed at.
- **A real, fixable, in-repo contributing factor**: `LiveFieldStateRepositoryImpl.kt:180-211` and `FieldStateCache` deliberately keep showing the **last successfully-fetched** reading whenever a poll fails or the server/ESP32 is unreachable — by design, for offline resilience (§2, "Working Features"). If the ESP32 ever sent one bad 100% reading (e.g. sensor unplugged, giving max resistance/min raw ADC depending on wiring), that 100% would stick on the Dashboard indefinitely across app restarts and network drops, with only `SyncStatus.isOnline` (a small dot + "online"/"offline" text, `DashboardScreen.kt:429-434`) hinting that the value might be stale — there's no explicit "last known value, age X" affordance on the sensor cards themselves. **This is a real UX gap worth fixing in Phase 1** even though it isn't the root cause of a single bad ADC reading.

Do not "fix" this by clamping or hiding the value in `DashboardScreen.kt` — that treats the symptom. The real fix is (a) get/build the ESP32 firmware conversion code and verify its calibration constants, and (b) in this repo, make staleness visible on the sensor cards themselves so a bad or old reading is never mistaken for a fresh one.

---

## 8. Disease Pipeline

Real, working, single model — see §2/§6. Preprocessing matches the vision doc exactly: 224×224, RGB, `/255.0` normalize (`disease_ai/disease_model.py`, mirrored in the unused `disease_model.py`). 23-class taxonomy matches the brief's list verbatim. Top-3 predictions computed (`disease_ai/disease_model.py:141-158`) but field-named `class`/`confidence` (0-100 scale) rather than the `label`/`confidence` (0-1 scale) the schema/docs describe (B6) — Android's `DiseaseResponseDto` needs to match whichever shape the server actually returns; this is the first thing to reconcile before touching UI.

---

## 9. Pest Pipeline

Real, working YOLOv8 pipeline (§2/§6): bounding boxes, per-detection confidence, sorted by confidence, `{model, model_version, detected, count, top_detection, detections}` (`pest_model.py:148-155`) — this shape **does** match what the brief describes. The problem is entirely on the reachability side (B1): the screen and ViewModel and Retrofit call (`PestApiService.kt`, `PestRepositoryImpl.kt`, `PestDetectionViewModel.kt`, `PestDetectionScreen.kt`) are all real and presumably functional in isolation, but there is no route into this screen from anywhere a farmer would tap.

---

## 10. Decision Engine

Pure Kotlin, confirmed no Android/Firebase/network imports (`DecisionEngine.kt` doc comment, verified on inspection). Current inputs: `sensors: SensorReading`, `modelOutput: IrrigationModelOutput?` (ONNX, currently always null), `diseaseResult: DiseaseResult?`, `deviceOnline: Boolean` (`DecisionInput.kt:14-19`). Rules: soil moisture <20%/<40% → HIGH/MEDIUM water stress; temp ≥38°C/≥33°C → HIGH/MEDIUM heat; overall risk = max severity across sub-risks, excluding `UNKNOWN` (`DecisionRules.kt`, `DecisionEngine.kt:58-61`). NPK/pH thresholds are defined in `DecisionRules.kt:19-25` but **not yet consumed** — no `DecisionOutput` slot exists for them (explicit doc comment, `DecisionEngine.kt:13-17`). **No weather, no farmer-profile, no market, no pest-result input exists in `DecisionInput` at all** — the vision doc's "fuse everything into one recommendation" is aspirational relative to today's inputs, which are sensors + disease + device-online only. 13 unit tests cover the existing rule table (`DecisionEngineTest.kt`).

---

## 11. Navigation

Outer `NavHost` (`KrishiNavGraph.kt`): `login` → `main`, plus 11 drill-down routes (monitoring, pest_detection, weather, analytics, schemes, my_documents, insights, what_if, offline_mode, chatbot, settings). Inner `MainScaffold`: 5 bottom tabs — Dashboard, Advisory, Crop Health, Alerts, Profile (`Destinations.kt:29-35`, `BottomNavBar.kt`). The Crop Health tab's label is `strings.diseaseDetectionTitle` and its icon is a camera — it's effectively branded as the disease-detection entry point, consistent with the Dashboard card. **Pest Detection has a route and a screen but zero inbound navigation calls anywhere** (B1) — it is registered but structurally orphaned, the single biggest, cheapest navigation fix available.

---

## 12. UI/UX (current state, not yet redesigned)

Material 3, custom design-system components (`core/designsystem/components/*`: `KnCard`, `KnTopBar`, `RiskBadge`, `MetricTile`, `SimpleLineChart` — a hand-rolled Canvas chart, no Vico dependency exists in `gradle/libs.versions.toml`, confirming the README's stated substitution). Dashboard already follows the brief's intended shape reasonably closely: top bar with online/offline chip, overall-risk hero card, 3 sub-risk cards (water/heat/crop-health), live sensor readings, a Disease Detection entry card, a quick-access row (Advisory/Weather/Monitoring/Schemes), device status, and a FAB into the Chatbot. What's missing relative to the brief: no Pest Detection entry anywhere on this screen (B1), no Market card, no farm-summary line ("5 acres • Cotton • Vidarbha") since `FarmerProfile` has no crop-variety/region fields to source it from, and the mixed-i18n literals noted in B12.

---

## 13. Backend (organization)

Already organized close to what the brief asks for: `routers/` (HTTP layer), `services/` (model loading + inference), `schemas/` (Pydantic contracts), `core/` (config + security). No `repositories/`, `decision/`, `weather/`, `market/`, or `IVR/` server-side packages exist — none of those server-side features exist yet (§4), so there's nothing to reorganize there; they'd be new packages, not renames.

---

## 14. Docs status

`docs/architecture.md`, `docs/api-contract.md`, `docs/firebase-schema.md`, and the root `README.md` are all **stale relative to current code** on multiple specific points: Live Mode = Firebase (false, §3 B3), pest/risk-fusion = "Phase 2, not implemented" 501 stubs (false — both are real, wired endpoints), disease model = "seeded pseudo-random stub" (false — it's a real trained Keras model). Treat these four documents as historical planning artifacts, not current-state references, until they're updated.

---

## 15. Security

- `.gitignore` correctly excludes `app/google-services.json`, `server/.env`/`server/.env.*`, `local.properties`, `*.jks`/`*.keystore` — confirmed neither `google-services.json` nor `.env` is git-tracked (`git ls-files` check).
- The three ML model binaries (**.keras** 21MB, **.pkl** 92.7MB, **.pt** 6.2MB) **are** git-tracked, not via Git LFS — this is a repo-bloat concern, not a secrets leak, worth flagging for later.
- `X-API-Key` is a real constant-time check (`secrets.compare_digest`, `security.py:30`) but the shipped default is `dev-only-change-me`, baked into Android's `BuildConfig.SERVER_API_KEY` at build time (`app/build.gradle.kts:48`) — fine for a local `adb reverse` dev loop, must not ship in a public APK as-is.
- The ESP32-facing endpoints (`/api/sensor-data`, `/api/latest-sensor`) have **no auth at all** — anyone who can reach the FastAPI port can post fake sensor data or read the latest reading. Acceptable for a LAN-only hackathon demo; a real gap if this server is ever internet-facing.
- `CORS_ORIGINS` is empty in `.env`, so no CORS middleware is added (`main.py:401-415`) — irrelevant for the Android client (not a browser), but would block any future web dashboard.

---

## 16. What Should Be Reused (do not rebuild)

- `DecisionEngine` / `DecisionRules` / `DecisionInput` / `DecisionOutcome` — solid, tested, pure Kotlin. Extend its input set; don't replace it.
- `FieldStateRepository` interface + `LiveFieldStateRepositoryImpl` + `FieldStateCache` + `DataStore` caching pattern — this is the right shape for the sensor pipeline; the fix is reconnecting Mock mode, not rearchitecting.
- `MockFieldStateRepositoryImpl` + `NarrativeEngine` + `ScenarioScript`/`SensorWalk` — already implements exactly the NORMAL/DRY/WET/HOT/RAIN_RISK/CRITICAL_DRY scenario concept the brief asks for (needs verification of exact scenario names/values against the brief's numbers, and needs to be reconnected to DI).
- Disease (`disease_ai/disease_model.py`) and Pest (`pest_model.py`) inference services and their trained model files — reuse as-is; only fix the response-shape mismatch (B6) and the requirements/Dockerfile gaps (B4/B5).
- `core/voice/SpeechRecognizerManager` + `TextToSpeechManager` — real, working native STT/TTS; extend to other screens rather than building a new voice layer.
- `AppStrings`/`AppStringsProvider`/`LocalAppStrings` i18n mechanism — extend with a Marathi variant and finish externalizing the remaining hardcoded literals (B12), don't switch to Android resource-locale.
- `GovtScheme`/`SchemesRepository` shape, `WeatherForecast`/`WeatherRepository` shape — the interfaces are reasonable; only the Mock-only implementations need real backing.

---

## 17. What Should Be Improved (ordered by leverage, not yet started)

1. Reconnect `DefaultFieldStateRepository` so the Settings Mock/Live toggle actually does something (B2) — prerequisite for hardware-free testing per the brief's Phase 1 ask.
2. Give Pest Detection a real entry point from the Dashboard/navigation (B1) — cheapest, highest-visibility fix.
3. Fix `server/requirements.txt` (add `tensorflow`, `Pillow`, `ultralytics`, `numpy`) and `Dockerfile` (copy `models/`) so the server can actually start clean (B4/B5).
4. Reconcile `/v1/predict/disease`'s real response shape with `schemas/disease.py` and the Android `DiseaseResponseDto`/`CropHealthRepositoryImpl` (B6) — pick one contract and make server + schema + app agree.
5. Make sensor staleness visible on the Dashboard's sensor cards (age/"last known" label), independent of the ESP32 firmware question (§7).
6. Delete or actively reconcile the dead duplicates (§5) so future work doesn't land in the wrong copy.
7. Update `docs/*.md` and `README.md` once the above land, so they stop contradicting the code (§14).

---

## 18. Existing AI / LLM / Offline support (explicit honesty check, per audit rules)

- **No local LLM exists.** All conversational AI goes through `POST /v1/chat` → Gemini, server-side only, requiring internet + a configured key. There is no on-device model, runtime, or `LocalAiRepository`-style abstraction to integrate into.
- **Voice is real but not "voice-to-voice with a local AI."** STT/TTS are wired around the Chatbot screen's existing Gemini-backed text pipeline — voice is an I/O layer over an online-only assistant, not evidence of offline AI.
- **Offline mode is data-caching, not AI.** `FieldStateCache`/DataStore lets the last known `FieldState` render with no network; there is no offline reasoning beyond the already-pure-Kotlin `DecisionEngine`, which does work fully offline today (this is the one genuinely offline "AI" in the app, and it already works without a network — worth foregrounding in the demo narrative).
- **IVR does not exist in any form** — no abstraction, no provider integration point, nothing to extend.

---

## 19. Exact Phase 1 Plan (Sensor System) — proposed, not yet implemented

Per the brief, Phase 1 = Mock/Real sensor abstraction, scenarios, correct moisture handling, caching, online/offline status. Concretely, given everything above:

1. **Verify `MockFieldStateRepositoryImpl`'s existing scenario data** (`core/mock/ScenarioScript.kt`, `SensorWalk.kt`, `NarrativeEngine.kt`) against the brief's exact NORMAL/DRY/WET/HOT/RAIN_RISK/CRITICAL_DRY numbers — reuse if it already matches the shape, adjust values only if it doesn't, rather than writing a new scenario engine.
2. **Reconnect Mock/Live switching**: bind `FieldStateRepository` to `DefaultFieldStateRepository` in `RepositoryModule.kt` instead of straight to `LiveFieldStateRepositoryImpl`; wire `DefaultFieldStateRepository`'s two branches to the existing `MockFieldStateRepositoryImpl` and `LiveFieldStateRepositoryImpl`. This alone makes the Settings toggle real and makes the whole app hardware-testable, satisfying "fully testable without hardware" with code that already exists.
3. **Make the data-source badge honest and visible**: `SyncStatus.source` already carries `AppMode` (`MOCK`/`LIVE`) — surface it explicitly on the Dashboard/sensor cards ("DEMO / MOCK DATA" vs "LIVE SENSOR DATA" vs a staleness-aware "CACHED" state) per the brief's labeling requirement, addressing the §7 UX gap at the same time.
4. **Do not touch the moisture math** in FastAPI or Android — confirmed no bug lives there (§7). If/when ESP32 firmware is supplied, that's where calibration verification belongs.
5. **Leave `/api/sensor-data` and `/api/latest-sensor` un-auth'd for now** (matches current LAN-demo posture) but note it for the security pass once/if this server is ever exposed beyond a local network.

**Explicitly not doing yet**: Firebase RTDB wiring (unused dependency, no evidence it's needed given Live Mode already works via REST), any UDP client on the Android side (server's discovery feature is unconsumed but functional; adding a client is a separate, well-scoped follow-up, not part of "make sensors testable without hardware"), any change to `DecisionEngine`'s rule thresholds.

---

**Stopping here per Phase 0 instructions.** No implementation has started. Awaiting go-ahead to begin Phase 1 (§19) exactly as scoped above, or redirection if any of the above should be reprioritized.
