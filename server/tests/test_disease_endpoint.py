from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)
client.__enter__()  # trigger the lifespan startup handler so the disease model is loaded
API_KEY_HEADERS = {"X-API-Key": "dev-only-change-me"}


def test_predict_disease_returns_valid_response():
    files = {"image": ("leaf.jpg", b"fake-image-bytes-1234567890", "image/jpeg")}
    response = client.post("/v1/predict/disease", headers=API_KEY_HEADERS, files=files)
    assert response.status_code == 200
    body = response.json()
    assert body["risk_level"] in {"LOW", "MEDIUM", "HIGH"}
    assert 0.0 <= body["confidence"] <= 1.0
    assert len(body["top_k"]) == 3


def test_predict_disease_requires_api_key():
    files = {"image": ("leaf.jpg", b"fake-image-bytes", "image/jpeg")}
    response = client.post("/v1/predict/disease", files=files)
    assert response.status_code == 401


def test_predict_disease_rejects_wrong_api_key():
    files = {"image": ("leaf.jpg", b"fake-image-bytes", "image/jpeg")}
    response = client.post("/v1/predict/disease", headers={"X-API-Key": "wrong"}, files=files)
    assert response.status_code == 401


def test_predict_disease_rejects_wrong_content_type():
    files = {"image": ("notes.txt", b"not-an-image", "text/plain")}
    response = client.post("/v1/predict/disease", headers=API_KEY_HEADERS, files=files)
    assert response.status_code == 415


def test_predict_disease_rejects_empty_upload():
    files = {"image": ("leaf.jpg", b"", "image/jpeg")}
    response = client.post("/v1/predict/disease", headers=API_KEY_HEADERS, files=files)
    assert response.status_code == 400


def test_predict_disease_rejects_oversized_upload():
    big = b"0" * (5 * 1024 * 1024 + 1)
    files = {"image": ("leaf.jpg", big, "image/jpeg")}
    response = client.post("/v1/predict/disease", headers=API_KEY_HEADERS, files=files)
    assert response.status_code == 413


def test_same_image_returns_same_stub_result():
    files = {"image": ("leaf.jpg", b"same-bytes-each-time", "image/jpeg")}
    first = client.post("/v1/predict/disease", headers=API_KEY_HEADERS, files=files)
    second = client.post("/v1/predict/disease", headers=API_KEY_HEADERS, files=files)
    assert first.json()["label"] == second.json()["label"]
