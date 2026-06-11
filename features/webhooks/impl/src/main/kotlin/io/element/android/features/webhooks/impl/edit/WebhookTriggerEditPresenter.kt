/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.webhooks.api.WebhookTriggerEditMode
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotCreateWebhookTriggerRequest
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotUpdateWebhookTriggerRequest
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventConnection
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventSource
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

@AssistedInject
class WebhookTriggerEditPresenter(
    @Assisted private val mode: WebhookTriggerEditMode,
    @Assisted private val navigator: WebhookTriggerEditNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<WebhookTriggerEditState> {
    @AssistedFactory
    interface Factory {
        fun create(mode: WebhookTriggerEditMode, navigator: WebhookTriggerEditNavigator): WebhookTriggerEditPresenter
    }

    @Composable
    override fun present(): WebhookTriggerEditState {
        val coroutineScope = rememberCoroutineScope()
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var eventSources by remember { mutableStateOf(emptyList<ChatbotWebhookEventSource>()) }
        var selectedSource by remember { mutableStateOf<ChatbotWebhookEventSource?>(null) }
        var selectedEventTypes by remember { mutableStateOf(emptySet<String>()) }
        var selectedConnection by remember { mutableStateOf<ChatbotWebhookEventConnection?>(null) }
        var connectedAccounts by remember { mutableStateOf(emptyList<ChatbotConnectedAccount>()) }
        var selectedAccount by remember { mutableStateOf<ChatbotConnectedAccount?>(null) }
        var availableRooms by remember { mutableStateOf(emptyList<RoomSummary>()) }
        var selectedRoomId by remember { mutableStateOf<String?>(null) }
        var availableAgents by remember { mutableStateOf(emptyList<ChatbotRoomAgent>()) }
        var selectedAgentId by remember { mutableStateOf<String?>(null) }
        var name by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var actionPrompt by remember { mutableStateOf("") }
        var draftPrompt by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var isDrafting by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        // Room→agents lives on the homeserver (/chatbot/v1/*), not the agent-api base URL.
        suspend fun homeserverApi() = chatbotApiServiceFactory.createForHomeserver(matrixClient)

        fun errorMessage(throwable: Throwable, fallback: String): String {
            return throwable.message ?: throwable::class.simpleName ?: fallback
        }

        fun resolveSource(eventTypes: List<String>, sources: List<ChatbotWebhookEventSource>): ChatbotWebhookEventSource? {
            return eventTypes.firstNotNullOfOrNull { eventType ->
                sources.firstOrNull { source -> source.eventTypes.any { it.eventType == eventType } }
            }
        }

        fun updateConnectionSelection(source: ChatbotWebhookEventSource?) {
            selectedConnection = source?.connections?.singleOrNull()
        }

        fun loadConnectedAccounts(toolkit: String) = coroutineScope.launch {
            api().listConnectedAccounts(toolkit = toolkit, cursor = null, limit = null)
                .onSuccess { response ->
                    connectedAccounts = response.items
                    if (selectedAccount == null) {
                        selectedAccount = when (val currentMode = mode) {
                            is WebhookTriggerEditMode.Edit -> {
                                val connectionId = currentMode.trigger.providerTriggers?.firstOrNull()?.connectionId
                                response.items.firstOrNull { it.id == connectionId } ?: response.items.firstOrNull()
                            }
                            is WebhookTriggerEditMode.Create -> response.items.firstOrNull()
                        }
                    }
                }
        }

        fun loadAgentsForRoom(roomId: String, selectedAfterLoad: String? = null) = coroutineScope.launch {
            homeserverApi().getRoomAgents(roomId)
                .onSuccess {
                    availableAgents = it.agents
                    if (selectedAfterLoad != null) {
                        selectedAgentId = selectedAfterLoad
                    }
                }
                .onFailure { error = errorMessage(it, "加载房间助手失败") }
        }

        fun selectSource(source: ChatbotWebhookEventSource) {
            selectedSource = source
            selectedEventTypes = emptySet()
            selectedConnection = null
            selectedAccount = null
            connectedAccounts = emptyList()
            updateConnectionSelection(source)
            loadConnectedAccounts(source.source)
        }

        fun selectRoom(roomId: String) {
            selectedRoomId = roomId
            selectedAgentId = null
            availableAgents = emptyList()
            loadAgentsForRoom(roomId)
        }

        fun seedFromMode(sources: List<ChatbotWebhookEventSource>) {
            when (val currentMode = mode) {
                is WebhookTriggerEditMode.Create -> {
                    selectedRoomId = currentMode.prefilledRoomId?.value
                    currentMode.prefilledRoomId?.value?.let { loadAgentsForRoom(it) }
                }
                is WebhookTriggerEditMode.Edit -> {
                    val trigger = currentMode.trigger
                    name = trigger.name
                    description = trigger.description.orEmpty()
                    actionPrompt = trigger.actionPrompt
                    selectedRoomId = trigger.roomId
                    selectedEventTypes = trigger.eventTypes.toSet()
                    selectedSource = resolveSource(trigger.eventTypes, sources)
                    updateConnectionSelection(selectedSource)
                    selectedSource?.source?.let { loadConnectedAccounts(it) }
                    loadAgentsForRoom(trigger.roomId, selectedAfterLoad = trigger.agentId)
                }
            }
        }

        fun loadInitial() = coroutineScope.launch {
            isLoading = true
            var loadedSources = emptyList<ChatbotWebhookEventSource>()
            api().listWebhookEventTypes()
                .onSuccess {
                    loadedSources = it.sources
                    eventSources = it.sources
                    error = null
                }
                .onFailure {
                    error = errorMessage(it, "加载事件类型失败")
                }
            availableRooms = matrixClient.roomListService.allRooms.summaries.firstOrNull().orEmpty()
            seedFromMode(loadedSources)
            isLoading = false
        }

        fun generateDraft() = coroutineScope.launch {
            val prompt = draftPrompt.trim()
            if (prompt.isEmpty()) return@launch
            isDrafting = true
            api().draftWebhookTrigger(prompt)
                .onSuccess { draft ->
                    eventSources.firstOrNull { it.source == draft.source }?.let { source ->
                        selectedSource = source
                        selectedConnection = null
                        selectedAccount = null
                        connectedAccounts = emptyList()
                        updateConnectionSelection(source)
                        loadConnectedAccounts(source.source)
                    }
                    selectedEventTypes = draft.eventTypes.toSet()
                    name = draft.name
                    actionPrompt = draft.actionPrompt
                    error = null
                }
                .onFailure {
                    error = errorMessage(it, "生成触发器草稿失败")
                }
            isDrafting = false
        }

        fun connectSource() = coroutineScope.launch {
            val source = selectedSource ?: return@launch
            val redirectUrl = "io.element.android.x://composio-callback?toolkit=${source.source}"
            api().initiateConnection(source.source, redirectUrl)
                .onSuccess {
                    navigator.onOpenConnectUrl(it.connectUrl)
                    error = null
                }
                .onFailure {
                    error = errorMessage(it, "连接失败，请重试。")
                }
        }

        fun save() = coroutineScope.launch {
            val currentState = WebhookTriggerEditState(
                mode = mode,
                eventSources = eventSources.toImmutableList(),
                selectedSource = selectedSource,
                selectedEventTypes = selectedEventTypes.toImmutableSet(),
                selectedConnection = selectedConnection,
                connectedAccounts = connectedAccounts.toImmutableList(),
                selectedAccount = selectedAccount,
                availableRooms = availableRooms.toImmutableList(),
                selectedRoomId = selectedRoomId,
                availableAgents = availableAgents.toImmutableList(),
                selectedAgentId = selectedAgentId,
                name = name,
                description = description,
                actionPrompt = actionPrompt,
                draftPrompt = draftPrompt,
                isLoading = isLoading,
                isSaving = isSaving,
                isDrafting = isDrafting,
                error = error,
                eventSink = {},
            )
            if (!currentState.canSave) return@launch
            isSaving = true
            val service = api()
            when (val currentMode = mode) {
                is WebhookTriggerEditMode.Create -> {
                    val request = ChatbotCreateWebhookTriggerRequest(
                        agentId = selectedAgentId.orEmpty(),
                        name = name.trim(),
                        description = description.takeIf { it.isNotBlank() },
                        connectionId = selectedAccount?.id,
                        eventTypes = selectedEventTypes.sorted(),
                        actionPrompt = actionPrompt.trim(),
                        roomId = selectedRoomId.orEmpty(),
                    )
                    service.createWebhookTrigger(request)
                        .onSuccess {
                            error = null
                            navigator.onSaved(it)
                        }
                        .onFailure { error = errorMessage(it, "保存触发器失败") }
                }
                is WebhookTriggerEditMode.Edit -> {
                    val request = ChatbotUpdateWebhookTriggerRequest(
                        name = name.trim(),
                        description = description.takeIf { it.isNotBlank() },
                        actionPrompt = actionPrompt.trim(),
                        roomId = selectedRoomId,
                    )
                    service.updateWebhookTrigger(currentMode.trigger.triggerId, request)
                        .onSuccess {
                            error = null
                            navigator.onSaved(it)
                        }
                        .onFailure { error = errorMessage(it, "保存触发器失败") }
                }
            }
            isSaving = false
        }

        fun handleEvent(event: WebhookTriggerEditEvents) {
            when (event) {
                WebhookTriggerEditEvents.OnAppear -> if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    loadInitial()
                }
                WebhookTriggerEditEvents.GenerateDraft -> generateDraft()
                is WebhookTriggerEditEvents.DraftPromptChanged -> draftPrompt = event.prompt
                is WebhookTriggerEditEvents.SelectSource -> selectSource(event.source)
                is WebhookTriggerEditEvents.ToggleEventType -> {
                    selectedEventTypes = if (event.eventType in selectedEventTypes) {
                        selectedEventTypes - event.eventType
                    } else {
                        selectedEventTypes + event.eventType
                    }
                }
                is WebhookTriggerEditEvents.SelectConnection -> selectedConnection = event.connection
                is WebhookTriggerEditEvents.SelectAccount -> selectedAccount = event.account
                WebhookTriggerEditEvents.ConnectSource -> connectSource()
                is WebhookTriggerEditEvents.SelectRoom -> selectRoom(event.roomId)
                is WebhookTriggerEditEvents.SelectAgent -> selectedAgentId = event.agentId
                is WebhookTriggerEditEvents.NameChanged -> name = event.name
                is WebhookTriggerEditEvents.DescriptionChanged -> description = event.description
                is WebhookTriggerEditEvents.ActionPromptChanged -> actionPrompt = event.prompt
                WebhookTriggerEditEvents.Save -> save()
                WebhookTriggerEditEvents.Cancel -> navigator.onCancelled()
                WebhookTriggerEditEvents.ClearError -> error = null
            }
        }

        return WebhookTriggerEditState(
            mode = mode,
            eventSources = eventSources.toImmutableList(),
            selectedSource = selectedSource,
            selectedEventTypes = selectedEventTypes.toImmutableSet(),
            selectedConnection = selectedConnection,
            connectedAccounts = connectedAccounts.toImmutableList(),
            selectedAccount = selectedAccount,
            availableRooms = availableRooms.toImmutableList(),
            selectedRoomId = selectedRoomId,
            availableAgents = availableAgents.toImmutableList(),
            selectedAgentId = selectedAgentId,
            name = name,
            description = description,
            actionPrompt = actionPrompt,
            draftPrompt = draftPrompt,
            isLoading = isLoading,
            isSaving = isSaving,
            isDrafting = isDrafting,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}
