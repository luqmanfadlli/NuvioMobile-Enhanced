package com.nuvio.app.features.settings

import androidx.compose.runtime.Composable

internal class SettingsBackgroundImagePicker internal constructor(
    private val launchPicker: () -> Unit,
) {
    fun launch() = launchPicker()
}

@Composable
internal expect fun rememberSettingsBackgroundImagePicker(
    onImageSelected: (ByteArray) -> Unit,
    onError: () -> Unit,
): SettingsBackgroundImagePicker
