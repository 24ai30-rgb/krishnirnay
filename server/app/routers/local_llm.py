import json
import time
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import FileResponse, StreamingResponse

from app.config import get_settings
from app.core.security import verify_api_key
from app.schemas.local_llm import (
    LocalLlmChatRequest,
    LocalLlmChatResponse,
    LocalLlmStatusResponse,
)
from app.services.local_llm_service import (
    LocalLlmError,
    _finalize_reply,
    check_status,
    generate,
    resolve_model_for_language,
    stream_generate,
)

router = APIRouter(prefix="/v1/local-llm", tags=["local-llm"], dependencies=[Depends(verify_api_key)])

# Where the developer places the real on-device model file after obtaining
# it under Google's Gemma license (see models/on_device/README.md) — never
# fetched or bundled by this server/app on its own. Matches
# OnDeviceModelManager's expected file name on the Android side.
_ON_DEVICE_MODEL_PATH = Path("models/on_device/gemma3_1b_int4.task")

# Shared anti-fabrication contract, appended after the language-specific
# instructions below (order matters for a small model: what comes first is
# followed most reliably, so the "answer only in X, never mention X" rule
# leads).
_GROUNDING_RULES = (
    "You are KrishiNirnay's farm assistant. Facts about the farmer's field are "
    "already computed by the app's deterministic decision engine and given to "
    "you below — you explain them in simple, encouraging language. You must "
    "NEVER invent sensor readings, weather, market prices, pest or disease "
    "diagnoses, fertilizer or pesticide dosages, or government schemes beyond "
    "what is listed. If something needed isn't in the facts, say it is not "
    "available instead of guessing. Keep answers short and practical."
)

# Per-language system prompts (Phase 4). English keeps the original English
# grounding rules. Hindi/Marathi are written ENTIRELY in that language,
# including the anti-fabrication rule — measured on this app's actual
# 13-field fact list (not a short hand-written test prompt), appending the
# English _GROUNDING_RULES text after a Hindi/Marathi instruction still
# produced garbled, half-invented answers from the smaller Qwen models. A
# system prompt that is English throughout most of its length seems to pull
# the model back toward English-pattern generation even when told to answer
# in Hindi/Marathi at the very start; keeping the whole thing in the target
# language avoids that. See LOCAL_LLM_IMPLEMENTATION_REPORT.md.
_SYSTEM_PROMPTS = {
    "en": _GROUNDING_RULES + " Answer in clear, simple English suitable for an Indian farmer.",
    "hi": (
        "उत्तर केवल सरल और स्पष्ट हिंदी में दें। अंग्रेज़ी में fallback न करें। "
        "किसान को सीधे उपयोगी सलाह दें। अपनी भाषा, अनुवाद, या किसी भी meta "
        "टिप्पणी का उल्लेख न करें — सीधे उत्तर से शुरू करें। नीचे दिए गए तथ्यों के "
        "अलावा कुछ भी न बताएं — कोई नई सेंसर रीडिंग, मौसम, बाज़ार भाव, रोग, "
        "उर्वरक मात्रा या योजना का आविष्कार न करें। यदि कोई जानकारी सूची में नहीं "
        "है, तो कहें कि वह उपलब्ध नहीं है। उत्तर छोटा और व्यावहारिक रखें।"
    ),
    "mr": (
        "उत्तर फक्त सोप्या आणि स्पष्ट मराठी भाषेत द्या. इंग्रजीमध्ये fallback करू "
        "नका. शेतकऱ्याला थेट उपयोगी सल्ला द्या. तुमची भाषा, भाषांतर किंवा "
        "कोणत्याही meta टिप्पणीचा उल्लेख करू नका — थेट उत्तराने सुरुवात करा. "
        "खालील तथ्यांशिवाय काहीही सांगू नका — नवीन सेन्सर रीडिंग, हवामान, "
        "बाजारभाव, रोग, खतांचे प्रमाण किंवा योजना तयार करू नका. यादीत नसलेली "
        "माहिती \"उपलब्ध नाही\" असे सांगा. उत्तर लहान आणि व्यवहार्य ठेवा."
    ),
}

