from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
client.__enter__()
API_KEY_HEADERS = {"X-API-Key": "dev-only-change-me"}

VALID_PAYLOAD = {
    "mode": "chat",
    "message": "Should I irrigate today?",
    "context": {
        "overallRisk": "LOW",
        "waterStressRisk": "LOW",
        "heatRisk": "LOW",
        "cropHealthRisk": "UNKNOWN",
        "reasons": ["Soil moisture at 58%, above the stress threshold"],
        "soilMoisturePct": 58.0,
        "temperatureC": 27.0,
        "humidityPct": 64.0,
    },
}


def test_chat_requires_api_key():
    response = client.post("/v1/chat", json=VALID_PAYLOAD)
    assert response.status_code == 401


def test_chat_without_gemini_key_configured_returns_502():
    # GEMINI_API_KEY is empty in this test environment (no .env) — the
    # proxy must fail clearly rather than silently succeed with nothing.
    response = client.post("/v1/chat", headers=API_KEY_HEADERS, json=VALID_PAYLOAD)
    assert response.status_code == 502
    assert response.json()["detail"]["error"] == "gemini_unavailable"


def test_chat_rejects_invalid_mode():
    payload = {**VALID_PAYLOAD, "mode": "not-a-real-mode"}
    response = client.post("/v1/chat", headers=API_KEY_HEADERS, json=payload)
    assert response.status_code == 422
