package com.krishinirnay.core.llm.local

/**
 * Builds the exact same grounded, fact-list prompt as the server's
 * `app/routers/local_llm.py` (`_build_prompt`/`_SYSTEM_PROMPTS`/`_FACT_LABELS`),
 * ported to Kotlin so [MediaPipeOnDeviceLlmProvider] can construct a real,
 * farmer-grounded prompt with zero server involvement — required for the app
 * to provide genuine Local AI when the PC/FastAPI/Ollama are completely off.
 *
 * Only English/Hindi/Marathi are supported, matching the server. Kept as a
 * single combined text block (system rules + facts + question + instruction)
 * because [LlmInferenceSession.addQueryChunk] takes one string, unlike
 * Ollama's `/api/chat` message-role array the server uses.
 */
internal object OnDeviceLlmPromptBuilder {

    private val supportedLanguages = setOf("en", "hi", "mr")

    private val languageNames = mapOf("en" to "English", "hi" to "Hindi (हिंदी)", "mr" to "Marathi (मराठी)")

    // Mirrors local_llm.py's _GROUNDING_RULES + _SYSTEM_PROMPTS exactly —
    // do not let this drift from the server's wording, the anti-fabrication
    // contract is what keeps the model from inventing sensor/weather/market
    // facts it wasn't given.
    private val systemPrompts = mapOf(
        "en" to (
            "You are KrishiNirnay's farm assistant. Facts about the farmer's field are " +
                "already computed by the app's deterministic decision engine and given to " +
                "you below — you explain them in simple, encouraging language. You must " +
                "NEVER invent sensor readings, weather, market prices, pest or disease " +
                "diagnoses, fertilizer or pesticide dosages, or government schemes beyond " +
                "what is listed. If something needed isn't in the facts, say it is not " +
                "available instead of guessing. Keep answers short and practical. " +
                "Answer in clear, simple English suitable for an Indian farmer."
            ),
        "hi" to (
            "उत्तर केवल सरल और स्पष्ट हिंदी में दें। अंग्रेज़ी में fallback न करें। " +
                "किसान को सीधे उपयोगी सलाह दें। अपनी भाषा, अनुवाद, या किसी भी meta " +
                "टिप्पणी का उल्लेख न करें — सीधे उत्तर से शुरू करें। नीचे दिए गए तथ्यों के " +
                "अलावा कुछ भी न बताएं — कोई नई सेंसर रीडिंग, मौसम, बाज़ार भाव, रोग, " +
                "उर्वरक मात्रा या योजना का आविष्कार न करें। यदि कोई जानकारी सूची में नहीं " +
                "है, तो कहें कि वह उपलब्ध नहीं है। उत्तर छोटा और व्यावहारिक रखें।"
            ),
        "mr" to (
            "उत्तर फक्त सोप्या आणि स्पष्ट मराठी भाषेत द्या. इंग्रजीमध्ये fallback करू " +
                "नका. शेतकऱ्याला थेट उपयोगी सल्ला द्या. तुमची भाषा, भाषांतर किंवा " +
                "कोणत्याही meta टिप्पणीचा उल्लेख करू नका — थेट उत्तराने सुरुवात करा. " +
                "खालील तथ्यांशिवाय काहीही सांगू नका — नवीन सेन्सर रीडिंग, हवामान, " +
                "बाजारभाव, रोग, खतांचे प्रमाण किंवा योजना तयार करू नका. यादीत नसलेली " +
                "माहिती \"उपलब्ध नाही\" असे सांगा. उत्तर लहान आणि व्यवहार्य ठेवा."
            ),
    )

    private data class FactLabels(
        val location: String, val crop: String, val stage: String, val farmingMethod: String,
        val soil: String, val moisture: String, val sensors: String, val temperature: String,
        val humidity: String, val weather: String, val market: String, val risk: String,
        val pest: String, val disease: String, val fertilizer: String, val recommendation: String,
        val reasons: String, val unknown: String, val notAvailable: String, val notAssessed: String,
        val none: String, val farmerAsks: String, val instruction: String,
    )

