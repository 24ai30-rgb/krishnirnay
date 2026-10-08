from unittest.mock import AsyncMock, patch

from fastapi.testclient import TestClient

from app.config import get_settings
from app.main import app
from app.services.local_llm_service import (
    STATUS_GENERATION_FAILED,
    STATUS_GENERATION_WORKING,
    STATUS_MODEL_AVAILABLE,
    STATUS_MODEL_MISSING,
    STATUS_OLLAMA_UNREACHABLE,
    LocalLlmError,
)

client = TestClient(app)


def _headers():
    return {"X-API-Key": get_settings().api_key}


def test_chat_requires_api_key():
    response = client.post("/v1/local-llm/chat", json={"message": "hi", "context": {}})
    assert response.status_code == 401


def test_chat_returns_a_stable_success_shape():
    with patch(
        "app.routers.local_llm.generate",
        new=AsyncMock(return_value={"reply": "Irrigate this evening.", "elapsed_ms": 8123}),
    ):
        response = client.post(
            "/v1/local-llm/chat",
            json={"message": "Should I irrigate?", "context": {"language": "en"}},
            headers=_headers(),
        )
    # Always HTTP 200 — success/failure is the `success` field, not the
    # status code, so Android has one response shape to parse either way.
    assert response.status_code == 200
    body = response.json()
    assert body["success"] is True
    assert body["language"] == "en"
    assert body["answer"] == "Irrigate this evening."
    assert body["source"] == "local_llm"
    assert body["error"] is None
    assert body["elapsed_ms"] == 8123
    assert body["model"]  # the configured model name, never blank


def test_chat_returns_a_stable_failure_shape_never_a_fabricated_answer():
    with patch("app.routers.local_llm.generate", new=AsyncMock(side_effect=LocalLlmError("model not installed"))):
        response = client.post(
            "/v1/local-llm/chat",
            json={"message": "Should I irrigate?", "context": {"language": "en"}},
            headers=_headers(),
        )
    assert response.status_code == 200
    body = response.json()
    assert body["success"] is False
    assert body["answer"] is None
    assert body["error"] == "model not installed"
    assert body["language"] == "en"


def test_chat_failure_echoes_the_requested_language_not_a_default():
    with patch("app.routers.local_llm.generate", new=AsyncMock(side_effect=LocalLlmError("timed out"))):
        response = client.post(
            "/v1/local-llm/chat",
            json={"message": "आज बारिश होगी क्या?", "context": {"language": "hi"}},
            headers=_headers(),
        )
    assert response.json()["language"] == "hi"


def test_chat_rejects_a_malformed_request_body():
    response = client.post(
        "/v1/local-llm/chat",
        json={"message": "hi"},  # missing required "context"
        headers=_headers(),
    )
    assert response.status_code == 422


def test_status_requires_api_key():
    response = client.get("/v1/local-llm/status")
    assert response.status_code == 401


def _status_result(status: str, ollama_running: bool, **availability: bool) -> dict:
    defaults = {"en": True, "hi": True, "mr": True}
    defaults.update(availability)
    return {"status": status, "ollama_running": ollama_running, "model_availability": defaults}


def test_status_reports_model_available_when_reachable_and_pulled():
    with patch(
        "app.routers.local_llm.check_status",
        new=AsyncMock(return_value=_status_result(STATUS_MODEL_AVAILABLE, True)),
    ):
        response = client.get("/v1/local-llm/status", headers=_headers())
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == STATUS_MODEL_AVAILABLE
    assert body["ollama_running"] is True


def test_status_reports_ollama_unreachable():
    with patch(
        "app.routers.local_llm.check_status",
        new=AsyncMock(return_value=_status_result(STATUS_OLLAMA_UNREACHABLE, False, en=False, hi=False, mr=False)),
    ):
        response = client.get("/v1/local-llm/status", headers=_headers())
    body = response.json()
    assert body["status"] == STATUS_OLLAMA_UNREACHABLE
    assert body["ollama_running"] is False


def test_status_reports_model_missing_when_ollama_is_up_but_model_not_pulled():
    # Must surface as its own state: the developer/farmer fix is "pull the
    # model", not "start Ollama", so it can never read as unreachable.
    with patch(
        "app.routers.local_llm.check_status",
        new=AsyncMock(return_value=_status_result(STATUS_MODEL_MISSING, True, en=False, hi=False, mr=False)),
    ):
        response = client.get("/v1/local-llm/status", headers=_headers())
    body = response.json()
    assert body["status"] == STATUS_MODEL_MISSING
    assert body["ollama_running"] is True


