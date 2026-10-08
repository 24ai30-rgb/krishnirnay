package com.krishinirnay.feature.chatbot

import com.krishinirnay.core.data.model.AppMode
import com.krishinirnay.core.data.model.DataSourceStatus
import com.krishinirnay.core.data.model.DecisionOutput
import com.krishinirnay.core.data.model.DeviceStatus
import com.krishinirnay.core.data.model.FarmerProfile
import com.krishinirnay.core.data.model.FieldState
import com.krishinirnay.core.data.model.MarketState
import com.krishinirnay.core.data.model.RiskLevel
import com.krishinirnay.core.data.model.SensorReading
import com.krishinirnay.core.data.model.WeatherCondition
import com.krishinirnay.core.data.model.WeatherState
import com.krishinirnay.core.data.repository.FieldStateRepository
import com.krishinirnay.core.data.repository.MarketRepository
import com.krishinirnay.core.data.repository.ProfileRepository
import com.krishinirnay.core.data.repository.SchemesRepository
import com.krishinirnay.core.data.repository.SettingsRepository
import com.krishinirnay.core.data.repository.WeatherRepository
import com.krishinirnay.core.decision.RecommendationOutcome
import com.krishinirnay.core.llm.local.AiProviderKind
import com.krishinirnay.core.llm.local.LocalLlmContextBuilder
import com.krishinirnay.core.llm.local.LocalLlmContextDto
import com.krishinirnay.core.llm.local.LocalLlmRepository
import com.krishinirnay.core.llm.local.LocalLlmResult
import com.krishinirnay.core.llm.local.LocalLlmStatus
import com.krishinirnay.core.llm.local.LocalLlmStreamEvent
import com.krishinirnay.core.voice.SpeechRecognizerManager
import com.krishinirnay.core.voice.TextToSpeechManager
import com.krishinirnay.core.voice.VoiceState
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Proves the Phase 4E/4F wiring: a question asked by voice is auto-spoken
 * once resolved (a typed question never is), the honest Local LLM fallback
 * never fabricates a reply, and voice state transitions (LISTENING ->
 * PROCESSING -> SPEAKING -> READY, or -> ERROR on recognition failure) are
 * real, not decorative.
 */
class ChatbotViewModelTest {

    // ChatbotViewModel uses viewModelScope (init{} + Local LLM calls), which needs
    // Dispatchers.Main set — there's no real Android main thread in a unit test.
    @Before
    fun setMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private fun fieldState() = FieldState(
        fieldId = "test-field",
        sensors = SensorReading(soilMoisturePct = 50f, temperatureC = 25f, humidityPct = 55f, timestamp = Instant.EPOCH),
        deviceStatus = DeviceStatus(isOnline = true, lastSeenAt = Instant.EPOCH),
        decision = DecisionOutput(
            overallRisk = RiskLevel.LOW, waterStressRisk = RiskLevel.LOW, heatRisk = RiskLevel.LOW,
            cropHealthRisk = RiskLevel.UNKNOWN, recommendation = RecommendationOutcome.HealthyRange,
            confidence = 1f, reasons = emptyList(),
        ),
        dataSource = AppMode.MOCK,
    )

    private fun weather() = WeatherState(
        locationLabel = "", currentTempC = 28, condition = WeatherCondition.CLOUDY, windKph = 0,
        humidityPct = 50, rainChancePct = 0, rainInHoursLabel = "-", daily = emptyList(),
        status = DataSourceStatus.UNAVAILABLE,
    )

    private fun market() = MarketState(
        crop = "Cotton", market = null, location = null, currentPricePerQuintal = null,
        minPricePerQuintal = null, maxPricePerQuintal = null, averagePricePerQuintal = null,
        fetchedAt = null, source = null, status = DataSourceStatus.UNAVAILABLE,
    )

    private fun profile() = FarmerProfile(name = "Test", phone = "0", location = "", farmSizeAcres = 1f, crops = listOf("Cotton"))

