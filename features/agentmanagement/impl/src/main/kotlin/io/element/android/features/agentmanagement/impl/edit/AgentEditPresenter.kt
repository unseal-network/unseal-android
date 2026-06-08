/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.agentmanagement.impl.shared.AgentDirectChatService
import io.element.android.features.agentmanagement.impl.shared.agentMatrixUserId
import io.element.android.features.agentmanagement.impl.shared.iosOrderedProviders
import io.element.android.features.agentmanagement.impl.shared.selectModelId
import io.element.android.features.agentmanagement.impl.shared.selectProviderId
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentSettings
import io.element.android.libraries.chatbot.api.model.agent.ChatbotCreateAgentRequest
import io.element.android.libraries.chatbot.api.model.agent.ChatbotUpdateAgentRequest
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AssistedInject
class AgentEditPresenter(
    @Assisted private val mode: AgentEditMode,
    @Assisted private val navigator: AgentEditNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
    private val directChatService: AgentDirectChatService,
) : Presenter<AgentEditState> {
    @AssistedFactory
    interface Factory {
        fun create(mode: AgentEditMode, navigator: AgentEditNavigator): AgentEditPresenter
    }

    @Composable
    override fun present(): AgentEditState {
        val coroutineScope = rememberCoroutineScope()
        var form by remember { mutableStateOf(initialForm(mode)) }
        var providers by remember { mutableStateOf(emptyList<ChatbotAgentProvider>()) }
        var nameAvailability by remember { mutableStateOf(AgentNameAvailability.Unknown) }
        var phase by remember { mutableStateOf<AgentEditPhase>(AgentEditPhase.Editing) }
        var isLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var nameCheckJob by remember { mutableStateOf<Job?>(null) }

        fun selectedProvider(providerId: String? = form.providerId): ChatbotAgentProvider? {
            return providers.firstOrNull { it.id == providerId }
        }

        fun normalizeProviderAndModel(nextProviders: List<ChatbotAgentProvider>, currentForm: AgentEditFormState): AgentEditFormState {
            val providerId = selectProviderId(nextProviders, currentForm.providerId)
            val model = selectModelId(nextProviders.firstOrNull { it.id == providerId }, currentForm.model)
            return currentForm.copy(providerId = providerId, model = model)
        }

        fun loadProviders(showLoading: Boolean) {
            coroutineScope.launch {
                if (showLoading) isLoading = true
                chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                    .getProviders()
                    .onSuccess { loadedProviders ->
                        val orderedProviders = loadedProviders.iosOrderedProviders()
                        providers = orderedProviders
                        form = normalizeProviderAndModel(orderedProviders, form)
                        error = null
                    }
                    .onFailure { error = it.message ?: it::class.simpleName ?: "Failed to load providers" }
                if (showLoading) isLoading = false
            }
        }

        fun loadAgentIfNeeded() {
            val editMode = mode as? AgentEditMode.Edit ?: return
            coroutineScope.launch {
                isLoading = true
                chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                    .getAgent(editMode.botName)
                    .onSuccess { agent ->
                        form = normalizeProviderAndModel(providers, AgentEditFormState.fromAgent(agent, editMode.botName))
                        error = null
                    }
                    .onFailure { error = it.message ?: it::class.simpleName ?: "Failed to load agent" }
                isLoading = false
            }
        }

        fun loadInitialData() {
            if (hasLoadedOnce) return
            hasLoadedOnce = true
            loadProviders(showLoading = true)
            loadAgentIfNeeded()
        }

        fun scheduleNameCheck(candidateValue: String) {
            nameCheckJob?.cancel()
            val candidate = candidateValue.trim()
            if (candidate.isEmpty()) {
                nameAvailability = AgentNameAvailability.Unknown
                return
            }
            nameAvailability = AgentNameAvailability.Checking
            nameCheckJob = coroutineScope.launch {
                delay(NAME_CHECK_DEBOUNCE_MS)
                val api = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                nameAvailability = nameCheckResult(api, candidate)
            }
        }

        fun createAnother() {
            if (mode !is AgentEditMode.Create) return
            nameCheckJob?.cancel()
            phase = AgentEditPhase.Editing
            nameAvailability = AgentNameAvailability.Unknown
            form = normalizeProviderAndModel(providers, initialForm(mode))
            error = null
        }

        fun submit() {
            coroutineScope.launch {
                val api = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                val trimmed = form.trimmed()
                try {
                    when (mode) {
                        AgentEditMode.Create -> {
                            phase = AgentEditPhase.Submitting(AgentEditSubmittingStep.CreateAgent)
                            if (trimmed.botName.isEmpty()) {
                                phase = AgentEditPhase.Editing
                                error = "bot_name cannot be empty."
                                return@launch
                            }
                            nameAvailability = AgentNameAvailability.Checking
                            nameAvailability = nameCheckResult(api, trimmed.botName)
                            if (nameAvailability == AgentNameAvailability.Taken) {
                                phase = AgentEditPhase.Editing
                                error = "Agent name already exists."
                                return@launch
                            }
                            val created = api.createAgent(trimmed.toCreateRequest())
                                .recoverCatching {
                                    if (it.isAlreadyExistsError()) {
                                        nameAvailability = AgentNameAvailability.Taken
                                        throw AgentNameConflictException
                                    }
                                    throw it
                                }
                                .getOrThrow()
                            phase = AgentEditPhase.Submitting(AgentEditSubmittingStep.CreateDM)
                            val directRoomId = createDirectRoomAndJoin(created, api)
                            phase = AgentEditPhase.Success(created.toSuccessSummary(directRoomId))
                            error = null
                        }
                        is AgentEditMode.Edit -> {
                            phase = AgentEditPhase.Submitting(AgentEditSubmittingStep.CreateAgent)
                            api.updateAgent(mode.botName, trimmed.toUpdateRequest()).getOrThrow()
                            phase = AgentEditPhase.Editing
                            error = null
                            navigator.onUpdated(mode.botName)
                        }
                    }
                } catch (failure: Throwable) {
                    phase = AgentEditPhase.Editing
                    error = if (failure === AgentNameConflictException) {
                        "Agent name already exists."
                    } else {
                        failure.message ?: failure::class.simpleName ?: "Failed to submit agent"
                    }
                }
            }
        }

        fun handleEvent(event: AgentEditEvents) {
            when (event) {
                AgentEditEvents.OnAppear -> loadInitialData()
                AgentEditEvents.RefreshProviders -> loadProviders(showLoading = true)
                is AgentEditEvents.BotNameChanged -> {
                    form = form.copy(botName = event.value)
                    if (mode is AgentEditMode.Create) scheduleNameCheck(event.value)
                }
                is AgentEditEvents.DisplayNameChanged -> form = form.copy(displayName = event.value)
                is AgentEditEvents.DescriptionChanged -> form = form.copy(description = event.value)
                is AgentEditEvents.AvatarUrlChanged -> form = form.copy(avatarUrl = event.value)
                is AgentEditEvents.IsPublicChanged -> form = form.copy(isPublic = event.value)
                is AgentEditEvents.AutoJoinChanged -> form = form.copy(autoJoin = event.value)
                is AgentEditEvents.ProviderChanged -> {
                    val nextProvider = selectedProvider(event.providerId)
                    form = form.copy(providerId = event.providerId, model = selectModelId(nextProvider, form.model))
                }
                is AgentEditEvents.ModelChanged -> form = form.copy(model = event.value)
                is AgentEditEvents.ApiKeyChanged -> form = form.copy(apiKey = event.value)
                is AgentEditEvents.BaseUrlChanged -> form = form.copy(baseUrl = event.value)
                is AgentEditEvents.SoulChanged -> form = form.copy(soul = event.value)
                AgentEditEvents.Submit -> submit()
                AgentEditEvents.GoToChat -> {
                    val summary = (phase as? AgentEditPhase.Success)?.summary ?: return
                    navigator.onCreated(summary.botName, summary.directRoomId)
                }
                AgentEditEvents.CreateAnother -> createAnother()
                AgentEditEvents.ClearError -> error = null
            }
        }

        return AgentEditState(
            mode = mode,
            form = form,
            providers = providers.toImmutableList(),
            nameAvailability = nameAvailability,
            phase = phase,
            isLoading = isLoading,
            error = error,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun createDirectRoomAndJoin(agent: ChatbotAgent, api: ChatbotApiService): RoomId? {
        val userId = agent.agentMatrixUserId() ?: return null
        val existingRoomId = directChatService.findExistingDirectRoom(userId).getOrNull()
        if (existingRoomId != null) {
            return existingRoomId
        }
        return directChatService.createDirectRoom(userId)
            .onSuccess { api.agentJoinRoom(agent.botName, it.value) }
            .getOrNull()
    }

    private suspend fun nameCheckResult(api: ChatbotApiService, botName: String): AgentNameAvailability {
        return api.getAgent(botName).fold(
            onSuccess = { AgentNameAvailability.Taken },
            onFailure = {
                if (it is ChatbotApiError.HttpError && it.statusCode == 404) {
                    AgentNameAvailability.Available
                } else {
                    AgentNameAvailability.Unknown
                }
            }
        )
    }

    private fun initialForm(mode: AgentEditMode): AgentEditFormState {
        return when (mode) {
            AgentEditMode.Create -> AgentEditFormState(isPublic = false, autoJoin = true)
            is AgentEditMode.Edit -> AgentEditFormState(botName = mode.botName, isPublic = false, autoJoin = true)
        }
    }

    private fun AgentEditFormState.trimmed(): AgentEditFormState {
        return copy(
            botName = botName.trim(),
            displayName = displayName.trim(),
            description = description.trim(),
            avatarUrl = avatarUrl.trim(),
            model = model.trim(),
            apiKey = apiKey.trim(),
            baseUrl = baseUrl.trim(),
            soul = soul.trim(),
        )
    }

    private fun AgentEditFormState.toCreateRequest(): ChatbotCreateAgentRequest {
        return ChatbotCreateAgentRequest(
            botName = botName,
            displayName = displayName.nullIfBlank(),
            description = description.nullIfBlank(),
            avatarUrl = avatarUrl.nullIfBlank(),
            isPublic = isPublic,
            provider = providerId,
            model = model.nullIfBlank(),
            apiKey = apiKey.nullIfBlank(),
            baseUrl = baseUrl.nullIfBlank(),
            soul = soul.nullIfBlank(),
            settings = ChatbotAgentSettings(autoJoin = autoJoin),
        )
    }

    private fun AgentEditFormState.toUpdateRequest(): ChatbotUpdateAgentRequest {
        return ChatbotUpdateAgentRequest(
            displayName = displayName.nullIfBlank(),
            description = description.nullIfBlank(),
            avatarUrl = avatarUrl.nullIfBlank(),
            isPublic = isPublic,
            provider = providerId,
            model = model.nullIfBlank(),
            apiKey = apiKey.nullIfBlank(),
            baseUrl = baseUrl.nullIfBlank(),
            soul = soul.nullIfBlank(),
            settings = ChatbotAgentSettings(autoJoin = autoJoin),
        )
    }

    private fun ChatbotAgent.toSuccessSummary(directRoomId: RoomId?): AgentCreateSuccessSummary {
        return AgentCreateSuccessSummary(
            botName = botName,
            localpart = localpart,
            serverName = serverName,
            displayName = displayName,
            avatarUrl = avatarUrl,
            provider = provider,
            model = model,
            baseUrl = baseUrl,
            isPublic = isPublic ?: false,
            directRoomId = directRoomId,
        )
    }

    private fun Throwable.isAlreadyExistsError(): Boolean {
        if (this !is ChatbotApiError.HttpError || statusCode != 500) return false
        val text = body.orEmpty().lowercase()
        return text.contains("already exists") || text.contains("already") || text.contains("exists")
    }

    private fun String.nullIfBlank(): String? = takeIf { it.isNotBlank() }

    private companion object {
        const val NAME_CHECK_DEBOUNCE_MS = 450L
        val AgentNameConflictException = IllegalStateException("Agent name already exists.")
    }
}
