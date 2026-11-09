package com.nuvio.app.features.sports.compat

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSURL
import platform.Foundation.NSDate
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Foundation.timeIntervalSince1970

// iOS actuals are written but not compiled: this machine has no full Xcode (see docs/integration/TEST_RESULTS.md).
@OptIn(ExperimentalForeignApi::class)
internal actual object LiveTvFiles {
    private fun path(dir: String, name: String): String {
        val root = (NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true).firstOrNull() as? String).orEmpty()
        val folder = "$root/sports-hub/$dir"
        NSFileManager.defaultManager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
        return "$folder/$name"
    }

    actual fun exists(dir: String, name: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path(dir, name))

    actual fun lastModified(dir: String, name: String): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path(dir, name), error = null) ?: return 0L
        val date = attrs[NSFileModificationDate] as? NSDate ?: return 0L
        return (date.timeIntervalSince1970 * 1000).toLong()
    }

    actual fun readSmall(dir: String, name: String): String? =
        NSString.stringWithContentsOfFile(path(dir, name), encoding = NSUTF8StringEncoding, error = null)

    actual fun writeSmall(dir: String, name: String, text: String) {
        (text as NSString).writeToFile(path(dir, name), atomically = true, encoding = NSUTF8StringEncoding, error = null)
    }
}