def test_status_deep_check_is_off_by_default():
    mock_check = AsyncMock(return_value=_status_result(STATUS_MODEL_AVAILABLE, True))
    with patch("app.routers.local_llm.check_status", new=mock_check):
        client.get("/v1/local-llm/status", headers=_headers())
    mock_check.assert_called_once_with(deep=False)


def test_status_deep_check_can_be_requested_and_reports_generation_working():
    mock_check = AsyncMock(return_value=_status_result(STATUS_GENERATION_WORKING, True))
    with patch("app.routers.local_llm.check_status", new=mock_check):
        response = client.get("/v1/local-llm/status?deep=true", headers=_headers())
    mock_check.assert_called_once_with(deep=True)
    assert response.json()["status"] == STATUS_GENERATION_WORKING


def test_status_deep_check_reports_generation_failed_when_a_listed_model_cannot_actually_generate():
    with patch(
        "app.routers.local_llm.check_status",
        new=AsyncMock(return_value=_status_result(STATUS_GENERATION_FAILED, True)),
    ):
        response = client.get("/v1/local-llm/status?deep=true", headers=_headers())
    assert response.json()["status"] == STATUS_GENERATION_FAILED


# --------------------------------------------------------------- Phase 4


def test_status_reports_hindi_and_marathi_model_availability_separately_from_english():
    """The diagnostics panel needs to tell a developer "English is ready but
    Hindi isn't pulled yet" — a single combined model flag can't say that."""
    with patch(
        "app.routers.local_llm.check_status",
        new=AsyncMock(return_value=_status_result(STATUS_MODEL_AVAILABLE, True, hi=False, mr=False)),
    ):
        response = client.get("/v1/local-llm/status", headers=_headers())
    body = response.json()
    assert body["model_availability"] == {"en": True, "hi": False, "mr": False}


def test_chat_routes_hindi_to_the_hindi_configured_model_not_the_english_one(monkeypatch):
    # Pin distinct models so the test doesn't depend on the developer's .env
    # (a single-model setup legitimately uses the same model for every language).
    monkeypatch.setattr(get_settings(), "local_llm_model", "english-model:1b")
    monkeypatch.setattr(get_settings(), "local_llm_model_hi", "hindi-model:3b")
    mock_generate = AsyncMock(return_value={"reply": "फफूंदनाशक का छिड़काव करें।", "elapsed_ms": 9000})
    with patch("app.routers.local_llm.generate", new=mock_generate):
        response = client.post(
            "/v1/local-llm/chat",
            json={"message": "क्या करूं?", "context": {"language": "hi"}},
            headers=_headers(),
        )
    assert response.status_code == 200
    called_model = mock_generate.call_args.kwargs["model"]
    assert called_model == get_settings().local_llm_model_hi
    assert called_model != get_settings().local_llm_model
    assert response.json()["model"] == get_settings().local_llm_model_hi


def test_chat_routes_marathi_to_the_marathi_configured_model():
    mock_generate = AsyncMock(return_value={"reply": "बुरशीनाशक फवारणी करा.", "elapsed_ms": 9000})
    with patch("app.routers.local_llm.generate", new=mock_generate):
        client.post(
            "/v1/local-llm/chat",
            json={"message": "मी काय करू?", "context": {"language": "mr"}},
            headers=_headers(),
        )
    assert mock_generate.call_args.kwargs["model"] == get_settings().local_llm_model_mr


def test_chat_uses_a_hindi_system_prompt_that_forbids_english_fallback():
    mock_generate = AsyncMock(return_value={"reply": "ठीक है।", "elapsed_ms": 100})
    with patch("app.routers.local_llm.generate", new=mock_generate):
        client.post(
            "/v1/local-llm/chat",
            json={"message": "क्या करूं?", "context": {"language": "hi"}},
            headers=_headers(),
        )
    system_prompt = mock_generate.call_args.kwargs["system"]
    assert "हिंदी" in system_prompt
    assert "अंग्रेज़ी में fallback न करें" in system_prompt


def test_chat_normalizes_a_bcp47_style_tag_to_its_base_language():
    mock_generate = AsyncMock(return_value={"reply": "ok", "elapsed_ms": 100})
    with patch("app.routers.local_llm.generate", new=mock_generate):
        response = client.post(
            "/v1/local-llm/chat",
            json={"message": "क्या करूं?", "context": {"language": "hi-IN"}},
            headers=_headers(),
        )
    # Android only ever sends the bare "hi"/"mr"/"en" codes, but a region tag
    # like "hi-IN" should still resolve to the Hindi model, not silently fall
    # through to English.
    assert response.json()["language"] == "hi"
    assert mock_generate.call_args.kwargs["model"] == get_settings().local_llm_model_hi


