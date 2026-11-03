package com.nuvio.app.core.ui.background

import android.content.Context
import android.content.SharedPreferences
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.nuvio.app.core.storage.ProfileScopedKey
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal actual object AppBackgroundStorage {
    private const val preferencesName = "nuvio_app_background"
    private const val configKey = "enhanced_local_app_background"
    private const val directoryName = "app_background"

    private var preferences: SharedPreferences? = null
    private var filesDir: File? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        filesDir = context.filesDir
    }

    actual fun load(): String? = preferences?.getString(ProfileScopedKey.of(configKey), null)

    actual fun save(value: String) {
        preferences?.edit()?.putString(ProfileScopedKey.of(configKey), value)?.apply()
    }

    internal fun directory(): File? = filesDir?.let { File(it, directoryName) }

    actual fun localFilePath(fileName: String): String =
        File(directory() ?: File(directoryName), fileName).absolutePath

    actual fun deleteLocalFile(fileName: String) {
        directory()?.let { File(it, fileName).delete() }
    }
}

internal actual fun ImageRequest.Builder.softwareBitmap(): ImageRequest.Builder = allowHardware(false)

@Composable
internal actual fun rememberAppBackgroundImagePicker(
    onPicked: (fileName: String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnPicked = rememberUpdatedState(onPicked)
    val currentOnError = rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val stored = withContext(Dispatchers.IO) {
                runCatching {
                    val mime = context.contentResolver.getType(uri)
                    val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: "jpg"
                    val directory = AppBackgroundStorage.directory()?.apply { mkdirs() }
                        ?: error("Storage unavailable")
                    val name = "bg-${System.currentTimeMillis()}.$extension"
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        File(directory, name).outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Unable to read the selected image")
                    name
                }
            }
            stored
                .onSuccess { currentOnPicked.value(it) }
                .onFailure { currentOnError.value(it.message ?: "Failed to load the selected image") }
        }
    }
    return remember(launcher) {
        {
            launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
}
