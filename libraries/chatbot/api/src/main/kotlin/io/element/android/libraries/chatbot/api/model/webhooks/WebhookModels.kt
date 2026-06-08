/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.webhooks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatbotWebhookTriggerStatus {
    @SerialName("enabled")
    Enabled,
    @SerialName("disabled")
    Disabled,
    @SerialName("provider_disabled")
    ProviderDisabled,
}

@Serializable
data class ChatbotWebhookTrigger(
    val triggerId: String,
    val agentId: String,
    val name: String,
    val description: String? = null,
    val source: String? = null,
    val mode: String? = null,
    val eventTypes: List<String> = emptyList(),
    val providerTriggers: List<ChatbotWebhookProviderTrigger>? = null,
    val actionPrompt: String,
    val roomId: String,
    val targetUserId: String? = null,
    val status: ChatbotWebhookTriggerStatus,
    val publicWebhookUrl: String? = null,
    val secretHint: String? = null,
    val lastTriggeredAt: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class ChatbotWebhookProviderTrigger(
    val id: String? = null,
    val connectionId: String? = null,
    val eventType: String? = null,
    val providerTriggerId: String? = null,
    val providerTriggerSlug: String? = null,
    val status: String? = null,
)

@Serializable
data class ChatbotWebhookEventSource(
    val source: String,
    val name: String,
    val connections: List<ChatbotWebhookEventConnection> = emptyList(),
    val eventTypes: List<ChatbotWebhookEventType> = emptyList(),
)

@Serializable
data class ChatbotWebhookEventConnection(
    val connectionId: String,
    val name: String? = null,
    val status: String? = null,
)

@Serializable
data class ChatbotWebhookEventType(
    val eventType: String,
    val name: String,
    val introduce: String? = null,
    val hasConfigSchema: Boolean? = null,
    val hasPayloadSchema: Boolean? = null,
    val triggerKind: String? = null,
)

@Serializable
data class ChatbotWebhookEventCatalogResponse(
    val sources: List<ChatbotWebhookEventSource> = emptyList(),
)

@Serializable
data class ChatbotCreateWebhookTriggerRequest(
    val agentId: String,
    val name: String,
    val description: String? = null,
    val connectionId: String? = null,
    val eventTypes: List<String>,
    val actionPrompt: String,
    val roomId: String,
)

@Serializable
data class ChatbotUpdateWebhookTriggerRequest(
    val name: String? = null,
    val description: String? = null,
    val actionPrompt: String? = null,
    val roomId: String? = null,
)

@Serializable
data class ChatbotWebhookTriggerStatusResponse(
    val triggerId: String,
    val status: String,
)

@Serializable
data class ChatbotWebhookTriggerDeleteResponse(
    val triggerId: String,
    val deleted: Boolean,
)

@Serializable
data class ChatbotWebhookTriggerDraftResponse(
    val source: String,
    val eventTypes: List<String>,
    val name: String,
    val actionPrompt: String,
    val missingFields: List<String>? = null,
    val confidence: Double? = null,
)
