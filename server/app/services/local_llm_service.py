"""Local LLM integration — talks to a self-hosted, Ollama-compatible
inference server (default http://127.0.0.1:11434), never a cloud LLM. This
is the "conversation" layer in the architecture:

    Facts (sensors/weather/market/DecisionEngine) -> Local LLM -> plain
    explanation

The LLM never computes risk, fertilizer, pest/disease, weather, or market
facts itself — see the system prompt built in app/routers/local_llm.py,
which explicitly instructs it to use only the facts it is given. Every
failure (model not installed, server not running, timeout, malformed
response) raises LocalLlmError, which the router turns into an honest
503 "unavailable" — never a fabricated explanation.

=== Why this uses /api/chat with a prefilled assistant turn ===

The configured model may be a *reasoning* model (deepseek-r1 and friends),
which emits a long <think> monologue before its actual answer. Measured on
the reference dev machine (CPU-only, deepseek-r1:7b, ~5 tokens/second):

    plain /api/generate      83.5s for a 2-sentence answer, and it was
                             still truncated — 368 of 400 generated tokens
                             were <think> tokens the farmer never sees
    /api/chat + prefilled    8.0s for the same answer, finished naturally
    closed <think> block     (done_reason=stop)

Prefilling the assistant turn with an already-closed, empty think block
makes the model treat reasoning as done and answer immediately. That is the
entire reason the Local LLM went from unusable to usable, so don't "simplify"
PREFILL_SKIP_THINKING away. It is harmless for non-reasoning models, which
just continue from an empty assistant prefix.

_strip_thinking() then defensively removes any <think> block that still
appears (the prefix is echoed back, and a model may open a fresh one).
"""

import json
import re
import time

import httpx

from app.config import get_settings

STATUS_OLLAMA_UNREACHABLE = "OLLAMA_UNREACHABLE"
STATUS_MODEL_MISSING = "MODEL_MISSING"
STATUS_MODEL_AVAILABLE = "MODEL_AVAILABLE"
STATUS_GENERATION_WORKING = "GENERATION_WORKING"
STATUS_GENERATION_FAILED = "GENERATION_FAILED"

# See the module docstring — this is the 10x latency fix, not boilerplate.
PREFILL_SKIP_THINKING = "<think>\n\n</think>\n\n"

_THINK_BLOCK = re.compile(r"<think>.*?</think>", re.DOTALL)
_UNCLOSED_THINK_TAIL = re.compile(r"^.*?</think>", re.DOTALL)

# Scripts the app's non-English languages are written in. Used only to detect
# that the model ignored the requested language entirely — never to judge
# whether the wording is *good*, which no cheap check can do.
_DEVANAGARI = re.compile(r"[ऀ-ॿ]")
_DEVANAGARI_LANGUAGES = {"hi", "mr"}

# Concrete meta-commentary patterns actually observed from a small model asked
# to answer in Hindi/Marathi (see LOCAL_LLM_IMPLEMENTATION_REPORT.md): instead
# of just answering, it labels its own output ("मराठी भाषेत:", "योग्य उत्तर:")
# or lapses into English scaffolding. This is deliberately NOT a general
# quality judge — it only catches these specific self-referential patterns,
# which a Devanagari-script check alone cannot.
_META_COMMENTARY_PATTERNS = [
    re.compile(r"(?i)\b(as an ai|language model|system prompt|internal reasoning)\b"),
    re.compile(r"(हिंदी में|मराठी भाषेत|मराठीत|इंग्रजीत|अंग्रेज़ी में|हिन्दी भाषा में)\s*[:：]"),
    re.compile(r"(?i)\b(here is the translation|translated (?:answer|response|text))\b"),
    # Bare "उत्तर:" ("Answer:") starting the reply or any line within it — a
    # farmer explaining something out loud never labels their own sentence
    # this way; a model doing it is describing its output, not giving it.
    re.compile(r"^\s*(योग्य |सही |अंतिम )?उत्तर\s*[:：]", re.MULTILINE),
    # Echoing the app's own "Field: value" fact-list format back verbatim
    # (e.g. "पीक: टोमॅटो") instead of speaking naturally — a real answer
    # never repeats the input's labelled structure.
    re.compile(r"^\s*(पीक|फसल|भाषा|Language|Crop)\s*[:：]", re.MULTILINE),
]


def _has_meta_commentary(reply: str) -> bool:
    """True if the reply is labelling/describing itself rather than just
    answering — see _META_COMMENTARY_PATTERNS. A response can pass the
    Devanagari-script check in _honors_language and still fail this."""
    return any(pattern.search(reply) for pattern in _META_COMMENTARY_PATTERNS)


class LocalLlmError(Exception):
    pass


