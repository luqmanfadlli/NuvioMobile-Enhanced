package com.nuvio.app.features.downloads

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

actual object DownloadNetworkGuard {
    private var connectivityManager: ConnectivityManager? = null
    private val _transportState = MutableStateFlow(true)
    actual val transportState = _transportState.asStateFlow()

    fun initialize(context: Context) {
        if (connectivityManager != null) return
        val manager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        connectivityManager = manager
        _transportState.value = isOnWifi()
        runCatching {
            // Use the values passed in; activeNetwork can be stale while callbacks are delivered.
            manager.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                private var current: android.net.Network? = null
                override fun onAvailable(network: android.net.Network) {
                    current = network
                    manager.getNetworkCapabilities(network)?.let { _transportState.value = it.isUnmetered() }
                }
                override fun onCapabilitiesChanged(network: android.net.Network, capabilities: NetworkCapabilities) {
                    current = network
                    _transportState.value = capabilities.isUnmetered()
                }
                override fun onLost(network: android.net.Network) {
                    if (network != current) return
                    current = null
                    _transportState.value = false
                }
            })
        }
    }

    /** True on an unmetered network, the same rule as the download job's NETWORK_TYPE_UNMETERED. */
    actual fun isOnWifi(): Boolean {
        val manager = connectivityManager ?: return true
        val capabilities = manager.activeNetwork?.let(manager::getNetworkCapabilities) ?: return false
        return capabilities.isUnmetered()
    }

    private fun NetworkCapabilities.isUnmetered(): Boolean =
        hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}
