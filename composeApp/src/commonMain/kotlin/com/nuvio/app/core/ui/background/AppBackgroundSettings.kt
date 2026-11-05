package com.nuvio.app.core.ui.background

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class AppBackgroundSource { NONE, LOCAL, URL }

enum class GlassUiPreset { DARK, BALANCED, VIVID }

@Serializable
internal data class AppBackgroundConfig(
    val source: AppBackgroundSource = AppBackgroundSource.NONE,
    val url: String = "",
    val localFile: String = "",
    val glassEnabled: Boolean = false,
    val glassPreset: GlassUiPreset = GlassUiPreset.BALANCED,
) {
    fun imageModel(): String? = when (source) {
        AppBackgroundSource.NONE -> null
        AppBackgroundSource.LOCAL -> localFile.takeIf { it.isNotBlank() }
            ?.let { "file://" + AppBackgroundStorage.localFilePath(it) }
        AppBackgroundSource.URL -> url.trim().takeIf {
            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
        }
    }
}

internal expect object AppBackgroundStorage {
    fun load(): String?
    fun save(value: String)

    fun localFilePath(fileName: String): String
    fun deleteLocalFile(fileName: String)
}

@Composable
internal expect fun rememberAppBackgroundImagePicker(
    onPicked: (fileName: String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit

internal object AppBackgroundRepository {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var loaded = false
    private val _config = MutableStateFlow(AppBackgroundConfig())
    val config: StateFlow<AppBackgroundConfig> = _config.asStateFlow()

    private val _suspendCount = MutableStateFlow(0)
    val suspended: StateFlow<Int> = _suspendCount.asStateFlow()

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        reload()
    }

    fun reload() {
        loaded = true
        _config.value = AppBackgroundStorage.load()
            ?.let { raw -> runCatching { json.decodeFromString(AppBackgroundConfig.serializer(), raw) }.getOrNull() }
            ?: AppBackgroundConfig()
    }

    private fun update(transform: (AppBackgroundConfig) -> AppBackgroundConfig) {
        ensureLoaded()
        val old = _config.value
        val new = transform(old)
        if (new == old) return
        _config.value = new
        AppBackgroundStorage.save(json.encodeToString(AppBackgroundConfig.serializer(), new))
        if (old.localFile.isNotBlank() && old.localFile != new.localFile) {
            runCatching { AppBackgroundStorage.deleteLocalFile(old.localFile) }
        }
    }

    fun setLocalImage(fileName: String) = update { it.copy(source = AppBackgroundSource.LOCAL, localFile = fileName) }

    fun setUrl(url: String) = update { it.copy(source = AppBackgroundSource.URL, url = url.trim(), localFile = "") }

    fun clear() = update { it.copy(source = AppBackgroundSource.NONE, localFile = "") }

    fun setGlassEnabled(enabled: Boolean) = update { it.copy(glassEnabled = enabled) }

    fun setGlassPreset(preset: GlassUiPreset) = update { it.copy(glassPreset = preset) }

    fun suspend() {
        _suspendCount.value += 1
    }

    fun resume() {
        _suspendCount.value = (_suspendCount.value - 1).coerceAtLeast(0)
    }
}
