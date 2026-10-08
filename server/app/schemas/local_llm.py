from pydantic import BaseModel, ConfigDict


class LocalLlmContext(BaseModel):
    """Structured, factual context only — every field must come from a real
    upstream source (Farmer Profile, sensors, WeatherState, MarketState,
    DecisionOutput). The prompt builder in the router instructs the model to
    answer using only these facts, never to invent beyond them. Any field the
    caller doesn't have real data for is left null/empty, not guessed."""

    language: str = "en"
    farmer_state: str | None = None
    farmer_district: str | None = None
    crop: str | None = None
    crop_stage: str | None = None
    farming_method: str | None = None
    soil_type: str | None = None
    soil_moisture_pct: float | None = None
    temperature_c: float | None = None
    humidity_pct: float | None = None
    weather_status: str | None = None
    weather_summary: str | None = None
    market_status: str | None = None
    market_summary: str | None = None
    overall_risk: str | None = None
    pest_summary: str | None = None
    disease_summary: str | None = None
    fertilizer_summary: str | None = None
    recommendation_summary: str | None = None
    reasons: list[str] = []


class LocalLlmChatRequest(BaseModel):
    message: str
    context: LocalLlmContext


class LocalLlmChatResponse(BaseModel):
    """One stable shape for both success and failure — the HTTP status code
    stays meaningful (200 vs 503) for tooling/logs, but the JSON body never
    changes shape between the two, so Android's Retrofit/kotlinx.serialization
    parsing is one data class, not a success model plus a separately-shaped
    error body.

    Success: success=true, answer set, error=null.
    Failure: success=false, answer=null, error is a short, human-readable
    reason — never a stack trace, never the raw exception text.
    """

    success: bool
    language: str
    answer: str | None = None
    source: str = "local_llm"
    model: str
    error: str | None = None
    # Real generation time in ms — advanced/debug display only, null on failure.
    elapsed_ms: int | None = None


class LocalLlmStatusResponse(BaseModel):
    """status is one of OLLAMA_UNREACHABLE / MODEL_MISSING / MODEL_AVAILABLE /
    GENERATION_WORKING / GENERATION_FAILED — see
    app/services/local_llm_service.py. MODEL_MISSING means Ollama itself is
    reachable but the configured model has not been pulled, a different fix
    from the server being down, so the two must never be conflated.

    GENERATION_WORKING/GENERATION_FAILED only appear when the caller asked for
    a deep check (`?deep=true`), which performs one real, tiny generation —
    it costs real inference time, so it is never done on every ordinary
    status poll (e.g. every time the chat screen opens).

    ollama_running is true whenever /api/tags answered at all, independent of
    whether the configured model is present — exposed separately so a caller
    can tell "Ollama is up but this model isn't pulled" apart from "Ollama
    itself is down" without parsing the status string.

    model_availability (Phase 4) reports, per supported language, whether
    that language's configured model (see
    app/services/local_llm_service.resolve_model_for_language) is actually
    pulled — e.g. {"en": true, "hi": false, "mr": false} means the English
    model is ready but the Hindi/Marathi model has not been pulled yet, even
    though `status` (which tracks the English/default model only) reads
    MODEL_AVAILABLE.
    """

    model_config = ConfigDict(protected_namespaces=())

    status: str
    ollama_running: bool
    provider: str
    model: str
    model_availability: dict[str, bool]
