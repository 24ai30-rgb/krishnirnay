import asyncio

from app.config import get_settings
from app.services.ivr_service import (
    FakeIVRProvider,
    IVRProviderError,
    UnconfiguredIVRProvider,
    get_ivr_provider,
)


def test_default_provider_is_unconfigured_and_honest():
    provider = get_ivr_provider()
    assert isinstance(provider, UnconfiguredIVRProvider)
    assert provider.configured() is False


async def _expect_error(coro):
    try:
        await coro
        return False
    except IVRProviderError:
        return True


def test_unconfigured_provider_raises_on_every_operation():
    provider = UnconfiguredIVRProvider()
    assert asyncio.run(_expect_error(provider.handle_incoming_call("+919876543210")))
    assert asyncio.run(_expect_error(provider.start_call("+919876543210")))
    assert asyncio.run(_expect_error(provider.collect_language("call-1")))
    assert asyncio.run(_expect_error(provider.collect_speech("call-1")))
    assert asyncio.run(_expect_error(provider.send_response("call-1", "hi", "en")))
    assert asyncio.run(_expect_error(provider.end_call("call-1")))


def test_a_named_but_unimplemented_provider_raises_clearly(monkeypatch):
    settings = get_settings()
    monkeypatch.setattr(settings, "ivr_provider", "twilio")
    monkeypatch.setattr(settings, "ivr_api_key", "key")
    monkeypatch.setattr(settings, "ivr_auth_token", "token")

    try:
        get_ivr_provider()
        assert False, "expected IVRProviderError"
    except IVRProviderError as exc:
        assert "twilio" in str(exc)


# --- FakeIVRProvider: a deterministic, always-succeeds provider for testing
# the full call flow end to end, without a real telephony account ---

def test_fake_provider_is_reported_as_configured():
    assert FakeIVRProvider().configured() is True


def test_fake_provider_handles_a_full_call_flow_deterministically():
    provider = FakeIVRProvider()

    call_id = asyncio.run(provider.handle_incoming_call("+919876543210"))
    assert call_id in provider.calls

    digit = asyncio.run(provider.collect_language(call_id))
    assert digit == "1"  # deterministic: always Marathi

    question = asyncio.run(provider.collect_speech(call_id))
    assert question  # a real, non-empty deterministic sample question

    asyncio.run(provider.send_response(call_id, "उत्तर", "mr"))
    assert (call_id, "उत्तर", "mr") in provider.responses_sent

    asyncio.run(provider.end_call(call_id))
    assert call_id in provider.ended_calls


def test_fake_provider_never_shares_state_with_a_real_provider_selection():
    # FakeIVRProvider must never be what get_ivr_provider() returns by default —
    # it exists only for tests to instantiate directly.
    assert not isinstance(get_ivr_provider(), FakeIVRProvider)
