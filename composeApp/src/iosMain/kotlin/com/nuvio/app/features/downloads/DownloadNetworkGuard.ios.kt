package com.nuvio.app.features.downloads

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_get_main_queue

actual object DownloadNetworkGuard {
    // Not Wi-Fi until the monitor's first update, which arrives right after it starts.
    private val _transportState = MutableStateFlow(false)
    actual val transportState = _transportState.asStateFlow()

    // Expensive paths are cellular and personal hotspots; the monitor only reports changes.
    private val monitor = nw_path_monitor_create().also { monitor ->
        nw_path_monitor_set_update_handler(monitor) { path ->
            _transportState.value = nw_path_get_status(path) == nw_path_status_satisfied && !nw_path_is_expensive(path)
        }
        nw_path_monitor_set_queue(monitor, dispatch_get_main_queue())
        nw_path_monitor_start(monitor)
    }

    actual fun isOnWifi(): Boolean = _transportState.value
}
