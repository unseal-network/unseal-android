/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.list

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import kotlinx.collections.immutable.ImmutableList

data class AgentListState(
    val agents: ImmutableList<ChatbotAgent>,
    val filteredAgents: ImmutableList<ChatbotAgent>,
    val searchQuery: String,
    val isLoading: Boolean,
    val error: String?,
    val eventSink: (AgentListEvents) -> Unit,
) {
    val renderModel: AgentListRenderModel = toRenderModel()
}
