"""Only place in the whole system that holds the Gemini API key — never
the Android app. See docs/api-contract.md."""

import logging

import httpx

from app.config import get_settings

GEMINI_MODEL = "gemini-1.5-flash"
GEMINI_URL = f"https://generativelanguage.googleapis.com/v1beta/models/{GEMINI_MODEL}:generateContent"

logger = logging.getLogger(__name__)

# A single shared client (reused connection pool/keep-alive) rather than one
# per request — see close_client(), called from the app lifespan on shutdown.
_client: httpx.AsyncClient | None = None


def _get_client() -> httpx.AsyncClient:
    global _client
    if _client is None:
        _client = httpx.AsyncClient(timeout=20.0)
    return _client


async def close_client() -> None:
    global _client
    if _client is not None:
        await _client.aclose()
        _client = None


class GeminiError(Exception):
    pass


async def generate_reply(prompt: str) -> str:
    settings = get_settings()
    if not settings.gemini_api_key:
        raise GeminiError("GEMINI_API_KEY is not configured on the server.")

    try:
        response = await _get_client().post(
            GEMINI_URL,
            params={"key": settings.gemini_api_key},
            json={"contents": [{"parts": [{"text": prompt}]}]},
        )
    except httpx.HTTPError as exc:
        # Network-level failure (timeout, DNS, connection refused, ...) — not a bad
        # HTTP status, so it wouldn't otherwise be caught by the status check below.
        logger.warning("Gemini request failed: %s", exc)
        raise GeminiError("Could not reach the chat service.") from exc

    if response.status_code != 200:
        # Log the raw upstream body server-side only — it can contain quota/project
        # details that shouldn't be forwarded verbatim to the client (see chat.py).
        logger.warning("Gemini API returned %s: %s", response.status_code, response.text[:200])
        raise GeminiError("Upstream chat service error.")

    data = response.json()
    try:
        return data["candidates"][0]["content"]["parts"][0]["text"]
    except (KeyError, IndexError) as exc:
        logger.warning("Unexpected Gemini API response shape: %s", data)
        raise GeminiError("Upstream chat service error.") from exc