    private fun viewModel(
        speechOnResult: String? = "should i do",
        speechError: Boolean = false,
        localLlmResult: LocalLlmResult = LocalLlmResult.Unavailable,
        askStreamProvider: (() -> Flow<LocalLlmStreamEvent>)? = null,
    ): Triple<ChatbotViewModel, SpeechRecognizerManager, TextToSpeechManager> {
        val fieldStateRepository = mockk<FieldStateRepository>()
        every { fieldStateRepository.fieldState } returns MutableStateFlow(fieldState())

        val weatherRepository = mockk<WeatherRepository>()
        every { weatherRepository.weather } returns MutableStateFlow(weather())

        val marketRepository = mockk<MarketRepository>()
        every { marketRepository.market } returns MutableStateFlow(market())

        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profile())

        val schemesRepository = mockk<SchemesRepository>()
        every { schemesRepository.schemes } returns MutableStateFlow(emptyList())

        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.language } returns MutableStateFlow("en")

        val localLlmRepository = mockk<LocalLlmRepository>()
        every { localLlmRepository.status } returns MutableStateFlow(LocalLlmStatus.READY)
        every { localLlmRepository.activeProviderKind } returns MutableStateFlow(AiProviderKind.SERVER)
        // ChatbotViewModel's init fires a background refreshStatus() so the
        // status shown is fresh as of opening Chat (Phase 5 Part 4) — never
        // awaited before a message can be sent, but still a real call this
        // mock must answer.
        coEvery { localLlmRepository.refreshStatus() } returns Unit
        if (askStreamProvider != null) {
            every { localLlmRepository.askStream(any(), any()) } answers { askStreamProvider() }
        } else {
            val streamEvent = when (localLlmResult) {
                is LocalLlmResult.Answered -> LocalLlmStreamEvent.Final(success = true, answer = localLlmResult.reply, error = null, elapsedMs = 100)
                LocalLlmResult.Unavailable -> LocalLlmStreamEvent.Final(success = false, answer = null, error = "unavailable", elapsedMs = null)
            }
            every { localLlmRepository.askStream(any(), any()) } returns flowOf(streamEvent)
        }

        val speechRecognizerManager = mockk<SpeechRecognizerManager>(relaxed = true)
        every { speechRecognizerManager.startListening(any(), any(), any()) } answers {
            if (speechError) {
                secondArg<(Int) -> Unit>()
                thirdArg<(Int) -> Unit>().invoke(1)
            } else {
                secondArg<(String) -> Unit>().invoke(speechOnResult ?: "")
            }
        }

        val textToSpeechManager = mockk<TextToSpeechManager>(relaxed = true)
        every { textToSpeechManager.speak(any(), any(), any()) } answers {
            thirdArg<() -> Unit>().invoke()
        }

        val vm = ChatbotViewModel(
            fieldStateRepository = fieldStateRepository,
            weatherRepository = weatherRepository,
            marketRepository = marketRepository,
            profileRepository = profileRepository,
            schemesRepository = schemesRepository,
            speechRecognizerManager = speechRecognizerManager,
            textToSpeechManager = textToSpeechManager,
            settingsRepository = settingsRepository,
            localLlmRepository = localLlmRepository,
            localLlmContextBuilder = LocalLlmContextBuilder(),
        )
        return Triple(vm, speechRecognizerManager, textToSpeechManager)
    }

    @Test
    fun `a typed message never auto-speaks the reply`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, tts) = viewModel()
        vm.onInputChange("what to do")
        vm.sendMessage()

        assertEquals(3, vm.uiState.value.messages.size) // welcome + user + assistant
        verify(exactly = 0) { tts.speak(any(), any(), any()) }
        assertEquals(VoiceState.READY, vm.uiState.value.voiceState)
    }

    @Test
    fun `a voice question with a keyword match is auto-spoken`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, tts) = viewModel(speechOnResult = "what to do")
        vm.startListening()

        verify(exactly = 1) { tts.speak(any(), any(), any()) }
        assertEquals(VoiceState.READY, vm.uiState.value.voiceState)
        assertFalse(vm.uiState.value.isListening)
    }

    @Test
    fun `a voice question with no keyword match falls back to the Local LLM and speaks its answer`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, _, tts) = viewModel(
                speechOnResult = "kapus la pani kadhi dyayacha",
                localLlmResult = LocalLlmResult.Answered("Irrigate lightly this evening."),
            )
            vm.startListening()

            val spoken = slot<String>()
            verify { tts.speak(capture(spoken), any(), any()) }
            assertEquals("Irrigate lightly this evening.", spoken.captured)
            assertTrue(vm.uiState.value.messages.any { it.text == "Irrigate lightly this evening." })
        }

    @Test
    fun `an unavailable Local LLM never fabricates a spoken reply`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, tts) = viewModel(speechOnResult = "kapus la pani kadhi dyayacha", localLlmResult = LocalLlmResult.Unavailable)
        vm.startListening()

        val spoken = slot<String>()
        verify { tts.speak(capture(spoken), any(), any()) }
        // The honest fallback-help text is spoken, never an invented answer.
        assertTrue(vm.uiState.value.messages.last().text.isNotBlank())
        assertEquals(vm.uiState.value.messages.last().text, spoken.captured)
    }

    @Test
    fun `a recognition failure sets voice state to ERROR, never READY or LISTENING`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = viewModel(speechError = true)
        vm.startListening()

        assertEquals(VoiceState.ERROR, vm.uiState.value.voiceState)
        assertFalse(vm.uiState.value.isListening)
    }

    @Test
    fun `starting to listen immediately reports LISTENING`() = runTest(UnconfinedTestDispatcher()) {
        // speechOnResult = null makes startListening's mock answer invoke onResult(""),
        // which resolves synchronously back to READY under UnconfinedTestDispatcher — this
        // test only needs to prove the *initial* state update fired before that happens,
        // which is the state ChatbotScreen's mic-pulse animation actually keys off.
        val fieldStateRepository = mockk<FieldStateRepository>()
        every { fieldStateRepository.fieldState } returns MutableStateFlow(fieldState())
        val weatherRepository = mockk<WeatherRepository>()
        every { weatherRepository.weather } returns MutableStateFlow(weather())
        val marketRepository = mockk<MarketRepository>()
        every { marketRepository.market } returns MutableStateFlow(market())
        val profileRepository = mockk<ProfileRepository>()
        every { profileRepository.profile } returns MutableStateFlow(profile())
        val schemesRepository = mockk<SchemesRepository>()
        every { schemesRepository.schemes } returns MutableStateFlow(emptyList())
        val settingsRepository = mockk<SettingsRepository>()
        every { settingsRepository.language } returns MutableStateFlow("en")
        val localLlmRepository = mockk<LocalLlmRepository>()
        every { localLlmRepository.status } returns MutableStateFlow(LocalLlmStatus.READY)
        every { localLlmRepository.activeProviderKind } returns MutableStateFlow(AiProviderKind.SERVER)
        // ChatbotViewModel's init fires a background refreshStatus() so the
        // status shown is fresh as of opening Chat (Phase 5 Part 4) — never
        // awaited before a message can be sent, but still a real call this
        // mock must answer.
        coEvery { localLlmRepository.refreshStatus() } returns Unit
        val speechRecognizerManager = mockk<SpeechRecognizerManager>(relaxed = true)
        val observedListeningState = mutableListOf<Boolean>()
        every { speechRecognizerManager.startListening(any(), any(), any()) } answers {
            observedListeningState += true // startListening was called only after isListening flipped true
        }
        val textToSpeechManager = mockk<TextToSpeechManager>(relaxed = true)

        val vm = ChatbotViewModel(
            fieldStateRepository, weatherRepository, marketRepository, profileRepository,
            schemesRepository, speechRecognizerManager, textToSpeechManager, settingsRepository,
            localLlmRepository, LocalLlmContextBuilder(),
        )
        vm.startListening()

        assertTrue(observedListeningState.isNotEmpty())
        assertEquals(VoiceState.LISTENING, vm.uiState.value.voiceState)
    }

    // ------------------------------------------------------- streaming (Part 1)

    @Test
    fun `a non-keyword question streams deltas into the assistant bubble progressively`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = viewModel(
            askStreamProvider = {
                flowOf(
                    LocalLlmStreamEvent.Delta("Irrigate "),
                    LocalLlmStreamEvent.Delta("lightly this evening."),
                    LocalLlmStreamEvent.Final(success = true, answer = "Irrigate lightly this evening.", error = null, elapsedMs = 500),
                )
            },
        )
        vm.onInputChange("kapus la pani kadhi dyayacha")
        vm.sendMessage()

        val assistantMessage = vm.uiState.value.messages.last()
        assertEquals("Irrigate lightly this evening.", assistantMessage.text)
        assertFalse(assistantMessage.isGenerating)
        assertFalse(assistantMessage.isError)
        assertFalse(vm.uiState.value.isGenerating)
    }

    @Test
    fun `the assistant bubble shows a generating state as soon as the question is asked`() = runTest(UnconfinedTestDispatcher()) {
        val (vm, _, _) = viewModel(
            askStreamProvider = {
                flow {
                    emit(LocalLlmStreamEvent.Delta("still going"))
                    awaitCancellation()
                }
            },
        )
        vm.onInputChange("kapus la pani kadhi dyayacha")
        vm.sendMessage()

        assertTrue(vm.uiState.value.isGenerating)
        assertTrue(vm.uiState.value.messages.last().isGenerating)
    }

    @Test
    fun `cancelGeneration stops the stream and marks the bubble stopped, never keeping the unvalidated partial text`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, _, _) = viewModel(
                askStreamProvider = {
                    flow {
                        emit(LocalLlmStreamEvent.Delta("Partial and unvalidated"))
                        awaitCancellation()
                    }
                },
            )
            vm.onInputChange("kapus la pani kadhi dyayacha")
            vm.sendMessage()
            assertTrue(vm.uiState.value.isGenerating)

            vm.cancelGeneration()

            assertFalse(vm.uiState.value.isGenerating)
            val assistantMessage = vm.uiState.value.messages.last()
            assertFalse(assistantMessage.isGenerating)
            assertTrue(assistantMessage.isError)
            assertFalse(assistantMessage.text.contains("Partial and unvalidated"))
        }

    @Test
    fun `retryMessage re-runs generation for the paired question and replaces the same bubble, not a new one`() =
        runTest(UnconfinedTestDispatcher()) {
            var callCount = 0
            val (vm, _, _) = viewModel(
                askStreamProvider = {
                    callCount++
                    flowOf(LocalLlmStreamEvent.Final(success = true, answer = "Answer #$callCount", error = null, elapsedMs = 100))
                },
            )
            vm.onInputChange("kapus la pani kadhi dyayacha")
            vm.sendMessage()
            val messageCountBeforeRetry = vm.uiState.value.messages.size
            val assistantId = vm.uiState.value.messages.last().id

            vm.retryMessage(assistantId)

            assertEquals(messageCountBeforeRetry, vm.uiState.value.messages.size)
            assertEquals("Answer #2", vm.uiState.value.messages.last().text)
        }

    @Test
    fun `clearChat resets the transcript to just the welcome message and cancels any in-flight generation`() =
        runTest(UnconfinedTestDispatcher()) {
            val (vm, _, _) = viewModel(
                askStreamProvider = {
                    flow {
                        emit(LocalLlmStreamEvent.Delta("still going"))
                        awaitCancellation()
                    }
                },
            )
            vm.onInputChange("kapus la pani kadhi dyayacha")
            vm.sendMessage()
            assertTrue(vm.uiState.value.isGenerating)

            vm.clearChat()

            assertEquals(1, vm.uiState.value.messages.size)
            assertFalse(vm.uiState.value.isGenerating)
        }

    @Test
    fun `cancelling generation then immediately sending a new message never leaves a zombie generating bubble`() =
        runTest(UnconfinedTestDispatcher()) {
            var callCount = 0
            val (vm, _, _) = viewModel(
                askStreamProvider = {
                    callCount++
                    if (callCount == 1) {
                        flow {
                            emit(LocalLlmStreamEvent.Delta("first, never finishes"))
                            awaitCancellation()
                        }
                    } else {
                        flowOf(LocalLlmStreamEvent.Final(success = true, answer = "second answer", error = null, elapsedMs = 50))
                    }
                },
            )
            vm.onInputChange("kapus la pani kadhi dyayacha")
            vm.sendMessage()
            assertTrue(vm.uiState.value.isGenerating)

            vm.cancelGeneration()
            vm.onInputChange("second question")
            vm.sendMessage()

            assertFalse(vm.uiState.value.isGenerating)
            assertEquals("second answer", vm.uiState.value.messages.last().text)
            assertFalse(vm.uiState.value.messages.last().isGenerating)
            // Only one bubble is still marked generating: none — the cancelled first request never resurrects itself.
            assertTrue(vm.uiState.value.messages.none { it.isGenerating })
        }

    @Test
    fun `when neither provider is available, the honest both-unavailable message is shown, never a generic failure`() =
        runTest(UnconfinedTestDispatcher()) {
            val fieldStateRepository = mockk<FieldStateRepository>()
            every { fieldStateRepository.fieldState } returns MutableStateFlow(fieldState())
            val weatherRepository = mockk<WeatherRepository>()
            every { weatherRepository.weather } returns MutableStateFlow(weather())
            val marketRepository = mockk<MarketRepository>()
            every { marketRepository.market } returns MutableStateFlow(market())
            val profileRepository = mockk<ProfileRepository>()
            every { profileRepository.profile } returns MutableStateFlow(profile())
            val schemesRepository = mockk<SchemesRepository>()
            every { schemesRepository.schemes } returns MutableStateFlow(emptyList())
            val settingsRepository = mockk<SettingsRepository>()
            every { settingsRepository.language } returns MutableStateFlow("en")
            val localLlmRepository = mockk<LocalLlmRepository>()
            every { localLlmRepository.status } returns MutableStateFlow(LocalLlmStatus.MODEL_MISSING)
            every { localLlmRepository.activeProviderKind } returns MutableStateFlow(AiProviderKind.NONE)
            coEvery { localLlmRepository.refreshStatus() } returns Unit
            every { localLlmRepository.askStream(any(), any()) } returns flowOf(
                LocalLlmStreamEvent.Final(success = false, answer = null, error = "no provider ready", elapsedMs = null),
            )
            val speechRecognizerManager = mockk<SpeechRecognizerManager>(relaxed = true)
            val textToSpeechManager = mockk<TextToSpeechManager>(relaxed = true)
            val vm = ChatbotViewModel(
                fieldStateRepository, weatherRepository, marketRepository, profileRepository,
                schemesRepository, speechRecognizerManager, textToSpeechManager, settingsRepository,
                localLlmRepository, LocalLlmContextBuilder(),
            )

            vm.onInputChange("kapus la pani kadhi dyayacha")
            vm.sendMessage()

            val expected = com.krishinirnay.core.designsystem.strings.appStringsFor("en").localAiBothUnavailable
            assertEquals(expected, vm.uiState.value.messages.last().text)
            assertTrue(vm.uiState.value.messages.last().isError)
        }
}