# A small local model follows a language *name* far more reliably than a
# two-letter code, and these are the app's three supported languages.
_LANGUAGE_NAMES = {"en": "English", "hi": "Hindi (हिंदी)", "mr": "Marathi (मराठी)"}
_SUPPORTED_LANGUAGES = frozenset(_LANGUAGE_NAMES)


def _normalize_language(raw: str) -> str:
    """Any unrecognised/malformed value (a stray region tag like "hi-IN",
    wrong case, blank) safely falls back to English rather than reaching
    model-selection or the prompt with a code nothing recognises."""
    code = (raw or "").strip().lower()[:2]
    return code if code in _SUPPORTED_LANGUAGES else "en"

# Shown to the farmer (not just logged) when generation fails specifically
# because the model couldn't produce a usable answer *in the requested
# language* — see _farmer_facing_error. Every other failure (Ollama down,
# model not pulled, timeout) keeps the raw LocalLlmError text, which Android
# never shows directly anyway (see LocalLlmRepositoryImpl.kt) but which stays
# useful in logs.
_LANGUAGE_UNAVAILABLE_MESSAGES = {
    "hi": "इस भाषा में स्थानीय AI उत्तर उपलब्ध नहीं है। कृपया थोड़ी देर बाद फिर प्रयास करें।",
    "mr": "या भाषेत स्थानिक AI उत्तर सध्या उपलब्ध नाही. कृपया थोड्या वेळाने पुन्हा प्रयत्न करा.",
}
_LANGUAGE_FAILURE_MARKERS = ("selected language", "meta-commentary")


def _farmer_facing_error(exc: LocalLlmError, language: str) -> str:
    message = str(exc)
    if any(marker in message for marker in _LANGUAGE_FAILURE_MARKERS):
        return _LANGUAGE_UNAVAILABLE_MESSAGES.get(language, message)
    return message


# Phase 4 finding: a small model (Qwen2.5 3B/7B, unlike the larger
# deepseek-r1:7b) tracks the language of the *whole* prompt, not just a
# trailing "answer in Hindi" instruction — an English-labelled fact list with
# a Hindi/Marathi instruction bolted on the end measurably produced garbled,
# half-English output (see LOCAL_LLM_IMPLEMENTATION_REPORT.md). So the fact
# labels themselves are localized too; only the underlying values (crop
# names, numbers, risk levels the rest of the app already computed) stay as
# given — this never translates or alters a fact, only how it's introduced.
_FACT_LABELS = {
    "en": {
        "location": "Location", "crop": "Crop", "stage": "stage", "farming_method": "Farming method",
        "soil": "Soil", "moisture": "moisture", "sensors": "Sensors", "temperature": "temperature",
        "humidity": "humidity", "weather": "Weather", "market": "Market", "risk": "Overall risk",
        "pest": "Pest", "disease": "Disease", "fertilizer": "Fertilizer", "recommendation": "Recommendation",
        "reasons": "Reasons", "unknown": "unknown", "not_available": "not available",
        "not_assessed": "not assessed", "none": "none", "farmer_asks": "The farmer asks",
        "instruction": (
            "Answer using ONLY the facts above, in the farmer's language ({lang}). "
            "If something needed isn't listed, say it isn't available. Reply in at most 2 short sentences."
        ),
    },
    "hi": {
        "location": "स्थान", "crop": "फसल", "stage": "चरण", "farming_method": "खेती की विधि",
        "soil": "मिट्टी", "moisture": "नमी", "sensors": "सेंसर", "temperature": "तापमान",
        "humidity": "आर्द्रता", "weather": "मौसम", "market": "बाज़ार", "risk": "कुल जोखिम",
        "pest": "कीट", "disease": "रोग", "fertilizer": "उर्वरक", "recommendation": "सिफारिश",
        "reasons": "कारण", "unknown": "अज्ञात", "not_available": "उपलब्ध नहीं",
        "not_assessed": "आकलन नहीं किया गया", "none": "कोई नहीं", "farmer_asks": "किसान पूछता है",
        "instruction": (
            "केवल ऊपर दिए गए तथ्यों का उपयोग करके हिंदी में उत्तर दें। यदि कोई जानकारी सूची में नहीं है, "
            "तो कहें कि वह उपलब्ध नहीं है। उत्तर अधिकतम 2 छोटे वाक्यों में दें।"
        ),
    },
    "mr": {
        "location": "स्थान", "crop": "पीक", "stage": "अवस्था", "farming_method": "शेतीची पद्धत",
        "soil": "माती", "moisture": "ओलावा", "sensors": "सेन्सर", "temperature": "तापमान",
        "humidity": "आर्द्रता", "weather": "हवामान", "market": "बाजार", "risk": "एकूण धोका",
        "pest": "कीड", "disease": "रोग", "fertilizer": "खत", "recommendation": "शिफारस",
        "reasons": "कारणे", "unknown": "अज्ञात", "not_available": "उपलब्ध नाही",
        "not_assessed": "मूल्यांकन केलेले नाही", "none": "काहीही नाही", "farmer_asks": "शेतकरी विचारतो",
        "instruction": (
            "फक्त वरील तथ्यांचा वापर करून मराठीत उत्तर द्या. यादीत नसलेली कोणतीही माहिती \"उपलब्ध नाही\" असे "
            "सांगा. उत्तर जास्तीत जास्त 2 लहान वाक्यांत द्या."
        ),
    },
}


