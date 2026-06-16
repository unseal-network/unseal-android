/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.AgentRoomKeyRecoveryRequest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class AgentRoomKeyRecoveryPendingStoreTest {
    private val clock = MutableClock()
    private val store = AgentRoomKeyRecoveryPendingStore(clock = clock)

    @Test
    fun `markPendingIfNeeded suppresses duplicate request for 60 seconds`() {
        assertThat(store.markPendingIfNeeded(aAgentRequest())).isTrue()
        assertThat(store.markPendingIfNeeded(aAgentRequest())).isFalse()

        clock.advanceBy(59.seconds)

        assertThat(store.markPendingIfNeeded(aAgentRequest())).isFalse()
    }

    @Test
    fun `markPendingIfNeeded allows request after 60 seconds`() {
        assertThat(store.markPendingIfNeeded(aAgentRequest())).isTrue()

        clock.advanceBy(60.seconds)

        assertThat(store.markPendingIfNeeded(aAgentRequest())).isTrue()
    }

    @Test
    fun `removePending clears request`() {
        store.markPendingIfNeeded(aAgentRequest())

        store.removePending(aAgentRequest())

        assertThat(store.markPendingIfNeeded(aAgentRequest())).isTrue()
    }

    @Test
    fun `retainOnly removes requests that are no longer visible`() {
        val retained = aAgentRequest(sessionId = "retained")
        val removed = aAgentRequest(sessionId = "removed")
        store.markPendingIfNeeded(retained)
        store.markPendingIfNeeded(removed)

        store.retainOnly(listOf(retained))

        assertThat(store.markPendingIfNeeded(retained)).isFalse()
        assertThat(store.markPendingIfNeeded(removed)).isTrue()
    }

    private fun aAgentRequest(
        roomId: RoomId = RoomId("!room:example.org"),
        senderUserId: UserId = UserId("@agent:example.org"),
        senderDeviceId: String = "BOT_DEVICE",
        senderKey: String = "SENDER_KEY",
        sessionId: String = "SESSION",
    ) = AgentRoomKeyRecoveryRequest(
        roomId = roomId,
        senderUserId = senderUserId,
        senderDeviceId = senderDeviceId,
        senderKey = senderKey,
        sessionId = sessionId,
    )
}
