/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.edit

import io.element.android.features.webhooks.api.WebhookTriggerEditMode
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventConnection
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventSource
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class WebhookTriggerEditState(
    val mode: WebhookTriggerEditMode,
    val eventSources: ImmutableList<ChatbotWebhookEventSource>,
    val selectedSource: ChatbotWebhookEventSource?,
    val selectedEventTypes: ImmutableSet<String>,
    val selectedConnection: ChatbotWebhookEventConnection?,
    val connectedAccounts: ImmutableList<ChatbotConnectedAccount>,
    val selectedAccount: ChatbotConnectedAccount?,
    val availableRooms: ImmutableList<RoomSummary>,
    val selectedRoomId: String?,
    val availableAgents: ImmutableList<ChatbotRoomAgent>,
    val selectedAgentId: String?,
    val name: String,
    val description: String,
    val actionPrompt: String,
    val draftPrompt: String,
    val isLoading: Boolean,
    val isSaving: Boolean,
    val isDrafting: Boolean,
    val error: String?,
    val eventSink: (WebhookTriggerEditEvents) -> Unit,
) {
    val isCreateMode: Boolean = mode is WebhookTriggerEditMode.Create
    val canSave: Boolean = name.trim().isNotEmpty() &&
        name.length <= 200 &&
        selectedEventTypes.isNotEmpty() &&
        actionPrompt.trim().isNotEmpty() &&
        selectedRoomId != null &&
        (!isCreateMode || selectedAgentId != null)
}
