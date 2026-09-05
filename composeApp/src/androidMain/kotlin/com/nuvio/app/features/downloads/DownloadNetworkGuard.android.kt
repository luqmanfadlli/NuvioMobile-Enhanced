package com.nuvio.app.features.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

actual object DownloadNetworkGuard {
    private var connectivityManager: ConnectivityManager? = null

    fun initialize(context: Context) {
        connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }

    actual fun isOnWifi(): Boolean {
        val manager = connectivityManager ?: return true
        val network = manager.activeNetwork ?: return true
        val capabilities = manager.getNetworkCapabilities(network) ?: return true
        val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        return !isCellular
    }
}
