/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room.member

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.room.aRoomMember
import org.junit.Test

class RoomAgentMemberEnricherTest {
    private val enricher = RoomAgentMemberEnricher()

    @Test
    fun `marks only joined members returned by the room agent endpoint`() {
        val members = listOf(
            aRoomMember(userId = UserId("@agent:server"), membership = RoomMembershipState.JOIN),
            aRoomMember(userId = UserId("@human:server"), membership = RoomMembershipState.JOIN),
        )
        val agents = listOf(
            RoomAgentMember(userId = "@agent:server", membership = "join", userType = "agent"),
            RoomAgentMember(userId = "@stale:server", membership = "join", userType = "agent"),
            RoomAgentMember(userId = "@left-agent:server", membership = "leave", userType = "agent"),
        )

        val enriched = enricher.enrich(members, agents)

        assertThat(enriched.first { it.userId == UserId("@agent:server") }.userType).isEqualTo("agent")
        assertThat(enriched.first { it.userId == UserId("@human:server") }.userType).isNull()
    }

    @Test
    fun `clears stale agent marks`() {
        val members = listOf(
            aRoomMember(userId = UserId("@agent:server"), userType = "agent"),
            aRoomMember(userId = UserId("@human:server")),
        )

        val enriched = enricher.enrich(members, emptyList())

        assertThat(enriched.first { it.userId == UserId("@agent:server") }.userType).isNull()
    }
}
