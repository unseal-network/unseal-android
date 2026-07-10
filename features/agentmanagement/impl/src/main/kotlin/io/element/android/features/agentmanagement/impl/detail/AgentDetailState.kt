/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.detail

import io.element.android.features.agentmanagement.impl.shared.agentMatrixUserId
import io.element.android.features.agentmanagement.impl.shared.copyableAgentId
import io.element.android.features.agentmanagement.impl.shared.displayTitle
import io.element.android.features.agentmanagement.impl.shared.matrixId
import io.element.android.features.agentmanagement.impl.shared.providerModelText
import io.element.android.libraries.chatbot.api.ChatbotConfig
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import kotlinx.collections.immutable.ImmutableList

data class AgentDetailState(
    val botName: String,
    val initialMatrixUserId: String? = null,
    val agent: ChatbotAgent?,
    val rooms: ImmutableList<ChatbotAgentRoom>,
    val agentSkills: ImmutableList<ChatbotUserSkill>,
    val channels: ImmutableList<ChatbotChannelSummary>,
    val isLoading: Boolean,
    val isStartingChat: Boolean,
    val isSoulExpanded: Boolean,
    /** Whether the current user owns this agent (only owners may edit it). */
    val canEdit: Boolean = true,
    val isStoppingTasks: Boolean = false,
    val confirmStopAllTasks: Boolean = false,
    val error: String?,
    val copiedAgentId: String?,
    val eventSink: (AgentDetailEvents) -> Unit,
) {
    val renderModel: AgentDetailRenderModel = toRenderModel()
    val navigationTitle: String = agent?.displayTitle() ?: botName
    val matrixId: String? = agent?.matrixId()
    val providerModelText: String? = agent?.providerModelText()
    val agentMatrixUserId: String? = agent?.agentMatrixUserId()
    val copyableAgentId: String = agent?.copyableAgentId() ?: botName
    val canStartChat: Boolean = agentMatrixUserId != null && !isLoading && !isStartingChat

    /** The agent's own host (e.g. keepsecret.io), taken from its Matrix id (`@localpart:host`). */
    private val agentHost: String? = agentMatrixUserId?.substringAfterLast(":")?.takeIf { it.isNotBlank() }
        ?: agent?.serverName?.takeIf { it.isNotBlank() }
        ?: initialMatrixUserId?.substringAfterLast(":")?.takeIf { it.isNotBlank() }

    private val agentLocalpart: String? = (agent?.localpart ?: botName).takeIf { it.isNotBlank() }

    /** Public web profile page for the agent: `https://<agent host>/@<localpart>` (top-bar link). */
    val agentProfileUrl: String? = agentLocalpart?.let { localpart ->
        agentHost?.let { "https://$it/@$localpart" } ?: "${ChatbotConfig.WEBSITE_BASE_URL}/@$localpart"
    }

    /** Connect link given to another agent: `https://<host>/a2a/@<localpart>.md` (the md self-describes). */
    val agentConnectUrl: String? = agentLocalpart?.let { localpart ->
        agentHost?.let { "https://$it/a2a/@$localpart.md" }
    }
}

fun ChatbotAgentRoom.displayName(): String = roomName?.takeIf { it.isNotBlank() } ?: alias?.takeIf { it.isNotBlank() } ?: roomId
