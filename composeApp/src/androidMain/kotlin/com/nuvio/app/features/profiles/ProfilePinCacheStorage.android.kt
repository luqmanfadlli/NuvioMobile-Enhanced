package com.nuvio.app.features.profiles

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

actual object ProfilePinCacheStorage {
    private const val PREFERENCES_NAME = "nuvio_profile_pin_cache"
    private const val KEY_ALIAS = "nuvio_profile_pin_cache_key_v1"
    private const val PAYLOAD_PREFIX = "v1:"
    private const val CIPHER = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val GCM_IV_BYTES = 12

    private var preferences: SharedPreferences? = null
    private val lock = Any()

    fun initialize(context: Context) {
        synchronized(lock) {
            preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        }
    }

    @Synchronized
    actual fun loadPayload(profileIndex: Int): String? {
        val values = preferences ?: return null
        val stored = values.getString(payloadKey(profileIndex), null) ?: return null

        if (stored.startsWith(PAYLOAD_PREFIX)) {
            return decrypt(stored.removePrefix(PAYLOAD_PREFIX))
        }

        // Migrate the legacy plaintext cache on first access. The old verifier remains usable
        // only long enough to be re-encrypted with an app-bound Android Keystore key.
        if (stored.isNotBlank()) {
            if (saveEncrypted(profileIndex, stored)) return stored
            // Do not leave a plaintext verifier behind when secure storage is unavailable.
            values.edit().remove(payloadKey(profileIndex)).commit()
        }

        return null
    }

    @Synchronized
    actual fun savePayload(profileIndex: Int, payload: String) {
        // Remove the previous verifier before replacing it. If secure persistence fails,
        // an old PIN hash must not remain usable as an offline fallback.
        if (!removePayload(profileIndex)) return
        if (!saveEncrypted(profileIndex, payload)) {
            // Best-effort cleanup if a failed commit left a payload behind.
            removePayload(profileIndex)
        }
    }

    @Synchronized
    actual fun removePayload(profileIndex: Int): Boolean {
        val values = preferences ?: return false
        val key = payloadKey(profileIndex)
        val committed = values.edit().remove(key).commit()
        return committed && !values.contains(key)
    }

    private fun saveEncrypted(profileIndex: Int, payload: String): Boolean {
        val values = preferences ?: return false
        val key = getOrCreateKey() ?: return false
        return runCatching {
            val cipher = Cipher.getInstance(CIPHER)
            cipher.init(Cipher.ENCRYPT_MODE, key)
            val iv = cipher.iv
            if (iv.size != GCM_IV_BYTES) return@runCatching false
            val ciphertext = cipher.doFinal(payload.encodeToByteArray())
            val envelope = ByteArray(1 + iv.size + ciphertext.size)
            envelope[0] = 1
            iv.copyInto(envelope, destinationOffset = 1)
            ciphertext.copyInto(envelope, destinationOffset = 1 + iv.size)
            val encoded = Base64.encodeToString(envelope, Base64.NO_WRAP)

            values.edit()
                .putString(payloadKey(profileIndex), PAYLOAD_PREFIX + encoded)
                .commit()
        }.getOrNull() == true
    }

    private fun decrypt(encoded: String): String? {
        val key = getExistingKey() ?: return null

        return try {
            val envelope = Base64.decode(encoded, Base64.DEFAULT)
            if (envelope.size <= 1 + GCM_IV_BYTES || envelope[0].toInt() != 1) {
                null
            } else {
                val iv = envelope.copyOfRange(1, 1 + GCM_IV_BYTES)
                val ciphertext = envelope.copyOfRange(1 + GCM_IV_BYTES, envelope.size)
                val cipher = Cipher.getInstance(CIPHER)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    GCMParameterSpec(GCM_TAG_BITS, iv),
                )
                cipher.doFinal(ciphertext).decodeToString()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getExistingKey(): SecretKey? =
        runCatching {
            KeyStore.getInstance(KEYSTORE).apply { load(null) }
                .getKey(KEY_ALIAS, null) as? SecretKey
        }.getOrNull()

    private fun getOrCreateKey(): SecretKey? {
        getExistingKey()?.let { return it }

        return runCatching {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE,
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generator.generateKey()
        }.getOrNull()
    }

    private fun payloadKey(profileIndex: Int): String = "profile_pin_cache_$profileIndex"

    private const val KEYSTORE = "AndroidKeyStore"
}
