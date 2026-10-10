package com.nuvio.app.features.profiles

import android.os.Build
import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import java.lang.ref.WeakReference
import java.security.KeyStore
import java.security.KeyStoreException
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.UserNotAuthenticatedException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import nuvio.composeapp.generated.resources.*
import org.jetbrains.compose.resources.getString

private val LegacyBiometricCleanupScope =
    CoroutineScope(SupervisorJob() + Dispatchers.IO)

actual object ProfileBiometricAuth {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_PREFIX = "nuvio_primary_profile_biometric_"
    private const val LEGACY_ALIAS = "nuvio_primary_profile_biometric_1"
    private val sentinel = "nuvio-profile-biometric".encodeToByteArray()

    private var activityReference: WeakReference<FragmentActivity>? = null

    actual fun initialize(host: Any) {
        activityReference = (host as? FragmentActivity)?.let(::WeakReference)
        // Keystore access is synchronous; keep legacy cleanup off the UI thread.
        // This legacy alias is not account-scoped and is never used by current credentials.
        LegacyBiometricCleanupScope.launch {
            deleteLegacyKey()
        }
    }

    private fun activity(): FragmentActivity? = activityReference?.get()

    actual fun isAvailable(): Boolean {
        val host = activity() ?: return false
        val keyguard = host.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguard != null && !keyguard.isDeviceSecure) return false
        return BiometricManager.from(host).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    actual suspend fun isConfigured(profileIndex: Int, userId: String): Boolean {
        if (profileIndex != 1 || userId.isBlank()) return false

        return withContext(Dispatchers.IO) {
            try {
                val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
                val key = keyStore.getKey(alias(profileIndex, userId), null) ?: return@withContext false

                try {
                    // A valid per-use biometric key is expected to reject an unauthenticated
                    // cipher initialization. A permanently invalidated key must be removed
                    // so the UI does not report biometric unlock as still enabled.
                    Cipher.getInstance("AES/GCM/NoPadding").init(Cipher.ENCRYPT_MODE, key)
                    true
                } catch (_: UserNotAuthenticatedException) {
                    true
                } catch (_: KeyPermanentlyInvalidatedException) {
                    deleteKey(profileIndex, userId)
                    false
                }
            } catch (_: KeyPermanentlyInvalidatedException) {
                deleteKey(profileIndex, userId)
                false
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        }
    }

    actual suspend fun enable(profileIndex: Int, userId: String): ProfileBiometricResult {
        if (profileIndex != 1 || userId.isBlank() || !isAvailable()) {
            return ProfileBiometricResult.Unavailable
        }

        val deleted = withContext(Dispatchers.IO) {
            deleteKey(profileIndex, userId)
            deleteLegacyKey()
            !keyExists(profileIndex, userId)
        }
        if (!deleted) return ProfileBiometricResult.Failed

        return try {
            withContext(Dispatchers.IO) {
                generateKey(profileIndex, userId)
            }
            val result = authenticateInternal(profileIndex, userId, setup = true)
            if (result != ProfileBiometricResult.Success) {
                withContext(Dispatchers.IO) {
                    deleteKey(profileIndex, userId)
                }
            }
            result
        } catch (error: CancellationException) {
            // Keep the cleanup non-cancellable, then switch dispatcher inside it.
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    deleteKey(profileIndex, userId)
                }
            }
            throw error
        } catch (_: Exception) {
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    deleteKey(profileIndex, userId)
                }
            }
            ProfileBiometricResult.Failed
        }
    }

    actual suspend fun authenticate(profileIndex: Int, userId: String): ProfileBiometricResult {
        if (profileIndex != 1 || userId.isBlank()) return ProfileBiometricResult.Unavailable
        if (!isConfigured(profileIndex, userId)) return ProfileBiometricResult.NotConfigured

        return try {
            authenticateInternal(profileIndex, userId, setup = false)
        } catch (error: CancellationException) {
            throw error
        } catch (error: KeyPermanentlyInvalidatedException) {
            withContext(Dispatchers.IO) {
                deleteKey(profileIndex, userId)
            }
            ProfileBiometricResult.Invalidated
        } catch (_: Exception) {
            ProfileBiometricResult.Failed
        }
    }

    actual fun disable(profileIndex: Int, userId: String): Boolean {
        if (profileIndex != 1) return true

        // The legacy alias is global, not account-scoped, so it can be removed even
        // when the current account ID is unavailable. Never guess an account alias.
        deleteLegacyKey()
        // We cannot safely resolve or verify an account-scoped alias without its account ID.
        // Removing the legacy alias alone must not be reported as complete cleanup.
        if (userId.isBlank()) return false

        deleteKey(profileIndex, userId)
        return !keyExists(profileIndex, userId) && !legacyKeyExists()
    }

    private suspend fun authenticateInternal(
        profileIndex: Int,
        userId: String,
        setup: Boolean,
    ): ProfileBiometricResult {
        val host = activity() ?: return ProfileBiometricResult.Unavailable
        val biometricManager = BiometricManager.from(host)
        if (
            biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) !=
                BiometricManager.BIOMETRIC_SUCCESS
        ) {
            return ProfileBiometricResult.Unavailable
        }

        val cipher = withContext(Dispatchers.IO) {
            createCipher(profileIndex, userId)
        }
        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        val promptTitle = getString(
            if (setup) {
                Res.string.profile_biometric_prompt_setup_title
            } else {
                Res.string.profile_biometric_prompt_unlock_title
            },
        )
        val promptSubtitle = getString(
            if (setup) {
                Res.string.profile_biometric_prompt_setup_subtitle
            } else {
                Res.string.profile_biometric_prompt_unlock_subtitle
            },
        )
        val negativeButtonText = getString(
            if (setup) {
                Res.string.action_cancel
            } else {
                Res.string.profile_biometric_prompt_use_pin
            },
        )

        val result = suspendCancellableCoroutine<ProfileBiometricResult> { continuation ->
            val executor = host.mainExecutor
            val prompt = BiometricPrompt(
                host,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult,
                    ) {
                        val authenticatedCipher = result.cryptoObject?.cipher
                        if (authenticatedCipher == null || authenticatedCipher !== cipher) {
                            if (continuation.isActive) continuation.resume(ProfileBiometricResult.Failed)
                            return
                        }

                        val outcome = runCatching {
                            authenticatedCipher.doFinal(sentinel)
                            ProfileBiometricResult.Success
                        }.getOrElse { error ->
                            if (error is KeyPermanentlyInvalidatedException) {
                                ProfileBiometricResult.Invalidated
                            } else {
                                ProfileBiometricResult.Failed
                            }
                        }
                        if (continuation.isActive) continuation.resume(outcome)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (!continuation.isActive) return
                        continuation.resume(
                            when (errorCode) {
                                BiometricPrompt.ERROR_NEGATIVE_BUTTON ->
                                    if (setup) {
                                        ProfileBiometricResult.Cancelled
                                    } else {
                                        ProfileBiometricResult.FallbackRequested
                                    }
                                BiometricPrompt.ERROR_USER_CANCELED,
                                BiometricPrompt.ERROR_CANCELED -> ProfileBiometricResult.Cancelled
                                BiometricPrompt.ERROR_NO_BIOMETRICS,
                                BiometricPrompt.ERROR_HW_NOT_PRESENT,
                                BiometricPrompt.ERROR_HW_UNAVAILABLE,
                                BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
                                BiometricPrompt.ERROR_LOCKOUT,
                                BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
                                BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED ->
                                    ProfileBiometricResult.Unavailable
                                else -> ProfileBiometricResult.Failed
                            },
                        )
                    }

                    override fun onAuthenticationFailed() {
                        // Keep the system prompt open for another biometric attempt.
                    }
                },
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(promptTitle)
                .setSubtitle(promptSubtitle)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setNegativeButtonText(negativeButtonText)
                .setConfirmationRequired(false)
                .build()

            continuation.invokeOnCancellation { prompt.cancelAuthentication() }
            prompt.authenticate(promptInfo, cryptoObject)
        }

        if (result == ProfileBiometricResult.Invalidated) {
            withContext(Dispatchers.IO) {
                deleteKey(profileIndex, userId)
            }
        }
        return result
    }

    private fun generateKey(profileIndex: Int, userId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                generateKey(profileIndex, userId, strongBox = true)
                return
            } catch (_: StrongBoxUnavailableException) {
                deleteKey(profileIndex, userId)
            }
        }
        generateKey(profileIndex, userId, strongBox = false)
    }

    private fun generateKey(profileIndex: Int, userId: String, strongBox: Boolean) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            alias(profileIndex, userId),
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG,
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        generator.init(builder.build())
        generator.generateKey()
    }

    private fun createCipher(profileIndex: Int, userId: String): Cipher {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val key = keyStore.getKey(alias(profileIndex, userId), null)
            ?: throw KeyStoreException("Biometric key is unavailable")
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key)
        }
    }

    private fun keyExists(profileIndex: Int, userId: String): Boolean =
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }
                .containsAlias(alias(profileIndex, userId))
        }.getOrDefault(true)

    private fun deleteKey(profileIndex: Int, userId: String) {
        if (userId.isBlank()) return
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply {
                load(null)
                if (containsAlias(alias(profileIndex, userId))) {
                    deleteEntry(alias(profileIndex, userId))
                }
            }
        }
    }

    private fun legacyKeyExists(): Boolean =
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }
                .containsAlias(LEGACY_ALIAS)
        }.getOrDefault(true)

    private fun deleteLegacyKey() {
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply {
                load(null)
                if (containsAlias(LEGACY_ALIAS)) deleteEntry(LEGACY_ALIAS)
            }
        }
    }

    private fun alias(profileIndex: Int, userId: String): String =
        KEY_PREFIX + ProfilePinCrypto.sha256Hex(
            "primary-profile-biometric:$profileIndex:$userId",
        )
}
