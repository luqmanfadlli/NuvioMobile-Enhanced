package com.nuvio.app.features.streams

expect class BadgePattern(pattern: String) {
    fun containsMatchIn(input: String): Boolean
}
