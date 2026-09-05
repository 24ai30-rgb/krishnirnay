# FastAPI server — API contract

Base URL: the deployed Render/Railway URL (or `http://localhost:8000` in dev). Every endpoint except `/health` requires an `X-API-Key` header matching the server's configured `API_KEY` — this is a floor against random internet traffic hitting a free-tier deployment, not real per-user auth (the app itself is what's protected by Firebase Auth).

## Why the server exists, not just the app

Two things must never be embedded in the Android APK, because any string in an APK is trivially extractable (`apktool`/`strings`):

1. **The Gemini API key** — a leaked key is a direct billing-abuse/quota-exhaustion risk. Both the Chatbot and the AI Insights "explanation polish" layer call `POST /v1/chat` on this server instead of Gemini directly.
2. **The trained disease-detection model's inference cost** — running it server-side also means the app doesn't need to bundle a large model file for something that requires network access anyway (a photo upload).

## Endpoints

### `GET /health`
No auth required.

```json
{ "status": "ok", "models_loaded": ["disease-v1"] }
```

### `POST /v1/predict/disease`
`multipart/form-data`, field name `image`. Server rejects anything above **5MB** with `413`. Client (`CropHealthViewModel`) downscales to ~1024px long edge / JPEG quality ~80 before upload — see architecture notes on the rural-bandwidth loophole.

Success — `200`:
```json
{
  "label": "tomato_early_blight",
  "display_name": "Tomato — Early Blight",
  "confidence": 0.82,
  "top_k": [
    { "label": "tomato_early_blight", "confidence": 0.82 },
    { "label": "tomato_late_blight", "confidence": 0.11 },
    { "label": "tomato_healthy", "confidence": 0.04 }
  ],
  "risk_level": "MEDIUM",
  "model_version": "disease-v1"
}
```

`risk_level` is always one of `LOW | MEDIUM | HIGH` — the server maps all 23 disease classes to a risk level via a static lookup table, so the app never needs to know the class taxonomy, only this field (consistent with every other risk badge in the app).

Failure — `4xx`:
```json
{ "error": "invalid_image", "message": "Could not decode the uploaded file as an image." }
```

### `POST /v1/predict/pest` — Phase 2, not implemented
### `POST /v1/predict/risk-fusion` — Phase 2, not implemented

Both return `501` with a structured body, not a bare 404, so the app can render a "Coming soon" state rather than treating it as a network failure:

```json
{ "error": "not_implemented", "message": "Pest detection is a Phase 2 feature." }
```

**The app-side repository methods for these two never call the network at all in Phase 1** — they return a local `ComingSoon` result directly. These endpoints exist purely to lock the HTTP contract early so client and server work could proceed in parallel; the app doesn't depend on them responding.

### `POST /v1/chat`
`application/json`. Used by both the Chatbot screen and AI Insights' "explanation polish" layer (`mode: "explain"` vs `mode: "chat"`).

Request:
```json
{
  "mode": "chat",
  "message": "Should I irrigate today?",
  "context": {
    "overallRisk": "LOW",
    "waterStressRisk": "LOW",
    "heatRisk": "LOW",
    "cropHealthRisk": "UNKNOWN",
    "reasons": ["Soil moisture at 58%, above the 20% stress threshold", "..."],
    "sensors": { "soilMoisturePct": 58.0, "temperatureC": 27.0, "humidityPct": 64.0 }
  }
}
```

Response — `200`:
```json
{ "reply": "Soil moisture is at 58%, within a healthy range. No irrigation needed today." }
```

The server prepends `context` to the prompt before calling Gemini — Gemini itself never touches Firebase or any app-internal system directly.

## Versioning

All endpoints are under `/v1/`. A breaking change bumps to `/v2/`; the app pins its `NetworkModule` base URL to one version at a time.
