package com.nuvio.app.features.sports.compat

import android.content.Context
import java.io.File

internal actual object LiveTvFiles {
    private var appContext: Context? = null

    fun initialize(context: Context) { appContext = context.applicationContext }

    private fun file(dir: String, name: String): File {
        val base = appContext?.filesDir ?: File(System.getProperty("java.io.tmpdir") ?: ".")
        return File(File(File(base, "sports-hub"), dir).also { it.mkdirs() }, name)
    }

    actual fun exists(dir: String, name: String): Boolean = file(dir, name).isFile
    actual fun lastModified(dir: String, name: String): Long = file(dir, name).takeIf { it.isFile }?.lastModified() ?: 0L
    actual fun readSmall(dir: String, name: String): String? = file(dir, name).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    actual fun writeSmall(dir: String, name: String, text: String) {
        runCatching {
            val target = file(dir, name)
            val tmp = File(target.parentFile, ".${target.name}.tmp")
            tmp.writeText(text)
            if (!tmp.renameTo(target)) { target.writeText(text); tmp.delete() }
        }
    }
}
