/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

class RoomKeyRecoveryPendingStoreTest {
    private val clock = MutableClock()
    private val store = RoomKeyRecoveryPendingStore(clock = clock)

    @Test
    fun `markPendingIfNeeded suppresses duplicate retry until deadline`() {
        assertThat(store.markPendingIfNeeded(aRequest())).isTrue()
        assertThat(store.markPendingIfNeeded(aRequest())).isFalse()

        clock.advanceBy(29.minutes)

        assertThat(store.remainingInterval(aRequest())).isEqualTo(1.minutes)
        assertThat(store.markPendingIfNeeded(aRequest())).isFalse()
    }

    @Test
    fun `markPendingIfNeeded allows retry after deadline`() {
        assertThat(store.markPendingIfNeeded(aRequest())).isTrue()

        clock.advanceBy(30.minutes)

        assertThat(store.remainingInterval(aRequest())).isNull()
        assertThat(store.markPendingIfNeeded(aRequest())).isTrue()
    }

    @Test
    fun `markPendingIfNeeded force refreshes deadline`() {
        store.markPendingIfNeeded(aRequest())
        clock.advanceBy(10.minutes)

        assertThat(store.markPendingIfNeeded(aRequest(), force = true)).isTrue()
        assertThat(store.remainingInterval(aRequest())).isEqualTo(30.minutes)
    }

    @Test
    fun `removePending clears request`() {
        store.markPendingIfNeeded(aRequest())

        store.removePending(aRequest())

        assertThat(store.remainingInterval(aRequest())).isNull()
        assertThat(store.markPendingIfNeeded(aRequest())).isTrue()
    }

    @Test
    fun `retainOnly removes requests that are no longer visible`() {
        val retained = aRequest(sessionId = "retained")
        val removed = aRequest(sessionId = "removed")
        store.markPendingIfNeeded(retained)
        store.markPendingIfNeeded(removed)

        store.retainOnly(listOf(retained))

        assertThat(store.remainingInterval(retained)).isEqualTo(30.minutes)
        assertThat(store.remainingInterval(removed)).isNull()
    }

    @Test
    fun `pruneExpired removes expired requests`() {
        store.markPendingIfNeeded(aRequest())
        clock.advanceBy(30.minutes)

        store.pruneExpired()

        assertThat(store.remainingInterval(aRequest())).isNull()
    }
}
