/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.list

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class AgentListRenderModelTest {
    @Test
    fun `toRenderModel - exposes iOS card fields without raw agent parsing in UI`() {
        val now = 1_700_000_000_000L
        val state = AgentListState(
            agents = persistentListOf(),
            filteredAgents = persistentListOf(
                ChatbotAgent(
                    botName = "writer",
                    displayName = "Writer",
                    description = "Drafts docs",
                    avatarUrl = "mxc://avatar",
                    provider = "openai",
                    model = "gpt-4o",
                    isPublic = true,
                    updatedAt = now - 2 * 60 * 60 * 1000,
                ),
                ChatbotAgent(
                    botName = "private-agent",
                    displayName = "",
                    description = "",
                    provider = "anthropic",
                    isPublic = false,
                    createdAt = now - 10 * 60 * 1000,
                ),
            ),
            searchQuery = "draft",
            isLoading = false,
            error = null,
            eventSink = {},
        )

        val renderModel = state.toRenderModel(now)

        assertThat(renderModel.title).isEqualTo("Agent 列表")
        assertThat(renderModel.searchPlaceholder).isEqualTo("搜索 Agent")
        assertThat(renderModel.createLabel).isEqualTo("创建 Agent")
        assertThat(renderModel.skillsLabel).isEqualTo("技能")
        assertThat(renderModel.query).isEqualTo("draft")

        assertThat(renderModel.items.map { it.botName }).containsExactly("writer", "private-agent").inOrder()
        assertThat(renderModel.items[0]).isEqualTo(
            AgentListItemRenderModel(
                botName = "writer",
                title = "Writer",
                avatarUrl = "mxc://avatar",
                providerModelLabel = "openai · gpt-4o",
                description = "Drafts docs",
                isPublic = true,
                visibilityLabel = "公开",
                relativeTimeLabel = "2小时前",
            )
        )
        assertThat(renderModel.items[1].title).isEqualTo("private-agent")
        assertThat(renderModel.items[1].providerModelLabel).isEqualTo("anthropic")
        assertThat(renderModel.items[1].description).isNull()
        assertThat(renderModel.items[1].visibilityLabel).isEqualTo("私密")
        assertThat(renderModel.items[1].relativeTimeLabel).isEqualTo("10分钟前")
    }

    @Test
    fun `toRenderModel - hides invalid timestamps`() {
        val items = listOf(
            ChatbotAgent(botName = "no-time"),
            ChatbotAgent(botName = "zero-time", updatedAt = 0),
            ChatbotAgent(botName = "old-time", updatedAt = 1),
        ).toRenderItems(nowMillis = 1_700_000_000_000L)

        assertThat(items.map { it.relativeTimeLabel }).containsExactly(null, null, null).inOrder()
    }
}