def test_chat_normalizes_a_genuinely_unrecognised_language_code_to_english():
    mock_generate = AsyncMock(return_value={"reply": "ok", "elapsed_ms": 100})
    with patch("app.routers.local_llm.generate", new=mock_generate):
        response = client.post(
            "/v1/local-llm/chat",
            json={"message": "hi", "context": {"language": "zz"}},
            headers=_headers(),
        )
    assert response.json()["language"] == "en"
    assert mock_generate.call_args.kwargs["model"] == get_settings().local_llm_model


def test_chat_language_failure_returns_a_farmer_readable_hindi_message_not_the_raw_english_exception():
    with patch(
        "app.routers.local_llm.generate",
        new=AsyncMock(side_effect=LocalLlmError("The local AI model could not answer in the selected language.")),
    ):
        response = client.post(
            "/v1/local-llm/chat",
            json={"message": "क्या करूं?", "context": {"language": "hi"}},
            headers=_headers(),
        )
    body = response.json()
    assert body["success"] is False
    assert "selected language" not in body["error"]
    assert any("ऀ" <= ch <= "ॿ" for ch in body["error"])  # the substitute message is itself in Hindi


def test_chat_non_language_failure_keeps_the_raw_message_even_for_hindi():
    """Only the language-quality failure gets a localized substitute — an
    unrelated failure (model missing, Ollama down) keeps its real reason."""
    with patch("app.routers.local_llm.generate", new=AsyncMock(side_effect=LocalLlmError("model not installed"))):
        response = client.post(
            "/v1/local-llm/chat",
            json={"message": "क्या करूं?", "context": {"language": "hi"}},
            headers=_headers(),
        )
    assert response.json()["error"] == "model not installed"


# ------------------------------------------------------ streaming (Part 1)


def _fake_stream(*chunks):
    """A stand-in for stream_generate that yields fixed chunks — patched
    directly since it's an async generator function, not an AsyncMock."""

    async def gen(*_args, **_kwargs):
        for chunk in chunks:
            yield chunk

    return gen


def _fake_stream_error(message: str):
    async def gen(*_args, **_kwargs):
        raise LocalLlmError(message)
        yield  # pragma: no cover — makes this a generator function

    return gen


def _read_ndjson_events(response) -> list[dict]:
    import json as _json

    return [_json.loads(line) for line in response.text.splitlines() if line.strip()]


def test_chat_stream_requires_api_key():
    response = client.post("/v1/local-llm/chat/stream", json={"message": "hi", "context": {}})
    assert response.status_code == 401


def test_chat_stream_sends_progressive_deltas_then_a_final_success_event():
    with patch("app.routers.local_llm.stream_generate", new=_fake_stream("Spray ", "fungicide now.")):
        response = client.post(
            "/v1/local-llm/chat/stream",
            json={"message": "Should I spray?", "context": {"language": "en"}},
            headers=_headers(),
        )
    assert response.status_code == 200
    events = _read_ndjson_events(response)
    assert events[0] == {"delta": "Spray "}
    assert events[1] == {"delta": "fungicide now."}
    final = events[-1]
    assert final["done"] is True
    assert final["success"] is True
    assert final["answer"] == "Spray fungicide now."
    assert final["error"] is None
    assert isinstance(final["elapsed_ms"], int)


def test_chat_stream_never_reports_success_when_the_final_language_check_fails():
    """Deltas can stream (feels fast) even though the assembled answer will
    ultimately fail validation — the FINAL event must still be honest and
    never claim success just because tokens were shown along the way."""
    with patch(
        "app.routers.local_llm.stream_generate",
        new=_fake_stream("Your soil moisture is low."),  # English text requested as Hindi
    ):
        response = client.post(
            "/v1/local-llm/chat/stream",
            json={"message": "क्या करूं?", "context": {"language": "hi"}},
            headers=_headers(),
        )
    events = _read_ndjson_events(response)
    final = events[-1]
    assert final["done"] is True
    assert final["success"] is False
    assert final["answer"] is None
    assert final["error"] is not None


def test_chat_stream_connection_failure_yields_one_honest_final_event_no_partial_deltas():
    with patch("app.routers.local_llm.stream_generate", new=_fake_stream_error("connection refused")):
        response = client.post(
            "/v1/local-llm/chat/stream",
            json={"message": "hi", "context": {"language": "en"}},
            headers=_headers(),
        )
    events = _read_ndjson_events(response)
    assert len(events) == 1
    assert events[0]["success"] is False
    assert events[0]["answer"] is None