def _build_prompt(request: LocalLlmChatRequest, language: str) -> str:
    c = request.context
    t = _FACT_LABELS[language]
    unknown, not_available, not_assessed = t["unknown"], t["not_available"], t["not_assessed"]
    facts = [
        f"{t['location']}: {c.farmer_district or '-'}, {c.farmer_state or '-'}",
        f"{t['crop']}: {c.crop or unknown} ({t['stage']}: {c.crop_stage or unknown})",
        f"{t['farming_method']}: {c.farming_method or unknown}",
        f"{t['soil']}: {c.soil_type or unknown}, {t['moisture']} "
        f"{c.soil_moisture_pct if c.soil_moisture_pct is not None else unknown}%",
        f"{t['sensors']}: {t['temperature']} {c.temperature_c if c.temperature_c is not None else unknown}C, "
        f"{t['humidity']} {c.humidity_pct if c.humidity_pct is not None else unknown}%",
        f"{t['weather']} ({c.weather_status or 'UNAVAILABLE'}): {c.weather_summary or not_available}",
        f"{t['market']} ({c.market_status or 'UNAVAILABLE'}): {c.market_summary or not_available}",
        f"{t['risk']}: {c.overall_risk or unknown}",
        f"{t['pest']}: {c.pest_summary or not_assessed}",
        f"{t['disease']}: {c.disease_summary or not_assessed}",
        f"{t['fertilizer']}: {c.fertilizer_summary or not_assessed}",
        f"{t['recommendation']}: {c.recommendation_summary or t['none']}",
    ]
    if c.reasons:
        facts.append(f"{t['reasons']}:\n" + "\n".join(f"- {r}" for r in c.reasons))

    instruction = t["instruction"].format(lang=_LANGUAGE_NAMES[language])
    return "\n".join(facts) + f'\n\n{t["farmer_asks"]}: "{request.message}"\n' + instruction


@router.post("/chat", response_model=LocalLlmChatResponse)
async def chat(request: LocalLlmChatRequest) -> LocalLlmChatResponse:
    """Always HTTP 200 with one stable JSON shape — see
    LocalLlmChatResponse's own docstring for why. `success` is the field a
    client checks, not the HTTP status code; this keeps Android's parsing to
    one data class instead of a success model plus a separately-shaped 503
    error body.

    Model AND system prompt are both chosen from the (normalized) requested
    language — see resolve_model_for_language and _SYSTEM_PROMPTS. Model
    selection never relies on the prompt text alone ("answer in Hindi") to
    make a Hindi-capable answer happen; it actually routes to a model
    verified to support that language (Phase 4).
    """
    settings = get_settings()
    language = _normalize_language(request.context.language)
    model = resolve_model_for_language(settings, language)
    prompt = _build_prompt(request, language)
    try:
        result = await generate(prompt, model=model, system=_SYSTEM_PROMPTS[language], language=language)
    except LocalLlmError as exc:
        return LocalLlmChatResponse(
            success=False,
            language=language,
            answer=None,
            model=model,
            error=_farmer_facing_error(exc, language),
        )
    return LocalLlmChatResponse(
        success=True,
        language=language,
        answer=result["reply"],
        model=model,
        error=None,
        elapsed_ms=result["elapsed_ms"],
    )


