from typing import Literal

from pydantic import BaseModel


class ChatContext(BaseModel):
    overallRisk: str
    waterStressRisk: str
    heatRisk: str
    cropHealthRisk: str
    reasons: list[str] = []
    soilMoisturePct: float
    temperatureC: float
    humidityPct: float


class ChatRequest(BaseModel):
    mode: Literal["chat", "explain"]
    message: str
    context: ChatContext


class ChatResponse(BaseModel):
    reply: str
