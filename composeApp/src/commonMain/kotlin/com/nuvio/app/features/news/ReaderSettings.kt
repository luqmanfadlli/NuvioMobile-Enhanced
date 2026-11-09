package com.nuvio.app.features.news

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.nuvio.app.features.music.data.MusicStorage

internal enum class ReaderAppearance(val label: String) { Auto("Auto"), Light("Light"), Dark("Dark"), Sepia("Sepia") }

internal enum class ReaderTypeface(val label: String) { Sans("Sans"), Serif("Serif") }

internal enum class ReaderSpacing(val label: String, val paragraphGapDp: Int, val lineHeightFactor: Float) {
    Compact("Compact", 10, 1.35f),
    Comfortable("Comfortable", 16, 1.5f),
    Spacious("Spacious", 24, 1.7f),
}

/** How the article reader looks, remembered between stories. */
internal object ReaderSettings {
    private const val STORE = "reader"
    const val MIN_SIZE_STEP = 0
    const val MAX_SIZE_STEP = 6

    private val appearanceState = mutableStateOf(readEnum("appearance", ReaderAppearance.Auto))
    private val typefaceState = mutableStateOf(readEnum("typeface", ReaderTypeface.Sans))
    private val spacingState = mutableStateOf(readEnum("spacing", ReaderSpacing.Comfortable))
    private val sizeState = mutableIntStateOf(
        (MusicStorage.getString(STORE, "size")?.toIntOrNull() ?: 3).coerceIn(MIN_SIZE_STEP, MAX_SIZE_STEP),
    )

    var appearance: ReaderAppearance
        get() = appearanceState.value
        set(value) { appearanceState.value = value; MusicStorage.putString(STORE, "appearance", value.name) }

    var typeface: ReaderTypeface
        get() = typefaceState.value
        set(value) { typefaceState.value = value; MusicStorage.putString(STORE, "typeface", value.name) }

    var spacing: ReaderSpacing
        get() = spacingState.value
        set(value) { spacingState.value = value; MusicStorage.putString(STORE, "spacing", value.name) }

    /** 0..6, 3 is the default body size; each step is 2 sp. */
    var sizeStep: Int
        get() = sizeState.intValue
        set(value) {
            val clamped = value.coerceIn(MIN_SIZE_STEP, MAX_SIZE_STEP)
            sizeState.intValue = clamped
            MusicStorage.putString(STORE, "size", clamped.toString())
        }

    private inline fun <reified E : Enum<E>> readEnum(key: String, default: E): E {
        val raw = MusicStorage.getString(STORE, key)
        return enumValues<E>().firstOrNull { it.name == raw } ?: default
    }
}
