"""Unit tests for app.services.local_llm_service. httpx.AsyncClient is mocked
throughout — no real Ollama server needs to be running for these to pass,
and they must never depend on one being reachable in CI. (A real, measured
smoke test against live Ollama is recorded in
LOCAL_LLM_IMPLEMENTATION_REPORT.md instead.)"""

import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

import httpx
import pytest

from app.config import get_settings
from app.services.local_llm_service import (
    PREFILL_SKIP_THINKING,
    STATUS_GENERATION_FAILED,
    STATUS_GENERATION_WORKING,
    STATUS_MODEL_AVAILABLE,
    STATUS_MODEL_MISSING,
    STATUS_OLLAMA_UNREACHABLE,
    LocalLlmError,
    check_status,
    generate,
    stream_generate,
)


def _run(coro):
    return asyncio.run(coro)


def _response(status_code: int, json_body: dict) -> MagicMock:
    resp = MagicMock()
    resp.status_code = status_code
    resp.json.return_value = json_body
    if status_code >= 400:
        resp.raise_for_status.side_effect = httpx.HTTPStatusError("error", request=MagicMock(), response=resp)
    else:
        resp.raise_for_status.return_value = None
    return resp


def _chat_body(content: str) -> dict:
    """Ollama /api/chat response shape."""
    return {"message": {"role": "assistant", "content": content}, "done": True}


def _tags_body(*model_names: str) -> dict:
    return {"models": [{"name": n, "model": n} for n in model_names]}


def _mock_client(response=None, side_effect=None):
    client = AsyncMock()
    if side_effect is not None:
        client.post.side_effect = side_effect
        client.get.side_effect = side_effect
    else:
        client.post.return_value = response
        client.get.return_value = response
    client.__aenter__.return_value = client
    client.__aexit__.return_value = False
    return client


def _mock_stream_client(lines: list[str] | None = None, side_effect=None):
    """Mocks httpx.AsyncClient().stream(...) — a sync method returning an
    async context manager whose response yields NDJSON lines via
    aiter_lines(), the shape stream_generate() actually consumes."""
    client = AsyncMock()
    client.__aenter__.return_value = client
    client.__aexit__.return_value = False
    if side_effect is not None:
        client.stream = MagicMock(side_effect=side_effect)
        return client

    response = MagicMock()
    response.raise_for_status.return_value = None

    async def _aiter_lines():
        for line in lines or []:
            yield line

    response.aiter_lines = _aiter_lines
    stream_cm = AsyncMock()
    stream_cm.__aenter__.return_value = response
    stream_cm.__aexit__.return_value = False
    client.stream = MagicMock(return_value=stream_cm)
    return client


# ---------------------------------------------------------------- generation


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_successful_generate_returns_the_models_reply_and_timing(mock_ctor):
    mock_ctor.return_value = _mock_client(_response(200, _chat_body("Irrigate lightly this evening.")))

    result = _run(generate("some prompt"))

    assert result["reply"] == "Irrigate lightly this evening."
    assert isinstance(result["elapsed_ms"], int)


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_the_request_prefills_a_closed_think_block_and_caps_output(mock_ctor):
    """The 10x latency fix: without the prefilled closed <think> block a
    reasoning model spends almost all of its token budget (and, locally,
    minutes) reasoning before answering. Measured: 83.5s -> 8.0s."""
    client = _mock_client(_response(200, _chat_body("ok")))
    mock_ctor.return_value = client

    _run(generate("some prompt", system="sys"))

    payload = client.post.call_args.kwargs["json"]
    assert payload["messages"][-1] == {"role": "assistant", "content": PREFILL_SKIP_THINKING}
    assert payload["messages"][0] == {"role": "system", "content": "sys"}
    assert payload["think"] is False
    assert payload["stream"] is False
    assert payload["options"]["num_predict"] == get_settings().local_llm_num_predict
    assert payload["options"]["temperature"] == get_settings().local_llm_temperature


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_it_posts_to_the_chat_endpoint_on_the_configured_base_url(mock_ctor):
    client = _mock_client(_response(200, _chat_body("ok")))
    mock_ctor.return_value = client

    _run(generate("some prompt"))

    assert client.post.call_args.args[0] == f"{get_settings().local_llm_url}/api/chat"


