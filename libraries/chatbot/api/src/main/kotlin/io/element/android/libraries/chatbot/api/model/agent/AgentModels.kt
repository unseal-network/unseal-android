/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.agent

import io.element.android.libraries.chatbot.api.model.json.ChatbotJsonMap
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatbotAgent(
    @SerialName("id")
    val configId: Long? = null,
    @SerialName("bot_name")
    val botName: String,
    val localpart: String? = null,
    @SerialName("server_name")
    val serverName: String? = null,
    @SerialName("display_name")
    val displayName: String? = null,
    val description: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("event_types")
    val eventTypes: List<String>? = null,
    val config: ChatbotAgentConfig? = null,
    val settings: ChatbotAgentSettings? = null,
    @SerialName("is_public")
    val isPublic: Boolean? = null,
    val provider: String? = null,
    val model: String? = null,
    @SerialName("base_url")
    val baseUrl: String? = null,
    @SerialName("api_key")
    val apiKey: String? = null,
    @SerialName("provider_agent_id")
    val providerAgentId: String? = null,
    val metadata: ChatbotJsonMap? = null,
    val soul: String? = null,
    @SerialName("created_at")
    val createdAt: Long? = null,
    @SerialName("updated_at")
    val updatedAt: Long? = null,
)

@Serializable
data class ChatbotAgentConfig(
    @SerialName("response_mode")
    val responseMode: String? = null,
    @SerialName("auto_join")
    val autoJoin: Boolean? = null,
    @SerialName("include_history")
    val includeHistory: Boolean? = null,
)

@Serializable
data class ChatbotAgentSettings(
    @SerialName("response_mode")
    val responseMode: String? = null,
    @SerialName("auto_join")
    val autoJoin: Boolean? = null,
    @SerialName("include_history")
    val includeHistory: Boolean? = null,
    @SerialName("api_key")
    val apiKey: String? = null,
)

@Serializable
data class ChatbotCreateAgentRequest(
    @SerialName("bot_name")
    val botName: String,
    @SerialName("display_name")
    val displayName: String? = null,
    val description: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("is_public")
    val isPublic: Boolean = false,
    val provider: String? = null,
    val model: String? = null,
    @SerialName("api_key")
    val apiKey: String? = null,
    @SerialName("base_url")
    val baseUrl: String? = null,
    val soul: String? = null,
    val settings: ChatbotAgentSettings? = null,
    val sandbox: AgentSandboxModeWrapper? = null,
)

@Serializable
data class ChatbotUpdateAgentRequest(
    @SerialName("display_name")
    val displayName: String? = null,
    val description: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("is_public")
    val isPublic: Boolean? = null,
    val provider: String? = null,
    val model: String? = null,
    @SerialName("api_key")
    val apiKey: String? = null,
    @SerialName("base_url")
    val baseUrl: String? = null,
    val soul: String? = null,
    val settings: ChatbotAgentSettings? = null,
    val sandbox: AgentSandboxModeWrapper? = null,
    @SerialName("agent_vault")
    val agentVault: AgentVaultEntriesWrapper? = null,
)

@Serializable
data class ChatbotListAgentsResponse(
    val agents: List<ChatbotAgent> = emptyList(),
)

@Serializable
data class ChatbotProviderModel(
    val id: String,
    @SerialName("display_name")
    val displayName: String? = null,
    val type: String? = null,
)

@Serializable
data class ChatbotAgentProviderInfo(
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("supports_base_url")
    val supportsBaseUrl: Boolean? = null,
    val models: List<ChatbotProviderModel> = emptyList(),
)

@Serializable
data class ChatbotAgentProvider(
    val id: String,
    @SerialName("display_name")
    val displayName: String? = null,
    val info: ChatbotAgentProviderInfo? = null,
)

@Serializable
data class ChatbotGetAgentProvidersResponse(
    val providers: List<ChatbotAgentProvider> = emptyList(),
)

@Serializable
data class ChatbotAgentRoom(
    @SerialName("room_id")
    val roomId: String,
    @SerialName("room_name")
    val roomName: String? = null,
    val alias: String? = null,
    val joined: Boolean? = null,
)

@Serializable
data class ChatbotListAgentRoomsResponse(
    val rooms: List<ChatbotAgentRoom> = emptyList(),
)

@Serializable
enum class AgentSandboxMode {
    @SerialName("none")
    None,
    @SerialName("blank")
    Blank,
    @SerialName("clone")
    Clone,
}

@Serializable
data class AgentSandboxStatus(
    val mode: AgentSandboxMode? = null,
    val status: String? = null,
)

@Serializable
data class AgentSandboxModeWrapper(
    val mode: AgentSandboxMode,
)

@Serializable
data class AgentVaultEntry(
    val key: String,
    val value: String? = null,
    val description: String? = null,
)

@Serializable
data class AgentVaultEntryInput(
    val key: String,
    val value: String,
    val description: String? = null,
)

@Serializable
data class AgentVaultEntriesWrapper(
    val entries: List<AgentVaultEntryInput> = emptyList(),
)
