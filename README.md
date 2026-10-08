# KrishiNirnay — Data-Driven Field Decisions for Small Farmers

> **Nexathon II · Project Competition · Domain: _Farming the Future_**
> Department of CSE (Data Science), AIKTC, New Panvel — 9th October

KrishiNirnay ("farm decision") is an Android app with a Python ML backend. It turns soil-sensor readings, weather, mandi prices and photos of a crop into **one clear daily decision** for a farmer: irrigate or not, what the risk is, and why. The answer is shown in English, Hindi or Marathi, and can be spoken aloud.

---

## 1. Problem Statement

Small and marginal farmers in India make daily decisions about irrigation, pest control and when to sell with almost no data:

- **Irrigation is guesswork.** Over-watering wastes water and electricity. Under-watering stresses the crop. Neither is visible until the damage shows.
- **Disease and pests are spotted late.** By the time a farmer recognises a leaf disease or pest, yield is already lost, and expert advice is far away.
- **The information that does exist is scattered and hard to read.** Weather apps, mandi price portals and government scheme lists are separate, mostly in English, and none of them tells the farmer *what to do today*.

**Goal:** fuse these data sources into one explainable recommendation that a farmer with a basic smartphone, a poor connection and limited English can actually act on.

## 2. Solution Overview

```
 Sense            Predict                 Decide                 Explain             Act
┌────────┐   ┌──────────────────┐   ┌────────────────┐   ┌──────────────────┐   ┌─────────┐
│ ESP32  │   │ Risk model (RF)  │   │ Deterministic  │   │ Local LLM        │   │ Farmer  │
│ soil / │──▶│ Disease CNN      │──▶│ Decision Engine│──▶│ (explains only,  │──▶│ text +  │
│ temp / │   │ Pest YOLOv8      │   │ + rules        │   │  never decides)  │   │ voice   │
│ humid. │   │ Weather, Mandi   │   └────────────────┘   └──────────────────┘   └────┬────┘
└────────┘   └──────────────────┘                                                    │
                                     Farmer feedback (“Did you follow it? Result?”) ◀─┘
```

**Key design choice:** the agricultural decision always comes from a **deterministic, testable Decision Engine**, never from an LLM. ML models produce *facts and predictions*; the engine combines them with agronomic thresholds into a decision with explicit reasons; the LLM is only allowed to *explain* that decision in plain language. A farmer always gets the same answer for the same field state, and every recommendation can be traced to a reason.

### Features

| Area | What the farmer gets |
|---|---|
| **Dashboard** | Today's decision, risk level, the reasons behind it, a fertilizer hint and today's mandi price |
| **Live monitoring** | Soil moisture, temperature and humidity from an ESP32 via Firebase, plus a full **Mock/Simulation mode** for demos without hardware |
| **Crop health** | Leaf photo → disease class + confidence (23 classes across apple, corn, pepper, potato and tomato) |
| **Pest detection** | Photo → detected pests with bounding boxes (10 pest classes, YOLOv8) |
| **Weather** | Forecast and rain outlook that feeds into the irrigation decision |
| **Market** | Live mandi (APMC) prices for the farmer's crop and district |
| **Govt. schemes** | Schemes the farmer is actually eligible for, matched deterministically on state, crop and land size |
| **AI assistant** | Chat and voice Q&A in EN / HI / MR, grounded in the farmer's own field data, with an on-device LLM option for offline use |
| **Advisory / What-if / Analytics** | Crop advisory, "what if I delay irrigation?" simulation, sensor history charts |
| **Feedback loop** | "Did you follow this? What happened?" — stored for evaluation and never silently changes the rules |
| **IVR (architecture)** | Webhook call flow for keypad phones, provider-agnostic (no telephony account is connected yet) |

## 3. Methodology

1. **Data acquisition**
   - On-field sensors (ESP32 → Firebase Realtime Database): soil moisture, temperature, humidity.
   - Weather forecast from WeatherAPI.com.
   - Mandi prices from the data.gov.in Agmarknet dataset *"Current Daily Price of Various Commodities from Various Markets (Mandi)"*.
   - Farmer profile: location, crop, soil type, land size, irrigation method.
