from fastapi import APIRouter, Request

router = APIRouter(tags=["health"])


@router.get("/health")
def health(request: Request) -> dict:
    """No auth required — used for uptime checks and to pre-warm a
    sleeping free-tier deployment before a demo reaches Crop Health."""
    return {
        "status": "ok",
        "models_loaded": getattr(request.app.state, "models_loaded", []),
    }
