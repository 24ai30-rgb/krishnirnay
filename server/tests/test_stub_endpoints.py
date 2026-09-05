from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
client.__enter__()
API_KEY_HEADERS = {"X-API-Key": "dev-only-change-me"}


def test_pest_returns_structured_501():
    response = client.post("/v1/predict/pest", headers=API_KEY_HEADERS)
    assert response.status_code == 501
    assert response.json()["detail"]["error"] == "not_implemented"


def test_pest_requires_api_key():
    response = client.post("/v1/predict/pest")
    assert response.status_code == 401


def test_risk_fusion_returns_structured_501():
    response = client.post("/v1/predict/risk-fusion", headers=API_KEY_HEADERS)
    assert response.status_code == 501
    assert response.json()["detail"]["error"] == "not_implemented"


def test_risk_fusion_requires_api_key():
    response = client.post("/v1/predict/risk-fusion")
    assert response.status_code == 401
