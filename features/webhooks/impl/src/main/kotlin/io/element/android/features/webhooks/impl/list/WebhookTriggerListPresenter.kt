/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.webhooks.impl.shared.isEnabled
import io.element.android.features.webhooks.impl.shared.matchesWebhookQuery
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventSource
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@AssistedInject
class WebhookTriggerListPresenter(
    @Assisted private val mode: WebhookTriggerListMode,
    @Assisted private val navigator: WebhookTriggerListNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<WebhookTriggerListState> {
    @AssistedFactory
    interface Factory {
        fun create(mode: WebhookTriggerListMode, navigator: WebhookTriggerListNavigator): WebhookTriggerListPresenter
    }

    @Composable
    override fun present(): WebhookTriggerListState {
        val coroutineScope = rememberCoroutineScope()
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var triggers by remember { mutableStateOf(emptyList<ChatbotWebhookTrigger>()) }
        var eventSources by remember { mutableStateOf(emptyList<ChatbotWebhookEventSource>()) }
        var availableRooms by remember { mutableStateOf(emptyList<RoomSummary>()) }
        var selectedRoomId by remember { mutableStateOf<String?>(null) }
        var availableAgents by remember { mutableStateOf(emptyList<ChatbotRoomAgent>()) }
        var selectedAgentId by remember { mutableStateOf<String?>(null) }
        var searchQuery by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var togglingTriggerId by remember { mutableStateOf<String?>(null) }
        var deletingTriggerId by remember { mutableStateOf<String?>(null) }
        var deleteConfirmationTriggerId by remember { mutableStateOf<String?>(null) }

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        fun errorMessage(throwable: Throwable, fallback: String): String {
            return throwable.message ?: throwable::class.simpleName ?: fallback
        }

        fun filtered(): List<ChatbotWebhookTrigger> {
            return triggers
                .filter { selectedAgentId == null || it.agentId == selectedAgentId }
                .filter { it.matchesWebhookQuery(searchQuery) }
        }

        fun loadEventCatalog() = coroutineScope.launch {
            api().listWebhookEventTypes()
                .onSuccess {
                    eventSources = it.sources
                }
        }

        fun loadFilterOptions() = coroutineScope.launch {
            when (val currentMode = mode) {
                WebhookTriggerListMode.Global -> {
                    availableRooms = matrixClient.roomListService.allRooms.summaries.firstOrNull().orEmpty()
                }
                is WebhookTriggerListMode.Room -> {
                    api().getRoomAgents(currentMode.roomId.value)
                        .onSuccess {
                            availableAgents = it.agents
                        }
                }
            }
        }

        fun loadTriggers() = coroutineScope.launch {
            isLoading = true
            val roomId = when (val currentMode = mode) {
                WebhookTriggerListMode.Global -> selectedRoomId
                is WebhookTriggerListMode.Room -> currentMode.roomId.value
            }
            api().listWebhookTriggers(agentId = null, source = null, roomId = roomId, status = null)
                .onSuccess {
                    triggers = it
                    error = null
                }
                .onFailure {
                    error = errorMessage(it, "加载触发器失败")
                }
            isLoading = false
        }

        fun toggleStatus(trigger: ChatbotWebhookTrigger) = coroutineScope.launch {
            togglingTriggerId = trigger.triggerId
            api().updateWebhookTriggerStatus(trigger.triggerId, !trigger.isEnabled())
                .onSuccess {
                    navigator.onTriggersChanged()
                    loadTriggers()
                }
                .onFailure {
                    error = errorMessage(it, "更新触发器状态失败")
                }
            togglingTriggerId = null
        }

        fun deleteConfirmed() = coroutineScope.launch {
            val triggerId = deleteConfirmationTriggerId ?: return@launch
            deletingTriggerId = triggerId
            deleteConfirmationTriggerId = null
            api().deleteWebhookTrigger(triggerId)
                .onSuccess {
                    navigator.onTriggersChanged()
                    loadTriggers()
                }
                .onFailure {
                    error = errorMessage(it, "删除触发器失败")
                }
            deletingTriggerId = null
        }

        fun handleEvent(event: WebhookTriggerListEvents) {
            when (event) {
                WebhookTriggerListEvents.OnAppear -> if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    loadEventCatalog()
                    loadFilterOptions()
                    loadTriggers()
                }
                WebhookTriggerListEvents.Refresh -> loadTriggers()
                is WebhookTriggerListEvents.SearchChanged -> searchQuery = event.query
                is WebhookTriggerListEvents.SelectRoomFilter -> {
                    selectedRoomId = event.roomId
                    loadTriggers()
                }
                is WebhookTriggerListEvents.SelectAgentFilter -> selectedAgentId = event.agentId
                WebhookTriggerListEvents.CreateTrigger -> navigator.onCreateTrigger((mode as? WebhookTriggerListMode.Room)?.roomId)
                is WebhookTriggerListEvents.EditTrigger -> navigator.onEditTrigger(event.trigger)
                is WebhookTriggerListEvents.ToggleStatus -> toggleStatus(event.trigger)
                is WebhookTriggerListEvents.RequestDelete -> deleteConfirmationTriggerId = event.trigger.triggerId
                WebhookTriggerListEvents.CancelDelete -> deleteConfirmationTriggerId = null
                WebhookTriggerListEvents.ConfirmDelete -> deleteConfirmed()
                WebhookTriggerListEvents.ClearError -> error = null
                WebhookTriggerListEvents.Dismiss -> navigator.onDone()
            }
        }

        return WebhookTriggerListState(
            mode = mode,
            triggers = triggers.toImmutableList(),
            filteredTriggers = filtered().toImmutableList(),
            eventSources = eventSources.toImmutableList(),
            availableRooms = availableRooms.toImmutableList(),
            selectedRoomId = selectedRoomId,
            availableAgents = availableAgents.toImmutableList(),
            selectedAgentId = selectedAgentId,
            searchQuery = searchQuery,
            isLoading = isLoading,
            error = error,
            togglingTriggerId = togglingTriggerId,
            deletingTriggerId = deletingTriggerId,
            deleteConfirmationTriggerId = deleteConfirmationTriggerId,
            eventSink = ::handleEvent,
        )
    }
}
