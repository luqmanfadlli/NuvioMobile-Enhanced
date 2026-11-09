package com.nuvio.app.core.ui

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

private const val IosImageMemoryCacheBytes = 256L * 1024 * 1024
private const val IosImageMemoryCacheDuringPlaybackBytes = 64L * 1024 * 1024

private var memoryWarningObserverInstalled = false

internal actual fun ImageLoader.Builder.configurePlatformImageLoader(): ImageLoader.Builder {
    installMemoryWarningObserver()
    return memoryCache {
        MemoryCache.Builder()
            .maxSizeBytes(IosImageMemoryCacheBytes)
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

@OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
fun trimImageMemoryForPlayback() {
    SingletonImageLoader.get(PlatformContext.INSTANCE).memoryCache?.trimToSize(IosImageMemoryCacheDuringPlaybackBytes)
    suspendAnimatedImagesForPlayback()
    kotlin.native.runtime.GC.collect()
}

@OptIn(kotlin.native.runtime.NativeRuntimeApi::class)
fun collectAfterPlayback() {
    kotlin.native.runtime.GC.collect()
    resumeAnimatedImagesAfterPlayback()
}
