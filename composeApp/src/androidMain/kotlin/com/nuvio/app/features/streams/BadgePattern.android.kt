package com.nuvio.app.features.streams

actual class BadgePattern actual constructor(pattern: String) {
    private val regex = Regex(pattern)

    actual fun containsMatchIn(input: String): Boolean =
        try {
            regex.containsMatchIn(input)
        } catch (_: StackOverflowError) {
            false
        }
}
