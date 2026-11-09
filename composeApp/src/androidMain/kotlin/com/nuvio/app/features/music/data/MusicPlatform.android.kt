package com.nuvio.app.features.music.data

import java.text.Normalizer
import java.util.Locale

internal actual fun systemLanguageTag(): String =
    Locale.getDefault().toLanguageTag().ifEmpty { "en" }

internal actual fun musicNormalize(text: String, form: NormForm): String =
    Normalizer.normalize(
        text,
        when (form) {
            NormForm.NFD -> Normalizer.Form.NFD
            NormForm.NFKC -> Normalizer.Form.NFKC
        },
    )
