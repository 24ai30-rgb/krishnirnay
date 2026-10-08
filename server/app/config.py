from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Server configuration."""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # ============================================================
    # API SECURITY
    # ============================================================

    # Must match Android SERVER_API_KEY
    api_key: str = "dev-only-change-me"

    # ============================================================
    # GEMINI
    # ============================================================

    gemini_api_key: str = ""

    # ============================================================
    # ML MODELS
    # ============================================================

    # Existing disease model placeholder
    disease_model_path: str = "models/disease-v1.onnx"

    # Actual trained Agricultural Risk Model
    risk_model_path: str = "models/agricultural_risk_final.pkl"

    # ============================================================
    # WEATHER — wires WeatherAPI.com's forecast endpoint
    # (api.weatherapi.com). Requires a real key from
    # https://www.weatherapi.com/my/ ; empty by default means
    # GET /v1/weather honestly reports 503 "unavailable" rather than
    # fabricating a reading. See app/services/weather_provider.py.
    #
    # MARKET — wires data.gov.in's AGMARKNET mandi-price resource.
    # Requires a real key from https://data.gov.in (sent as the
    # `api-key` query parameter, which is what that API actually
    # accepts — not a Bearer header); empty by default means
    # GET /v1/market honestly reports 503 "unavailable" rather than
    # fabricating a price. See app/services/market_provider.py.
    # ============================================================

    weather_api_key: str = ""
    data_gov_api_key: str = ""

    # ============================================================
    # LOCAL LLM (Phase 4E) — a self-hosted Ollama-compatible server,
    # never a cloud LLM. Connection config only (no secret), so a
    # sensible local default is fine; if nothing is listening at
    # local_llm_url, every call honestly reports "unavailable" rather
    # than fabricating a reply. See app/services/local_llm_service.py.
    # ============================================================

    local_llm_provider: str = "ollama"
    # 127.0.0.1 rather than "localhost" on purpose: localhost can resolve to
    # ::1 first and stall on hosts where Ollama only listens on IPv4.
    local_llm_url: str = "http://127.0.0.1:11434"
    # English default/fallback model.
    local_llm_model: str = "deepseek-r1:7b"

    # Hindi/Marathi (Phase 4) — deepseek-r1:7b was verified to answer in the
    # right script but incoherently for these two languages (see
    # LOCAL_LLM_IMPLEMENTATION_REPORT.md), so they use a separate,
    # Indic-capable model instead. Empty means "use local_llm_model", so a
    # single-model setup (just pulling deepseek-r1:7b) still works unchanged.
    # See app/services/local_llm_service.py:resolve_model_for_language — this
    # is the one place model choice is configured; nothing else hardcodes it.
    #
    # Measured (Phase 4, real grounded prompts matching this app's actual
    # fact-list format, not bare questions):
    #   qwen2.5:3b Hindi   — consistently coherent, correctly grounded, ~12s
    #   qwen2.5:3b Marathi — unreliable: labelled its own output ("उत्तर:")
    #                        and used broken/invented words in 2/2 trials
    #   qwen2.5:7b Marathi — meaningfully better: correctly grounded core
    #                        advice in 3/3 trials, occasional grammatical
    #                        roughness in a second sentence, ~25-44s (slower,
    #                        but within local_llm_read_timeout_seconds)
    # Marathi therefore uses the larger model; Hindi does not need to.
    local_llm_model_hi: str = "qwen2.5:3b"
    local_llm_model_mr: str = "qwen2.5:7b"

    # Local inference is slow (measured ~5 tokens/second for a 7B model on a
    # CPU-only dev box, plus ~13s cold model load), so the read budget is much
    # larger than for any network API — while the connect budget stays small,
    # since a local port either answers immediately or isn't listening.
    local_llm_connect_timeout_seconds: float = 5.0
    local_llm_read_timeout_seconds: float = 120.0
    # Caps worst-case latency: a farmer-facing explanation is a few sentences.
    local_llm_num_predict: int = 220
    # Low: this layer explains already-computed facts, it doesn't invent any.
    local_llm_temperature: float = 0.2

    # ============================================================
    # IVR (Phase 4G) — no telephony provider is configured by
    # default. A real phone call always requires *some* telephony
    # provider (the network itself is never "offline"); only the AI
    # reasoning behind the call is local/self-hosted. See
    # app/services/ivr_service.py.
    # ============================================================

    ivr_provider: str = ""
    ivr_api_key: str = ""
    ivr_auth_token: str = ""
    ivr_phone_number: str = ""

    # ============================================================
    # CORS
    # ============================================================

    cors_origins: str = ""


@lru_cache
def get_settings() -> Settings:
    """Return cached application settings."""
    return Settings()