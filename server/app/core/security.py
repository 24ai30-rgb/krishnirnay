import secrets

from fastapi import Header, HTTPException

from app.config import get_settings


async def verify_api_key(
    x_api_key: str | None = Header(
        default=None,
        alias="X-API-Key",
    ),
) -> None:
    """Verify the shared API key sent by the Android app."""

    settings = get_settings()

    expected_key = settings.api_key.strip()
    received_key = (x_api_key or "").strip()

    if not received_key:
        raise HTTPException(
            status_code=401,
            detail={
                "error": "unauthorized",
                "message": "Missing API key.",
            },
        )

    if not secrets.compare_digest(received_key, expected_key):
        raise HTTPException(
            status_code=401,
            detail={
                "error": "unauthorized",
                "message": "Invalid API key.",
            },
        )