def _chat_endpoint(base_url: str) -> str:
    return f"{base_url.rstrip('/')}/api/chat"


def _tags_endpoint(base_url: str) -> str:
    return f"{base_url.rstrip('/')}/api/tags"


def _strip_thinking(text: str) -> str:
    """Removes reasoning-model scaffolding so a farmer never sees it."""
    cleaned = _THINK_BLOCK.sub("", text)
    if "</think>" in cleaned:
        # An unclosed/echoed opener with a stray close — keep only what follows.
        cleaned = _UNCLOSED_THINK_TAIL.sub("", cleaned)
    return cleaned.replace("<think>", "").strip()


def _honors_language(reply: str, language: str) -> bool:
    """True unless the farmer asked for a Devanagari language and the model
    answered with essentially no Devanagari at all. A small local model can
    ignore the requested language entirely; showing that to the farmer is
    worse than honestly reporting the request failed. This deliberately does
    NOT try to assess wording quality — see LOCAL_LLM_IMPLEMENTATION_REPORT.md
    for the measured quality limits of small reasoning models on Hindi/Marathi.
    """
    if language not in _DEVANAGARI_LANGUAGES:
        return True
    return len(_DEVANAGARI.findall(reply)) >= max(5, len(reply) // 20)


def resolve_model_for_language(settings, language: str) -> str:
    """The one place model choice is decided — router.chat() and the deep
    status check both call this rather than reading settings fields directly,
    so model selection is never duplicated/hardcoded in more than one place.
    Falls back to local_llm_model (the English/default model) whenever a
    per-language override is unset, so a single-model setup keeps working."""
    if language == "hi":
        return settings.local_llm_model_hi or settings.local_llm_model
    if language == "mr":
        return settings.local_llm_model_mr or settings.local_llm_model
    return settings.local_llm_model


def _timeout(settings) -> httpx.Timeout:
    """Separate connect/read budgets: a local server either accepts the
    connection right away or isn't there, while generation legitimately takes
    tens of seconds (cold model load alone measured ~13s). One flat timeout
    would either kill real generation or hang for a minute on a dead port.
    """
    return httpx.Timeout(
        connect=settings.local_llm_connect_timeout_seconds,
        read=settings.local_llm_read_timeout_seconds,
        write=settings.local_llm_connect_timeout_seconds,
        pool=settings.local_llm_connect_timeout_seconds,
    )


def _chat_messages(prompt: str, system: str | None) -> list[dict]:
    messages: list[dict] = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})
    messages.append({"role": "assistant", "content": PREFILL_SKIP_THINKING})
    return messages


def _finalize_reply(raw_reply: str, language: str) -> str:
    """Shared end-of-generation validation for both generate() and
    stream_generate() — one place a reply can be rejected, so streaming can
    never be "less honest" than the non-streaming path just because it also
    has to look right progressively on screen."""
    reply = _strip_thinking(raw_reply)
    if not reply:
        raise LocalLlmError("Local LLM returned an empty response.")
    if not _honors_language(reply, language):
        raise LocalLlmError("The local AI model could not answer in the selected language.")
    if _has_meta_commentary(reply):
        raise LocalLlmError("The local AI model returned meta-commentary instead of an answer.")
    return reply


_THINK_OPEN = "<think>"
_THINK_CLOSE = "</think>"
# Generous cap on how long to wait for a closing tag before giving up and
# showing what's buffered anyway — num_predict already bounds total output,
# so this only matters if a model opens <think> and truly never closes it.
_MAX_THINK_BUFFER_CHARS = 4000


async def _without_leading_think_block(deltas):
    """Wraps a raw delta stream so a caller never sees the prefill echo
    (PREFILL_SKIP_THINKING is echoed back verbatim as the model's first
    output — confirmed by measurement, see module docstring) or any other
    leading <think>...</think> block. The non-streaming generate() gets this
    for free from _strip_thinking() running on the complete text; streaming
    needs the equivalent done incrementally so the live "typing" UI never
    flashes raw reasoning-scaffolding tags. Only a LEADING think block is
    filtered — once real content starts, everything after is forwarded
    untouched even if "<think>" later appears as ordinary text (matches
    _strip_thinking()'s own defensive-but-not-paranoid scope).
    """
    buffer = ""
    resolved = False
    async for delta in deltas:
        if resolved:
            yield delta
            continue
        buffer += delta
        stripped = buffer.lstrip()
        if stripped.startswith(_THINK_OPEN):
            close_idx = buffer.find(_THINK_CLOSE)
            if close_idx != -1:
                resolved = True
                # lstrip: the prefill echo is followed by a couple of blank
                # lines before real content — a one-time cleanup at this
                # transition point, not general per-delta trimming.
                remainder = buffer[close_idx + len(_THINK_CLOSE) :].lstrip()
                if remainder:
                    yield remainder
            elif len(buffer) > _MAX_THINK_BUFFER_CHARS:
                resolved = True
                yield buffer.replace(_THINK_OPEN, "")
            # else: still buffering, waiting for the close tag.
        elif stripped and _THINK_OPEN.startswith(stripped):
            pass  # buffer could still become "<think>" with more characters — keep waiting.
        else:
            resolved = True
            yield buffer
    if not resolved and buffer:
        # Stream ended mid-buffer (e.g. a very short reply) without ever
        # resolving — show what's left rather than silently dropping it.
        yield buffer.replace(_THINK_OPEN, "")


