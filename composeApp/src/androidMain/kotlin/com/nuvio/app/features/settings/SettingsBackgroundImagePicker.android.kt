package com.nuvio.app.features.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MaximumBackgroundImageDimensionPx = 2400

@Composable
internal actual fun rememberSettingsBackgroundImagePicker(
    onImageSelected: (ByteArray) -> Unit,
    onError: () -> Unit,
): SettingsBackgroundImagePicker {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = readSettingsBackgroundImageResult {
                withContext(Dispatchers.IO) { readAndNormalizeBackgroundImage(context, uri) }
            }
            result.onSuccess(onImageSelected).onFailure { onError() }
        }
    }

    return remember(launcher) {
        SettingsBackgroundImagePicker {
            launcher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
}


/** Treat a cancelled picker operation as cancellation, not as an image-processing error. */
internal suspend fun <T> readSettingsBackgroundImageResult(
    read: suspend () -> T,
): Result<T> = try {
    Result.success(read())
} catch (cancellation: CancellationException) {
    throw cancellation
} catch (failure: Exception) {
    Result.failure(failure)
}

private fun readAndNormalizeBackgroundImage(context: Context, uri: Uri): ByteArray {
    val orientation = readExifOrientation(context, uri)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = context.contentResolver.openInputStream(uri)
        ?: error("Unable to read selected image.")
    boundsStream.use { input ->
        BitmapFactory.decodeStream(input, null, bounds)
    }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image." }

    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / sampleSize > MaximumBackgroundImageDimensionPx) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inJustDecodeBounds = false
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val source = context.contentResolver.openInputStream(uri)?.use { input ->
        BitmapFactory.decodeStream(input, null, options)
    } ?: error("Unable to decode selected image.")
    val oriented = try {
        applyExifOrientation(source, orientation)
    } catch (failure: Throwable) {
        source.recycle()
        throw failure
    }

    try {
        val scale = min(
            1f,
            MaximumBackgroundImageDimensionPx / max(oriented.width, oriented.height).toFloat(),
        )
        val output = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                oriented,
                (oriented.width * scale).roundToInt().coerceAtLeast(1),
                (oriented.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            oriented
        }
        try {
            val stream = ByteArrayOutputStream()
            check(output.compress(Bitmap.CompressFormat.JPEG, 86, stream)) {
                "Unable to encode selected image."
            }
            val bytes = stream.toByteArray()
            require(bytes.isNotEmpty() && bytes.size <= CUSTOM_SETTINGS_BACKGROUND_MAX_IMAGE_BYTES) {
                "Image is too large."
            }
            return bytes
        } finally {
            if (output !== oriented) output.recycle()
        }
    } finally {
        oriented.recycle()
    }
}

private fun readExifOrientation(context: Context, uri: Uri): Int =
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (_: Exception) {
        // Some image formats and malformed files don't provide readable EXIF metadata.
        ExifInterface.ORIENTATION_NORMAL
    }

private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_NORMAL, ExifInterface.ORIENTATION_UNDEFINED -> return source
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postScale(-1f, 1f)
            matrix.postRotate(270f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postScale(-1f, 1f)
            matrix.postRotate(90f)
        }
        else -> return source
    }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true).also {
        if (it !== source) source.recycle()
    }
}
