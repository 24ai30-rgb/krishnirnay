# KRISHINIRNAY Phase 4E Implementation Report — Local LLM

Scope: a real Local LLM abstraction that explains already-computed facts — it never decides risk, fertilizer, pest/disease, weather, or market data itself.

---

## 1. Audit

- **Existing conversational code**: `ChatbotViewModel` was already fully offline — keyword-matched replies against `FieldStateRepository`/`WeatherRepository`/`SchemesRepository`, no server call at all. `ExplanationService`/`ChatApiService`/`AiInsightsViewModel` called this app's own server's `/v1/chat`, which proxies to cloud Gemini (`gemini_proxy.py`) — used only to "polish" the deterministic recommendation into friendlier prose, silently falling back to the plain text on any failure. **Cloud Gemini was never the primary chatbot** — it was already an auxiliary explanation-polish layer, but it was called unconditionally, with no explicit opt-in/opt-out.
- **Reused, not duplicated**: the exact "server holds the keys, Android never sees the credential" pattern already used for Gemini/Weather/Market; the exact Retrofit/DI/repository patterns from Phase 4C/4D.

## 2. Architecture

```
Farmer question / DecisionOutput
    ↓
LocalLlmContextBuilder (Android) — snapshots FieldState/FarmerProfile/WeatherState/MarketState
    ↓
POST /v1/local-llm/chat (this app's own FastAPI server)
    ↓
local_llm_service.generate() — real HTTP call to a self-hosted Ollama-compatible server
    ↓
Structured reply, grounded only in the facts given — never invents beyond them
```

Android never talks to Ollama directly — only to this app's own server, exactly like Weather/Market/Gemini.

## 3. Files Created

**Server**: `app/services/local_llm_service.py` (real Ollama REST client — `POST /api/generate`, `GET /api/tags` for status), `app/schemas/local_llm.py`, `app/routers/local_llm.py` (`POST /v1/local-llm/chat`, `GET /v1/local-llm/status`, with a system prompt that explicitly forbids inventing facts beyond what's given).

**Android**: `core/llm/local/{LocalLlmStatus, LocalLlmDto, LocalLlmApiService, LocalLlmContextBuilder, LocalLlmRepository, LocalLlmRepositoryImpl}.kt`.

## 4. Files Modified

- `server/app/config.py` — added `local_llm_provider`/`local_llm_url`/`local_llm_model` (connection config, not secrets — safe defaults pointing at a local `ollama serve`).
- `server/app/main.py` — registered the new router.
- `core/network/di/NetworkModule.kt`, `core/data/di/RepositoryModule.kt` — DI wiring, same pattern as every other repository.
- `feature/chatbot/ChatbotViewModel.kt` — keyword match stays the guaranteed, instant, fully-offline first answer; only when nothing matches does it call the Local LLM, grounded in the same real data. Never blocks, never calls cloud.
- `feature/insights/AiInsightsViewModel.kt` — now tries the Local LLM first; cloud Gemini (`ExplanationService`) is only reached if the farmer has explicitly turned on `cloudFallbackEnabled` (new setting, **off by default**) AND the Local LLM didn't answer.
- `core/data/repository/SettingsRepository.kt` / `core/data/local/AppPreferences.kt` — new `cloudFallbackEnabled` (default `false`).
- `feature/settings/{SettingsUiState, SettingsViewModel, SettingsScreen}.kt` — new "AI Mode" status row (READY/LOADING/UNAVAILABLE/ERROR, never a fake "online") and the cloud-fallback toggle, off by default.
- `core/designsystem/strings/AppStrings.kt` — new EN/HI/MR keys for AI Mode + cloud fallback.

## 5. Local LLM Setup (what you need to do)

1. Install [Ollama](https://ollama.com) on the machine running the FastAPI server.
2. Pull a small quantized model: `ollama pull llama3.2:1b` (or any Ollama-compatible model — swap via `LOCAL_LLM_MODEL`, no code change).
3. `ollama serve` (defaults to `http://localhost:11434`, matching `LOCAL_LLM_URL`'s default).
4. No `.env` changes needed for the defaults; override `LOCAL_LLM_PROVIDER`/`LOCAL_LLM_URL`/`LOCAL_LLM_MODEL` if your setup differs.

**Not verified live in this environment** — no Ollama server is running here. Every code path was tested against realistic mocked HTTP responses, matching the same honesty discipline as Weather (Phase 4C) and Market (Phase 4D): until a real model is reachable, `/v1/local-llm/status` honestly reports `UNAVAILABLE`, and the rest of the app (Decision Engine, sensors, weather, market, the keyword-based chat) works exactly as before.

## 6. Offline/Error Handling

`LocalLlmStatus`: `READY` / `LOADING` / `UNAVAILABLE` / `ERROR` — never a fake "online." Missing model, server not running, timeout, and malformed responses all map to `UNAVAILABLE`/`ERROR`, never a fabricated reply. The chatbot's honest fallback-help text and the plain rule-based recommendation always remain available.

## 7. Tests

- **Server** (`test_local_llm_service.py`, 9 tests; `test_local_llm_endpoint.py`, 5 tests): successful reply, unsupported provider, missing URL, network error, malformed response, empty-reply rejection, status READY/UNAVAILABLE, misconfigured provider, router success/503/status passthrough. All mock `httpx.AsyncClient` — no real Ollama needed.
- **Android** (`LocalLlmRepositoryImplTest.kt`, 7 tests): status READY/UNAVAILABLE/ERROR, a real reply surfaces as `Answered`, timeout/503 never fabricate a reply, `ask()` only ever calls this app's own `LocalLlmApiService`.
- **Offline-mode-never-calls-cloud**: proven structurally — `LocalLlmApiService`/`LocalLlmRepository` have no reference to Gemini or any cloud endpoint; `AiInsightsViewModel` only reaches `ExplanationService` (the Gemini path) behind the explicit, default-off `cloudFallbackEnabled` check.

## 8. Test Results / Build

Android: 110/110 tests pass (`./gradlew testDebugUnitTest --rerun-tasks`, fully fresh). Server: 71 passed, 6 pre-existing unrelated failures (disease/pest/risk-fusion stub endpoints, present before this phase). `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.

## 9. Known Limitations / Blockers

- **BLOCKED_EXTERNAL_DEPENDENCY**: a real Ollama installation + model file. Everything else (server endpoint, Android client, status UI, fallback wiring, tests) is complete and ready — only the model needs to be added.

---

**Stopping analysis here for 4E** — see `PHASE_4F_IMPLEMENTATION_REPORT.md` for Offline Voice.