@pytest.mark.parametrize(
    "raw,expected",
    [
        ("<think>\n\n</think>\n\nThe soil is dry.", "The soil is dry."),
        ("<think>long reasoning here</think>Answer text", "Answer text"),
        ("<think>unclosed reasoning\n</think> Final answer", "Final answer"),
        ("No thinking at all.", "No thinking at all."),
    ],
)
@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_reasoning_scaffolding_is_stripped_so_the_farmer_never_sees_it(mock_ctor, raw, expected):
    mock_ctor.return_value = _mock_client(_response(200, _chat_body(raw)))

    assert _run(generate("some prompt"))["reply"] == expected


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_a_reply_that_is_only_reasoning_is_never_presented_as_an_answer(mock_ctor):
    mock_ctor.return_value = _mock_client(_response(200, _chat_body("<think>only reasoning, no answer</think>")))

    with pytest.raises(LocalLlmError, match="empty response"):
        _run(generate("some prompt"))


def test_unsupported_provider_is_rejected_without_any_network_call(monkeypatch):
    monkeypatch.setattr(get_settings(), "local_llm_provider", "openai")
    with pytest.raises(LocalLlmError):
        _run(generate("some prompt"))


def test_missing_url_is_rejected_without_any_network_call(monkeypatch):
    monkeypatch.setattr(get_settings(), "local_llm_url", "")
    with pytest.raises(LocalLlmError):
        _run(generate("some prompt"))


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_connection_failure_is_honestly_unavailable(mock_ctor):
    mock_ctor.return_value = _mock_client(side_effect=httpx.ConnectError("connection refused"))

    with pytest.raises(LocalLlmError):
        _run(generate("some prompt"))


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_generation_timeout_is_reported_as_a_timeout_not_a_fabricated_reply(mock_ctor):
    mock_ctor.return_value = _mock_client(side_effect=httpx.ReadTimeout("too slow"))

    with pytest.raises(LocalLlmError, match="too long"):
        _run(generate("some prompt"))


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_malformed_response_is_honestly_unavailable(mock_ctor):
    mock_ctor.return_value = _mock_client(_response(200, {"unexpected": "shape"}))

    with pytest.raises(LocalLlmError):
        _run(generate("some prompt"))


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_non_json_response_is_honestly_unavailable(mock_ctor):
    bad = MagicMock()
    bad.status_code = 200
    bad.raise_for_status.return_value = None
    bad.json.side_effect = ValueError("not json")
    mock_ctor.return_value = _mock_client(bad)

    with pytest.raises(LocalLlmError):
        _run(generate("some prompt"))


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_empty_reply_is_never_treated_as_success(mock_ctor):
    mock_ctor.return_value = _mock_client(_response(200, _chat_body("   ")))

    with pytest.raises(LocalLlmError):
        _run(generate("some prompt"))


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_no_secret_or_cloud_endpoint_is_ever_contacted(mock_ctor):
    """Local-first guarantee: the only host this service talks to is the
    configured local model server. Nothing cloud, no credentials."""
    client = _mock_client(_response(200, _chat_body("ok")))
    mock_ctor.return_value = client

    _run(generate("some prompt"))

    url = client.post.call_args.args[0]
    assert url.startswith(get_settings().local_llm_url)
    for banned in ("openai.com", "googleapis.com", "anthropic.com", "generativelanguage"):
        assert banned not in url
    assert "Authorization" not in (mock_ctor.call_args.kwargs.get("headers") or {})


# ----------------------------------------------------------------- streaming


async def _collect(agen):
    return [item async for item in agen]


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_stream_generate_yields_deltas_as_ollama_sends_them(mock_ctor):
    mock_ctor.return_value = _mock_stream_client(
        lines=[
            '{"message": {"content": "Spray "}, "done": false}',
            '{"message": {"content": "fungicide."}, "done": false}',
            '{"message": {"content": ""}, "done": true}',
        ],
    )

    deltas = _run(_collect(stream_generate("some prompt")))

    assert deltas == ["Spray ", "fungicide."]


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_stream_generate_stops_reading_once_done_is_true(mock_ctor):
    """Ollama can send a trailing stats-only line after done:true — must not
    be treated as more content."""
    mock_ctor.return_value = _mock_stream_client(
        lines=[
            '{"message": {"content": "ok"}, "done": true}',
            '{"message": {"content": "should not appear"}, "done": false}',
        ],
    )

    assert _run(_collect(stream_generate("some prompt"))) == ["ok"]


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_stream_generate_also_prefills_a_closed_think_block(mock_ctor):
    """Same 10x latency fix as generate() — streaming must not lose it."""
    client = _mock_stream_client(lines=['{"message": {"content": "ok"}, "done": true}'])
    mock_ctor.return_value = client

    _run(_collect(stream_generate("some prompt", system="sys")))

    payload = client.stream.call_args.kwargs["json"]
    assert payload["messages"][-1] == {"role": "assistant", "content": PREFILL_SKIP_THINKING}
    assert payload["stream"] is True
    assert payload["think"] is False


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_stream_generate_connection_failure_is_honestly_unavailable(mock_ctor):
    mock_ctor.return_value = _mock_stream_client(side_effect=httpx.ConnectError("connection refused"))

    with pytest.raises(LocalLlmError):
        _run(_collect(stream_generate("some prompt")))


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_stream_generate_ignores_unparseable_lines(mock_ctor):
    """A stray blank line or non-JSON keepalive must never crash the stream."""
    mock_ctor.return_value = _mock_stream_client(
        lines=["", "not json", '{"message": {"content": "ok"}, "done": true}'],
    )

    assert _run(_collect(stream_generate("some prompt"))) == ["ok"]


