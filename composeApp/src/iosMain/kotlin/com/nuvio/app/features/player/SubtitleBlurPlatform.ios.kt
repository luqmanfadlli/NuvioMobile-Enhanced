package com.nuvio.app.features.player

// Compose's RenderEffect-backed blur is not used on iOS in this Android-focused renderer path.
internal actual fun supportsNativeSubtitleBlur(): Boolean = false
