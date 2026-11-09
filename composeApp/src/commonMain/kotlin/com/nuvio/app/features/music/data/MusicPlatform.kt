package com.nuvio.app.features.music.data

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlin.time.Clock

/** Wall-clock milliseconds since the epoch. */
internal fun musicNowMs(): Long = Clock.System.now().toEpochMilliseconds()

/** The device language as a BCP-47 tag, e.g. "en-CA". */
internal expect fun systemLanguageTag(): String

internal enum class NormForm { NFD, NFKC }

/** Unicode normalisation, done by the platform (java.text.Normalizer / NSString). */
internal expect fun musicNormalize(text: String, form: NormForm): String

/** The combining marks (U+0300..U+036F) a locale puts on its letters. */
internal fun String.stripCombiningMarks(): String = filterNot { it.code in 0x0300..0x036F }

/** `Map.putIfAbsent` is JVM-only; this is the common equivalent. */
internal fun <K, V> MutableMap<K, V>.putIfAbsent(key: K, value: V): V? {
    val existing = this[key]
    if (existing != null) return existing
    this[key] = value
    return null
}

/** Minimal thread-safe map used where the original code used ConcurrentHashMap. */
internal class SyncMap<K, V> {
    private val lock = SynchronizedObject()
    private val map = HashMap<K, V>()

    operator fun get(key: K): V? = synchronized(lock) { map[key] }
    operator fun set(key: K, value: V) { synchronized(lock) { map[key] = value } }
    fun put(key: K, value: V): V? = synchronized(lock) { map.put(key, value) }
    fun putIfAbsent(key: K, value: V): V? = synchronized(lock) { map[key] ?: run { map[key] = value; null } }
    fun containsKey(key: K): Boolean = synchronized(lock) { map.containsKey(key) }
    fun remove(key: K): V? = synchronized(lock) { map.remove(key) }
    fun clear() { synchronized(lock) { map.clear() } }
    val size: Int get() = synchronized(lock) { map.size }
    fun getOrPut(key: K, create: () -> V): V = synchronized(lock) { map.getOrPut(key, create) }
    fun computeIfAbsent(key: K, create: (K) -> V): V = synchronized(lock) { map.getOrPut(key) { create(key) } }
    fun keysSnapshot(): List<K> = synchronized(lock) { map.keys.toList() }
}

/** Minimal thread-safe set used where the original code used ConcurrentHashMap.newKeySet(). */
internal class SyncSet<T> {
    private val lock = SynchronizedObject()
    private val set = HashSet<T>()

    fun add(value: T): Boolean = synchronized(lock) { set.add(value) }
    operator fun contains(value: T): Boolean = synchronized(lock) { set.contains(value) }
    fun remove(value: T): Boolean = synchronized(lock) { set.remove(value) }
    fun clear() { synchronized(lock) { set.clear() } }
    val size: Int get() = synchronized(lock) { set.size }
    fun toSet(): Set<T> = synchronized(lock) { set.toSet() }
}