def test_stream_generate_missing_url_is_rejected_without_any_network_call(monkeypatch):
    monkeypatch.setattr(get_settings(), "local_llm_url", "")
    with pytest.raises(LocalLlmError):
        _run(_collect(stream_generate("some prompt")))


# The prefill echo bug: Ollama echoes PREFILL_SKIP_THINKING back verbatim as
# the model's first streamed output (measured live). Without filtering, a
# farmer would see the literal text "<think>\n\n</think>" flash on screen
# before the real answer — invisible in the non-streaming path only because
# _strip_thinking() cleans the complete text before it's ever shown.


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_stream_generate_never_yields_the_prefill_echo(mock_ctor):
    mock_ctor.return_value = _mock_stream_client(
        lines=[
            '{"message": {"content": "<think>\\n\\n</think>\\n\\nYour"}, "done": false}',
            '{"message": {"content": " soil is dry."}, "done": true}',
        ],
    )

    deltas = _run(_collect(stream_generate("some prompt")))

    assert "".join(deltas) == "Your soil is dry."
    assert not any("<think>" in d for d in deltas)


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_stream_generate_filters_a_leading_think_block_split_across_many_chunks(mock_ctor):
    """The close tag can arrive several chunks after the open tag — the
    filter must keep buffering across chunk boundaries, not just handle the
    single-chunk case."""
    mock_ctor.return_value = _mock_stream_client(
        lines=[
            '{"message": {"content": "<think>"}, "done": false}',
            '{"message": {"content": "reasoning I should never see "}, "done": false}',
            '{"message": {"content": "more reasoning</think>"}, "done": false}',
            '{"message": {"content": "The real answer."}, "done": true}',
        ],
    )

    deltas = _run(_collect(stream_generate("some prompt")))

    assert "".join(deltas) == "The real answer."


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_stream_generate_does_not_delay_a_reply_with_no_think_block_at_all(mock_ctor):
    """The common case for a non-reasoning model (or when think:false fully
    suppresses it) — must flush immediately, not wait around for a close
    tag that will never come."""
    mock_ctor.return_value = _mock_stream_client(
        lines=[
            '{"message": {"content": "Spray "}, "done": false}',
            '{"message": {"content": "fungicide."}, "done": true}',
        ],
    )

    deltas = _run(_collect(stream_generate("some prompt")))

    assert deltas == ["Spray ", "fungicide."]


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_stream_generate_does_not_prematurely_flush_a_partial_think_tag_prefix(mock_ctor):
    """"<thi" alone is still an ambiguous prefix of "<think>" — must not be
    mistaken for "definitely not a think block" and flushed early."""
    mock_ctor.return_value = _mock_stream_client(
        lines=[
            '{"message": {"content": "<thi"}, "done": false}',
            '{"message": {"content": "nk>hidden</think>Real answer."}, "done": true}',
        ],
    )

    deltas = _run(_collect(stream_generate("some prompt")))

    assert "".join(deltas) == "Real answer."


# ------------------------------------------------------------- multilingual


@pytest.mark.parametrize("language", ["hi", "mr"])
@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_a_devanagari_language_answered_in_devanagari_is_accepted(mock_ctor, language):
    mock_ctor.return_value = _mock_client(_response(200, _chat_body("आपकी मिट्टी में नमी कम है।")))

    assert _run(generate("p", language=language))["reply"] == "आपकी मिट्टी में नमी कम है।"


