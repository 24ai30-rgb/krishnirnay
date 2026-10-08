"""IVR (keypad-phone) provider abstraction.

IMPORTANT — real-world constraint: a phone call is NEVER completely
offline. It always requires a telephony provider (a public number, call
routing, DTMF/speech capture during the live call). Only the AI reasoning
behind the call — farmer lookup, the deterministic DecisionEngine output,
and the Local LLM explanation — runs locally/self-hosted. Never claim the
call itself is offline.

One interface, swappable concrete providers (Twilio, Exotel, ...) — the
rest of the system only ever depends on IVRProvider, never a specific
provider SDK. No provider account is configured in this environment, so the
only concrete implementation below is UnconfiguredIVRProvider, which raises
IVRProviderError from every operation rather than pretending to place or
receive a call — this is a genuine BLOCKED_EXTERNAL_DEPENDENCY (a telephony
account), not a bug.
"""

from abc import ABC, abstractmethod

from app.config import get_settings


class IVRProviderError(Exception):
    pass


class IVRProvider(ABC):
    @abstractmethod
    def configured(self) -> bool:
        """True once real provider credentials are present."""

    @abstractmethod
    async def handle_incoming_call(self, from_phone_number: str) -> str:
        """Returns a provider call id for an inbound call."""

    @abstractmethod
    async def start_call(self, to_phone_number: str) -> str:
        """Returns a provider call id for an outbound call."""

    @abstractmethod
    async def collect_language(self, call_id: str) -> str:
        """Returns the DTMF digit pressed for language selection."""

    @abstractmethod
    async def collect_speech(self, call_id: str) -> str:
        """Returns the transcribed farmer question."""

    @abstractmethod
    async def send_response(self, call_id: str, text: str, language: str) -> None:
        """Speaks `text` back to the caller (provider TTS or a media URL)."""

    @abstractmethod
    async def end_call(self, call_id: str) -> None: ...


class UnconfiguredIVRProvider(IVRProvider):
    def configured(self) -> bool:
        return False

    async def handle_incoming_call(self, from_phone_number: str) -> str:
        raise IVRProviderError(self._message())

    async def start_call(self, to_phone_number: str) -> str:
        raise IVRProviderError(self._message())

    async def collect_language(self, call_id: str) -> str:
        raise IVRProviderError(self._message())

    async def collect_speech(self, call_id: str) -> str:
        raise IVRProviderError(self._message())

    async def send_response(self, call_id: str, text: str, language: str) -> None:
        raise IVRProviderError(self._message())

    async def end_call(self, call_id: str) -> None:
        raise IVRProviderError(self._message())

    @staticmethod
    def _message() -> str:
        return (
            "No IVR telephony provider is configured "
            "(IVR_PROVIDER/IVR_API_KEY/IVR_AUTH_TOKEN unset). "
            "BLOCKED_EXTERNAL_DEPENDENCY: a telephony provider account."
        )


class FakeIVRProvider(IVRProvider):
    """Deterministic, always-succeeds provider — for automated tests only, to
    exercise the full IVRProvider-driven call flow (incoming call -> language
    -> speech -> response -> end call) as if a real telephony provider were
    connected, without making any real call. Never returned by
    get_ivr_provider() in production; tests instantiate it directly."""

    def __init__(self):
        self.calls: list[str] = []
        self.responses_sent: list[tuple[str, str, str]] = []
        self.ended_calls: list[str] = []

    def configured(self) -> bool:
        return True

    async def handle_incoming_call(self, from_phone_number: str) -> str:
        call_id = f"fake-call-{len(self.calls) + 1}"
        self.calls.append(call_id)
        return call_id

    async def start_call(self, to_phone_number: str) -> str:
        call_id = f"fake-outbound-{len(self.calls) + 1}"
        self.calls.append(call_id)
        return call_id

    async def collect_language(self, call_id: str) -> str:
        return "1"  # deterministic: always the Marathi digit

    async def collect_speech(self, call_id: str) -> str:
        return "पाणी कधी द्यायचं?"  # deterministic sample farming question

    async def send_response(self, call_id: str, text: str, language: str) -> None:
        self.responses_sent.append((call_id, text, language))

    async def end_call(self, call_id: str) -> None:
        self.ended_calls.append(call_id)


def get_ivr_provider() -> IVRProvider:
    settings = get_settings()
    if settings.ivr_provider and settings.ivr_api_key and settings.ivr_auth_token:
        # A real Twilio/Exotel/etc. implementation would be selected here by
        # settings.ivr_provider. None is implemented without a real account to
        # build and verify it against — this session will not guess a
        # provider's webhook/API shape and call it "working".
        raise IVRProviderError(
            f"IVR_PROVIDER='{settings.ivr_provider}' is set but no concrete provider "
            "integration is registered for it yet. Implement one in ivr_service.py "
            "once real credentials are available to test against."
        )
    return UnconfiguredIVRProvider()
