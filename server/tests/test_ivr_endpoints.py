from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import app
from app.services.local_llm_service import LocalLlmError

client = TestClient(app)


def _headers():
    return {"X-API-Key": get_settings().api_key}


def test_incoming_call_requires_api_key():
    response = client.post("/v1/ivr/incoming-call", json={"call_id": "c1", "from_phone_number": "+919876543210"})
    assert response.status_code == 401


def test_incoming_call_returns_the_welcome_message():
    response = client.post(
        "/v1/ivr/incoming-call", json={"call_id": "c2", "from_phone_number": "+919876543210"}, headers=_headers(),
    )
    assert response.status_code == 200
    assert response.json()["next_step"] == "language"


def test_language_selection_marathi():
    client.post("/v1/ivr/incoming-call", json={"call_id": "c3", "from_phone_number": "+919876543210"}, headers=_headers())
    response = client.post("/v1/ivr/language", json={"call_id": "c3", "digit": "1"}, headers=_headers())
    body = response.json()
    assert body["language"] == "mr"
    assert body["next_step"] == "question"


def test_language_selection_invalid_digit_retries():
    client.post("/v1/ivr/incoming-call", json={"call_id": "c4", "from_phone_number": "+919876543210"}, headers=_headers())
    response = client.post("/v1/ivr/language", json={"call_id": "c4", "digit": "9"}, headers=_headers())
    body = response.json()
    assert body["language"] is None
    assert body["next_step"] == "language"


def test_unknown_caller_gets_the_not_registered_message():
    client.post("/v1/ivr/incoming-call", json={"call_id": "c5", "from_phone_number": "+919876543210"}, headers=_headers())
    client.post("/v1/ivr/language", json={"call_id": "c5", "digit": "1"}, headers=_headers())
    response = client.post(
        "/v1/ivr/question",
        json={"call_id": "c5", "transcript": "पाणी कधी द्यायचं?"},
        headers=_headers(),
    )
    body = response.json()
    assert body["farmer_found"] is False
    assert "नोंदवा" in body["message"]


def test_question_remembers_the_language_selected_earlier_in_the_call():
    """Session state: the /question call never repeats the language — it must
    be recalled server-side from what /language recorded for this call_id."""
    client.post("/v1/ivr/incoming-call", json={"call_id": "c6", "from_phone_number": "+919876543210"}, headers=_headers())
    client.post("/v1/ivr/language", json={"call_id": "c6", "digit": "1"}, headers=_headers())  # Marathi

    response = client.post(
        "/v1/ivr/question",
        json={"call_id": "c6", "transcript": "पाणी कधी द्यायचं?"},
        headers=_headers(),
    )
    # Unregistered caller -> the not-registered message, but in the
    # Marathi selected earlier, never the English default.
    assert response.json()["message"] == "कृपया प्रथम KRISHINIRNAY मध्ये तुमची शेती माहिती नोंदवा."


def test_question_without_a_prior_language_selection_defaults_honestly_to_english():
    # No /incoming-call or /language was ever made for this call_id.
    response = client.post(
        "/v1/ivr/question",
        json={"call_id": "never-started", "transcript": "water?"},
        headers=_headers(),
    )
    assert response.json()["message"] == "Please first register your farm details in KRISHINIRNAY."


def test_registered_caller_reaches_the_local_llm():
    with patch("app.routers.ivr._farmer_lookup") as mock_lookup:
        from app.services.ivr_flow import FarmerLookupResult

        mock_lookup.lookup.return_value = FarmerLookupResult(found=True, farmer_id="f1")
        client.post("/v1/ivr/incoming-call", json={"call_id": "c7", "from_phone_number": "+919876543210"}, headers=_headers())
        client.post("/v1/ivr/language", json={"call_id": "c7", "digit": "3"}, headers=_headers())  # English
        with patch("app.routers.ivr.generate", new=AsyncMock(return_value="Soil moisture is fine.")):
            response = client.post(
                "/v1/ivr/question",
                json={"call_id": "c7", "transcript": "water?"},
                headers=_headers(),
            )
    body = response.json()
    assert body["farmer_found"] is True
    assert body["message"] == "Soil moisture is fine."


def test_provider_failure_during_question_is_honestly_reported():
    with patch("app.routers.ivr._farmer_lookup") as mock_lookup:
        from app.services.ivr_flow import FarmerLookupResult

        mock_lookup.lookup.return_value = FarmerLookupResult(found=True, farmer_id="f1")
        client.post("/v1/ivr/incoming-call", json={"call_id": "c8", "from_phone_number": "+919876543210"}, headers=_headers())
        client.post("/v1/ivr/language", json={"call_id": "c8", "digit": "3"}, headers=_headers())
        with patch("app.routers.ivr.generate", new=AsyncMock(side_effect=LocalLlmError("unreachable"))):
            response = client.post(
                "/v1/ivr/question",
                json={"call_id": "c8", "transcript": "water?"},
                headers=_headers(),
            )
    body = response.json()
    assert body["farmer_found"] is True
    assert "available" in body["message"]


def test_end_call_clears_the_session():
    client.post("/v1/ivr/incoming-call", json={"call_id": "c9", "from_phone_number": "+919876543210"}, headers=_headers())
    response = client.post("/v1/ivr/end-call", json={"call_id": "c9"}, headers=_headers())
    assert response.json()["ended"] is True

    # Ending an already-ended (or never-started) call is honestly reported too.
    response = client.post("/v1/ivr/end-call", json={"call_id": "c9"}, headers=_headers())
    assert response.json()["ended"] is False


def test_status_reports_unconfigured_by_default():
    response = client.get("/v1/ivr/status", headers=_headers())
    assert response.status_code == 200
    body = response.json()
    assert body["configured"] is False
