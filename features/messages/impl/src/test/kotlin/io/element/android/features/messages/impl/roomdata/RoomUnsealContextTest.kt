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
    fun `from exposes room agents and skill targets from active room members`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(
                aRoomMember(userId = ACTIVE_AGENT_ID, displayName = "Gemini", membership = RoomMembershipState.JOIN),
                aRoomMember(userId = INVITED_AGENT_ID, displayName = "Invited", membership = RoomMembershipState.INVITE),
            ),
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    listOf(
                        RoomAgentDescriptor(ACTIVE_AGENT_ID.value, "Gemini", null, "agent", "join"),
                        RoomAgentDescriptor(INVITED_AGENT_ID.value, "Invited", null, "agent", "invite"),
                    )
                ),
                allAgents = RoomUnsealResource.success(
                    listOf(
                        agentAccount(mxid = ACTIVE_AGENT_ID.value, label = "Gemini"),
                        agentAccount(mxid = INVITED_AGENT_ID.value, label = "Invited"),
                    )
                ),
            ),
        )

        assertThat(context.agentsInRoom.map { it.mxid }).containsExactly(ACTIVE_AGENT_ID.value)
        assertThat(context.agentSkillTargets.map { it.mxid }).containsExactly(ACTIVE_AGENT_ID.value)
        assertThat(context.hasAgentInRoom).isTrue()
    }

    @Test
    fun `from keeps member agent skill target when all agents response is incomplete`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(aRoomMember(userId = ACTIVE_AGENT_ID, displayName = "Mail Agent", membership = RoomMembershipState.JOIN)),
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    listOf(RoomAgentDescriptor(ACTIVE_AGENT_ID.value, "Mail Agent", null, "agent", "join"))
                ),
                allAgents = RoomUnsealResource.success(emptyList()),
            ),
        )

        assertThat(context.agentsInRoom).isEmpty()
        assertThat(context.agentSkillTargets.single()).isEqualTo(
            RoomAgentSkillTargetDescriptor(
                agentId = ACTIVE_AGENT_ID.value,
                mxid = ACTIVE_AGENT_ID.value,
                label = "Mail Agent",
            )
        )
        assertThat(context.hasAgentInRoom).isTrue()
    }

    @Test
    fun `from prefers room agent display data for skill targets`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(
                aRoomMember(userId = ACTIVE_AGENT_ID, displayName = "Matrix fallback", membership = RoomMembershipState.JOIN),
            ),
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    listOf(RoomAgentDescriptor(ACTIVE_AGENT_ID.value, "Room Agent", null, "agent", "join"))
                ),
                allAgents = RoomUnsealResource.success(
                    listOf(agentAccount(mxid = ACTIVE_AGENT_ID.value, label = "Global Agent"))
                ),
            ),
        )

        assertThat(context.agentSkillTargets.single()).isEqualTo(
            RoomAgentSkillTargetDescriptor(
                agentId = ACTIVE_AGENT_ID.value,
                mxid = ACTIVE_AGENT_ID.value,
                label = "Room Agent",
            )
        )
    }

    @Test
    fun `from exposes webhook summary from room context`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = emptyList(),
            snapshot = RoomUnsealDataSnapshot(
                webhookTriggers = RoomUnsealResource.success(
                    listOf(
                        webhookTrigger(id = "enabled", status = "enabled"),
                        webhookTrigger(id = "active", status = "ACTIVE"),
                        webhookTrigger(id = "disabled", status = "disabled"),
                    )
                )
            ),
        )

        assertThat(context.webhookSummary).isEqualTo(RoomWebhookSummary(totalCount = 3, activeCount = 2))
    }

    private fun agentAccount(mxid: String, label: String): AgentAccountDescriptor {
        return AgentAccountDescriptor(
            botName = label,
            localpart = mxid.substringAfter("@").substringBefore(":"),
            serverName = mxid.substringAfter(":"),
            matrixUserId = mxid,
            displayName = label,
            avatarUrl = null,
            isDeviceAgent = false,
            boundDeviceId = null,
        )
    }

    private fun webhookTrigger(id: String, status: String): RoomWebhookTriggerDescriptor {
        return RoomWebhookTriggerDescriptor(
            id = id,
            agentId = "agent",
            name = "Trigger $id",
            source = "gmail",
            roomId = ROOM_ID.value,
            status = status,
            actionPrompt = "Run",
        )
    }

    private companion object {
        val ROOM_ID = RoomId("!room:example.org")
        val ACTIVE_AGENT_ID = UserId("@agent:example.org")
        val INVITED_AGENT_ID = UserId("@invited:example.org")
    }
}
