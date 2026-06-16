/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.list

import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger

sealed interface WebhookTriggerListEvents {
    data object OnAppear : WebhookTriggerListEvents
    data object Refresh : WebhookTriggerListEvents
    data class SearchChanged(val query: String) : WebhookTriggerListEvents
    data class SelectRoomFilter(val roomId: String?) : WebhookTriggerListEvents
    data class SelectAgentFilter(val agentId: String?) : WebhookTriggerListEvents
    data object CreateTrigger : WebhookTriggerListEvents
    data class EditTrigger(val trigger: ChatbotWebhookTrigger) : WebhookTriggerListEvents
    data class ToggleStatus(val trigger: ChatbotWebhookTrigger) : WebhookTriggerListEvents
    data class RequestDelete(val trigger: ChatbotWebhookTrigger) : WebhookTriggerListEvents
    data object CancelDelete : WebhookTriggerListEvents
    data object ConfirmDelete : WebhookTriggerListEvents
    data object ClearError : WebhookTriggerListEvents
    data object Dismiss : WebhookTriggerListEvents
}
