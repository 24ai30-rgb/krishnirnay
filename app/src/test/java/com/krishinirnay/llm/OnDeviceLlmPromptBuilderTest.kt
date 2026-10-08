package com.krishinirnay.llm

import com.krishinirnay.core.llm.local.LocalLlmContextDto
import com.krishinirnay.core.llm.local.OnDeviceLlmPromptBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [OnDeviceLlmPromptBuilder] is the Kotlin port of the server's
 * `_build_prompt`/`_SYSTEM_PROMPTS` (see `server/app/routers/local_llm.py`)
 * — this is what lets the on-device path build a real, farmer-grounded
 * prompt with zero server involvement. These tests only need to prove the
 * real facts land in the prompt text and nothing is silently dropped; the
 * server's own tests already cover the underlying grounding-rule wording.
 */
class OnDeviceLlmPromptBuilderTest {

    private fun context(language: String = "en") = LocalLlmContextDto(
        language = language,
        farmer_state = "Maharashtra",
        farmer_district = "Kolhapur",
        crop = "Soybean",
        crop_stage = "Flowering",
        soil_type = "Black Soil",
        soil_moisture_pct = 18f,
        temperature_c = 34f,
        overall_risk = "MEDIUM",
        recommendation_summary = "Irrigate within 24 hours",
        reasons = listOf("Soil moisture trending low"),
    )

    @Test
    fun `every real fact from the context appears in the prompt, never dropped`() {
        val prompt = OnDeviceLlmPromptBuilder.build("What should I do?", context())

        assertTrue(prompt.contains("Kolhapur"))
        assertTrue(prompt.contains("Maharashtra"))
        assertTrue(prompt.contains("Soybean"))
        assertTrue(prompt.contains("Flowering"))
        assertTrue(prompt.contains("Black Soil"))
        assertTrue(prompt.contains("18"))
        assertTrue(prompt.contains("34"))
        assertTrue(prompt.contains("MEDIUM"))
        assertTrue(prompt.contains("Irrigate within 24 hours"))
        assertTrue(prompt.contains("Soil moisture trending low"))
        assertTrue(prompt.contains("What should I do?"))
    }

    @Test
    fun `a missing fact is reported as unknown or not available, never invented`() {
        val prompt = OnDeviceLlmPromptBuilder.build("How is my field?", LocalLlmContextDto(language = "en"))

        assertTrue(prompt.contains("unknown"))
        assertTrue(prompt.contains("not available") || prompt.contains("not assessed"))
    }

    @Test
    fun `Hindi context produces a prompt written in Hindi, never English fallback text`() {
        val prompt = OnDeviceLlmPromptBuilder.build("Aaj kya karna chahiye?", context(language = "hi"))

        assertTrue(prompt.contains("हिंदी"))
        assertFalse(prompt.contains("Answer in clear, simple English"))
    }

    @Test
    fun `Marathi context produces a prompt written in Marathi, never English fallback text`() {
        val prompt = OnDeviceLlmPromptBuilder.build("Kay karave?", context(language = "mr"))

        assertTrue(prompt.contains("मराठी"))
        assertFalse(prompt.contains("Answer in clear, simple English"))
    }

    @Test
    fun `an unsupported or malformed language code normalizes to English, never crashes`() {
        // Matches the server's own _normalize_language exactly: a region
        // suffix doesn't matter since only the first two characters are
        // read ("hi-IN" -> "hi", a real supported language), but a
        // genuinely unsupported code or blank value falls back to English.
        assertEquals("hi", OnDeviceLlmPromptBuilder.normalizeLanguage("hi-IN"))
        assertEquals("en", OnDeviceLlmPromptBuilder.normalizeLanguage(""))
        assertEquals("en", OnDeviceLlmPromptBuilder.normalizeLanguage("fr"))
        assertEquals("hi", OnDeviceLlmPromptBuilder.normalizeLanguage("HI"))
    }
}
