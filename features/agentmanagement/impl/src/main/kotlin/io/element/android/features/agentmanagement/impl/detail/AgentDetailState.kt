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
import kotlinx.collections.immutable.ImmutableList

data class AgentDetailState(
    val botName: String,
    val agent: ChatbotAgent?,
    val rooms: ImmutableList<ChatbotAgentRoom>,
    val isLoading: Boolean,
    val isStartingChat: Boolean,
    val isSoulExpanded: Boolean,
    val error: String?,
    val copiedAgentId: String?,
    val eventSink: (AgentDetailEvents) -> Unit,
) {
    val navigationTitle: String = agent?.displayTitle() ?: botName
    val matrixId: String? = agent?.matrixId()
    val providerModelText: String? = agent?.providerModelText()
    val agentMatrixUserId: String? = agent?.agentMatrixUserId()
    val copyableAgentId: String = agent?.copyableAgentId() ?: botName
    val canStartChat: Boolean = agentMatrixUserId != null && !isLoading && !isStartingChat

    /** Public web profile page for the agent (`<website>/@<localpart>`), shown via the top-bar link. */
    val agentProfileUrl: String? = (agent?.localpart ?: botName)
        .takeIf { it.isNotBlank() }
        ?.let { "${ChatbotConfig.WEBSITE_BASE_URL}/@$it" }
}

fun ChatbotAgentRoom.displayName(): String = roomName?.takeIf { it.isNotBlank() } ?: alias?.takeIf { it.isNotBlank() } ?: roomId