async def stream_generate(
    prompt: str,
    model: str | None = None,
    system: str | None = None,
):
    """Yields clean text deltas from Ollama AS THEY ARRIVE (`stream: true`)
    — for a progressive "Thinking..." -> live-typing UI (Part 1). Never
    yields raw <think> scaffolding (see _without_leading_think_block).
    Deltas are otherwise NOT validated (a meta-commentary phrase can
    straddle two chunks) — the caller must accumulate every yielded delta
    and pass the joined text through _finalize_reply() once the stream
    ends, exactly like generate() does internally. Raises LocalLlmError for
    connection/timeout failures, same as generate().
    """
    async for delta in _without_leading_think_block(_stream_raw(prompt, model, system)):
        yield delta


async def _stream_raw(prompt: str, model: str | None, system: str | None):
    """The actual Ollama-talking half of stream_generate — separated so the
    think-block filter above can wrap it without duplicating the HTTP/error
    handling code."""
    settings = get_settings()
    if settings.local_llm_provider != "ollama":
        raise LocalLlmError(f"Unsupported LOCAL_LLM_PROVIDER '{settings.local_llm_provider}'.")
    resolved_model = model or settings.local_llm_model
    if not settings.local_llm_url or not resolved_model:
        raise LocalLlmError("LOCAL_LLM_URL / LOCAL_LLM_MODEL is not configured.")

    payload = {
        "model": resolved_model,
        "messages": _chat_messages(prompt, system),
        "stream": True,
        "think": False,
        "options": {
            "num_predict": settings.local_llm_num_predict,
            "temperature": settings.local_llm_temperature,
        },
    }

    try:
        async with httpx.AsyncClient(timeout=_timeout(settings)) as client:
            async with client.stream("POST", _chat_endpoint(settings.local_llm_url), json=payload) as response:
                response.raise_for_status()
                async for line in response.aiter_lines():
                    if not line.strip():
                        continue
                    try:
                        data = json.loads(line)
                    except ValueError:
                        continue
                    content = data.get("message", {}).get("content")
                    if content:
                        yield content
                    if data.get("done"):
                        break
    except httpx.TimeoutException as exc:
        raise LocalLlmError(
            "The local AI model took too long to answer "
            f"(over {settings.local_llm_read_timeout_seconds}s)."
        ) from exc
    except httpx.HTTPError as exc:
        raise LocalLlmError(f"Local LLM request failed: {type(exc).__name__}") from exc


async def generate(
    prompt: str,
    model: str | None = None,
    system: str | None = None,
    language: str = "en",
) -> dict:
    """Returns {"reply": str, "elapsed_ms": int} — never a fabricated reply.

    [model] defaults to settings.local_llm_model when omitted (existing
    single-model behavior, unchanged); callers that need per-language model
    selection pass resolve_model_for_language(settings, language) explicitly
    — see app/routers/local_llm.py.

    Raises LocalLlmError for every failure mode (provider misconfigured,
    server unreachable, timeout, malformed/empty response, the model
    ignoring the requested language, or the model producing meta-commentary
    instead of an answer).
    """
    settings = get_settings()
    if settings.local_llm_provider != "ollama":
        raise LocalLlmError(f"Unsupported LOCAL_LLM_PROVIDER '{settings.local_llm_provider}'.")
    resolved_model = model or settings.local_llm_model
    if not settings.local_llm_url or not resolved_model:
        raise LocalLlmError("LOCAL_LLM_URL / LOCAL_LLM_MODEL is not configured.")

    messages: list[dict] = []
    if system:
        messages.append({"role": "system", "content": system})
    messages.append({"role": "user", "content": prompt})
    messages.append({"role": "assistant", "content": PREFILL_SKIP_THINKING})

    payload = {
        "model": resolved_model,
        "messages": messages,
        "stream": False,
        # Reasoning models: don't route tokens into a separate thinking phase.
        "think": False,
        "options": {
            # Bounds worst-case latency. A farmer-facing explanation is a few
            # sentences; without a cap a reasoning model will happily spend
            # hundreds of tokens (and, at local speeds, minutes) on one answer.
            "num_predict": settings.local_llm_num_predict,
            # Low, because this layer explains already-computed facts rather
            # than inventing anything.
            "temperature": settings.local_llm_temperature,
        },
    }

    started = time.monotonic()
    try:
        async with httpx.AsyncClient(timeout=_timeout(settings)) as client:
            response = await client.post(_chat_endpoint(settings.local_llm_url), json=payload)
            response.raise_for_status()
    except httpx.TimeoutException as exc:
        raise LocalLlmError(
            "The local AI model took too long to answer "
            f"(over {settings.local_llm_read_timeout_seconds}s)."
        ) from exc
    except httpx.HTTPError as exc:
        raise LocalLlmError(f"Local LLM request failed: {type(exc).__name__}") from exc
    elapsed_ms = int((time.monotonic() - started) * 1000)

    try:
        data = response.json()
        raw_reply = data["message"]["content"]
    except (ValueError, KeyError, TypeError) as exc:
        raise LocalLlmError(f"Malformed local LLM response: {type(exc).__name__}") from exc

    if not isinstance(raw_reply, str):
        raise LocalLlmError("Local LLM returned a non-text response.")

    return {"reply": _finalize_reply(raw_reply, language), "elapsed_ms": elapsed_ms}


