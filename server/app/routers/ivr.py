"""Webhook-style endpoints a telephony provider (Twilio/Exotel/...) would
call as a farmer progresses through a call. No provider account is
configured in this environment (see app/services/ivr_service.py), so
IVRProvider methods aren't invoked for the basic incoming-call/language/
question flow below — the flow's response body is itself the instruction
the platform plays back, exactly like TwiML/webhook-response patterns.
get_ivr_provider() is exposed via /status for observability and for a
future outbound-notification feature (e.g. proactively calling a farmer
about a HIGH risk), which does need a real configured provider.

Session state: a real provider's webhook always includes a call id (e.g.
Twilio's CallSid) and does not repeat earlier answers on later requests —
`_sessions` (an IVRSessionStore) remembers the farmer's selected language
between /language and /question for the life of one call, keyed on that id.
"""

from fastapi import APIRouter, Depends

from app.config import get_settings
from app.core.security import verify_api_key
from app.schemas.ivr import (
    IVREndCallRequest,
    IVREndCallResponse,
    IVRIncomingCallRequest,
    IVRIncomingCallResponse,
    IVRLanguageRequest,
    IVRLanguageResponse,
    IVRQuestionRequest,
    IVRQuestionResponse,
    IVRStatusResponse,
)
from app.services import ivr_flow
from app.services.ivr_service import get_ivr_provider
from app.services.local_llm_service import LocalLlmError, generate

router = APIRouter(prefix="/v1/ivr", tags=["ivr"], dependencies=[Depends(verify_api_key)])

# No server-side farmer directory exists yet (see ivr_flow.FarmerLookupService's
# docstring) — this stays empty until one is wired in, so every real lookup
# below is honest ("not registered"), never fabricated.
_farmer_lookup = ivr_flow.FarmerLookupService()
_sessions = ivr_flow.IVRSessionStore()


@router.post("/incoming-call", response_model=IVRIncomingCallResponse)
async def incoming_call(request: IVRIncomingCallRequest) -> IVRIncomingCallResponse:
    _sessions.start_call(request.call_id, request.from_phone_number)
    return IVRIncomingCallResponse(message=ivr_flow.welcome_message("en"), next_step="language")


@router.post("/language", response_model=IVRLanguageResponse)
async def select_language(request: IVRLanguageRequest) -> IVRLanguageResponse:
    language = ivr_flow.parse_language_digit(request.digit)
    if language is None:
        return IVRLanguageResponse(
            language=None,
            message=ivr_flow.welcome_message("en"),
            next_step="language",
        )
    _sessions.set_language(request.call_id, language)
    return IVRLanguageResponse(
        language=language,
        message=ivr_flow.welcome_message(language),
        next_step="question",
    )


@router.post("/question", response_model=IVRQuestionResponse)
async def ask_question(request: IVRQuestionRequest) -> IVRQuestionResponse:
    session = _sessions.get(request.call_id)
    # No selected language on record for this call -> honest English default,
    # never a guess at which language the farmer actually wants.
    language = session.language if session and session.language else "en"
    phone_number = session.phone_number if session else ""

    farmer = _farmer_lookup.lookup(phone_number)
    if not farmer.found:
        return IVRQuestionResponse(
            message=ivr_flow.not_registered_message(language),
            farmer_found=False,
        )

    # A real farmer directory would supply full farm context (crop, sensors,
    # weather, market, DecisionOutput) here — until one exists server-side,
    # only language is real; the Local LLM's own "say it's not available"
    # instruction (see routers/local_llm.py's system prompt) covers the rest
    # honestly rather than this endpoint inventing farm facts.
    try:
        reply = await generate(
            prompt=f'The farmer asks: "{request.transcript}". No farm facts are available yet.',
            system="Answer briefly and honestly say farm data isn't available for this call yet.",
        )
    except LocalLlmError:
        reply = _llm_unavailable_message(language)

    return IVRQuestionResponse(message=reply, farmer_found=True)


def _llm_unavailable_message(language: str) -> str:
    return {
        "mr": "सध्या उत्तर उपलब्ध नाही. कृपया नंतर पुन्हा प्रयत्न करा.",
        "hi": "अभी उत्तर उपलब्ध नहीं है। कृपया बाद में पुनः प्रयास करें।",
        "en": "An answer isn't available right now. Please try again later.",
    }.get(language, "An answer isn't available right now. Please try again later.")


@router.post("/end-call", response_model=IVREndCallResponse)
async def end_call(request: IVREndCallRequest) -> IVREndCallResponse:
    existed = _sessions.get(request.call_id) is not None
    _sessions.end_call(request.call_id)
    return IVREndCallResponse(ended=existed)


@router.get("/status", response_model=IVRStatusResponse)
async def status() -> IVRStatusResponse:
    provider = get_ivr_provider()
    settings = get_settings()
    return IVRStatusResponse(configured=provider.configured(), provider=settings.ivr_provider or "none")
