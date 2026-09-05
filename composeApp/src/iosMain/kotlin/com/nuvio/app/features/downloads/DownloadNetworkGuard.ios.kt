package com.nuvio.app.features.downloads

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreFoundation.CFRelease
import platform.SystemConfiguration.SCNetworkReachabilityCreateWithName
import platform.SystemConfiguration.SCNetworkReachabilityFlagsVar
import platform.SystemConfiguration.SCNetworkReachabilityGetFlags
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsIsWWAN
import platform.SystemConfiguration.kSCNetworkReachabilityFlagsReachable

@OptIn(ExperimentalForeignApi::class)
actual object DownloadNetworkGuard {
    actual fun isOnWifi(): Boolean {
        val reachability = SCNetworkReachabilityCreateWithName(null, "www.apple.com") ?: return true
        try {
            memScoped {
                val flagsVar = alloc<SCNetworkReachabilityFlagsVar>()
                val ok = SCNetworkReachabilityGetFlags(reachability, flagsVar.ptr)
                if (!ok) return true

                val flags = flagsVar.value
                val isReachable = (flags and kSCNetworkReachabilityFlagsReachable.toUInt()) != 0u
                if (!isReachable) return true // No connectivity at all: nothing to gate.

                val isCellular = (flags and kSCNetworkReachabilityFlagsIsWWAN.toUInt()) != 0u
                return !isCellular
            }
        } finally {
            CFRelease(reachability)
        }
    }
}
