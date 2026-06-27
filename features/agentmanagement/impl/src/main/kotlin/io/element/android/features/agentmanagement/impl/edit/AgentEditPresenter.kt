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
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.agentmanagement.impl.R
import io.element.android.features.agentmanagement.impl.shared.AgentDirectChatService
import io.element.android.features.agentmanagement.impl.shared.AgentVoiceSelection
import io.element.android.features.agentmanagement.impl.shared.agentMatrixUserId
import io.element.android.features.agentmanagement.impl.shared.iosOrderedProviders
import io.element.android.features.agentmanagement.impl.shared.selectModelId
import io.element.android.features.agentmanagement.impl.shared.selectProviderId
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.agent.AgentSandboxMode
import io.element.android.libraries.chatbot.api.model.agent.AgentSandboxModeWrapper
import io.element.android.libraries.chatbot.api.model.agent.AgentSandboxStatus
import io.element.android.libraries.chatbot.api.model.agent.AgentVaultEntriesWrapper
import io.element.android.libraries.chatbot.api.model.agent.AgentVaultEntryInput
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentSettings
import io.element.android.libraries.chatbot.api.model.agent.ChatbotCreateAgentRequest
import io.element.android.libraries.chatbot.api.model.agent.ChatbotUpdateAgentRequest
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
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
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var form by remember { mutableStateOf(initialForm(mode)) }
        var providers by remember { mutableStateOf(emptyList<ChatbotAgentProvider>()) }
        var nameAvailability by remember { mutableStateOf(AgentNameAvailability.Unknown) }
        var phase by remember { mutableStateOf<AgentEditPhase>(AgentEditPhase.Editing) }
        var isLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var nameCheckJob by remember { mutableStateOf<Job?>(null) }
        var availableSkills by remember { mutableStateOf(emptyList<ChatbotUserSkill>()) }
        var selectedSkillIds by remember { mutableStateOf(emptySet<String>()) }
        var originalSkillIds by remember { mutableStateOf(emptySet<String>()) }
        var voiceProfiles by remember { mutableStateOf(emptyList<ChatbotVoiceProfile>()) }
        var providerVoices by remember { mutableStateOf(emptyList<ChatbotProviderVoice>()) }
        var voiceSelection by remember { mutableStateOf(AgentVoiceSelection.DEFAULT) }
        var initialVoiceSelection by remember { mutableStateOf(AgentVoiceSelection.DEFAULT) }
        var agentVoiceId by remember { mutableStateOf<String?>(null) }
        var personalVaultKeys by remember { mutableStateOf(emptyList<ChatbotVaultItem>()) }
        var selectedVaultKeys by remember { mutableStateOf(emptySet<String>()) }
        var sandboxStatus by remember { mutableStateOf<AgentSandboxStatus?>(null) }
        var sandboxBusy by remember { mutableStateOf(false) }
        var sandboxMessage by remember { mutableStateOf<String?>(null) }
        var pendingSandboxAction by remember { mutableStateOf<AgentSandboxInitMethod?>(null) }

        fun string(resId: Int): String = context.getString(resId)
        fun Throwable.messageOr(resId: Int): String = message?.takeIf { it.isNotBlank() } ?: string(resId)
        val renderLabels = AgentEditRenderLabels(
            titleCreate = string(R.string.agent_edit_title_create),
            titleEdit = string(R.string.agent_edit_title_edit),
            create = string(R.string.agent_edit_create),
            saveChanges = string(R.string.agent_edit_save_changes),
            sectionLabels = AgentEditSectionLabels(
                avatar = string(R.string.agent_edit_avatar),
                basicInfo = string(R.string.agent_edit_basic_info),
                accessControl = string(R.string.agent_edit_access_control),
                aiEngine = string(R.string.agent_edit_ai_engine),
                voice = string(R.string.agent_edit_voice),
                soul = string(R.string.agent_edit_role),
                runtime = string(R.string.agent_edit_runtime),
                vault = string(R.string.agent_edit_vault),
                skills = string(R.string.agent_edit_owned_skills),
            ),
            identifierRequired = string(R.string.agent_edit_identifier_required),
            identifier = string(R.string.agent_edit_identifier),
            identifierHelper = string(R.string.agent_edit_identifier_helper),
            noVault = string(R.string.agent_edit_no_vault),
            chooseVault = string(R.string.agent_edit_choose_vault),
            noSkills = string(R.string.agent_edit_no_skills),
            addSkill = string(R.string.agent_edit_add_skill),
            checking = string(R.string.agent_edit_checking),
            available = string(R.string.agent_edit_available),
            taken = string(R.string.agent_edit_taken),
            savingAgent = string(R.string.agent_edit_saving_agent),
            creatingDirectChat = string(R.string.agent_edit_creating_dm),
            runtimePerUser = string(R.string.agent_edit_runtime_per_user),
            runtimeDedicated = string(R.string.agent_edit_runtime_dedicated),
            voiceDefault = string(R.string.agent_edit_voice_default),
            voicePersonal = string(R.string.agent_edit_voice_personal),
            voiceProvider = string(R.string.agent_edit_voice_provider),
            runtimePerUserDescription = string(R.string.agent_edit_runtime_per_user_description),
            runtimeDedicatedDescription = string(R.string.agent_edit_runtime_dedicated_description),
            runtimeDedicatedWarning = string(R.string.agent_edit_runtime_dedicated_warning),
            runtimeCreateEmpty = string(R.string.agent_edit_runtime_create_empty),
            runtimeCreateEmptySubtitle = string(R.string.agent_edit_runtime_create_empty_subtitle),
            runtimeCloneOwner = string(R.string.agent_edit_runtime_clone_owner),
            runtimeCloneOwnerSubtitle = string(R.string.agent_edit_runtime_clone_owner_subtitle),
            runtimeConfiguredClone = string(R.string.agent_edit_runtime_configured_clone),
            runtimeConfiguredEmpty = string(R.string.agent_edit_runtime_configured_empty),
        )

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
                chatbotApiServiceFactory.createForHomeserver(matrixClient)
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
                chatbotApiServiceFactory.createForHomeserver(matrixClient)
                    .getAgent(editMode.botName)
                    .onSuccess { agent ->
                        form = normalizeProviderAndModel(providers, AgentEditFormState.fromAgent(agent, editMode.botName))
                        error = null
                        val voiceId = resolveAgentId(agent)
                        agentVoiceId = voiceId
                        run {
                            val envApi = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                            envApi.getAgentVoiceConfig(voiceId)
                                .onSuccess { resolution ->
                                    val token = AgentVoiceSelection.fromConfig(resolution)
                                    voiceSelection = token
                                    initialVoiceSelection = token
                                }
                            // Mirror iOS: the persisted sandbox mode lives on the sandbox response, not the agent record.
                            envApi.getAgentSandbox(voiceId).onSuccess { response ->
                                sandboxStatus = response.sandbox
                                form = form.copy(sandboxMode = response.sandboxMode ?: AgentSandboxMode.PerUser)
                            }
                        }
                    }
                    .onFailure { error = it.message ?: it::class.simpleName ?: "Failed to load agent" }
                isLoading = false
            }
        }

        fun loadSkills() {
            coroutineScope.launch {
                val api = chatbotApiServiceFactory.createForHomeserver(matrixClient)
                api.listUserSkills(null).onSuccess { availableSkills = it }
                val editMode = mode as? AgentEditMode.Edit ?: return@launch
                api.listAgentSkills(editMode.botName).onSuccess { skills ->
                    val ids = skills.map { it.id }.toSet()
                    selectedSkillIds = ids
                    originalSkillIds = ids
                }
            }
        }

        fun loadVoice() {
            coroutineScope.launch {
                val api = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                api.listProviderVoices("elevenlabs", "available", null, 100, null).onSuccess { providerVoices = it }
                api.listVoiceProfiles("elevenlabs", "available", null, 100, null).onSuccess { voiceProfiles = it }
            }
        }

        fun loadVault() {
            coroutineScope.launch {
                chatbotApiServiceFactory.createForAiStream(matrixClient).listVault().onSuccess { personalVaultKeys = it }
            }
        }

        fun loadInitialData() {
            if (hasLoadedOnce) return
            hasLoadedOnce = true
            loadProviders(showLoading = true)
            loadAgentIfNeeded()
            loadSkills()
            loadVoice()
            loadVault()
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
                val api = chatbotApiServiceFactory.createForHomeserver(matrixClient)
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
                val api = chatbotApiServiceFactory.createForHomeserver(matrixClient)
                val trimmed = form.trimmed()
                try {
                    when (mode) {
                        AgentEditMode.Create -> {
                            phase = AgentEditPhase.Submitting(AgentEditSubmittingStep.CreateAgent)
                            if (trimmed.botName.isEmpty()) {
                                phase = AgentEditPhase.Editing
                                error = string(R.string.agent_edit_error_empty_identifier)
                                return@launch
                            }
                            nameAvailability = AgentNameAvailability.Checking
                            nameAvailability = nameCheckResult(api, trimmed.botName)
                            if (nameAvailability == AgentNameAvailability.Taken) {
                                phase = AgentEditPhase.Editing
                                error = string(R.string.agent_edit_error_name_exists)
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
                            selectedSkillIds.forEach { skillId ->
                                api.addAgentSkill(created.botName, skillId, null)
                            }
            val createdAgentId = resolveAgentId(created)
                            AgentVoiceSelection.request(voiceSelection)?.let { voiceRequest ->
                                chatbotApiServiceFactory.createForUnsealApi(matrixClient).setAgentVoiceConfig(createdAgentId, voiceRequest)
                            }
                            // Dedicated sandbox setup mirrors iOS: create-empty or clone-owner after the agent exists.
                            if (trimmed.sandboxMode == AgentSandboxMode.AgentDedicated) {
                                val envApi = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                                when (trimmed.sandboxInitMethod) {
                                    AgentSandboxInitMethod.Empty -> envApi.createAgentSandbox(createdAgentId)
                                    AgentSandboxInitMethod.CloneOwner -> envApi.cloneAgentSandbox(createdAgentId)
                                }
                            }
                            if (selectedVaultKeys.isNotEmpty()) {
                                chatbotApiServiceFactory.createForUnsealApi(matrixClient).cloneAgentVault(createdAgentId, selectedVaultKeys.toList())
                            }
                            phase = AgentEditPhase.Submitting(AgentEditSubmittingStep.CreateDM)
                            val directRoomId = createDirectRoomAndJoin(created, api)
                            phase = AgentEditPhase.Success(created.toSuccessSummary(directRoomId))
                            error = null
                        }
                        is AgentEditMode.Edit -> {
                            phase = AgentEditPhase.Submitting(AgentEditSubmittingStep.CreateAgent)
                            api.updateAgent(mode.botName, trimmed.toUpdateRequest()).getOrThrow()
                            (selectedSkillIds - originalSkillIds).forEach { skillId ->
                                api.addAgentSkill(mode.botName, skillId, null)
                            }
                            val voiceId = agentVoiceId
                            if (voiceId != null && voiceSelection != initialVoiceSelection) {
                                val voiceApi = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                                val voiceRequest = AgentVoiceSelection.request(voiceSelection)
                                if (voiceRequest != null) {
                                    voiceApi.setAgentVoiceConfig(voiceId, voiceRequest)
                                } else {
                                    voiceApi.deleteAgentVoiceConfig(voiceId)
                                }
                                initialVoiceSelection = voiceSelection
                            }
                            if (selectedVaultKeys.isNotEmpty()) {
                                agentVoiceId?.let { vaultAgentId ->
                                    chatbotApiServiceFactory.createForUnsealApi(matrixClient).cloneAgentVault(vaultAgentId, selectedVaultKeys.toList())
                                }
                            }
                            phase = AgentEditPhase.Editing
                            error = null
                            navigator.onUpdated(mode.botName)
                        }
                    }
                } catch (failure: Throwable) {
                    phase = AgentEditPhase.Editing
                    error = if (failure === AgentNameConflictException) {
                        string(R.string.agent_edit_error_name_exists)
                    } else {
                        failure.messageOr(R.string.agent_edit_error_submit)
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
                is AgentEditEvents.AvatarPicked -> coroutineScope.launch {
                    isLoading = true
                    matrixClient.uploadMedia(event.mimeType, event.data)
                        .onSuccess { mxcUrl -> form = form.copy(avatarUrl = mxcUrl) }
                        .onFailure { error = it.messageOr(R.string.agent_edit_error_upload_avatar) }
                    isLoading = false
                }
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
                is AgentEditEvents.SandboxModeChanged -> form = form.copy(sandboxMode = event.mode)
                is AgentEditEvents.SandboxInitMethodChanged -> form = form.copy(sandboxInitMethod = event.method)
                is AgentEditEvents.SandboxActionRequested -> {
                    // Mirror iOS: ask for confirmation before mutating the runtime.
                    sandboxMessage = null
                    error = null
                    pendingSandboxAction = event.method
                }
                AgentEditEvents.SandboxActionDismissed -> pendingSandboxAction = null
                AgentEditEvents.SandboxActionConfirmed -> {
                    val method = pendingSandboxAction
                    pendingSandboxAction = null
                    if (method != null) {
                        coroutineScope.launch {
                            val id = agentVoiceId ?: (mode as? AgentEditMode.Edit)?.let { resolveAgentId(ChatbotAgent(botName = it.botName)) }
                            if (id == null) {
                                error = string(R.string.agent_edit_error_no_matrix_user_id)
                                return@launch
                            }
                            sandboxBusy = true
                            error = null
                            sandboxMessage = null
                            val envApi = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                            when (method) {
                                AgentSandboxInitMethod.Empty -> envApi.createAgentSandbox(id)
                                    .onFailure {
                                        error = it.humanizeChatbotError(
                                            fallback = string(R.string.agent_edit_error_create_runtime),
                                            creditsExhausted = string(R.string.agent_edit_error_runtime_credits_exhausted),
                                        )
                                    }
                                AgentSandboxInitMethod.CloneOwner -> envApi.cloneAgentSandbox(id)
                                    .onSuccess { sandboxMessage = it.message?.takeIf { m -> m.isNotBlank() } ?: string(R.string.agent_edit_runtime_cloned) }
                                    .onFailure {
                                        error = it.humanizeChatbotError(
                                            fallback = string(R.string.agent_edit_error_clone_runtime),
                                            creditsExhausted = string(R.string.agent_edit_error_runtime_credits_exhausted),
                                        )
                                    }
                            }
                            envApi.getAgentSandbox(id).onSuccess { response ->
                                sandboxStatus = response.sandbox
                                response.sandboxMode?.let { form = form.copy(sandboxMode = it) }
                                if (method == AgentSandboxInitMethod.Empty && error == null && response.sandbox != null) {
                                    sandboxMessage = string(R.string.agent_edit_runtime_created)
                                }
                            }
                            sandboxBusy = false
                        }
                    }
                }
                AgentEditEvents.AddVaultEntry -> form = form.copy(vaultEntries = form.vaultEntries + AgentVaultEntryInput(key = "", value = ""))
                is AgentEditEvents.UpdateVaultEntry -> form = form.copy(
                    vaultEntries = form.vaultEntries.toMutableList().also { list ->
                        if (event.index in list.indices) {
                            list[event.index] = AgentVaultEntryInput(
                                key = event.key,
                                value = event.value,
                                description = event.description.ifBlank { null },
                            )
                        }
                    },
                )
                is AgentEditEvents.RemoveVaultEntry -> form = form.copy(
                    vaultEntries = form.vaultEntries.filterIndexed { index, _ -> index != event.index },
                )
                is AgentEditEvents.ToggleVaultKey -> selectedVaultKeys =
                    if (event.key in selectedVaultKeys) selectedVaultKeys - event.key else selectedVaultKeys + event.key
                is AgentEditEvents.ToggleSkill -> selectedSkillIds =
                    if (event.skillId in selectedSkillIds) selectedSkillIds - event.skillId else selectedSkillIds + event.skillId
                is AgentEditEvents.VoiceSelectionChanged -> voiceSelection = event.token
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
            availableSkills = availableSkills.toImmutableList(),
            selectedSkillIds = selectedSkillIds.toImmutableSet(),
            voiceProfiles = voiceProfiles.toImmutableList(),
            providerVoices = providerVoices.toImmutableList(),
            voiceSelection = voiceSelection,
            personalVaultKeys = personalVaultKeys.toImmutableList(),
            selectedVaultKeys = selectedVaultKeys.toImmutableSet(),
            sandboxStatus = sandboxStatus,
            sandboxBusy = sandboxBusy,
            sandboxMessage = sandboxMessage,
            pendingSandboxAction = pendingSandboxAction,
            renderLabels = renderLabels,
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
            AgentEditMode.Create -> AgentEditFormState(isPublic = false, autoJoin = true, soul = DEFAULT_SOUL)
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
            sandbox = AgentSandboxModeWrapper(mode = sandboxMode),
        )
    }

    private fun AgentEditFormState.toUpdateRequest(): ChatbotUpdateAgentRequest {
        val cleanVault = vaultEntries.filter { it.key.isNotBlank() }
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
            sandbox = AgentSandboxModeWrapper(mode = sandboxMode),
            agentVault = if (cleanVault.isEmpty()) null else AgentVaultEntriesWrapper(entries = cleanVault),
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

    /** Resolve the agent's Matrix user id, falling back to localpart/botName + the user's server (mirrors iOS). */
    private fun resolveAgentId(agent: ChatbotAgent): String {
        agent.agentMatrixUserId()?.let { return it }
        val localpart = agent.localpart?.takeIf { it.isNotBlank() } ?: agent.botName
        val server = agent.serverName?.takeIf { it.isNotBlank() } ?: matrixClient.userIdServerName()
        return if (localpart.startsWith("@")) localpart else "@$localpart:$server"
    }

    /** Turns a raw chatbot error into a user-facing message, recognising known server error codes. */
    private fun Throwable.humanizeChatbotError(fallback: String, creditsExhausted: String): String {
        val raw = (this as? ChatbotApiError.HttpError)?.body ?: message
        return when {
            raw == null -> fallback
            raw.contains("credits_exhausted") -> creditsExhausted
            else -> Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                ?.takeIf { it.isNotBlank() }
                ?: message
                ?: fallback
        }
    }

    private companion object {
        const val NAME_CHECK_DEBOUNCE_MS = 450L
        val AgentNameConflictException = IllegalStateException()
        val DEFAULT_SOUL = """
            You are a helpful AI assistant. Your role is to assist users with their questions and tasks.

            ## Guidelines
            - Be helpful, accurate, and concise
            - Ask clarifying questions when needed
            - Provide structured responses when appropriate
            - Be honest about limitations
        """.trimIndent()
    }
}
