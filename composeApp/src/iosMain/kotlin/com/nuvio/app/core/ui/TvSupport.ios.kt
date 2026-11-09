package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberIsTelevision(): Boolean = false

@Composable
internal actual fun rememberRequestPlatformFocus(): () -> Unit = {}
