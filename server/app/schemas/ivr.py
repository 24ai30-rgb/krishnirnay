from pydantic import BaseModel


class IVRIncomingCallRequest(BaseModel):
    call_id: str
    from_phone_number: str


class IVRIncomingCallResponse(BaseModel):
    message: str
    next_step: str = "language"


class IVRLanguageRequest(BaseModel):
    call_id: str
    digit: str


class IVRLanguageResponse(BaseModel):
    language: str | None
    message: str
    next_step: str


class IVRQuestionRequest(BaseModel):
    call_id: str
    transcript: str


class IVRQuestionResponse(BaseModel):
    message: str
    farmer_found: bool


class IVREndCallRequest(BaseModel):
    call_id: str


class IVREndCallResponse(BaseModel):
    ended: bool


class IVRStatusResponse(BaseModel):
    configured: bool
    provider: str