@pytest.mark.parametrize("language", ["hi", "mr"])
@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_a_devanagari_language_answered_in_english_falls_back_honestly(mock_ctor, language):
    """A small local model can ignore the requested language entirely. Showing
    the farmer an answer in a language they did not ask for is worse than
    saying the request failed."""
    mock_ctor.return_value = _mock_client(
        _response(200, _chat_body("Your soil moisture is low and rain is expected today.")),
    )

    with pytest.raises(LocalLlmError, match="selected language"):
        _run(generate("p", language=language))


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_english_is_never_rejected_by_the_language_check(mock_ctor):
    mock_ctor.return_value = _mock_client(_response(200, _chat_body("Your soil moisture is low.")))

    assert _run(generate("p", language="en"))["reply"] == "Your soil moisture is low."


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_generate_uses_the_explicit_model_argument_over_the_configured_default(mock_ctor):
    """Phase 4: callers pick the model per-language via
    resolve_model_for_language — generate() must use exactly what it's given,
    not silently substitute settings.local_llm_model."""
    client = _mock_client(_response(200, _chat_body("ok")))
    mock_ctor.return_value = client

    _run(generate("p", model="qwen2.5:3b"))

    assert client.post.call_args.kwargs["json"]["model"] == "qwen2.5:3b"


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_generate_falls_back_to_the_configured_model_when_none_is_given(mock_ctor):
    """Existing single-model callers (and the deep status check) don't pass
    model= at all — must behave exactly as before Phase 4."""
    client = _mock_client(_response(200, _chat_body("ok")))
    mock_ctor.return_value = client

    _run(generate("p"))

    assert client.post.call_args.kwargs["json"]["model"] == get_settings().local_llm_model


# ---------------------------------------------------------- meta-commentary


@pytest.mark.parametrize(
    "bad_reply",
    [
        "मराठी भाषेत: फफूंदनाशक का छिड़काव करें।",
        "योग्य उत्तर: सिंचन बढ़ाएं।",
        "पीक: टोमॅटो\nउत्तर: फवारणी करा आणि सिंचन वाढवा.",
        # Enough Devanagari to pass _honors_language on its own, but still
        # opens with English meta-commentary ("as an ai language model") —
        # the language-script gate alone would let this through.
        "as an ai language model, मैं आपको बता सकता हूं: फफूंदनाशक का छिड़काव करें और सिंचन बढ़ाएं।",
    ],
)
@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_meta_commentary_is_rejected_even_when_the_script_is_correct(mock_ctor, bad_reply):
    """The concrete failure mode measured against a small model: it answers
    in the right script (passes _honors_language) but labels its own output
    instead of just giving the advice — a farmer reading that gets a
    confusing half-answer, so it must be treated as no answer at all."""
    mock_ctor.return_value = _mock_client(_response(200, _chat_body(bad_reply)))

    with pytest.raises(LocalLlmError, match="meta-commentary"):
        _run(generate("p", language="hi"))


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_a_natural_answer_that_happens_to_contain_the_word_उत्तर_mid_sentence_is_not_rejected(mock_ctor):
    """Only a *labelling* use of उत्तर ("Answer:") is meta-commentary — the
    word appearing naturally inside a sentence must not be penalised."""
    mock_ctor.return_value = _mock_client(
        _response(200, _chat_body("किसान के हर सवाल का उत्तर देना हमारा काम है, इसलिए फफूंदनाशक छिड़कें।")),
    )

    assert "उत्तर" in _run(generate("p", language="hi"))["reply"]


