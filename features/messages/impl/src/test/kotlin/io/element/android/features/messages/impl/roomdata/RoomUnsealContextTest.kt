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
    fun `from uses room agents when all agents response is incomplete`() {
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

        assertThat(context.agentsInRoom.single()).isEqualTo(
            RoomAgentInRoomDescriptor(
                agentId = ACTIVE_AGENT_ID.value,
                mxid = ACTIVE_AGENT_ID.value,
                label = "Mail Agent",
                avatarUrl = null,
                isDeviceAgent = false,
                boundDeviceId = null,
            )
        )
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
    fun `from uses explicit room agents before matrix members are available`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = emptyList(),
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    listOf(RoomAgentDescriptor(ACTIVE_AGENT_ID.value, "Room Device", "mxc://avatar", "bot", "join"))
                ),
                allAgents = RoomUnsealResource.success(
                    listOf(
                        AgentAccountDescriptor(
                            botName = "device",
                            localpart = ACTIVE_AGENT_ID.value.substringAfter("@").substringBefore(":"),
                            serverName = ACTIVE_AGENT_ID.value.substringAfter(":"),
                            matrixUserId = ACTIVE_AGENT_ID.value,
                            displayName = "Global Device",
                            avatarUrl = null,
                            isDeviceAgent = true,
                            boundDeviceId = "device-1",
                        )
                    )
                ),
            ),
        )

        assertThat(context.agentsInRoom.single()).isEqualTo(
            RoomAgentInRoomDescriptor(
                agentId = ACTIVE_AGENT_ID.value,
                mxid = ACTIVE_AGENT_ID.value,
                label = "Room Device",
                avatarUrl = "mxc://avatar",
                isDeviceAgent = true,
                boundDeviceId = "device-1",
            )
        )
        assertThat(context.deviceAgentInRoom).isEqualTo(
            RoomDeviceAgent(
                boundDeviceId = "device-1",
                displayName = "Room Device",
                matrixUserId = ACTIVE_AGENT_ID.value,
            )
        )
        assertThat(context.hasAgentInRoom).isTrue()
    }

    @Test
    fun `from matches global agent to room member by bot name when mxid is missing`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(aRoomMember(userId = ACTIVE_AGENT_ID, displayName = "geminirayson", membership = RoomMembershipState.JOIN)),
            snapshot = RoomUnsealDataSnapshot(
                allAgents = RoomUnsealResource.success(
                    listOf(
                        AgentAccountDescriptor(
                            botName = "geminirayson",
                            localpart = null,
                            serverName = null,
                            matrixUserId = null,
                            displayName = "GeminiRayson",
                            avatarUrl = null,
                            isDeviceAgent = false,
                            boundDeviceId = null,
                        )
                    )
                ),
            ),
        )

        assertThat(context.agentsInRoom.map { it.mxid }).containsExactly(ACTIVE_AGENT_ID.value)
        assertThat(context.agentSkillTargets.single()).isEqualTo(
            RoomAgentSkillTargetDescriptor(
                agentId = ACTIVE_AGENT_ID.value,
                mxid = ACTIVE_AGENT_ID.value,
                label = "GeminiRayson",
            )
        )
        assertThat(context.hasAgentInRoom).isTrue()
    }

    @Test
    fun `from does not match global agent by name when multiple room members match`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(
                aRoomMember(userId = ACTIVE_AGENT_ID, displayName = "geminirayson", membership = RoomMembershipState.JOIN),
                aRoomMember(userId = UserId("@other:example.org"), displayName = "geminirayson", membership = RoomMembershipState.JOIN),
            ),
            snapshot = RoomUnsealDataSnapshot(
                allAgents = RoomUnsealResource.success(
                    listOf(
                        AgentAccountDescriptor(
                            botName = "geminirayson",
                            localpart = null,
                            serverName = null,
                            matrixUserId = null,
                            displayName = "GeminiRayson",
                            avatarUrl = null,
                            isDeviceAgent = false,
                            boundDeviceId = null,
                        )
                    )
                ),
            ),
        )

        assertThat(context.agentsInRoom).isEmpty()
        assertThat(context.agentSkillTargets).isEmpty()
        assertThat(context.hasAgentInRoom).isFalse()
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
    fun `from preserves global device metadata while using room agent mxid and label`() {
        val context = RoomUnsealContext.from(
            roomId = ROOM_ID,
            members = listOf(
                aRoomMember(userId = ACTIVE_AGENT_ID, displayName = "Matrix fallback", membership = RoomMembershipState.JOIN),
            ),
            snapshot = RoomUnsealDataSnapshot(
                roomAgents = RoomUnsealResource.success(
                    listOf(RoomAgentDescriptor(ACTIVE_AGENT_ID.value, "Room Device", "mxc://avatar", "agent", "join"))
                ),
                allAgents = RoomUnsealResource.success(
                    listOf(
                        AgentAccountDescriptor(
                            botName = "device",
                            localpart = null,
                            serverName = null,
                            matrixUserId = ACTIVE_AGENT_ID.value,
                            displayName = "Global Device",
                            avatarUrl = null,
                            isDeviceAgent = true,
                            boundDeviceId = "device-1",
                        )
                    )
                ),
            ),
        )

        assertThat(context.agentsInRoom.single()).isEqualTo(
            RoomAgentInRoomDescriptor(
                agentId = ACTIVE_AGENT_ID.value,
                mxid = ACTIVE_AGENT_ID.value,
                label = "Room Device",
                avatarUrl = "mxc://avatar",
                isDeviceAgent = true,
                boundDeviceId = "device-1",
            )
        )
        assertThat(context.deviceAgentInRoom).isEqualTo(
            RoomDeviceAgent(
                boundDeviceId = "device-1",
                displayName = "Room Device",
                matrixUserId = ACTIVE_AGENT_ID.value,
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
