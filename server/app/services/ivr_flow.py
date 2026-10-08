"""Deterministic IVR call-flow logic — language selection, phone
normalization, and farmer lookup. Kept separate from ivr_service.py's
provider abstraction so this logic is fully unit-testable without any real
telephony provider, and separate from local_llm_service.py since it never
calls an LLM: language selection and phone lookup are simple, deterministic
rules, never something an AI should "decide"."""

import re

WELCOME_MESSAGES = {
    "mr": "क्रिशीनिर्णयमध्ये आपले स्वागत आहे. मराठीसाठी १ दाबा, हिंदीसाठी २ दाबा, इंग्रजीसाठी ३ दाबा.",
    "hi": "कृषिनिर्णय में आपका स्वागत है। मराठी के लिए 1 दबाएँ, हिंदी के लिए 2 दबाएँ, अंग्रेज़ी के लिए 3 दबाएँ।",
    "en": "Welcome to KrishiNirnay. Press 1 for Marathi, 2 for Hindi, 3 for English.",
}

NOT_REGISTERED_MESSAGES = {
    "mr": "कृपया प्रथम KRISHINIRNAY मध्ये तुमची शेती माहिती नोंदवा.",
    "hi": "कृपया पहले KRISHINIRNAY में अपनी खेती की जानकारी दर्ज करें।",
    "en": "Please first register your farm details in KRISHINIRNAY.",
}

_LANGUAGE_BY_DIGIT = {
    "1": "mr",
    "2": "hi",
    "3": "en",
}


def parse_language_digit(digit: str) -> str | None:
    """Press 1 = Marathi, 2 = Hindi, 3 = English — deterministic, never guessed."""
    return _LANGUAGE_BY_DIGIT.get(digit.strip())


def welcome_message(language: str = "en") -> str:
    return WELCOME_MESSAGES.get(language, WELCOME_MESSAGES["en"])


def not_registered_message(language: str = "en") -> str:
    return NOT_REGISTERED_MESSAGES.get(language, NOT_REGISTERED_MESSAGES["en"])


def normalize_phone_number(raw: str) -> str:
    """Strips everything but digits and a leading '+', then drops a bare
    Indian country code prefix so the same farmer normalizes the same way
    whether they're stored/dialled as "+919876543210", "919876543210", or
    "9876543210". Never masks/hashes here — that's the caller's concern
    (see FarmerLookupService's logging discipline)."""
    digits = re.sub(r"[^\d+]", "", raw)
    digits = digits.lstrip("+")
    if digits.startswith("91") and len(digits) == 12:
        digits = digits[2:]
    return digits


def masked_phone_number(raw: str) -> str:
    """For logging only — never log a complete phone number."""
    normalized = normalize_phone_number(raw)
    if len(normalized) <= 4:
        return "*" * len(normalized)
    return "*" * (len(normalized) - 4) + normalized[-4:]


class IVRSession:
    def __init__(self, phone_number: str, language: str | None = None):
        self.phone_number = phone_number
        self.language = language


class IVRSessionStore:
    """In-memory, per-call session state — a real telephony provider's webhook
    always includes a call id (Twilio's CallSid, Exotel's equivalent); the
    farmer's selected language must be remembered against that id between the
    /language and /question webhook calls, since the provider doesn't repeat
    it on every request. Deterministic and dependency-free; a real deployment
    serving multiple server processes would need a shared store (e.g. Redis)
    instead of this per-process dict — not needed for a single-instance dev
    server, and not invented here without a real multi-instance requirement.
    """

    def __init__(self):
        self._sessions: dict[str, IVRSession] = {}

    def start_call(self, call_id: str, phone_number: str) -> None:
        self._sessions[call_id] = IVRSession(phone_number=phone_number)

    def set_language(self, call_id: str, language: str) -> None:
        session = self._sessions.get(call_id)
        if session is not None:
            session.language = language

    def get(self, call_id: str) -> IVRSession | None:
        return self._sessions.get(call_id)

    def end_call(self, call_id: str) -> None:
        self._sessions.pop(call_id, None)


class FarmerLookupResult:
    def __init__(self, found: bool, farmer_id: str | None = None, primary_crop: str | None = None):
        self.found = found
        self.farmer_id = farmer_id
        self.primary_crop = primary_crop


class FarmerLookupService:
    """Phone -> Farmer Profile lookup.

    BLOCKED_EXTERNAL_DEPENDENCY: there is currently no server-side farmer
    directory — FarmerProfile lives only in each Android device's local
    DataStore, keyed to Firebase Auth, never synced to this server by phone
    number. Until a real farmer directory (e.g. Firestore, keyed on a
    normalized phone number) is added server-side, every lookup here
    honestly reports "not found" rather than inventing farmer data — the
    same "never fabricate" discipline as weather/market. The interface is
    real and ready: wiring in a real directory later requires no change to
    ivr_flow.py's callers.
    """

    def __init__(self, directory: dict[str, FarmerLookupResult] | None = None):
        # `directory` exists so a real backing store can be injected later
        # (or a test can inject fixtures) without changing this class's shape.
        self._directory = directory or {}

    def lookup(self, raw_phone_number: str) -> FarmerLookupResult:
        normalized = normalize_phone_number(raw_phone_number)
        return self._directory.get(normalized, FarmerLookupResult(found=False))
