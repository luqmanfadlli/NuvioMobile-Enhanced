package com.nuvio.app.core.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPhone

/**
 * Coil's default memory cache on iOS is a share of the device's physical RAM (well over 1 GB on
 * 8 GB iPhones). Images alone kept the app at 1.1-1.4 GB, leaving too little headroom under the
 * ~2.3 GB jetsam limit for a 4K HDR/DV player. Cap it and give memory back on warnings.
 */
private const val IosImageMemoryCacheBytes = 256L * 1024 * 1024
private const val IosPhoneImageMemoryCacheBytes = 160L * 1024 * 1024
private const val IosImageMemoryCacheDuringPlaybackBytes = 64L * 1024 * 1024

private val isPhone: Boolean by lazy {
    UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone
}

private var memoryWarningObserverInstalled = false

internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder {
    installMemoryWarningObserver()
    return memoryCache {
        MemoryCache.Builder()
            .maxSizeBytes(if (isPhone) IosPhoneImageMemoryCacheBytes else IosImageMemoryCacheBytes)
            .build()
    }
}

internal actual val platformProvidesImageLoader: Boolean = false

private fun installMemoryWarningObserver() {
    if (memoryWarningObserverInstalled) return
    memoryWarningObserverInstalled = true
    NSNotificationCenter.defaultCenter.addObserverForName(
        name = UIApplicationDidReceiveMemoryWarningNotification,
        `object` = null,
        queue = NSOperationQueue.mainQueue,
    ) { _ ->
        SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache?.clear()
    }
}

/**
 * Called when a player starts: decoded posters/backdrops are cheap to reload afterwards, and a
 * full Kotlin GC returns garbage the scheduler has not collected yet (the Kotlin heap, VM tag 246,
 * held 380-490 MB before playback in the memory logs).
 */
@OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
fun trimImageMemoryForPlayback() {
    val memoryCache = SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache
    if (isPhone) memoryCache?.clear() else memoryCache?.trimToSize(IosImageMemoryCacheDuringPlaybackBytes)
    suspendAnimatedImagesForPlayback()
    kotlin.native.runtime.GC.collect()
    if (isPhone) kotlin.native.runtime.GC.collect()
}

/** After a player is gone its Kotlin-side state (snapshots, track lists, bridges) is garbage. */
@OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
fun collectAfterPlayback() {
    kotlin.native.runtime.GC.collect()
    resumeAnimatedImagesAfterPlayback()
}
