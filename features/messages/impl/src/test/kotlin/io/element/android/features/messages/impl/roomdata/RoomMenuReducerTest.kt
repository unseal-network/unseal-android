/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.room.aRoomMember
import org.junit.Test

class RoomMenuReducerTest {
    @Test
    fun `reduce exposes threads action outside thread timeline`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Uninitialized,
            hasThreads = true,
            isThreadTimeline = false,
        )

        assertThat(roomMenu.topbarActions).containsExactly(RoomTopbarAction.Threads)
    }

    @Test
    fun `reduce hides threads action inside thread timeline`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Uninitialized,
            hasThreads = true,
            isThreadTimeline = true,
        )

        assertThat(roomMenu.topbarActions).isEmpty()
    }

    @Test
    fun `reduce exposes schedules action and badge when an agent is in the room`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Success(contextWithAgent(activeScheduleCount = 2)),
            hasThreads = false,
            isThreadTimeline = false,
        )

        assertThat(roomMenu.topbarActions).containsExactly(RoomTopbarAction.Schedules)
        assertThat(roomMenu.scheduleBadge?.activeScheduleCount).isEqualTo(2)
        assertThat(roomMenu.scheduleBadge?.isLoading).isFalse()
    }

    @Test
    fun `reduce exposes device agent actions when a device agent is in the room`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Success(contextWithAgent(isDeviceAgent = true)),
            hasThreads = false,
            isThreadTimeline = false,
        )

        assertThat(roomMenu.topbarActions).containsExactly(
            RoomTopbarAction.Schedules,
            RoomTopbarAction.DeviceAgentChat,
            RoomTopbarAction.DeviceAgentTerminal,
        ).inOrder()
        assertThat(roomMenu.deviceAgent?.boundDeviceId).isEqualTo("device-1")
    }

    private fun contextWithAgent(
        activeScheduleCount: Int = 0,
        isDeviceAgent: Boolean = false,
    ): RoomUnsealContext {
        val schedules = List(activeScheduleCount) { index ->
            RoomScheduleDescriptor(
                id = "schedule-$index",
                name = "Schedule $index",
                cron = "* * * * *",
                action = "Run",
                agentId = "agent",
                roomId = ROOM_ID.value,
                timezone = null,
                isEnabled = true,
            )
        }
        return RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(aRoomMember(userId = AGENT_USER_ID, membership = RoomMembershipState.JOIN)),
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    listOf(
                        RoomAgentDescriptor(
                            userId = AGENT_USER_ID.value,
                            displayName = "Agent",
                            avatarUrl = null,
                            userType = "agent",
                            membership = "join",
                        )
                    )
                ),
                allAgents = RoomUnsealResource.success(
                    listOf(
                        AgentAccountDescriptor(
                            botName = "agent",
                            localpart = "agent",
                            serverName = "example.org",
                            matrixUserId = AGENT_USER_ID.value,
                            displayName = "Agent",
                            avatarUrl = null,
                            isDeviceAgent = isDeviceAgent,
                            boundDeviceId = "device-1".takeIf { isDeviceAgent },
                        )
                    )
                ),
                schedules = RoomUnsealResource.success(schedules),
            )
        )
    }

    private companion object {
        val ROOM_ID = RoomId("!room:example.org")
        val AGENT_USER_ID = UserId("@agent:example.org")
    }
}
