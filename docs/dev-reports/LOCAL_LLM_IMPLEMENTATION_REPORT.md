# Local LLM — Implementation Report

The generation timeout is fixed and **real local generation is verified working**
against the running Ollama instance. English is usable end to end. Hindi/Marathi
generate but the output quality is not usable with the currently installed model —
reported honestly below rather than papered over.

## 1. Root cause of the timeout (measured, not guessed)

The previous audit saw `/api/generate` time out. Measuring the actual behaviour
found **three separate problems stacked on top of each other**:

**a) `deepseek-r1:7b` is a reasoning model.** It writes a long hidden `<think>`
monologue before answering. Measured on this machine (CPU-only, **5.07
tokens/second**, ~13s cold model load):

| Setup | Result |
|---|---|
| Plain `/api/generate`, `num_predict=400` | **83.5s**, and *still truncated* — **368 of 400 tokens were `<think>` tokens** the farmer never sees. `</think>` was not reached until 76.5s. |
| `/api/chat` + prefilled closed `<think></think>` | **8.0s**, finished naturally (`done_reason=stop`) |

So ~92% of generation time was spent on reasoning the user never sees.

**b) Android's OkHttp had no timeouts configured at all**, so it used the 10s
defaults. Even when the server eventually answered, **Android had already given
up after 10 seconds.** This alone would have broken the feature regardless of
model speed.

**c) The server used a single flat 30s timeout**, which is simultaneously too
short for real local generation and too long to notice a dead port.

## 2. The fix

**Prefilled assistant turn (the 10× win).** The service now calls `/api/chat`
with a final assistant message of `"<think>\n\n</think>\n\n"`, which makes a
reasoning model treat reasoning as already done and answer immediately. Harmless
for non-reasoning models. `_strip_thinking()` then removes any `<think>` block
that still appears, so scaffolding never reaches the farmer.

**Layered, purposeful timeouts** — not one inflated number:

| Layer | Connect | Read | Why |
|---|---|---|---|
| Android → server (Local LLM only) | 15s | **150s** | Local inference legitimately takes tens of seconds |
| Android → server (everything else) | 15s | **15s** | Weather/market answer in ~1-2s and should fail fast |
| Server → Ollama | **5s** | **120s** | A local port answers at once or isn't listening |

The Local LLM gets its **own** OkHttp client and Retrofit (`@LocalLlmClient`), so
a slow model can never make weather, market or sensor calls hang — and the fast
endpoints keep failing fast.

**Bounded output.** `num_predict=220` and an explicit *"Reply in at most 2 short
sentences"* instruction. Output length is the dominant latency term at 5 tok/s;
adding the sentence budget cut a real end-to-end answer from **36.1s → 11.0s**.

**Real model-missing detection.** `check_status()` previously only pinged
`/api/tags` and returned READY if *anything* answered — even with the configured
model not pulled. It now checks the configured model is actually installed and
returns a distinct `MODEL_MISSING`.

## 3. Verified real smoke test (live Ollama, not mocked)

```
Ollama 0.34.0 · deepseek-r1:7b (7.6B, Q4_K_M) · CPU-only · 5.07 tok/s

check_status()                                   -> READY
Tiny prompt ("Reply with exactly: ...")          -> 3.6s
   reply: "KRISHINIRNAY LOCAL AI READY"          (exact, no <think> leakage)

Full structured context through the real FastAPI route
POST /v1/local-llm/chat                          -> HTTP 200 in 11.0s
   reply: "The farmer should delay irrigation because there is a 70% chance of
           rain within the next 24 hours. This means the soil moisture will
           likely replenish naturally, reducing the need for artificial
           irrigation."
   elapsed_ms reported, contains <think>: False
```

