package com.nuvio.app.features.home

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSPropertyListBinaryFormat_v1_0
import platform.Foundation.NSPropertyListSerialization
import platform.Foundation.NSUserDefaults

@OptIn(ExperimentalForeignApi::class)
internal actual fun platformPreferencesSizeBytes(): Long? = runCatching {
    val dictionary = NSUserDefaults.standardUserDefaults.dictionaryRepresentation()
    NSPropertyListSerialization.dataWithPropertyList(
        plist = dictionary,
        format = NSPropertyListBinaryFormat_v1_0,
        options = 0uL,
        error = null,
    )?.length?.toLong()
}.getOrNull()