def test_chat_stream_routes_to_the_hindi_model_same_as_the_non_streaming_endpoint():
    captured_kwargs = {}

    async def gen(prompt, **kwargs):
        captured_kwargs.update(kwargs)
        yield "ठीक है।"

    with patch("app.routers.local_llm.stream_generate", new=gen):
        client.post(
            "/v1/local-llm/chat/stream",
            json={"message": "क्या करूं?", "context": {"language": "hi"}},
            headers=_headers(),
        )
    assert captured_kwargs["model"] == get_settings().local_llm_model_hi


# ------------------------------------------------------- on-device model


def test_on_device_model_requires_api_key():
    response = client.get("/v1/local-llm/on-device-model")
    assert response.status_code == 401


def test_on_device_model_honestly_404s_when_not_staged(tmp_path, monkeypatch):
    """A real 404 (never an empty/fabricated file) is exactly what tells
    OnDeviceModelManager the developer hasn't placed the licensed model file
    yet — see server/models/on_device/README.md."""
    monkeypatch.setattr(
        "app.routers.local_llm._ON_DEVICE_MODEL_PATH",
        tmp_path / "does_not_exist.task",
    )
    response = client.get("/v1/local-llm/on-device-model", headers=_headers())
    assert response.status_code == 404


def test_on_device_model_serves_the_real_file_with_its_real_size_when_staged(tmp_path, monkeypatch):
    model_file = tmp_path / "gemma3_1b_int4.task"
    model_file.write_bytes(b"fake-model-bytes-for-test")
    monkeypatch.setattr("app.routers.local_llm._ON_DEVICE_MODEL_PATH", model_file)

    response = client.get("/v1/local-llm/on-device-model", headers=_headers())

    assert response.status_code == 200
    assert response.content == b"fake-model-bytes-for-test"
    # OnDeviceModelManager refuses to download without a real declared size.
    assert int(response.headers["content-length"]) == len(b"fake-model-bytes-for-test")


# ---------------------------------------------------------------------------
# The assistant must ANSWER the farmer's question, not recite the field facts.
# Observed on a real device: "hello" and "my name is X" both got "your soil
# moisture is a bit low..." because the prompt said "answer using ONLY the
# facts above in at most 2 sentences".
# ---------------------------------------------------------------------------

from app.routers.local_llm import _SYSTEM_PROMPTS, _build_prompt
from app.schemas.local_llm import LocalLlmChatRequest, LocalLlmContext


def _request(message: str, language: str = "en") -> LocalLlmChatRequest:
    return LocalLlmChatRequest(
        message=message,
        context=LocalLlmContext(language=language, crop="Soybean", soil_moisture_pct=21.0, temperature_c=29.0),
    )


def test_prompt_asks_the_model_to_answer_the_question_not_to_recite_facts():
    prompt = _build_prompt(_request("hello"), "en")
    assert '"hello"' in prompt
    assert "ONLY the facts" not in prompt
    assert "directly" in prompt.lower()
    assert "do not just repeat" in prompt.lower()


def test_prompt_frames_field_data_as_optional_background():
    prompt = _build_prompt(_request("how to protect soybean from yellow mosaic virus?"), "en")
    assert "only if relevant" in prompt.lower()
    # the facts are still there for questions that need them
    assert "Soybean" in prompt and "21.0" in prompt


def test_english_system_prompt_handles_greetings_and_general_farming_questions():
    system = _SYSTEM_PROMPTS["en"]
    assert "greet" in system.lower()
    assert "general farming" in system.lower()


def test_system_prompts_still_forbid_inventing_field_data_and_doses():
    system = _SYSTEM_PROMPTS["en"]
    assert "NEVER invent" in system
    assert "dose" in system.lower()


def test_hindi_and_marathi_prompts_also_answer_the_question():
    # (language, word in the per-message instruction, word in the system prompt)
    for language, prompt_marker, system_marker in (("hi", "सीधा", "सीधे"), ("mr", "थेट", "थेट")):
        prompt = _build_prompt(_request("नमस्ते", language), language)
        assert '"नमस्ते"' in prompt
        assert prompt_marker in prompt
        assert system_marker in _SYSTEM_PROMPTS[language]


def test_prompt_tells_the_model_not_to_mention_field_data_for_small_talk():
    for language, marker in (("en", "greeting or small talk"), ("hi", "अभिवादन"), ("mr", "नमस्कार")):
        prompt = _build_prompt(_request("hello", language), language)
        # the per-message instruction (the last thing the model reads) must say it
        assert marker in prompt.split('"hello"')[-1]