    private val factLabels = mapOf(
        "en" to FactLabels(
            location = "Location", crop = "Crop", stage = "stage", farmingMethod = "Farming method",
            soil = "Soil", moisture = "moisture", sensors = "Sensors", temperature = "temperature",
            humidity = "humidity", weather = "Weather", market = "Market", risk = "Overall risk",
            pest = "Pest", disease = "Disease", fertilizer = "Fertilizer", recommendation = "Recommendation",
            reasons = "Reasons", unknown = "unknown", notAvailable = "not available",
            notAssessed = "not assessed", none = "none", farmerAsks = "The farmer asks",
            instruction = "Answer using ONLY the facts above, in the farmer's language ({lang}). " +
                "If something needed isn't listed, say it isn't available. Reply in at most 2 short sentences.",
        ),
        "hi" to FactLabels(
            location = "स्थान", crop = "फसल", stage = "चरण", farmingMethod = "खेती की विधि",
            soil = "मिट्टी", moisture = "नमी", sensors = "सेंसर", temperature = "तापमान",
            humidity = "आर्द्रता", weather = "मौसम", market = "बाज़ार", risk = "कुल जोखिम",
            pest = "कीट", disease = "रोग", fertilizer = "उर्वरक", recommendation = "सिफारिश",
            reasons = "कारण", unknown = "अज्ञात", notAvailable = "उपलब्ध नहीं",
            notAssessed = "आकलन नहीं किया गया", none = "कोई नहीं", farmerAsks = "किसान पूछता है",
            instruction = "केवल ऊपर दिए गए तथ्यों का उपयोग करके हिंदी में उत्तर दें। यदि कोई जानकारी सूची में नहीं है, " +
                "तो कहें कि वह उपलब्ध नहीं है। उत्तर अधिकतम 2 छोटे वाक्यों में दें।",
        ),
        "mr" to FactLabels(
            location = "स्थान", crop = "पीक", stage = "अवस्था", farmingMethod = "शेतीची पद्धत",
            soil = "माती", moisture = "ओलावा", sensors = "सेन्सर", temperature = "तापमान",
            humidity = "आर्द्रता", weather = "हवामान", market = "बाजार", risk = "एकूण धोका",
            pest = "कीड", disease = "रोग", fertilizer = "खत", recommendation = "शिफारस",
            reasons = "कारणे", unknown = "अज्ञात", notAvailable = "उपलब्ध नाही",
            notAssessed = "मूल्यांकन केलेले नाही", none = "काहीही नाही", farmerAsks = "शेतकरी विचारतो",
            instruction = "फक्त वरील तथ्यांचा वापर करून मराठीत उत्तर द्या. यादीत नसलेली कोणतीही माहिती \"उपलब्ध नाही\" असे " +
                "सांगा. उत्तर जास्तीत जास्त 2 लहान वाक्यांत द्या.",
        ),
    )

    /** Any unrecognised/malformed value falls back to English, same as the server's `_normalize_language`. */
    fun normalizeLanguage(raw: String): String {
        val code = raw.trim().lowercase().take(2)
        return if (code in supportedLanguages) code else "en"
    }

    fun build(message: String, context: LocalLlmContextDto): String {
        val language = normalizeLanguage(context.language)
        val t = factLabels.getValue(language)
        val unknown = t.unknown

        val facts = buildList {
            add("${t.location}: ${context.farmer_district.orDash()}, ${context.farmer_state.orDash()}")
            add("${t.crop}: ${context.crop ?: unknown} (${t.stage}: ${context.crop_stage ?: unknown})")
            add("${t.farmingMethod}: ${context.farming_method ?: unknown}")
            add(
                "${t.soil}: ${context.soil_type ?: unknown}, ${t.moisture} " +
                    "${context.soil_moisture_pct?.toDisplay() ?: unknown}%",
            )
            add(
                "${t.sensors}: ${t.temperature} ${context.temperature_c?.toDisplay() ?: unknown}C, " +
                    "${t.humidity} ${context.humidity_pct?.toDisplay() ?: unknown}%",
            )
            add("${t.weather} (${context.weather_status ?: "UNAVAILABLE"}): ${context.weather_summary ?: t.notAvailable}")
            add("${t.market} (${context.market_status ?: "UNAVAILABLE"}): ${context.market_summary ?: t.notAvailable}")
            add("${t.risk}: ${context.overall_risk ?: unknown}")
            add("${t.pest}: ${context.pest_summary ?: t.notAssessed}")
            add("${t.disease}: ${context.disease_summary ?: t.notAssessed}")
            add("${t.fertilizer}: ${context.fertilizer_summary ?: t.notAssessed}")
            add("${t.recommendation}: ${context.recommendation_summary ?: t.none}")
            if (context.reasons.isNotEmpty()) {
                add(t.reasons + ":\n" + context.reasons.joinToString("\n") { "- $it" })
            }
        }

        val instruction = t.instruction.replace("{lang}", languageNames.getValue(language))
        return systemPrompts.getValue(language) + "\n\n" +
            facts.joinToString("\n") + "\n\n" +
            "${t.farmerAsks}: \"$message\"\n" + instruction
    }

    private fun String?.orDash(): String = this?.takeIf { it.isNotBlank() } ?: "-"

    /** Drops a trailing ".0" for a whole-number reading (e.g. 18f -> "18" not "18.0"), matching the app's other display formatting. */
    private fun Float.toDisplay(): String = if (this == toInt().toFloat()) toInt().toString() else toString()
}
