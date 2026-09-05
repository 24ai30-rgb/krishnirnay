from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
client.__enter__()  # trigger the lifespan startup handler


def test_health_returns_ok():
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert "models_loaded" in body


def test_health_requires_no_api_key():
    # /health is the one endpoint that must work with no X-API-Key header,
    # so uptime checks and demo pre-warming can hit it unauthenticated.
    response = client.get("/health", headers={})
    assert response.status_code == 200
