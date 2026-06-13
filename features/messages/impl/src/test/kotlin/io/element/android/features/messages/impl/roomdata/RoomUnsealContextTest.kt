/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.room.aRoomMember
import org.junit.Test

class RoomUnsealContextTest {
    @Test
    fun `from derives agent state schedules and device agent`() {
        val members = listOf(
            aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN),
            aRoomMember(userId = USER_ID, membership = RoomMembershipState.JOIN),
        )
        val snapshot = RoomUnsealDataSnapshot(
            roomAgents = RoomUnsealResource.success(
                listOf(RoomAgentDescriptor(userId = AGENT_ID.value, displayName = "Agent", avatarUrl = null, userType = null, membership = "join"))
            ),
            allAgents = RoomUnsealResource.success(
                listOf(
                    AgentAccountDescriptor(
                        botName = "device",
                        localpart = "agent",
                        serverName = "example.org",
                        matrixUserId = AGENT_ID.value,
                        displayName = "Device",
                        avatarUrl = null,
                        isDeviceAgent = true,
                        boundDeviceId = "DEVICEID",
                    )
                )
            ),
            schedules = RoomUnsealResource.success(
                listOf(
                    RoomScheduleDescriptor("enabled", "Enabled", "* * * * *", "ping", AGENT_ID.value, ROOM_ID.value, null, isEnabled = true),
                    RoomScheduleDescriptor("disabled", "Disabled", "* * * * *", "ping", AGENT_ID.value, ROOM_ID.value, null, isEnabled = false),
                )
            ),
            workingMemory = RoomUnsealResource.success("memory"),
        )

        val context = RoomUnsealContext.from(ROOM_ID, members, snapshot)

        assertThat(context.members.single { it.userId == AGENT_ID }.isAgent).isTrue()
        assertThat(context.hasAgentInRoom).isTrue()
        assertThat(context.deviceAgentInRoom?.boundDeviceId).isEqualTo("DEVICEID")
        assertThat(context.activeScheduleCount).isEqualTo(1)
        assertThat(context.workingMemory).isEqualTo("memory")
        assertThat(context.errors).isEmpty()
    }

    @Test
    fun `from preserves partial errors`() {
        val error = IllegalStateException("room agents failed")
        val snapshot = RoomUnsealDataSnapshot(
            roomAgents = RoomUnsealResource.failure(emptyList(), error),
        )

        val context = RoomUnsealContext.from(ROOM_ID, emptyList(), snapshot)

        assertThat(context.hasAgentInRoom).isFalse()
        assertThat(context.errors).containsExactly(error)
    }

    private companion object {
        val ROOM_ID = RoomId("!room:example.org")
        val AGENT_ID = UserId("@agent:example.org")
        val USER_ID = UserId("@user:example.org")
    }
}
