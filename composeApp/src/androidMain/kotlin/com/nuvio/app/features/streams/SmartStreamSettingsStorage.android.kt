package com.nuvio.app.features.streams

// This fork targets iOS; on Android the sort and filter choices last until the app closes.
internal actual object SmartStreamSettingsStorage {
    private var value: String? = null

    actual fun load(): String? = value

    actual fun save(value: String) {
        this.value = value
    }
}
