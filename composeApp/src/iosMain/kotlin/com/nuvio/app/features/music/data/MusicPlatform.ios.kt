package com.nuvio.app.features.music.data

import platform.Foundation.NSLocale
import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCanonicalMapping
import platform.Foundation.precomposedStringWithCompatibilityMapping
import platform.Foundation.preferredLanguages

internal actual fun systemLanguageTag(): String =
    (NSLocale.preferredLanguages.firstOrNull() as? String)?.ifBlank { null } ?: "en"

internal actual fun musicNormalize(text: String, form: NormForm): String {
    val ns = NSString.create(string = text)
    return when (form) {
        NormForm.NFD -> ns.decomposedStringWithCanonicalMapping
        NormForm.NFKC -> ns.precomposedStringWithCompatibilityMapping
    }
}
