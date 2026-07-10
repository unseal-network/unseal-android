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
    fun `reduce exposes a stable tool menu before unseal context loads`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Uninitialized,
            hasThreads = false,
            isThreadTimeline = false,
        )

        assertThat(roomMenu.topbarActions).containsExactly(
            RoomTopbarAction.Schedules,
            RoomTopbarAction.Webhooks,
            RoomTopbarAction.DeviceAgentChat,
            RoomTopbarAction.DeviceAgentTerminal,
        ).inOrder()
        assertThat(roomMenu.topbarTools.map { it.action }).containsExactly(
            RoomTopbarAction.DeviceAgentTerminal,
            RoomTopbarAction.DeviceAgentChat,
            RoomTopbarAction.Webhooks,
            RoomTopbarAction.Schedules,
        ).inOrder()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.DeviceAgentTerminal }.isEnabled).isFalse()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.DeviceAgentChat }.isEnabled).isFalse()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.Schedules }.badgeCount).isNull()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.Webhooks }.badgeCount).isNull()
    }

    @Test
    fun `reduce does not expose threads as an iOS parity room topbar action`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Uninitialized,
            hasThreads = true,
            isThreadTimeline = false,
        )

        assertThat(roomMenu.topbarActions).doesNotContain(RoomTopbarAction.Threads)
    }

    @Test
    fun `reduce hides threads action inside thread timeline`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Uninitialized,
            hasThreads = true,
            isThreadTimeline = true,
        )

        assertThat(roomMenu.topbarActions).doesNotContain(RoomTopbarAction.Threads)
    }

    @Test
    fun `reduce exposes schedules action and badge when an agent is in the room`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Success(contextWithAgent(activeScheduleCount = 2)),
            hasThreads = false,
            isThreadTimeline = false,
        )

        assertThat(roomMenu.topbarActions).containsExactly(
            RoomTopbarAction.Schedules,
            RoomTopbarAction.Webhooks,
            RoomTopbarAction.DeviceAgentChat,
            RoomTopbarAction.DeviceAgentTerminal,
        ).inOrder()
        assertThat(roomMenu.topbarTools.map { it.action }).containsExactly(
            RoomTopbarAction.DeviceAgentTerminal,
            RoomTopbarAction.DeviceAgentChat,
            RoomTopbarAction.Webhooks,
            RoomTopbarAction.Schedules,
        ).inOrder()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.DeviceAgentTerminal }.isEnabled).isFalse()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.DeviceAgentChat }.isEnabled).isFalse()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.Schedules }.badgeCount).isEqualTo(2)
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
            RoomTopbarAction.Webhooks,
            RoomTopbarAction.DeviceAgentChat,
            RoomTopbarAction.DeviceAgentTerminal,
        ).inOrder()
        assertThat(roomMenu.topbarTools.map { it.action }).containsExactly(
            RoomTopbarAction.DeviceAgentTerminal,
            RoomTopbarAction.DeviceAgentChat,
            RoomTopbarAction.Webhooks,
            RoomTopbarAction.Schedules,
        ).inOrder()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.DeviceAgentTerminal }.isEnabled).isTrue()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.DeviceAgentChat }.isEnabled).isTrue()
        assertThat(roomMenu.deviceAgent?.boundDeviceId).isEqualTo("device-1")
    }

    @Test
    fun `reduce marks device agent chat active when bound device matches`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Success(contextWithAgent(isDeviceAgent = true)),
            hasThreads = false,
            isThreadTimeline = false,
            activeDeviceAgentBoundDeviceId = "device-1",
        )

        assertThat(roomMenu.isDeviceAgentChatActive).isTrue()
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.DeviceAgentChat }.isActive).isTrue()
    }

    @Test
    fun `reduce exposes webhook summary and working memory state from room context`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Success(
                contextWithAgent(
                    webhookTriggers = listOf(
                        webhookTrigger("enabled", status = "enabled"),
                        webhookTrigger("active", status = "ACTIVE"),
                        webhookTrigger("disabled", status = "disabled"),
                    ),
                    workingMemory = "Remember\n  this room prefers concise agent replies.",
                )
            ),
            hasThreads = false,
            isThreadTimeline = false,
        )

        assertThat(roomMenu.webhookSummary?.totalCount).isEqualTo(3)
        assertThat(roomMenu.webhookSummary?.activeCount).isEqualTo(2)
        assertThat(roomMenu.topbarActions).contains(RoomTopbarAction.Webhooks)
        assertThat(roomMenu.topbarTools.single { it.action == RoomTopbarAction.Webhooks }.badgeCount).isEqualTo(2)
        assertThat(roomMenu.workingMemory?.hasContent).isTrue()
        assertThat(roomMenu.workingMemory?.preview).isEqualTo("Remember this room prefers concise agent replies.")
    }

    @Test
    fun `reduce exposes attachment actions in stable composer order`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Uninitialized,
            hasThreads = false,
            isThreadTimeline = false,
            canShareLocation = true,
            enableTextFormatting = true,
        )

        assertThat(roomMenu.attachmentActions).containsExactly(
            RoomAttachmentAction.Game,
            RoomAttachmentAction.TextFormatting,
            RoomAttachmentAction.Poll,
            RoomAttachmentAction.Ping,
            RoomAttachmentAction.Location,
            RoomAttachmentAction.Files,
            RoomAttachmentAction.Gallery,
            RoomAttachmentAction.PhotoFromCamera,
            RoomAttachmentAction.VideoFromCamera,
        ).inOrder()
    }

    @Test
    fun `reduce exposes skill attachment action when an agent is in the room`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Success(contextWithAgent()),
            hasThreads = false,
            isThreadTimeline = false,
            canShareLocation = true,
            enableTextFormatting = true,
        )

        assertThat(roomMenu.attachmentActions).containsExactly(
            RoomAttachmentAction.Game,
            RoomAttachmentAction.TextFormatting,
            RoomAttachmentAction.Skill,
            RoomAttachmentAction.Poll,
            RoomAttachmentAction.Ping,
            RoomAttachmentAction.Location,
            RoomAttachmentAction.Files,
            RoomAttachmentAction.Gallery,
            RoomAttachmentAction.PhotoFromCamera,
            RoomAttachmentAction.VideoFromCamera,
        ).inOrder()
    }

    @Test
    fun `reduce exposes skill attachment action immediately for direct agent room member`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Uninitialized,
            hasThreads = false,
            isThreadTimeline = false,
            hasDirectAgentMember = true,
        )

        assertThat(roomMenu.attachmentActions).contains(RoomAttachmentAction.Skill)
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Skill }.isAvailable).isTrue()
    }

    @Test
    fun `reduce exposes full ios attachment action contract with unavailable gaps`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Uninitialized,
            hasThreads = false,
            isThreadTimeline = false,
            canShareLocation = true,
            enableTextFormatting = true,
        )

        assertThat(roomMenu.attachmentActionEntries.map { it.action }).containsExactly(
            RoomAttachmentAction.Game,
            RoomAttachmentAction.TextFormatting,
            RoomAttachmentAction.Skill,
            RoomAttachmentAction.Poll,
            RoomAttachmentAction.Ping,
            RoomAttachmentAction.Sketch,
            RoomAttachmentAction.Location,
            RoomAttachmentAction.Files,
            RoomAttachmentAction.Gallery,
            RoomAttachmentAction.PhotoFromCamera,
            RoomAttachmentAction.VideoFromCamera,
        ).inOrder()
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Ping }.isAvailable).isTrue()
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Ping }.unavailableReason)
            .isNull()
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Sketch }.isAvailable).isFalse()
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Sketch }.unavailableReason)
            .isEqualTo(RoomAttachmentActionUnavailableReason.RequiresBottomLayer)
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Skill }.isAvailable).isFalse()
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Skill }.unavailableReason)
            .isEqualTo(RoomAttachmentActionUnavailableReason.DisabledByRoomCapability)
    }

    @Test
    fun `reduce hides gated attachment actions`() {
        val roomMenu = RoomMenuReducer.reduce(
            roomUnsealContext = AsyncData.Uninitialized,
            hasThreads = false,
            isThreadTimeline = false,
            canShareLocation = false,
            enableTextFormatting = false,
        )

        assertThat(roomMenu.attachmentActions).containsExactly(
            RoomAttachmentAction.Game,
            RoomAttachmentAction.Poll,
            RoomAttachmentAction.Ping,
            RoomAttachmentAction.Files,
            RoomAttachmentAction.Gallery,
            RoomAttachmentAction.PhotoFromCamera,
            RoomAttachmentAction.VideoFromCamera,
        ).inOrder()
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.TextFormatting }.isAvailable).isFalse()
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.TextFormatting }.unavailableReason)
            .isEqualTo(RoomAttachmentActionUnavailableReason.DisabledByRoomCapability)
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Location }.isAvailable).isFalse()
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Location }.unavailableReason)
            .isEqualTo(RoomAttachmentActionUnavailableReason.DisabledByRoomCapability)
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Skill }.isAvailable).isFalse()
        assertThat(roomMenu.attachmentActionEntries.single { it.action == RoomAttachmentAction.Skill }.unavailableReason)
            .isEqualTo(RoomAttachmentActionUnavailableReason.DisabledByRoomCapability)
    }

    private fun contextWithAgent(
        activeScheduleCount: Int = 0,
        isDeviceAgent: Boolean = false,
        webhookTriggers: List<RoomWebhookTriggerDescriptor> = emptyList(),
        workingMemory: String = "",
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
                webhookTriggers = RoomUnsealResource.success(webhookTriggers),
                workingMemory = RoomUnsealResource.success(workingMemory),
            )
        )
    }

    private fun webhookTrigger(
        id: String,
        status: String,
    ) = RoomWebhookTriggerDescriptor(
        id = id,
        agentId = "agent",
        name = "Trigger $id",
        source = "gmail",
        roomId = ROOM_ID.value,
        status = status,
        actionPrompt = "Run",
    )

    private companion object {
        val ROOM_ID = RoomId("!room:example.org")
        val AGENT_USER_ID = UserId("@agent:example.org")
    }
}
