/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.cache

internal class BoundedTimelineCache<K, V>(
    private val maxEntries: Int,
) {
    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    private val store = object : LinkedHashMap<K, V>(initialCapacity(maxEntries), LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean {
            return size > maxEntries
        }
    }

    val size: Int
        @Synchronized get() = store.size

    @Synchronized
    operator fun get(key: K): V? = store[key]

    @Synchronized
    operator fun set(key: K, value: V) {
        store[key] = value
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f

        fun initialCapacity(maxEntries: Int): Int {
            return (maxEntries / LOAD_FACTOR).toInt() + 1
        }
    }
}
