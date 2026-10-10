package com.nuvio.app.features.profiles

enum class ProfileBiometricResult {
    Success,
    Cancelled,
    FallbackRequested,
    Unavailable,
    NotConfigured,
    Invalidated,
    Failed,
}

expect object ProfileBiometricAuth {
    fun initialize(host: Any)
    fun isAvailable(): Boolean
    suspend fun isConfigured(profileIndex: Int, userId: String): Boolean
    suspend fun enable(profileIndex: Int, userId: String): ProfileBiometricResult
    suspend fun authenticate(profileIndex: Int, userId: String): ProfileBiometricResult
    fun disable(profileIndex: Int, userId: String): Boolean
}