# ------------------------------------------------------------------- status


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_model_available_when_the_configured_model_is_installed(mock_ctor):
    mock_ctor.return_value = _mock_client(
        _response(200, _tags_body(get_settings().local_llm_model, get_settings().local_llm_model_hi)),
    )

    result = _run(check_status())
    assert result["status"] == STATUS_MODEL_AVAILABLE
    assert result["ollama_running"] is True
    assert result["model_availability"]["en"] is True


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_model_missing_when_server_is_up_but_model_is_not_pulled(mock_ctor):
    """Must never read as available: Ollama is fine, but generation would
    fail — and the fix (pull the model) is different from starting Ollama."""
    mock_ctor.return_value = _mock_client(_response(200, _tags_body("some-other-model:1b")))

    result = _run(check_status())
    assert result["status"] == STATUS_MODEL_MISSING
    assert result["ollama_running"] is True
    assert result["model_availability"] == {"en": False, "hi": False, "mr": False}


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_reports_hindi_and_marathi_availability_independently_of_english(mock_ctor, monkeypatch):
    """Phase 4: English/Hindi/Marathi can each be a different model, so one
    being missing must never be hidden by the others being present."""
    monkeypatch.setattr(get_settings(), "local_llm_model_hi", "qwen2.5:3b")
    monkeypatch.setattr(get_settings(), "local_llm_model_mr", "qwen2.5:3b")
    mock_ctor.return_value = _mock_client(_response(200, _tags_body(get_settings().local_llm_model)))

    result = _run(check_status())
    assert result["model_availability"] == {"en": True, "hi": False, "mr": False}


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_model_missing_when_no_models_are_installed(mock_ctor):
    mock_ctor.return_value = _mock_client(_response(200, {"models": []}))

    assert _run(check_status())["status"] == STATUS_MODEL_MISSING


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_matches_a_bare_model_name_against_its_tagged_form(mock_ctor, monkeypatch):
    monkeypatch.setattr(get_settings(), "local_llm_model", "deepseek-r1")
    mock_ctor.return_value = _mock_client(_response(200, _tags_body("deepseek-r1:7b")))

    assert _run(check_status())["status"] == STATUS_MODEL_AVAILABLE


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_ollama_unreachable_when_connection_fails(mock_ctor):
    mock_ctor.return_value = _mock_client(side_effect=httpx.ConnectError("connection refused"))

    result = _run(check_status())
    assert result["status"] == STATUS_OLLAMA_UNREACHABLE
    assert result["ollama_running"] is False
    assert result["model_availability"] == {"en": False, "hi": False, "mr": False}


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_ollama_unreachable_on_timeout(mock_ctor):
    mock_ctor.return_value = _mock_client(side_effect=httpx.ReadTimeout("too slow"))

    assert _run(check_status())["status"] == STATUS_OLLAMA_UNREACHABLE


def test_check_status_ollama_unreachable_when_provider_misconfigured(monkeypatch):
    monkeypatch.setattr(get_settings(), "local_llm_provider", "openai")

    result = _run(check_status())
    assert result["status"] == STATUS_OLLAMA_UNREACHABLE
    assert result["ollama_running"] is False


# --------------------------------------------------------- deep status check


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_shallow_never_triggers_a_real_generation_call(mock_ctor):
    """The default (deep=False) status check must cost zero inference time —
    only ever GET /api/tags, never POST /api/chat."""
    client = _mock_client(_response(200, _tags_body(get_settings().local_llm_model)))
    mock_ctor.return_value = client

    _run(check_status(deep=False))

    client.post.assert_not_called()
    client.get.assert_called_once()


def _mock_client_with(get_response=None, post_response=None) -> AsyncMock:
    """Unlike `_mock_client`, sets GET and POST independently — needed for
    the deep status check, which calls GET /api/tags then POST /api/chat in
    the same request."""
    client = AsyncMock()
    client.get.return_value = get_response
    client.post.return_value = post_response
    client.__aenter__.return_value = client
    client.__aexit__.return_value = False
    return client


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_deep_reports_generation_working_when_a_real_answer_comes_back(mock_ctor):
    mock_ctor.return_value = _mock_client_with(
        get_response=_response(200, _tags_body(get_settings().local_llm_model)),
        post_response=_response(200, _chat_body("OK")),
    )

    result = _run(check_status(deep=True))
    assert result["status"] == STATUS_GENERATION_WORKING
    assert result["ollama_running"] is True


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_deep_reports_generation_failed_when_a_listed_model_cannot_generate(mock_ctor):
    """A model can be listed by /api/tags yet fail to actually generate
    (corrupt weights, incompatible Ollama version, out of memory) — the deep
    check is exactly what catches that gap between "installed" and "working"."""
    mock_ctor.return_value = _mock_client_with(
        get_response=_response(200, _tags_body(get_settings().local_llm_model)),
        post_response=_response(200, {"unexpected": "shape"}),
    )

    result = _run(check_status(deep=True))
    assert result["status"] == STATUS_GENERATION_FAILED
    assert result["ollama_running"] is True


@patch("app.services.local_llm_service.httpx.AsyncClient")
def test_check_status_deep_is_never_attempted_when_the_model_is_missing(mock_ctor):
    """No point spending inference time proving generation works for a model
    that isn't even installed — MODEL_MISSING takes precedence over deep."""
    client = _mock_client(_response(200, _tags_body("some-other-model:1b")))
    mock_ctor.return_value = client

    result = _run(check_status(deep=True))

    assert result["status"] == STATUS_MODEL_MISSING
    client.post.assert_not_called()
