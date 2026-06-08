/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.list

import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventSource
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import kotlinx.collections.immutable.ImmutableList

data class WebhookTriggerListState(
    val mode: WebhookTriggerListMode,
    val triggers: ImmutableList<ChatbotWebhookTrigger>,
    val filteredTriggers: ImmutableList<ChatbotWebhookTrigger>,
    val eventSources: ImmutableList<ChatbotWebhookEventSource>,
    val availableRooms: ImmutableList<RoomSummary>,
    val selectedRoomId: String?,
    val availableAgents: ImmutableList<ChatbotRoomAgent>,
    val selectedAgentId: String?,
    val searchQuery: String,
    val isLoading: Boolean,
    val error: String?,
    val togglingTriggerId: String?,
    val deletingTriggerId: String?,
    val deleteConfirmationTriggerId: String?,
    val eventSink: (WebhookTriggerListEvents) -> Unit,
)
