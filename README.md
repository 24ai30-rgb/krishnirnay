# KrishiNirnay

Agriculture field-monitoring app for Smart India Hackathon. Pipeline: **Sense (ESP32) → ML Models → Facts/Predictions → Decision Engine → Explanation (LLM) → Farmer**.

Monorepo layout:

- [`app/`](app/) — Android app (Kotlin, Jetpack Compose, MVVM + StateFlow, Hilt)
- [`server/`](server/) — FastAPI inference backend (crop disease detection, Gemini chat proxy)
- [`docs/`](docs/) — architecture notes, Firebase Realtime Database schema/security rules, API contract

Full architecture and build-order plan: see the plan doc referenced in project history, or `docs/architecture.md` for a summary.

## App (`/app`)

Requires Android Studio (current stable) with an Android SDK matching `compileSdk`/`targetSdk` in `app/build.gradle.kts`, and a JDK 17+.

1. Open the `KrishiNirnay/` root in Android Studio — it will offer to generate the Gradle wrapper jar on first sync if one isn't present.
2. Copy `app/google-services.json.example` to `app/google-services.json` and fill in your Firebase project's real values (this file is gitignored — never commit real Firebase credentials).
3. Add `GEMINI_API_KEY` and any other server secrets to `server/.env` (not the app — the app never holds the Gemini key; see `docs/architecture.md`).
4. Sync Gradle, run the `app` configuration on an emulator or device.

The app defaults to **Mock Mode** (`AppMode.MOCK`) so it's fully demoable offline with no Firebase project configured. Switch to Live Mode from Settings once a real Firebase project + ESP32 device are wired up.

## Server (`/server`)

```
cd server
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # fill in GEMINI_API_KEY, API_KEY, MODEL_PATH
uvicorn app.main:app --reload
```

`GET /health` should return `{"status": "ok", ...}` once running.

## Status

All 10 Phase-1 screens, the full data layer (Mock Mode + Decision Engine + DataStore caching + Alerts), on-device ML wiring, and the server's disease/chat endpoints are implemented — see below for what's real vs. stubbed.

**Verified by actually running**:
- Server: full pytest suite (12 tests across health, disease, chat, Phase 2 stubs) passes in a clean venv.
- Android: **not yet compiled** — no JDK/Android SDK was available in the environment this was built in. Everything was written carefully and cross-checked for import/package/Hilt-binding consistency by hand and by script, but the first real Gradle build in Android Studio is the actual test. Report any build errors and they'll get fixed directly.

**Honest gaps / stand-ins, not silently glossed over**:
- **Model 1** (`model1_irrigation_rf.onnx`): no trained file was provided. `OnnxModelRunner` fails gracefully (returns `null`), and `DecisionEngine` falls back to its rule-based threshold — the app works fully either way. See `app/src/main/assets/models/README.md`.
- **Model 2** (server disease detection): no trained model file either. `DiseaseModel` returns a seeded pseudo-random stub result (same photo -> same result) from a placeholder class list, clearly TODO-marked in `server/app/services/disease_model.py`. The HTTP contract and error handling (size cap, empty-upload, auth) are real and tested.
- **Charts**: the plan named Vico; its exact beta-version Compose API couldn't be verified without a compiler, so Analytics uses a small hand-rolled Canvas line chart instead (`SimpleLineChart.kt`) — documented in-code as a deliberate substitution.
- **Rate limiting**: `slowapi` was dropped from scope rather than risk getting decorator wiring wrong unverified — the API-key gate is real, per-IP rate limiting is a documented follow-up.
- **i18n**: the Settings screen's English/Hindi toggle stores the preference correctly, but most on-screen text is still hardcoded English literals rather than externalized string resources — switching languages doesn't yet retranslate the UI. A real remaining task, not done.
- **Gemini/Firebase**: need real `GEMINI_API_KEY` (server `.env`) and a real Firebase project (`app/google-services.json` + RTDB security rules from `docs/firebase-schema.md`) to actually function — both gracefully degrade without them (chat/explain return a clear error; app defaults to Mock Mode either way).
- **Live Mode**: `RepositoryModule` still binds `FieldStateRepository` straight to Mock Mode. `DefaultFieldStateRepository` (the mode-switcher) is written and unit-tested against fakes, but there's no `FirebaseFieldStateRepositoryImpl` yet to plug in as the Live branch — that's the one piece of the originally-planned build order not started.