async def _installed_models(base_url: str, timeout: httpx.Timeout) -> list[str] | None:
    """Model names currently pulled into the local server, or None if the
    server itself couldn't be reached."""
    try:
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.get(_tags_endpoint(base_url))
            response.raise_for_status()
            body = response.json()
    except (httpx.HTTPError, ValueError):
        return None
    models = body.get("models")
    if not isinstance(models, list):
        return []
    return [str(m.get("model") or m.get("name") or "") for m in models if isinstance(m, dict)]


def _model_installed(wanted: str, installed: list[str]) -> bool:
    # Ollama reports "name:tag"; treat a bare configured name as matching its
    # default tag so "deepseek-r1" still matches "deepseek-r1:7b".
    return any(name == wanted or name.split(":")[0] == wanted.split(":")[0] for name in installed)


async def check_status(deep: bool = False) -> dict:
    """Returns {"status": str, "ollama_running": bool, "model_availability":
    {"en": bool, "hi": bool, "mr": bool}}.

    The shallow path (default, `deep=False`) is a lightweight reachability
    probe (GET /api/tags) — never a full generation call, so an ordinary
    status poll (e.g. every time the chat screen opens) costs no real
    inference time. It distinguishes "Ollama unreachable" from "Ollama
    reachable but the configured model isn't pulled", because those need
    different fixes and must never both read as available.

    model_availability is reported per-language (en/hi/mr each resolve to
    their own configured model — see resolve_model_for_language) from that
    same /api/tags call, at no extra cost, so the diagnostics panel can show
    "Hindi model: MISSING" distinctly from "English model: AVAILABLE" instead
    of one combined model flag.

    `status`/top-level model-missing detection stays keyed to the English/
    default model, matching this function's pre-existing behavior, so an
    English-only setup (just deepseek-r1:7b pulled) is unaffected.

    The deep path (`deep=True`) additionally performs one real, tiny
    generation (using the English/default model) to prove generation itself
    actually works, not just that a model is *listed* as installed (a listed
    model can still fail to generate — corrupt weights, an incompatible
    Ollama version, out of memory). This is the check a developer/tester
    should trigger deliberately, not one that runs on every ordinary status
    poll.
    """
    settings = get_settings()
    no_availability = {"en": False, "hi": False, "mr": False}
    if settings.local_llm_provider != "ollama" or not settings.local_llm_url:
        return {"status": STATUS_OLLAMA_UNREACHABLE, "ollama_running": False, "model_availability": no_availability}

    installed = await _installed_models(settings.local_llm_url, _timeout(settings))
    if installed is None:
        return {"status": STATUS_OLLAMA_UNREACHABLE, "ollama_running": False, "model_availability": no_availability}

    availability = {
        lang: _model_installed(resolve_model_for_language(settings, lang), installed) for lang in ("en", "hi", "mr")
    }

    if not availability["en"]:
        return {"status": STATUS_MODEL_MISSING, "ollama_running": True, "model_availability": availability}

    if not deep:
        return {"status": STATUS_MODEL_AVAILABLE, "ollama_running": True, "model_availability": availability}

    try:
        await generate(
            "Reply with exactly one word: OK",
            model=settings.local_llm_model,
            system="Reply with exactly the word requested, nothing else.",
        )
        return {"status": STATUS_GENERATION_WORKING, "ollama_running": True, "model_availability": availability}
    except LocalLlmError:
        return {"status": STATUS_GENERATION_FAILED, "ollama_running": True, "model_availability": availability}
