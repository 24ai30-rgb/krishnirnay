"""Unit tests for app.services.ivr_flow — pure, deterministic logic, no
network/telephony involved."""

from app.services.ivr_flow import (
    FarmerLookupResult,
    FarmerLookupService,
    IVRSessionStore,
    masked_phone_number,
    normalize_phone_number,
    not_registered_message,
    parse_language_digit,
    welcome_message,
)


def test_language_digit_mapping_is_deterministic():
    assert parse_language_digit("1") == "mr"
    assert parse_language_digit("2") == "hi"
    assert parse_language_digit("3") == "en"
    assert parse_language_digit("9") is None
    assert parse_language_digit("") is None


def test_welcome_message_per_language_and_fallback():
    assert "मराठी" in welcome_message("mr") or "1" in welcome_message("mr")
    assert welcome_message("hi") != welcome_message("en")
    assert welcome_message("xx") == welcome_message("en")  # unknown -> English fallback


def test_not_registered_message_matches_the_required_marathi_text():
    assert not_registered_message("mr") == "कृपया प्रथम KRISHINIRNAY मध्ये तुमची शेती माहिती नोंदवा."


def test_phone_number_normalization_is_consistent_across_formats():
    assert normalize_phone_number("+91 98765 43210") == "9876543210"
    assert normalize_phone_number("919876543210") == "9876543210"
    assert normalize_phone_number("9876543210") == "9876543210"
    assert normalize_phone_number("(987) 654-3210") == "9876543210"


def test_masked_phone_number_never_reveals_the_full_number():
    masked = masked_phone_number("+919876543210")
    assert masked.endswith("3210")
    assert "987654" not in masked
    assert len(masked) == len("9876543210")


def test_unregistered_farmer_lookup_is_honest_not_found():
    service = FarmerLookupService()
    result = service.lookup("+919876543210")
    assert result.found is False


def test_registered_farmer_lookup_finds_an_injected_record():
    service = FarmerLookupService(
        directory={"9876543210": FarmerLookupResult(found=True, farmer_id="f1", primary_crop="Cotton")},
    )
    result = service.lookup("+91 98765 43210")
    assert result.found is True
    assert result.farmer_id == "f1"
    assert result.primary_crop == "Cotton"


# --- IVRSessionStore: language selected in one webhook must be recalled in the next ---

def test_session_store_recalls_the_language_selected_earlier_in_the_call():
    sessions = IVRSessionStore()
    sessions.start_call("call-1", "+919876543210")
    sessions.set_language("call-1", "mr")

    session = sessions.get("call-1")
    assert session is not None
    assert session.language == "mr"
    assert session.phone_number == "+919876543210"


def test_session_store_has_no_language_until_one_is_set():
    sessions = IVRSessionStore()
    sessions.start_call("call-2", "+919876543210")
    assert sessions.get("call-2").language is None


def test_session_store_returns_none_for_an_unknown_call():
    sessions = IVRSessionStore()
    assert sessions.get("never-started") is None


def test_ending_a_call_clears_its_session():
    sessions = IVRSessionStore()
    sessions.start_call("call-3", "+919876543210")
    sessions.end_call("call-3")
    assert sessions.get("call-3") is None


def test_sessions_for_different_calls_never_leak_into_each_other():
    sessions = IVRSessionStore()
    sessions.start_call("call-a", "+911111111111")
    sessions.set_language("call-a", "hi")
    sessions.start_call("call-b", "+912222222222")
    sessions.set_language("call-b", "en")

    assert sessions.get("call-a").language == "hi"
    assert sessions.get("call-b").language == "en"
