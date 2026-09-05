from fastapi import APIRouter, Depends, HTTPException

from app.core.security import verify_api_key
from app.schemas.chat import ChatRequest, ChatResponse
from app.services.gemini_proxy import GeminiError, generate_reply

router = APIRouter(prefix="/v1", tags=["chat"], dependencies=[Depends(verify_api_key)])


def _build_prompt(request: ChatRequest) -> str:
    context = request.context
    reasons = "\n".join(f"- {reason}" for reason in context.reasons)
    field_summary = (
        "You are KrishiNirnay's farm assistant, grounded only in the field data below — "
        "never invent facts not present here.\n\n"
        f"Overall risk: {context.overallRisk}\n"
        f"Water stress: {context.waterStressRisk}\n"
        f"Heat: {context.heatRisk}\n"
        f"Crop health: {context.cropHealthRisk}\n"
        f"Soil moisture: {context.soilMoisturePct}%\n"
        f"Temperature: {context.temperatureC}C\n"
        f"Humidity: {context.humidityPct}%\n"
        f"Reasons:\n{reasons}\n\n"
    )
    if request.mode == "explain":
        instruction = "Explain this recommendation in one or two friendly, plain-language sentences for a farmer."
    else:
        instruction = (
            f'The farmer asks: "{request.message}"\n'
            "Answer helpfully in plain, encouraging language, using only the field data above."
        )
    return field_summary + instruction


@router.post("/chat", response_model=ChatResponse)
async def chat(request: ChatRequest) -> ChatResponse:
    prompt = _build_prompt(request)
    try:
        reply = await generate_reply(prompt)
    except GeminiError as exc:
        raise HTTPException(
            status_code=502,
            detail={"error": "gemini_unavailable", "message": str(exc)},
        ) from exc
    return ChatResponse(reply=reply)
