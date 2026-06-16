/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.edit

import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventConnection
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventSource

sealed interface WebhookTriggerEditEvents {
    data object OnAppear : WebhookTriggerEditEvents
    data object GenerateDraft : WebhookTriggerEditEvents
    data class DraftPromptChanged(val prompt: String) : WebhookTriggerEditEvents
    data class SelectSource(val source: ChatbotWebhookEventSource) : WebhookTriggerEditEvents
    data class ToggleEventType(val eventType: String) : WebhookTriggerEditEvents
    data class SelectConnection(val connection: ChatbotWebhookEventConnection) : WebhookTriggerEditEvents
    data class SelectAccount(val account: ChatbotConnectedAccount) : WebhookTriggerEditEvents
    data object ConnectSource : WebhookTriggerEditEvents
    data class SelectRoom(val roomId: String) : WebhookTriggerEditEvents
    data class SelectAgent(val agentId: String) : WebhookTriggerEditEvents
    data class NameChanged(val name: String) : WebhookTriggerEditEvents
    data class DescriptionChanged(val description: String) : WebhookTriggerEditEvents
    data class ActionPromptChanged(val prompt: String) : WebhookTriggerEditEvents
    data object Save : WebhookTriggerEditEvents
    data object Cancel : WebhookTriggerEditEvents
    data object ClearError : WebhookTriggerEditEvents
}
