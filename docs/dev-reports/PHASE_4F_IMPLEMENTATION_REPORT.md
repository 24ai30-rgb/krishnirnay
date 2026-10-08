# KRISHINIRNAY Phase 4F Implementation Report — Offline Voice

Scope: real voice-to-voice interaction using local Android speech components, extended to actually speak the resolved answer back — closing the loop that previously stopped at "fill the text box."

---

## 1. Audit

- **Existing voice code**: `core/voice/{SpeechRecognizerManager, TextToSpeechManager}` — real native Android `SpeechRecognizer`/`TextToSpeech` wrappers, already wired into `ChatbotViewModel`/`ChatbotScreen` (mic button with a pulse animation, per-message "🔊 Listen" replay button). These already function as the requested `SpeechRecognizer`/`TextToSpeechEngine` abstractions — kept, not renamed or rebuilt, since renaming working, referenced classes would be unnecessary churn.
- **What was missing**: (a) no state machine (`LISTENING`/`PROCESSING`/`SPEAKING`/`READY`/`ERROR`) — only a boolean `isListening`; (b) a question asked by voice was never spoken back automatically — the farmer had to manually tap "Listen" on the reply after it appeared in the transcript; (c) Marathi ("mr") was missing from the STT/TTS locale mapping (only "hi"/"en" were handled, even though Marathi UI strings and language selection already existed).
- **Multilingual support**: English/Hindi/Marathi language selection already existed in `SettingsScreen` (`SegmentedToggle`) and `AppStrings` (`appStringsFor("mr")` already returned `MarathiStrings`) — fully satisfied before this phase, not rebuilt.

## 2. Architecture

```
Farmer taps mic (ChatbotScreen)
    ↓
SpeechRecognizerManager.startListening() — native Android STT, local
    ↓
ChatbotViewModel.resolveAndAppend(text, autoSpeak = true)
    ↓
  keyword match (instant, offline) OR Local LLM (Phase 4E, grounded, offline-safe)
    ↓
TextToSpeechManager.speak(reply) — native Android TTS, local
    ↓
Farmer hears the answer
```

A **typed** question (`sendMessage()`) uses the same resolution path with `autoSpeak = false` — it is shown in the transcript exactly as before, never auto-spoken, preserving existing behavior exactly.

## 3. Files Created

- `core/voice/VoiceState.kt` — `READY`/`LISTENING`/`PROCESSING`/`SPEAKING`/`ERROR`.

## 4. Files Modified

- `core/voice/TextToSpeechManager.kt` — `speak()` gained an optional `onDone: () -> Unit = {}` callback (via `UtteranceProgressListener`), so callers can know when speech playback actually finishes rather than firing and forgetting. Default parameter preserves every existing call site.
- `feature/chatbot/ChatbotViewModel.kt` — refactored `sendMessage()`'s reply-resolution logic into one shared `resolveAndAppend(text, autoSpeak)` used by both typed and spoken input (no duplicated logic); `startListening()` now drives `VoiceState` transitions and, when a voice question is resolved, automatically speaks the answer and returns to `READY`; recognition failure sets `ERROR`. `currentSpeechLocale()` now also maps `"mr"` → `"mr-IN"` (previously only `"hi"`/`"en"` were handled — the missing Marathi STT/TTS locale gap is now closed).
- `feature/chatbot/ChatbotUiState.kt` — added `voiceState: VoiceState` (kept `isListening` for the existing mic-pulse animation, unchanged).

## 5. Languages Supported

English, Hindi (हिन्दी), Marathi (मराठी) — for both UI text (`AppStrings`, pre-existing) and voice I/O locale tags (`en-IN`/`hi-IN`/`mr-IN`, the `mr-IN` gap now fixed). Real on-device language pack availability for Marathi STT/TTS is device-dependent — this is an Android platform/OEM constraint, not something this codebase can guarantee; see §7.

## 6. Voice UI

Kept the existing simple design (large pulsing mic button, clear per-message replay button) rather than redesigning it — it already matches the "extremely simple for low-literacy users" bar. The only behavioral addition is automatic playback of a voice-asked answer.

## 7. Offline Voice — Honesty About Limits

Speech recognition/synthesis run through Android's on-device `SpeechRecognizer`/`TextToSpeech` — real, local APIs, not a cloud call from this app. However, **whether the underlying engine itself operates fully offline is device/OEM-dependent** (some devices transparently fall back to Google's network-based recognition depending on installed language packs) — this codebase cannot control or verify that from here, and this report does not claim a guarantee it can't back. What is guaranteed: neither `SpeechRecognizerManager`, `TextToSpeechManager`, nor `VoiceConversationManager`-equivalent logic in `ChatbotViewModel` ever makes a network call themselves, and the answer-resolution behind the voice loop (keyword match, then Local LLM) never silently calls a cloud LLM.

## 8. Tests

`ChatbotViewModelTest.kt` (new, 6 tests): a typed message never auto-speaks; a voice question with a keyword match is auto-spoken; a voice question with no keyword match falls back to the Local LLM and speaks its real answer; an unavailable Local LLM never fabricates a spoken reply (speaks the honest fallback-help text instead); a recognition failure sets `VoiceState.ERROR`, never `READY`/`LISTENING`; starting to listen immediately reports `LISTENING`.

**A note on test infrastructure**: `ChatbotViewModel` uses `viewModelScope`, which requires `Dispatchers.setMain()`/`resetMain()` in test setup — added via `@Before`/`@After`, a pattern not previously used in this codebase's tests (no prior ViewModel test touched `viewModelScope` directly).

## 9. Test Results / Build

Included in Phase 4E's totals: Android 110/110 tests pass, fully fresh rebuild. `./gradlew assembleDebug`: **BUILD SUCCESSFUL**.

## 10. Known Limitations / Blockers

- **BLOCKED_EXTERNAL_DEPENDENCY**: a verified fully-offline STT/TTS path (e.g. bundling Vosk) if a hard offline guarantee is required regardless of device/OEM behavior — not attempted, since it would mean adding a new native dependency without real hardware to verify it against.
- Real device testing (actual Marathi speech recognition quality, mic latency, etc.) was not possible in this environment — only the orchestration logic was tested.

---

**Stopping analysis here for 4F** — see `PHASE_4G_IMPLEMENTATION_REPORT.md` for IVR.