2. **Prediction models** (served by the FastAPI backend)
   - **Agricultural risk model:** a scikit-learn classifier on `crop_ID`, `soil_type`, `Seedling_Stage`, `MOI` (soil moisture), `temp` and `humidity`. It returns a risk class with class probabilities.
   - **Leaf-disease model:** a fine-tuned CNN (Keras, 224×224 input, 23 classes). It is converted to TFLite and served with LiteRT, which cut memory by more than 300 MB with no loss in accuracy (max abs. output diff ≈ 5e-6, identical top-1 class).
   - **Pest detector:** YOLOv8 (10 classes), exported to ONNX and run with onnxruntime, with our own YOLOv8 decode and NMS. We checked it against Ultralytics' own NMS and it gives the same detections.
3. **Decision Engine** (pure Kotlin, on device): combines sensor readings, model outputs and the rain outlook using explicit agronomic thresholds into a `DecisionOutput` (action + risk level + reasons). Fully unit-tested.
4. **Explanation layer:** a local LLM through Ollama (`deepseek-r1:7b` for English, `qwen2.5` for Hindi and Marathi), or Gemma 3 1B on the device. It rewrites the `DecisionOutput` in the farmer's language and cannot override it. Cloud Gemini is an opt-in fallback that is off by default.
5. **Offline-first delivery:** the last known field, weather and market state is cached on the device (DataStore). Every screen shows whether its data is *Live*, *Cached* or *Unavailable*, and never invents a value.
6. **Evaluation:** automated tests on both sides, plus farmer feedback on decisions that were actually followed.

## 4. Technology Stack

| Layer | Technologies |
|---|---|
| Android app | Kotlin 2.2, Jetpack Compose (Material 3), MVVM + StateFlow, Hilt, Navigation-Compose, Retrofit + OkHttp + kotlinx-serialization, DataStore, Coil, ONNX Runtime Android, LiteRT-LM (on-device LLM), Android SpeechRecognizer / TextToSpeech |
| Cloud / IoT | Firebase Auth, Firestore (farmer profile), Realtime Database (sensor stream), ESP32 |
| Backend | Python 3.12, FastAPI, Pydantic, Uvicorn, scikit-learn, pandas, joblib, ai-edge-litert, onnxruntime, Pillow, httpx |
| AI / LLM | Ollama (local), Gemma 3 1B (on-device), Google Gemini (optional cloud fallback) |
| Deployment | Docker, Render (free tier — every model loaded fits in ~335 MB RAM) |
| Testing | JUnit 4, MockK, Turbine (Android), pytest (server) |

## 5. Repository Structure

```
app/                      Android app
  src/main/java/com/krishinirnay/
    core/decision/        Decision Engine, rules, rain outlook, market insight
    core/data/            repositories (Mock / Live / cached), Firebase, DataStore
    core/ml/              on-device ONNX model runner
    core/llm/             local / on-device LLM integration
    core/fertilizer/      fertilizer recommendation
    core/schemes/         government scheme eligibility matcher
    core/voice/           speech-to-text / text-to-speech
    feature/*             one package per screen (dashboard, crophealth, pest, market, ...)
  src/test/               unit tests
server/                   FastAPI backend
  app/routers/            /v1/predict/{risk-fusion,disease,pest}, /v1/weather, /v1/market, /v1/chat, /v1/local-llm, /v1/ivr
  app/services/           model inference + external data providers
  models/                 trained model files (risk .pkl, pest .onnx/.pt)
  tests/                  pytest suite
docs/                     architecture, API contract, Firebase schema, technical-approach PDF,
                          dev-reports/ (phase-by-phase engineering notes)
```

## 6. How to Run

### Backend

```bash
cd server
python -m venv .venv
.venv\Scripts\activate          # Windows   (Linux/macOS: source .venv/bin/activate)
pip install -r requirements.txt
copy .env.example .env          # add WEATHER_API_KEY, DATA_GOV_API_KEY, API_KEY (optional: GEMINI_API_KEY)
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Check it at `http://localhost:8000/health`; interactive API docs are at `/docs`. Weather and Market return an honest `503 unavailable` if their keys are missing. For the local LLM, see [docs/LOCAL_LLM_SETUP.md](docs/LOCAL_LLM_SETUP.md).

### Android app

Requirements: Android Studio, JDK 17, Android SDK 35.

1. Put your Firebase `google-services.json` in `app/` (gitignored).
2. In `local.properties`, point the app at the backend:
   ```
   krishinirnay.serverBaseUrl=http://10.0.2.2:8000/      # emulator; physical device: use adb reverse or your PC's LAN IP
   krishinirnay.serverApiKey=<same value as the server's API_KEY>
   ```
