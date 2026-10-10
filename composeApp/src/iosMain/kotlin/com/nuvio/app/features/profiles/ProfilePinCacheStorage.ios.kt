package com.nuvio.app.features.profiles

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.reinterpret
import platform.CoreFoundation.CFDataRefVar
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual object ProfilePinCacheStorage {
    private const val SERVICE = "com.nuvio.media.profile-pin-cache"
    private const val LEGACY_PREFIX = "profile_pin_cache_"

    actual fun loadPayload(profileIndex: Int): String? {
        val (status, data) = copyMatching(createQuery(profileIndex))
        val payload = if (status == errSecSuccess) {
            data?.let {
                NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString()
            }
        } else {
            null
        }
        return payload ?: migrateLegacyPayload(profileIndex)
    }

    actual fun savePayload(profileIndex: Int, payload: String) {
        // Remove a legacy plaintext verifier and verify removal before writing a replacement.
        val legacyKey = "${LEGACY_PREFIX}${profileIndex}"
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.removeObjectForKey(legacyKey)
        if (defaults.objectForKey(legacyKey) != null) return

        val deleteStatus = try {
            deleteExisting(profileIndex)
        } catch (_: Exception) {
            return
        }
        if (deleteStatus != errSecSuccess && deleteStatus != errSecItemNotFound) return

        val data = payload.toNSData()
        val query = try {
            createQuery(profileIndex)
        } catch (_: Exception) {
            return
        }

        try {
            CFDictionarySetValue(
                query,
                kSecAttrAccessible,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            )
            val bridgedData = CFBridgingRetain(data)
            try {
                CFDictionarySetValue(query, kSecValueData, bridgedData)
                // The prior item is gone. A failed add therefore cannot silently retain an
                // older PIN verifier.
                SecItemAdd(query, null)
            } finally {
                bridgedData?.let { CFRelease(it) }
            }
        } catch (_: Exception) {
            // Fail closed; do not attempt to restore an older verifier.
        } finally {
            CFRelease(query)
        }
    }

    actual fun removePayload(profileIndex: Int): Boolean {
        val keychainStatus = deleteExisting(profileIndex)
        val legacyKey = "${LEGACY_PREFIX}${profileIndex}"
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.removeObjectForKey(legacyKey)
        val legacyRemoved = defaults.objectForKey(legacyKey) == null
        val keychainRemoved =
            keychainStatus == errSecSuccess || keychainStatus == errSecItemNotFound
        return keychainRemoved && legacyRemoved
    }

    private fun migrateLegacyPayload(profileIndex: Int): String? {
        val legacyKey = "${LEGACY_PREFIX}${profileIndex}"
        val legacy = NSUserDefaults.standardUserDefaults
            .stringForKey(legacyKey)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        if (savePayloadInternal(profileIndex, legacy)) {
            NSUserDefaults.standardUserDefaults.removeObjectForKey(legacyKey)
            return legacy
        }

        NSUserDefaults.standardUserDefaults.removeObjectForKey(legacyKey)
        return null
    }

    private fun savePayloadInternal(profileIndex: Int, payload: String): Boolean {
        val data = payload.toNSData()
        val query = createQuery(profileIndex)

        return try {
            CFDictionarySetValue(
                query,
                kSecAttrAccessible,
                kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            )
            val bridgedData = CFBridgingRetain(data)
            try {
                CFDictionarySetValue(query, kSecValueData, bridgedData)
                val deleteStatus = deleteExisting(profileIndex)
                if (deleteStatus != errSecSuccess && deleteStatus != errSecItemNotFound) {
                    return false
                }
                SecItemAdd(query, null) == errSecSuccess
            } finally {
                bridgedData?.let { CFRelease(it) }
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun deleteExisting(profileIndex: Int): Int {
        val query = createQuery(profileIndex)
        return try {
            SecItemDelete(query)
        } finally {
            CFRelease(query)
        }
    }

    private fun createQuery(profileIndex: Int): CFMutableDictionaryRef {
        return CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            5,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )!!.also {
            CFDictionarySetValue(it, kSecClass, kSecClassGenericPassword)
            addString(it, kSecAttrService, SERVICE)
            addString(it, kSecAttrAccount, "profile-${profileIndex}")
        }
    }

    private fun copyMatching(
        query: CFMutableDictionaryRef,
    ): Pair<Int, NSData?> {
        return memScoped {
            CFDictionarySetValue(query, kSecReturnData, kCFBooleanTrue)
            val result = alloc<CFDataRefVar>()
            result.value = null
            val status = SecItemCopyMatching(query, result.ptr.reinterpret())
            val data = if (status == errSecSuccess) {
                CFBridgingRelease(result.value) as? NSData
            } else {
                result.value?.let { CFRelease(it) }
                null
            }

            try {
                status to data
            } finally {
                CFRelease(query)
            }
        }
    }

    private fun addString(
        query: CFMutableDictionaryRef,
        key: CFStringRef?,
        value: String,
    ) {
        val retained = CFBridgingRetain(value) ?: return
        try {
            CFDictionarySetValue(query, key, retained)
        } finally {
            CFRelease(retained)
        }
    }

    private fun String.toNSData(): NSData =
        NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding) ?: NSData()
}
