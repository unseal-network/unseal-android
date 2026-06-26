/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.detail

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class AgentDetailRenderModelTest {
    @Test
    fun `toRenderModel - exposes iOS detail header actions skills and rooms`() {
        val state = AgentDetailState(
            botName = "planner",
            agent = ChatbotAgent(
                botName = "planner",
                localpart = "planner",
                serverName = "example.org",
                displayName = "Planner",
                description = "Plans work",
                avatarUrl = "mxc://avatar",
                provider = "openai",
                model = "gpt-4o",
                isPublic = true,
                soul = "You plan clearly and concisely. ".repeat(8),
            ),
            rooms = persistentListOf(
                ChatbotAgentRoom(roomId = "!ops:example.org", roomName = "Ops"),
                ChatbotAgentRoom(roomId = "!raw:example.org"),
            ),
            agentSkills = persistentListOf(
                ChatbotUserSkill(id = "calendar", name = "Calendar", description = "Reads events"),
                ChatbotUserSkill(id = "", name = "Research"),
            ),
            channels = persistentListOf(),
            isLoading = false,
            isStartingChat = false,
            isSoulExpanded = false,
            error = null,
            copiedAgentId = null,
            eventSink = {},
        )

        val model = state.toRenderModel()

        assertThat(model.navigationTitle).isEqualTo("Planner")
        assertThat(model.displayName).isEqualTo("Planner")
        assertThat(model.matrixId).isEqualTo("@planner:example.org")
        assertThat(model.providerModelLabel).isEqualTo("openai · gpt-4o")
        assertThat(model.visibilityLabel).isEqualTo("公开")
        assertThat(model.description).isEqualTo("Plans work")
        assertThat(model.canStartChat).isTrue()
        assertThat(model.copyableAgentId).isEqualTo("@planner:example.org")
        assertThat(model.agentProfileUrl).endsWith("/@planner")

        assertThat(model.soul?.title).isEqualTo("角色设定")
        assertThat(model.soul?.canToggle).isTrue()
        assertThat(model.soul?.toggleLabel).isEqualTo("展开 ↓")

        assertThat(model.skills.items.map { it.name }).containsExactly("Calendar", "Research").inOrder()
        assertThat(model.skills.items[0].initial).isEqualTo("C")
        assertThat(model.skills.items[0].description).isEqualTo("Reads events")
        assertThat(model.skills.items[1].colorKey).isEqualTo("1")

        assertThat(model.rooms.countLabel).isEqualTo("2")
        assertThat(model.rooms.items[0]).isEqualTo(AgentRoomRenderModel(roomId = "!ops:example.org", displayName = "Ops", subtitle = "!ops:example.org"))
        assertThat(model.rooms.items[1]).isEqualTo(AgentRoomRenderModel(roomId = "!raw:example.org", displayName = "!raw:example.org", subtitle = null))
    }

    @Test
    fun `toRenderModel - disables chat without mxid and keeps empty section labels`() {
        val model = AgentDetailState(
            botName = "plain",
            agent = ChatbotAgent(botName = "plain", displayName = "", isPublic = false),
            rooms = persistentListOf(),
            agentSkills = persistentListOf(),
            channels = persistentListOf(),
            isLoading = false,
            isStartingChat = false,
            isSoulExpanded = false,
            error = null,
            copiedAgentId = null,
            eventSink = {},
        ).toRenderModel()

        assertThat(model.navigationTitle).isEqualTo("plain")
        assertThat(model.canStartChat).isFalse()
        assertThat(model.visibilityLabel).isEqualTo("私密")
        assertThat(model.skills.addFirstLabel).isEqualTo("为此 Agent 添加技能")
        assertThat(model.rooms.emptyLabel).isEqualTo("尚未加入任何房间")
    }
}