3. Run the `app` configuration. The app starts in **Mock Mode**, so the whole decision flow can be demoed without an ESP32. Settings → *Sensor Simulation* lets you apply dry, wet, hot or humid field scenarios and watch the decision change.

## 7. Testing

| Suite | Command | Result |
|---|---|---|
| Android unit tests | `gradlew :app:testDebugUnitTest` | 218 passing |
| Android lint | `gradlew :app:lintDebug` | 0 errors |
| Server | `cd server && python -m pytest` | 223 passing |

The tests cover the Decision Engine rules, the Mock/Live repository switching, the caching and data-source status logic, the scheme matcher, the fertilizer logic, the API contracts between app and server, and every server endpoint, including auth and bad-input handling.

## 8. Acknowledgements — External Datasets, Models, APIs and Tools

As required by the rule book (§3, §4.2), these are all the external resources we used:

| Resource | Used for | Source |
|---|---|---|
| **PlantVillage** (Hughes & Salathé, 2015) | Training the leaf-disease CNN (23 of its classes) | https://github.com/spMohanty/PlantVillage-Dataset |
| **IP102** (Wu et al., CVPR 2019) — 10-class subset | Training the pest YOLOv8 model | https://github.com/xpwu95/IP102 |
| **Kaggle smart-irrigation dataset** (`crop_ID`, `soil_type`, `Seedling_Stage`, `MOI`, `temp`, `humidity`) | Training the agricultural risk model | Kaggle <!-- TODO: exact dataset link --> |
| Ultralytics YOLOv8 (pre-trained weights) | Base model for the pest detector | https://github.com/ultralytics/ultralytics |
| MobileNetV2 (ImageNet weights, Keras Applications) | Base model for the disease CNN (transfer learning) | https://keras.io/api/applications/mobilenet/ |
| WeatherAPI.com | Weather forecast | https://www.weatherapi.com |
| data.gov.in — Agmarknet mandi prices | Market prices | https://data.gov.in (resource `9ef84268-d588-465a-a308-a864a43d0070`) |
| Gemma 3 1B (Google, via LiteRT community) | On-device LLM | https://huggingface.co/litert-community |
| DeepSeek-R1 7B, Qwen 2.5 (via Ollama) | Local LLM explanations | https://ollama.com |
| Google Gemini API | Optional cloud fallback for explanations | https://ai.google.dev |
| Firebase (Auth, Firestore, Realtime DB) | Login, profile, sensor stream | https://firebase.google.com |
| Open-source libraries | Listed in `gradle/libs.versions.toml` and `server/requirements.txt` | — |

## 9. Our Contribution

Pre-trained models and public datasets were *starting points*. What we built:

- **Model training and adaptation:** fine-tuned the disease CNN and trained the pest detector and the risk classifier. Converted all three to lightweight runtimes (TFLite / ONNX) and verified numerically that the outputs are unchanged, so the whole backend runs within a 512 MB free-tier server.
- **Our own YOLOv8 post-processing:** decode and NMS written in NumPy and checked against Ultralytics' reference implementation, so the server does not need PyTorch.
- **Decision Engine:** a deterministic multi-source decision engine with explicit reasons, written by us and fully unit-tested. This is the core of the project.
- **Data fusion and reliability:** Mock/Live/Cached data layer with honest source-status reporting, offline caching, and adapters for the weather and mandi APIs (including state and commodity name normalisation for the data.gov.in dataset).
- **Explainable, multilingual AI:** an LLM layer that only explains and cannot decide. Per-language model routing (EN/HI/MR), on-device fallback, and a voice-to-voice loop.
- **Farmer-facing app:** 20+ Compose screens, a government-scheme eligibility matcher, a fertilizer recommender, a feedback loop and a sensor-simulation mode.

## 10. Team

| Name | Role / contribution |
|---|---|
| <!-- TODO --> | <!-- TODO --> |
| <!-- TODO --> | <!-- TODO --> |
| <!-- TODO (optional 3rd member) --> | <!-- TODO --> |

## 11. Known Limitations and Future Work

- **IVR:** the call-flow logic and webhooks are built and tested, but no telephony provider account is connected.
- **On-device irrigation model** (`app/src/main/assets/models`): no trained file is bundled yet. The Decision Engine's rule-based path is used instead, and the app works fully either way.
- **On-device LLM file** (Gemma 3 1B, ~529 MB) is not committed, because of its size and Google's license terms. See `server/models/on_device/README.md`.
- **Next steps:** field trials with farmer feedback; more crops in the disease model; per-IP rate limiting on the server.
