# On-device AI model

`GET /v1/local-llm/on-device-model` (see `app/routers/local_llm.py`) serves
whatever file is placed here to the Android app's `OnDeviceModelManager`,
which downloads it into the phone's private storage so the app's Local AI
keeps working with this PC, this server, and Ollama completely off.

**This file is deliberately not checked into git and not fetched
automatically by anything in this repo.** It requires accepting Google's
Gemma license, which only a human can do.

## What to place here

`gemma3_1b_int4.task` — Gemma 3 1B, int4 quantized, in MediaPipe/LiteRT-LM's
`.task` format (~529MB). Download it yourself from the LiteRT community on
Hugging Face after accepting the license terms:

https://huggingface.co/litert-community

Rename/move the downloaded file to exactly:

```
server/models/on_device/gemma3_1b_int4.task
```

Once it's here, `GET /v1/local-llm/on-device-model` starts serving it
(currently returns a real 404 — never a fabricated response — until this
file exists), and the "Download offline AI model" button in the Android
app's Settings screen will succeed.

## Why this isn't automated

- The model is gated behind Google's Gemma license — an automated download
  would need to embed or prompt for credentials this app has no business
  holding.
- It is genuinely large (~500MB) — committing it to git, or having this
  server fetch it on startup, would be a silent multi-hundred-MB action
  nobody explicitly asked for.
