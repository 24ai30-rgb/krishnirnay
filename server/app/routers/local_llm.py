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
    "You are KrishiNirnay's friendly farm assistant for Indian farmers. Answer the "
    "farmer's actual question directly and naturally. If they greet you or just "
    "chat, reply warmly in one short sentence and offer help — do not recite field "
    "data unless it is relevant to what they asked. You are also given the current "
    "facts about the farmer's field as background: use them when the question is "
    "about their field, and never contradict them. For general farming questions "
    "(crop care, diseases, pests, irrigation, soil, seasons) give practical, "
    "well-known advice in simple words. NEVER invent this field's sensor readings, "
    "weather, market prices, diagnoses or government schemes, and never state "
    "specific pesticide or fertilizer dose amounts — say a local agriculture "
    "officer should confirm the dose. If the field facts you would need are marked "
    "'not available', say so. Keep answers short and practical."
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
        "आप KrishiNirnay के मित्रवत कृषि सहायक हैं। उत्तर केवल सरल और स्पष्ट हिंदी में दें। "
        "अंग्रेज़ी में fallback न करें। किसान के प्रश्न का सीधे और स्वाभाविक रूप से उत्तर दें। "
        "यदि किसान अभिवादन करे या सामान्य बात करे, तो विनम्रता से एक छोटा उत्तर दें और मदद की "
        "पेशकश करें — खेत के आंकड़े तभी बताएं जब प्रश्न से संबंधित हों। खेत की मौजूदा जानकारी "
        "पृष्ठभूमि के रूप में दी गई है; खेत से जुड़े प्रश्नों में उसका उपयोग करें और उसके विपरीत "
        "कुछ न कहें। फसल देखभाल, रोग, कीट, सिंचाई और मिट्टी जैसे सामान्य खेती के प्रश्नों पर "
        "सरल शब्दों में व्यावहारिक और प्रचलित सलाह दें। इस खेत की सेंसर रीडिंग, मौसम, बाज़ार भाव, "
        "रोग-निदान या सरकारी योजनाओं का आविष्कार न करें, और कीटनाशक या उर्वरक की सटीक मात्रा न "
        "बताएं — कहें कि मात्रा की पुष्टि स्थानीय कृषि अधिकारी से करें। अपनी भाषा, अनुवाद, या किसी "
        "भी meta टिप्पणी का उल्लेख न करें — सीधे उत्तर से शुरू करें। उत्तर छोटा और व्यावहारिक रखें।"
    ),
    "mr": (
        "तुम्ही KrishiNirnay चे मैत्रीपूर्ण शेती सहाय्यक आहात. उत्तर फक्त सोप्या आणि स्पष्ट "
        "मराठी भाषेत द्या. इंग्रजीमध्ये fallback करू नका. शेतकऱ्याच्या प्रश्नाला थेट आणि "
        "नैसर्गिकपणे उत्तर द्या. शेतकऱ्याने नमस्कार केला किंवा सहज गप्पा मारल्या, तर नम्रपणे एक "
        "लहान उत्तर द्या आणि मदतीची तयारी दाखवा — शेताची आकडेवारी प्रश्नाशी संबंधित असेल "
        "तरच सांगा. शेताची सध्याची माहिती पार्श्वभूमी म्हणून दिली आहे; शेताशी संबंधित "
        "प्रश्नांसाठी ती वापरा आणि तिच्या विरुद्ध काही सांगू नका. पीक निगा, रोग, कीड, सिंचन आणि "
        "माती यांसारख्या सामान्य शेतीच्या प्रश्नांवर सोप्या शब्दांत व्यवहार्य आणि प्रचलित सल्ला द्या. "
        "या शेताचे सेन्सर रीडिंग, हवामान, बाजारभाव, रोगनिदान किंवा सरकारी योजना तयार करू नका, "
        "आणि कीटकनाशक किंवा खताचे नेमके प्रमाण सांगू नका — प्रमाणाची खात्री स्थानिक कृषी "
        "अधिकाऱ्याकडून करा असे सांगा. तुमची भाषा, भाषांतर किंवा कोणत्याही meta टिप्पणीचा उल्लेख "
        "करू नका — थेट उत्तराने सुरुवात करा. उत्तर लहान आणि व्यवहार्य ठेवा."
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
        "facts_header": "Background facts about the farmer's field (use only if relevant):",
        "instruction": (
            "Answer the farmer's question above directly, in {lang}, in 2 to 4 short sentences. "
            "If the message is only a greeting or small talk, reply with one friendly sentence "
            "and ask how you can help — do not mention soil, weather or crop data. "
            "Otherwise use the field facts only if they help answer it — do not just repeat them. "
            "If a needed fact isn't listed, say it isn't available."
        ),
    },
    "hi": {
        "location": "स्थान", "crop": "फसल", "stage": "चरण", "farming_method": "खेती की विधि",
        "soil": "मिट्टी", "moisture": "नमी", "sensors": "सेंसर", "temperature": "तापमान",
        "humidity": "आर्द्रता", "weather": "मौसम", "market": "बाज़ार", "risk": "कुल जोखिम",
        "pest": "कीट", "disease": "रोग", "fertilizer": "उर्वरक", "recommendation": "सिफारिश",
        "reasons": "कारण", "unknown": "अज्ञात", "not_available": "उपलब्ध नहीं",
        "not_assessed": "आकलन नहीं किया गया", "none": "कोई नहीं", "farmer_asks": "किसान पूछता है",
        "facts_header": "किसान के खेत की पृष्ठभूमि जानकारी (केवल प्रासंगिक होने पर उपयोग करें):",
        "instruction": (
            "ऊपर किसान के प्रश्न का हिंदी में सीधा उत्तर दें, 2 से 4 छोटे वाक्यों में। खेत की जानकारी "
            "केवल तभी उपयोग करें जब वह उत्तर में मदद करे; उसे दोहराएं नहीं। यदि संदेश केवल अभिवादन या "
            "सामान्य बातचीत है, तो एक मित्रवत वाक्य में उत्तर दें और पूछें कि आप कैसे मदद कर सकते हैं — "
            "मिट्टी, मौसम या फसल का आंकड़ा न बताएं। यदि कोई ज़रूरी जानकारी सूची में नहीं है, तो कहें कि "
            "वह उपलब्ध नहीं है।"
        ),
    },
    "mr": {
        "location": "स्थान", "crop": "पीक", "stage": "अवस्था", "farming_method": "शेतीची पद्धत",
        "soil": "माती", "moisture": "ओलावा", "sensors": "सेन्सर", "temperature": "तापमान",
        "humidity": "आर्द्रता", "weather": "हवामान", "market": "बाजार", "risk": "एकूण धोका",
        "pest": "कीड", "disease": "रोग", "fertilizer": "खत", "recommendation": "शिफारस",
        "reasons": "कारणे", "unknown": "अज्ञात", "not_available": "उपलब्ध नाही",
        "not_assessed": "मूल्यांकन केलेले नाही", "none": "काहीही नाही", "farmer_asks": "शेतकरी विचारतो",
        "facts_header": "शेतकऱ्याच्या शेतातील पार्श्वभूमी माहिती (संबंधित असेल तरच वापरा):",
        "instruction": (
            "वर शेतकऱ्याच्या प्रश्नाला मराठीत थेट उत्तर द्या, 2 ते 4 लहान वाक्यांत. शेताची माहिती "
            "उत्तराला मदत करत असेल तरच वापरा; ती पुन्हा सांगू नका. संदेश फक्त नमस्कार किंवा सहज गप्पा "
            "असतील, तर एका मैत्रीपूर्ण वाक्यात उत्तर द्या आणि कशी मदत करू शकता ते विचारा — माती, हवामान "
            "किंवा पिकाची आकडेवारी सांगू नका. एखादी गरजेची माहिती यादीत नसेल, तर ती \"उपलब्ध नाही\" "
            "असे सांगा."
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
    return t["facts_header"] + "\n" + "\n".join(facts) + f'\n\n{t["farmer_asks"]}: "{request.message}"\n' + instruction


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
