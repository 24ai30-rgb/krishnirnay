# KRISHINIRNAY Phase 4G Implementation Report — IVR for Keypad Phones

Scope: a real, provider-agnostic IVR architecture and call-flow logic — the parts that can be built and tested without a telephony account. **A real phone call is never "completely offline"** — this report does not claim otherwise anywhere.

---

## 1. Audit

- **Confirmed by fresh search**: no IVR/telephony code existed anywhere in the repository before this phase.
- **No server-side farmer directory exists**: `FarmerProfile` lives only in each Android device's local DataStore, keyed to Firebase Auth — never synced to this server by phone number. This is a genuine, pre-existing architecture gap for IVR's "phone → farmer profile" requirement, not something this phase could close without a new server-side farmer database (a real design decision, not something to invent silently).
- **What was reusable**: `AppStrings`'s EN/HI/MR text conventions (for the required-message wording), the existing FastAPI router/schema/service layering, and — once wired — the new Local LLM service (Phase 4E) for question-answering.

## 2. Architecture

```
Telephony provider webhook (Twilio/Exotel/... — not configured in this environment)
    ↓
POST /v1/ivr/incoming-call → welcome message + "press 1/2/3" instruction
    ↓
POST /v1/ivr/language → digit 1=Marathi, 2=Hindi, 3=English (deterministic, ivr_flow.py)
    ↓
POST /v1/ivr/question → phone number normalized → farmer lookup
    ↓
  not registered → honest "please register first" message (exact required Marathi text)
  registered → Local LLM (Phase 4E), never fabricated facts
    ↓
Response body IS the instruction the telephony platform plays back (webhook-response pattern)
```

`IVRProvider` (a Python ABC) exists for the *other* direction — actions this server would initiate back to a provider (e.g. a future proactive "your field is at HIGH risk" call) — `handle_incoming_call`/`start_call`/`collect_language`/`collect_speech`/`send_response`/`end_call`. The only concrete implementation is `UnconfiguredIVRProvider`, which raises `IVRProviderError` from every method rather than pretending to place or receive a call.

## 3. Files Created

- `server/app/services/ivr_service.py` — `IVRProvider` ABC + `UnconfiguredIVRProvider` + `get_ivr_provider()` (selects a concrete provider by `IVR_PROVIDER`/`IVR_API_KEY`/`IVR_AUTH_TOKEN`; none is implemented yet since no real account exists to build/verify one against — it errors clearly rather than guessing a provider's API shape).
- `server/app/services/ivr_flow.py` — pure, deterministic call-flow logic: `parse_language_digit` (1/2/3 → mr/hi/en), `welcome_message`/`not_registered_message` (per language, including the exact required Marathi text), `normalize_phone_number`/`masked_phone_number` (never logs a full phone number), `FarmerLookupService`/`FarmerLookupResult`.
- `server/app/schemas/ivr.py`, `server/app/routers/ivr.py` — the 3-step webhook flow (`/incoming-call`, `/language`, `/question`) plus `/status` (reports whether a real provider is configured).

## 4. Files Modified

- `server/app/config.py` — added `ivr_provider`/`ivr_api_key`/`ivr_auth_token`/`ivr_phone_number` (all empty by default).
- `server/app/main.py` — registered the new router.
- `server/.env.example` — documents the 4 new variables with no real values.

## 5. IVR User Flow — Implemented vs. Blocked

| Step | Status |
|---|---|
| Welcome message | ✅ Real, deterministic, 3-language |
| Language selection (1/2/3) | ✅ Real, deterministic, unit-tested |
| Farmer phone lookup | ✅ Real lookup interface + phone normalization; ⚠️ the backing directory is empty (see §1) — every real call today gets the honest "not registered" message until a farmer directory is added |
| Speech-to-text during a live call | 🚫 BLOCKED_EXTERNAL_DEPENDENCY — requires a telephony provider's own speech capture (this repo has no phone-call audio pipeline) |
| DecisionEngine / farm context | ⚠️ Not reachable per-call yet — blocked on the same missing farmer directory |
| Local AI explanation | ✅ Wired — `/v1/ivr/question` calls the same Local LLM service as Phase 4E |
| Text-to-speech / voice response | 🚫 BLOCKED_EXTERNAL_DEPENDENCY — a telephony provider must convert the response text to voice during the live call; this server can only return text |
| Actual phone call connectivity | 🚫 BLOCKED_EXTERNAL_DEPENDENCY — requires a real Twilio/Exotel/etc. account and phone number |

## 6. Farmer Phone Identity

`normalize_phone_number()` strips formatting and a leading `+91`/`91` country-code prefix so `"+91 98765 43210"`, `"919876543210"`, and `"9876543210"` all normalize identically. `masked_phone_number()` (last 4 digits only) is provided for any future logging — no code path in this phase logs a complete phone number. Unregistered callers receive exactly: *"कृपया प्रथम KRISHINIRNAY मध्ये तुमची शेती माहिती नोंदवा."*

## 7. Configuration

```
IVR_PROVIDER=
IVR_API_KEY=
IVR_AUTH_TOKEN=
IVR_PHONE_NUMBER=
```
All empty by default in `.env.example`; never committed with real values. `GET /v1/ivr/status` honestly reports `configured: false` until real credentials are supplied.

## 8. Tests

`test_ivr_flow.py` (8 tests): language-digit mapping, welcome/not-registered messages per language (including the exact required Marathi string), phone normalization across formats, masking never reveals the full number, unregistered/registered lookup. `test_ivr_service.py` (3 tests): default provider is unconfigured, every operation on it raises rather than pretending to succeed, a named-but-unimplemented provider fails clearly. `test_ivr_endpoints.py` (8 tests): auth required, welcome message, language selection (valid + invalid digit retry), unknown caller gets the not-registered message, registered caller reaches the Local LLM, a Local LLM failure is reported honestly, status reports unconfigured by default.

## 9. Test Results / Build

Included in the server's overall total: 71 passed (19 IVR-specific), 6 pre-existing unrelated failures. No Android changes in this phase.

## 10. Known Limitations / Blockers

- **BLOCKED_EXTERNAL_DEPENDENCY**: a telephony provider account (Twilio/Exotel/etc.) + phone number — required for any real call to exist at all. A normal Android phone cannot itself serve as a public telephone number.
- **BLOCKED_EXTERNAL_DEPENDENCY**: a server-side farmer directory keyed by phone number — does not exist yet (Farmer Profile is Android-local only); this is required before `/v1/ivr/question` can honestly reach a specific farmer's real sensor/weather/market/decision context instead of the generic "not registered" path.
- No real provider's webhook payload shape was guessed or implemented — building against an unverified, made-up API contract would risk shipping something that silently doesn't work with the real service.

**This must never be described as "completely offline"** — the call itself always requires a telephony network; only the reasoning behind it (farmer lookup logic, Local LLM) is local/self-hosted.

---

**Stopping analysis here for 4G** — see `FINAL_SYSTEM_REPORT.md` for Government Schemes, Farmer Feedback, Dashboard UI, and the complete system summary.
