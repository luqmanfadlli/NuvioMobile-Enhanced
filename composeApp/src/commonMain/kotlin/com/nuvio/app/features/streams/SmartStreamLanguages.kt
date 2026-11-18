package com.nuvio.app.features.streams

import com.nuvio.app.features.player.DeviceLanguagePreferences
import com.nuvio.app.features.player.PlayerSettingsRepository
import com.nuvio.app.features.player.resolvePreferredAudioLanguageTargets

internal data class SmartStreamLanguages(
    val codes: Set<String>,
    val multiAudio: Boolean,
)

internal object SmartLanguageReader {
    private val tokenCodes: Map<String, String> = buildMap {
        fun add(code: String, vararg tokens: String) = tokens.forEach { put(it, code) }
        add("en", "eng", "english")
        add("it", "ita", "italian", "italiano")
        add("es", "spa", "spanish", "espanol", "español", "castellano", "latino")
        add("fr", "fre", "fra", "french", "francais", "français", "vff", "vfq", "truefrench")
        add("de", "ger", "deu", "german", "deutsch")
        add("pt", "por", "portuguese", "portugues", "português", "dublado")
        add("id", "ind", "indonesian", "indonesia")
        add("ja", "jpn", "japanese")
        add("ko", "kor", "korean")
        add("zh", "zho", "chinese", "mandarin", "cantonese")
        add("hi", "hin", "hindi")
        add("ru", "rus", "russian")
        add("ar", "ara", "arabic")
        add("tr", "tur", "turkish")
        add("nl", "dut", "nld", "dutch")
        add("pl", "pol", "polish")
        add("sv", "swe", "swedish")
        add("th", "tha", "thai")
        add("vi", "vie", "vietnamese")
        add("ms", "msa", "malay")
        add("ta", "tam", "tamil")
        add("te", "tel", "telugu")
        add("uk", "ukr", "ukrainian")
        add("he", "heb", "hebrew")
        add("cs", "cze", "ces", "czech")
        add("hu", "hun", "hungarian")
        add("el", "gre", "ell", "greek")
        add("da", "dan", "danish")
        add("no", "nor", "norwegian")
        add("fi", "fin", "finnish")
        add("ro", "rum", "ron", "romanian")
    }

    private val flagCodes: Map<String, String> = mapOf(
        "🇬🇧" to "en", "🇺🇸" to "en", "🇮🇹" to "it", "🇪🇸" to "es", "🇲🇽" to "es",
        "🇫🇷" to "fr", "🇩🇪" to "de", "🇵🇹" to "pt", "🇧🇷" to "pt", "🇮🇩" to "id",
        "🇯🇵" to "ja", "🇰🇷" to "ko", "🇨🇳" to "zh", "🇹🇼" to "zh", "🇭🇰" to "zh",
        "🇮🇳" to "hi", "🇷🇺" to "ru", "🇸🇦" to "ar", "🇹🇷" to "tr", "🇳🇱" to "nl",
        "🇵🇱" to "pl", "🇸🇪" to "sv", "🇹🇭" to "th", "🇻🇳" to "vi", "🇲🇾" to "ms",
        "🇺🇦" to "uk", "🇮🇱" to "he", "🇨🇿" to "cs", "🇭🇺" to "hu", "🇬🇷" to "el",
        "🇩🇰" to "da", "🇳🇴" to "no", "🇫🇮" to "fi", "🇷🇴" to "ro",
    )

    private val multiTokens = setOf("multi", "dual", "multiaudio", "dualaudio")

    fun read(raw: String, text: String, parsedLanguages: List<String>): SmartStreamLanguages {
        val codes = linkedSetOf<String>()
        parsedLanguages.forEach { language ->
            val key = language.trim().lowercase()
            (tokenCodes[key] ?: key.takeIf { it.length == 2 })?.let(codes::add)
        }
        flagCodes.forEach { (flag, code) -> if (raw.contains(flag)) codes += code }
        val tokens = text.split(' ').filter { it.isNotBlank() }
        tokens.forEach { token -> tokenCodes[token]?.let(codes::add) }
        val multi = tokens.any { it in multiTokens }
        return SmartStreamLanguages(codes = codes, multiAudio = multi)
    }

    fun preferredLanguages(): List<String> {
        val settings = PlayerSettingsRepository.uiState.value
        return resolvePreferredAudioLanguageTargets(
            preferredAudioLanguage = settings.preferredAudioLanguage,
            secondaryPreferredAudioLanguage = settings.secondaryPreferredAudioLanguage,
            deviceLanguages = DeviceLanguagePreferences.preferredLanguageCodes(),
        ).map { it.substringBefore('-') }.distinct()
    }
}