@router.post("/chat/stream")
async def chat_stream(request: LocalLlmChatRequest) -> StreamingResponse:
    """Newline-delimited JSON events so Android can show tokens as they
    generate ("Thinking..." -> live text) instead of waiting the full ~20-70s
    for a complete answer (Part 1 — the Local LLM must feel fast).

    Each line is one JSON object:
      - progress: {"delta": "next chunk of text"}
      - final:    {"done": true, "success": bool, "answer": str|null,
                   "error": str|null, "model": str, "language": str,
                   "elapsed_ms": int|null}

    The streamed deltas are shown to the farmer immediately for perceived
    speed, but they are NOT validated individually — language-honesty and
    meta-commentary checks only run once generation finishes, exactly like
    the non-streaming /chat endpoint (see _finalize_reply, shared by both).
    If that final check fails, `success` is false and `answer` is null in
    the final event; Android must then replace whatever partial text it
    displayed with the honest error state, never leave the unvalidated
    partial text on screen as if it were a real answer.
    """
    settings = get_settings()
    language = _normalize_language(request.context.language)
    model = resolve_model_for_language(settings, language)
    prompt = _build_prompt(request, language)

    async def event_stream():
        started = time.monotonic()
        accumulated = ""
        try:
            async for delta in stream_generate(prompt, model=model, system=_SYSTEM_PROMPTS[language]):
                accumulated += delta
                yield json.dumps({"delta": delta}, ensure_ascii=False) + "\n"
            reply = _finalize_reply(accumulated, language)
        except LocalLlmError as exc:
            yield json.dumps(
                {
                    "done": True,
                    "success": False,
                    "answer": None,
                    "error": _farmer_facing_error(exc, language),
                    "model": model,
                    "language": language,
                    "elapsed_ms": None,
                },
                ensure_ascii=False,
            ) + "\n"
            return
        elapsed_ms = int((time.monotonic() - started) * 1000)
        yield json.dumps(
            {
                "done": True,
                "success": True,
                "answer": reply,
                "error": None,
                "model": model,
                "language": language,
                "elapsed_ms": elapsed_ms,
            },
            ensure_ascii=False,
        ) + "\n"

    return StreamingResponse(event_stream(), media_type="application/x-ndjson")


@router.get("/on-device-model")
async def on_device_model() -> FileResponse:
    """Serves the on-device model file for [OnDeviceModelManager] to
    download — this server never fetches it from Hugging Face/Kaggle itself
    (that requires accepting Google's Gemma license, a one-time step for
    whoever runs this server, not something to automate). A real HTTP 404
    here (never a fabricated/empty body) means the developer hasn't placed
    the file yet — see models/on_device/README.md.
    """
    if not _ON_DEVICE_MODEL_PATH.is_file():
        raise HTTPException(
            status_code=404,
            detail="On-device model not staged on this server yet — see server/models/on_device/README.md.",
        )
    return FileResponse(
        path=_ON_DEVICE_MODEL_PATH,
        media_type="application/octet-stream",
        filename=_ON_DEVICE_MODEL_PATH.name,
    )


@router.get("/status", response_model=LocalLlmStatusResponse)
async def status(deep: bool = False) -> LocalLlmStatusResponse:
    """`deep=true` performs one real, tiny generation to prove the model
    actually answers, not just that it's listed — see check_status()'s own
    docstring. Off by default so an ordinary status poll never costs real
    inference time.
    """
    settings = get_settings()
    result = await check_status(deep=deep)
    return LocalLlmStatusResponse(
        status=result["status"],
        ollama_running=result["ollama_running"],
        provider=settings.local_llm_provider,
        model=settings.local_llm_model,
        model_availability=result["model_availability"],
    )
