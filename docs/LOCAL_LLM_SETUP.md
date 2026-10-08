# KrishiNirnay — Local LLM Setup

The Local AI runs entirely on your own machine. Android never talks to Ollama
directly: it calls this project's FastAPI server, and the server calls Ollama on
localhost. No cloud AI is involved and no API key is needed.

```
Android app ──► KrishiNirnay FastAPI server ──► Ollama (127.0.0.1:11434)
```

## 1. Install Ollama

Download from <https://ollama.com/download> (Windows, macOS, Linux).

Verify:
```bash
ollama --version
```

## 2. Start Ollama

```bash
ollama serve
```

On Windows the installer usually runs it as a background service already. It
listens on `127.0.0.1:11434` only — local interfaces, never the public network.
Keep it that way unless you have a specific, secured reason to change it.

Verify it is up:
```bash
curl http://127.0.0.1:11434/api/tags
```

## 3. Pull the model

```bash
ollama pull deepseek-r1:7b
```

Confirm it is installed:
```bash
ollama list
```

If the configured model is not in that list, the app will honestly report
**"Local AI model not installed"** rather than failing mysteriously.

### Which model should I use?

Measured on the reference dev machine (CPU-only, ~5 tokens/second):

| Model | English | Hindi / Marathi | Notes |
|---|---|---|---|
| `deepseek-r1:7b` (current default) | works, ~11s per answer | **not usable** — generates Devanagari but the content is garbled | Reasoning model; needs the `<think>` handling this project implements |
| A smaller non-reasoning model (e.g. `llama3.2:3b`, `qwen2.5:3b`) | faster | varies | No reasoning preamble, so lower latency |
| A model with strong Indic support | — | **recommended if you need Hindi/Marathi** | See the limitation section in `LOCAL_LLM_IMPLEMENTATION_REPORT.md` |

Switching model is configuration only — no code change:
```bash
ollama pull qwen2.5:3b
# then set LOCAL_LLM_MODEL=qwen2.5:3b in server/.env
```

## 4. Configure the server

Copy the template and edit if your setup differs from the defaults:
```bash
cp server/.env.example server/.env
```

| Variable | Default | Meaning |
|---|---|---|
| `LOCAL_LLM_PROVIDER` | `ollama` | Only `ollama` is implemented |
| `LOCAL_LLM_URL` | `http://127.0.0.1:11434` | Use `127.0.0.1`, not `localhost` — `localhost` can resolve to `::1` first and stall |
| `LOCAL_LLM_MODEL` | `deepseek-r1:7b` | Must match a name from `ollama list` |
| `LOCAL_LLM_CONNECT_TIMEOUT_SECONDS` | `5` | A local port answers immediately or isn't listening |
| `LOCAL_LLM_READ_TIMEOUT_SECONDS` | `120` | Generation genuinely takes tens of seconds locally |
| `LOCAL_LLM_NUM_PREDICT` | `220` | Caps output length, and therefore worst-case wait |
| `LOCAL_LLM_TEMPERATURE` | `0.2` | Low: this layer explains facts, it doesn't invent them |

No API key exists for the Local LLM — there is nothing secret to leak.

## 5. Test the Local AI

Start the server:
```bash
cd server && uvicorn app.main:app --reload
```

Check status (`X-API-Key` is the app's own server key from `server/.env`):
```bash
curl -H "X-API-Key: dev-only-change-me" http://127.0.0.1:8000/v1/local-llm/status
```

Expected: `{"status":"READY","provider":"ollama","model":"deepseek-r1:7b"}`

| `status` | Meaning | Fix |
|---|---|---|
| `READY` | Server reachable and model installed | — |
| `MODEL_MISSING` | Ollama is running but the model isn't pulled | `ollama pull <model>` |
| `UNAVAILABLE` | Ollama not reachable at all | `ollama serve` |

Ask a real question:
```bash
curl -X POST http://127.0.0.1:8000/v1/local-llm/chat \
  -H "X-API-Key: dev-only-change-me" -H "Content-Type: application/json" \
  -d '{"message":"Why should I delay irrigation?",
       "context":{"language":"en","crop":"Cotton","soil_moisture_pct":28,
                  "weather_status":"LIVE","weather_summary":"31C, rain chance 70%",
                  "overall_risk":"MEDIUM",
                  "recommendation_summary":"Delay irrigation, rain expected within 24 hours"}}'
```

In the app: open the chat/assistant screen and ask "What should I do today?".
Settings shows the current Local AI status.

## 6. Test offline mode

The Local AI needs **no internet** — only Ollama on localhost. To prove it:

1. Disconnect the machine from the internet (keep Ollama running).
2. Ask the assistant a question. It still answers.
3. Weather/market will correctly show `CACHED` or `UNAVAILABLE`, because those
   *do* need the internet. The Local AI explains whatever real data is present
   and says so plainly when something isn't available.

To see the honest failure states:
- Stop Ollama → status becomes `UNAVAILABLE`, chat replies "Local AI unavailable".
- Set `LOCAL_LLM_MODEL` to a model you have not pulled → `MODEL_MISSING`.

The app never silently falls back to a cloud AI. (There is a separate,
**off-by-default**, opt-in cloud fallback for the AI Insights screen only; the
Local AI chat path never uses it.)

## 7. Troubleshooting timeouts

Symptom: the assistant takes very long or reports it could not process the request.

1. **First request after starting Ollama is slow.** Cold model load measured ~13s
   for a 7B model, on top of generation. Ask once to warm it up.
2. **Check raw model speed:**
   ```bash
   curl http://127.0.0.1:11434/api/generate -d '{"model":"deepseek-r1:7b","prompt":"hi","stream":false}'
   ```
   If this alone takes minutes, the hardware is the bottleneck — use a smaller
   model.
3. **Reasoning models are the usual cause.** `deepseek-r1` writes a long hidden
   `<think>` monologue before answering. This project already suppresses that
   (see `PREFILL_SKIP_THINKING` in `server/app/services/local_llm_service.py`) —
   measured 83.5s → 8.0s. Do not remove it.
4. **Shorten the answer** — lower `LOCAL_LLM_NUM_PREDICT`. Output length is the
   single biggest latency lever at ~5 tokens/second.
5. **Still slow?** Use a smaller model (3B instead of 7B), or a GPU-backed host.
6. Timeouts are layered and already sized for local inference: Android 150s read,
   server 120s read, 5s connect. Raising them further only makes the farmer wait
   longer — fix the speed instead.

## 8. Supported languages

The reply language follows the farmer's language setting (English / Hindi /
Marathi) via the existing localization architecture.

**Honest status:** with `deepseek-r1:7b`, only **English output is usable**.
Hindi and Marathi come back in the right script but the wording is unreliable —
a limitation of this small reasoning model, not of the integration. If the model
answers in the wrong script entirely, the server rejects the reply rather than
showing it. For production Hindi/Marathi, use a model with genuine Indic support
and set `LOCAL_LLM_MODEL` accordingly.

## 9. Voice architecture status

Current pipeline:

```
Farmer speaks → Android SpeechRecognizer → Local LLM (local) → TextToSpeech → Farmer hears
```

The Local LLM step is genuinely local. **The speech steps are not guaranteed
offline:** Android's `SpeechRecognizer` and `TextToSpeech` are provided by the
device, and on many devices recognition falls back to Google's online service
unless offline language packs are installed.

So this is **not** fully offline voice-to-voice, and the app does not claim it is.
To get closer: install offline speech recognition and TTS language packs in
Android settings for the farmer's language.
