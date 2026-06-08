/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.edit

import io.element.android.features.agentmanagement.impl.shared.needsApiKey
import io.element.android.features.agentmanagement.impl.shared.supportsBaseUrl
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotProviderModel
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList

sealed interface AgentEditMode {
    data object Create : AgentEditMode
    data class Edit(val botName: String) : AgentEditMode
}

enum class AgentNameAvailability {
    Unknown,
    Checking,
    Available,
    Taken,
}

sealed interface AgentEditPhase {
    data object Editing : AgentEditPhase
    data class Submitting(val step: AgentEditSubmittingStep) : AgentEditPhase
    data class Success(val summary: AgentCreateSuccessSummary) : AgentEditPhase
}

enum class AgentEditSubmittingStep {
    CreateAgent,
    CreateDM,
}

data class AgentCreateSuccessSummary(
    val botName: String,
    val localpart: String?,
    val serverName: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val provider: String?,
    val model: String?,
    val baseUrl: String?,
    val isPublic: Boolean,
    val directRoomId: RoomId?,
)

data class AgentEditFormState(
    val botName: String = "",
    val displayName: String = "",
    val description: String = "",
    val avatarUrl: String = "",
    val isPublic: Boolean = false,
    val autoJoin: Boolean = true,
    val providerId: String? = null,
    val model: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val soul: String = "",
) {
    companion object {
        fun fromAgent(agent: ChatbotAgent, botName: String = agent.botName): AgentEditFormState {
            return AgentEditFormState(
                botName = botName,
                displayName = agent.displayName.orEmpty(),
                description = agent.description.orEmpty(),
                avatarUrl = agent.avatarUrl.orEmpty(),
                isPublic = agent.isPublic ?: false,
                autoJoin = agent.settings?.autoJoin ?: agent.config?.autoJoin ?: true,
                providerId = agent.provider,
                model = agent.model.orEmpty(),
                apiKey = agent.apiKey.orEmpty(),
                baseUrl = agent.baseUrl.orEmpty(),
                soul = agent.soul.orEmpty(),
            )
        }
    }
}

data class AgentEditState(
    val mode: AgentEditMode,
    val form: AgentEditFormState,
    val providers: ImmutableList<ChatbotAgentProvider>,
    val nameAvailability: AgentNameAvailability,
    val phase: AgentEditPhase,
    val isLoading: Boolean,
    val error: String?,
    val eventSink: (AgentEditEvents) -> Unit,
) {
    val selectedProvider: ChatbotAgentProvider? = providers.firstOrNull { it.id == form.providerId }
    val availableModels: List<ChatbotProviderModel> = selectedProvider?.info?.models.orEmpty()
    val needsApiKey: Boolean = needsApiKey(form.providerId)
    val supportsBaseUrl: Boolean = supportsBaseUrl(selectedProvider)
    val isCreate: Boolean = mode is AgentEditMode.Create
}
