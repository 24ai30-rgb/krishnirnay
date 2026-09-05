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
    # CORS
    # ============================================================

    cors_origins: str = ""


@lru_cache
def get_settings() -> Settings:
    """Return cached application settings."""
    return Settings()