# Architecture summary

Pipeline: **Sense (ESP32) → ML Models → Facts/Predictions → Decision Engine → Explanation (LLM) → Farmer**.

Two components live in this repo:

- **`/app`** — Android app. Kotlin + Jetpack Compose, MVVM + StateFlow, Hilt DI, package-by-feature. One `FieldStateRepository` is the single source of truth every screen reads from (Mock or Firebase-backed, switchable at runtime) — this is what keeps Dashboard/Alerts/AI Insights risk always in agreement.
- **`/server`** — FastAPI inference backend. Serves crop disease detection (Model 2) and proxies Gemini calls for the chatbot and AI Insights explanation polish. The Gemini API key lives **only** here, never in the app — see `api-contract.md` for why.

ESP32 firmware is out of scope for this repo. `firebase-schema.md` is the data contract both this app and any future firmware code against.

## Key architectural decisions

- **Mock vs Live mode**: both are implementations of the same `FieldStateRepository` interface, switched at runtime via a `flatMapLatest` on the selected mode — never a DI graph rebuild or Activity restart.
- **Decision Engine is pure Kotlin**: no Android, Firebase, or network imports. It's the one piece of "AI" that must work with zero connectivity, and it's the easiest thing in the app to unit-test exhaustively.
- **LLM calls are always server-proxied**: neither the Gemini key nor any cloud AI credential is ever bundled into the APK. The app calls its own FastAPI server; the server calls Gemini.
- **On-device ML (Model 1, irrigation risk)** runs via ONNX Runtime Mobile, bundled as an asset. **Server-side ML (Model 2, disease detection)** runs in `/server`, called over HTTPS.
- **Offline-first**: DataStore is the single authority for "last known state" shown to the UI; Firebase Realtime Database's own disk persistence is treated purely as a transport optimization, never read directly by ViewModels.

## Where to look for more detail

The full architecture plan (data models, per-screen navigation, DI wiring, threat/loophole notes, UI design tokens, and the build order this repo is being built against) lives in the project's planning history. This file will grow into the canonical reference as pieces land; for now it's a summary, not a duplicate.
