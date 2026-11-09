package com.nuvio.app.features.player

import android.os.Build

internal actual fun supportsNativeSubtitleBlur(): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