The answer uses only the supplied facts (70% rain, soil moisture, the
DecisionEngine's own recommendation) and invents nothing.

Status paths verified against real conditions, not mocks:

| Condition | Result |
|---|---|
| Model installed, Ollama running | `READY` |
| Configured model not pulled | `MODEL_MISSING` |
| Ollama not running (port closed) | `UNAVAILABLE` |
| Non-ollama provider configured | `UNAVAILABLE` |

## 4. Honest limitation: Hindi and Marathi are not usable with this model

Measured through the real route after all fixes:

| Language | Time | Result |
|---|---|---|
| English | 11.0s | Correct, grounded, 2 sentences — **usable** |
| Hindi | 44.7s | Correct Devanagari script, but garbled content (e.g. renders "Flowering" as "फ्लोटिंग क्षेत्र" / "floating area", "you should pour rain") — **not usable** |
| Marathi | 22.9s | Largely nonsense, does not answer the question — **not usable** |

This is a **model capability limit**, not an integration bug: the same pipeline
produces good English. `deepseek-r1:7b` is a small distilled reasoning model with
weak Indic support, and Devanagari also tokenizes far less efficiently (hence the
much longer times).

What was implemented for this: the server rejects a reply that ignores the
requested script entirely (`_honors_language`), so a farmer who asked in Hindi is
never handed an English answer — they get an honest failure instead. That check
deliberately does **not** attempt to judge wording quality; no cheap check can,
and pretending otherwise would be dishonest. This limit is documented in the code
itself so nobody later mistakes it for a validated guarantee.

**Recommendation (not applied):** for production Hindi/Marathi, set
`LOCAL_LLM_MODEL` to a model with genuine Indic support. No code change needed.
Per the brief, `deepseek-r1:7b` was **not** silently replaced.

## 5. Decision Engine safety — unchanged and preserved

The architecture is untouched:

```
Sensors / Weather / Market / Farmer Profile → DecisionEngine → DecisionOutput
                                                                     ↓
                                                     Local LLM = explanation only
```

- `LocalLlmContextBuilder` (already existed, unchanged) snapshots real, already
  computed values into structured context; every field it has no real data for
  stays null.
- The system prompt still forbids inventing readings, prices, weather, dosages or
  scheme eligibility.
- The LLM's output is never fed back into risk, fertilizer, pest/disease or
  scheme logic — it is display text only.
- Government scheme matching remains deterministic Kotlin (`GovernmentSchemeMatcher`).

## 6. Local-only guarantee

- The Local LLM path calls only this project's server, which calls only
  `LOCAL_LLM_URL` on localhost. Verified by test that no cloud host is ever
  contacted and no `Authorization` header is sent.
- `ChatbotViewModel` contains **no** reference to any cloud AI service.
- The pre-existing cloud fallback stays opt-in, default **off**, and is confined
  to the AI Insights screen — it is not part of the Local AI chat path.
- Ollama is listening on `127.0.0.1:11434` only (verified via `netstat`) — not
  exposed to the network.
- No API key or secret exists anywhere in the Local LLM path; there is nothing to
  leak.

## 7. Honest status states (farmer-facing, all three languages)

| State | Farmer sees |
|---|---|
| `READY` | "Local AI — works offline" |
| `GENERATING` | "Local AI is thinking..." |
| `MODEL_MISSING` | "Local AI model not installed" |
| `UNAVAILABLE` | "Local model unavailable" |
| `ERROR` | "Local AI could not process this request" |

The chat screen previously answered a failed Local AI call with a generic
"I can help with..." message, which a farmer could mistake for the AI's actual
answer. It now shows the specific honest reason instead. Technical details
(model name, response time in ms) are captured for an advanced/debug view and
are never shown in the farmer-facing chat.

## 8. Files changed

**Server**
- `app/services/local_llm_service.py` — rewritten: `/api/chat` + prefilled think
  block, `<think>` stripping, layered timeouts, `num_predict`/`temperature`,
  language-script check, `MODEL_MISSING` detection, returns `elapsed_ms`.
- `app/routers/local_llm.py` — passes language through, returns `elapsed_ms`,
  explicit language names and a 2-sentence budget in the prompt.
- `app/schemas/local_llm.py` — `elapsed_ms` on the chat response; documented statuses.
- `app/config.py` — `127.0.0.1` default, `deepseek-r1:7b` default, plus
  connect/read timeout, `num_predict` and `temperature` settings.
- `.env.example` — updated Local LLM block.

**Android**
- `core/network/di/NetworkModule.kt` — explicit 15s timeouts for the default
  client; new `@LocalLlmClient` client + Retrofit with a 150s read timeout.
- `core/llm/local/LocalLlmStatus.kt` — added `GENERATING`, `MODEL_MISSING`.
- `core/llm/local/LocalLlmRepository.kt` / `LocalLlmRepositoryImpl.kt` — status
  mapping incl. `MODEL_MISSING`, `GENERATING` during inference, re-check on
  failure, `lastResponseMillis`.
- `core/llm/local/LocalLlmDto.kt` — `elapsed_ms`.
- `feature/chatbot/ChatbotViewModel.kt` — honest per-reason failure message.
- `feature/settings/SettingsScreen.kt` — renders the new states.
- `core/designsystem/strings/AppStrings.kt` — 4 new strings × EN/HI/MR.

**Docs** — `LOCAL_LLM_SETUP.md`, `LOCAL_LLM_IMPLEMENTATION_REPORT.md` (new).

## 9. Tests

`server/tests/test_local_llm_service.py` rewritten (**28 tests**): successful
generation; the prefill/think/num_predict/temperature payload; correct endpoint;
`<think>` stripping (4 shapes); reasoning-only reply rejected; unsupported
provider; missing URL; connection failure; **timeout**; malformed response;
non-JSON; empty reply; no cloud host or credential ever used; Devanagari accepted
for hi/mr; non-Devanagari reply for hi/mr rejected; English never rejected;
`READY` / `MODEL_MISSING` (two shapes) / bare-name tag match / `UNAVAILABLE`
(unreachable, timeout, misconfigured provider).

`server/tests/test_local_llm_endpoint.py` — updated for the new contract, plus a
`MODEL_MISSING` status test (**6 tests**).

`app/src/test/.../LocalLlmRepositoryImplTest.kt` — **+5 tests**: `MODEL_MISSING`
surfaced; unknown status never becomes READY; successful answer records real
generation time and settles to READY; blank reply never presented as an answer;
failed generation re-checks status.

No test was deleted or disabled. The three pre-existing tests that failed did so
because of my deliberate contract change (`generate()` now returns
`{reply, elapsed_ms}` and uses `/api/chat`); they were updated to the new
contract, not weakened.

## 10. Results

```
server: full suite         127 passed, 6 failed
android: testDebugUnitTest 131 passed, 0 failed   (was 126 — 5 new tests)
android: assembleDebug     BUILD SUCCESSFUL
```

The 6 server failures are the same pre-existing, unrelated ones documented in
earlier phases (`test_disease_endpoint.py` ×4, `test_stub_endpoints.py` ×2 —
disease/pest/risk-fusion stubs). Nothing in this phase touches them.

## 11. Remaining limitations

1. **Hindi/Marathi output quality** (§4) — needs a model with real Indic support.
   Configuration change only.
2. **Speed is hardware-bound.** 5 tok/s on CPU. English answers land in ~11s,
   which is usable but not instant. A GPU host or a 3B model would cut this
   substantially.
3. **No token streaming to the app.** The brief allowed either streaming or a
   safe longer read timeout; the timeout path was taken because the existing
   Android↔server contract is request/response, and with answers now at ~11s the
   added complexity of SSE through FastAPI → Retrofit → Compose isn't justified
   yet. The UI does not block — the call runs in `viewModelScope` and the status
   shows `GENERATING`.
4. **Voice-to-voice is not fully offline** — the Local LLM step is local, but
   Android's speech recognition/TTS may use Google's online services unless
   offline language packs are installed. Documented in `LOCAL_LLM_SETUP.md` §9;
   the app does not claim otherwise.
5. First request after Ollama starts pays ~13s cold model load.

## 12. Status

**Local LLM generation is verified working for English** — real, measured,
through the real route, with no fabricated results. The timeout problem is
resolved (83.5s-and-truncated → 11.0s-and-complete, with Android's 10s ceiling
raised to a purpose-sized 150s for this path only).

**Not claimed complete for Hindi/Marathi**: the pipeline runs and returns
correctly-scripted text, but the installed model's output quality there is not
fit for farmers. That requires a different model, not more code.
