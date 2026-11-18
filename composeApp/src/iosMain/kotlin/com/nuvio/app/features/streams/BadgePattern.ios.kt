package com.nuvio.app.features.streams

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSMakeRange
import platform.Foundation.NSRegularExpression
import platform.Foundation.firstMatchInString

@OptIn(ExperimentalForeignApi::class)
actual class BadgePattern actual constructor(pattern: String) {
    private val expression: NSRegularExpression =
        NSRegularExpression.regularExpressionWithPattern(pattern, options = 0u, error = null)
            ?: throw IllegalArgumentException("Invalid badge pattern")

    actual fun containsMatchIn(input: String): Boolean {
        if (input.isEmpty()) return false
        return expression.firstMatchInString(
            input,
            options = 0u,
            range = NSMakeRange(0u, input.length.toULong()),
        ) != null
    }
}
