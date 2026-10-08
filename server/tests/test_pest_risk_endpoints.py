from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
client.__enter__()
API_KEY_HEADERS = {"X-API-Key": "dev-only-change-me"}


def test_pest_requires_image():
    response = client.post("/v1/predict/pest", headers=API_KEY_HEADERS)
    assert response.status_code == 422


def test_pest_requires_api_key():
    response = client.post("/v1/predict/pest")
    assert response.status_code == 401


def test_pest_rejects_undecodable_image():
    files = {"file": ("bug.jpg", b"not-really-a-jpeg", "image/jpeg")}
    response = client.post("/v1/predict/pest", headers=API_KEY_HEADERS, files=files)
    assert response.status_code == 400


def test_pest_rejects_oversized_upload():
    files = {"file": ("bug.jpg", b"0" * (5 * 1024 * 1024 + 1), "image/jpeg")}
    response = client.post("/v1/predict/pest", headers=API_KEY_HEADERS, files=files)
    assert response.status_code == 413


def test_risk_fusion_requires_body():
    response = client.post("/v1/predict/risk-fusion", headers=API_KEY_HEADERS)
    assert response.status_code == 422


def test_risk_fusion_requires_api_key():
    response = client.post("/v1/predict/risk-fusion")
    assert response.status_code == 401
