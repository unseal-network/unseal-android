/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.edit

import io.element.android.features.agentmanagement.impl.shared.AgentVoiceSelection
import io.element.android.libraries.chatbot.api.model.agent.AgentSandboxMode
import io.element.android.libraries.chatbot.api.model.agent.AgentSandboxStatus
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotProviderModel
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class AgentEditRenderModel(
    val title: String,
    val isCreate: Boolean,
    val submitLabel: String,
    val isSubmitting: Boolean,
    val submittingLabel: String?,
    val sectionLabels: AgentEditSectionLabels,
    val botNameLabel: String,
    val botNameHelper: String?,
    val botNameAvailabilityLabel: String?,
    val providerOptions: ImmutableList<AgentEditOption>,
    val selectedProviderLabel: String,
    val modelOptions: ImmutableList<AgentEditOption>,
    val selectedModelLabel: String,
    val needsApiKey: Boolean,
    val supportsBaseUrl: Boolean,
    val voiceOptions: ImmutableList<AgentEditOption>,
    val selectedVoiceLabel: String,
    val sandbox: AgentSandboxRenderModel,
    val selectedSkills: ImmutableList<AgentEditChipRenderModel>,
    val selectedVaultKeys: ImmutableList<AgentEditChipRenderModel>,
    val vaultPickerLabel: String,
    val skillPickerLabel: String,
)

data class AgentEditRenderLabels(
    val titleCreate: String = "Create agent",
    val titleEdit: String = "Edit agent",
    val create: String = "Create",
    val saveChanges: String = "Save changes",
    val sectionLabels: AgentEditSectionLabels = AgentEditSectionLabels(),
    val identifierRequired: String = "Identifier (required)",
    val identifier: String = "Identifier",
    val identifierHelper: String = "The identifier cannot be changed after creation. Use lowercase letters and numbers.",
    val noVault: String = "No vault entries.",
    val chooseVault: String = "Choose from my vault",
    val noSkills: String = "No skills available",
    val addSkill: String = "Add skill",
    val checking: String = "Checking...",
    val available: String = "Available",
    val taken: String = "Taken",
    val savingAgent: String = "Saving agent…",
    val creatingDirectChat: String = "Creating direct chat…",
    val runtimePerUser: String = "Separate runtime per user",
    val runtimeDedicated: String = "Dedicated agent runtime",
    val voiceDefault: String = "Use server default",
    val voicePersonal: String = "Personal voice",
    val voiceProvider: String = "Provider voice",
    val runtimePerUserDescription: String = "Each user uses their own runtime when chatting with the agent. Data stays separate.",
    val runtimeDedicatedDescription: String = "All users share the agent's dedicated runtime.",
    val runtimeDedicatedWarning: String = "All users will share this runtime. Make sure it does not contain private information, or use it only in trusted groups.",
    val runtimeCreateEmpty: String = "Create empty runtime",
    val runtimeCreateEmptySubtitle: String = "Start from scratch with a fresh runtime for this agent",
    val runtimeCloneOwner: String = "Copy my runtime",
    val runtimeCloneOwnerSubtitle: String = "Copy your current runtime as a starting point; future changes stay separate",
    val runtimeConfiguredClone: String = "Configured · Source: copied from user runtime",
    val runtimeConfiguredEmpty: String = "Configured · Source: empty runtime",
)

data class AgentEditSectionLabels(
    val avatar: String = "Avatar",
    val basicInfo: String = "Basic information",
    val accessControl: String = "Access control",
    val aiEngine: String = "AI engine",
    val voice: String = "Voice",
    val soul: String = "Role prompt",
    val runtime: String = "Runtime",
    val vault: String = "Vault variables",
    val skills: String = "Owned skills",
)

data class AgentEditOption(
    val id: String,
    val label: String,
    val isSelected: Boolean,
)

data class AgentEditChipRenderModel(
    val id: String,
    val label: String,
    val description: String?,
)

data class AgentSandboxRenderModel(
    val modeLabel: String,
    val modeOptions: ImmutableList<AgentEditOption>,
    val description: String,
    val warning: String?,
    val initOptions: ImmutableList<AgentSandboxInitOptionRenderModel>,
    val statusLabel: String?,
    val isBusy: Boolean,
    val message: String?,
)

data class AgentSandboxInitOptionRenderModel(
    val method: AgentSandboxInitMethod,
    val title: String,
    val subtitle: String,
    val isSelected: Boolean,
)

fun AgentEditState.toRenderModel(labels: AgentEditRenderLabels = AgentEditRenderLabels()): AgentEditRenderModel {
    val isSubmitting = phase is AgentEditPhase.Submitting
    return AgentEditRenderModel(
        title = if (isCreate) labels.titleCreate else labels.titleEdit,
        isCreate = isCreate,
        submitLabel = if (isCreate) labels.create else labels.saveChanges,
        isSubmitting = isSubmitting,
        submittingLabel = (phase as? AgentEditPhase.Submitting)?.step?.displayLabel(labels),
        sectionLabels = labels.sectionLabels,
        botNameLabel = if (isCreate) labels.identifierRequired else labels.identifier,
        botNameHelper = if (isCreate) labels.identifierHelper else null,
        botNameAvailabilityLabel = nameAvailability.displayLabel(labels),
        providerOptions = providers.toProviderOptions(form.providerId),
        selectedProviderLabel = selectedProvider?.displayLabel() ?: "—",
        modelOptions = availableModels.toModelOptions(form.model),
        selectedModelLabel = availableModels.firstOrNull { it.id == form.model }?.displayLabel() ?: form.model.ifEmpty { "—" },
        needsApiKey = needsApiKey,
        supportsBaseUrl = supportsBaseUrl,
        voiceOptions = voiceOptions(voiceSelection, voiceProfiles, providerVoices, labels),
        selectedVoiceLabel = AgentVoiceSelection.label(
            token = voiceSelection,
            profiles = voiceProfiles,
            providerVoices = providerVoices,
            defaultLabel = labels.voiceDefault,
            personalVoiceLabel = labels.voicePersonal,
            providerVoiceLabel = labels.voiceProvider,
        ),
        sandbox = sandboxRenderModel(form.sandboxMode, form.sandboxInitMethod, sandboxStatus, sandboxBusy, sandboxMessage, labels),
        selectedSkills = availableSkills
            .filter { it.id in selectedSkillIds }
            .map { AgentEditChipRenderModel(id = it.id, label = it.name, description = it.description?.takeIf { desc -> desc.isNotBlank() }) }
            .toImmutableList(),
        selectedVaultKeys = selectedVaultKeys
            .map { key ->
                val vault = personalVaultKeys.firstOrNull { it.key == key }
                AgentEditChipRenderModel(id = key, label = key, description = vault?.description?.takeIf { it.isNotBlank() })
            }
            .toImmutableList(),
        vaultPickerLabel = if (personalVaultKeys.isEmpty()) labels.noVault else labels.chooseVault,
        skillPickerLabel = if (availableSkills.isEmpty()) labels.noSkills else labels.addSkill,
    )
}

fun AgentNameAvailability.displayLabel(labels: AgentEditRenderLabels = AgentEditRenderLabels()): String? = when (this) {
    AgentNameAvailability.Unknown -> null
    AgentNameAvailability.Checking -> labels.checking
    AgentNameAvailability.Available -> labels.available
    AgentNameAvailability.Taken -> labels.taken
}

fun AgentEditSubmittingStep.displayLabel(labels: AgentEditRenderLabels = AgentEditRenderLabels()): String = when (this) {
    AgentEditSubmittingStep.CreateAgent -> labels.savingAgent
    AgentEditSubmittingStep.CreateDM -> labels.creatingDirectChat
}

fun ChatbotAgentProvider.displayLabel(): String = displayName ?: info?.displayName ?: id

fun ChatbotProviderModel.displayLabel(): String = displayName ?: id

fun AgentSandboxMode.displayLabel(labels: AgentEditRenderLabels = AgentEditRenderLabels()): String = when (this) {
    AgentSandboxMode.PerUser -> labels.runtimePerUser
    AgentSandboxMode.AgentDedicated -> labels.runtimeDedicated
}

fun List<ChatbotAgentProvider>.toProviderOptions(selectedId: String?): ImmutableList<AgentEditOption> {
    return map { AgentEditOption(id = it.id, label = it.displayLabel(), isSelected = it.id == selectedId) }.toImmutableList()
}

fun List<ChatbotProviderModel>.toModelOptions(selectedId: String): ImmutableList<AgentEditOption> {
    return map { AgentEditOption(id = it.id, label = it.displayLabel(), isSelected = it.id == selectedId) }.toImmutableList()
}

private fun voiceOptions(
    selectedToken: String,
    profiles: List<ChatbotVoiceProfile>,
    providerVoices: List<ChatbotProviderVoice>,
    labels: AgentEditRenderLabels = AgentEditRenderLabels(),
): ImmutableList<AgentEditOption> {
    return buildList {
        add(AgentEditOption(AgentVoiceSelection.DEFAULT, labels.voiceDefault, selectedToken == AgentVoiceSelection.DEFAULT))
        profiles.forEach { add(AgentEditOption(AgentVoiceSelection.profile(it.id), it.displayName, selectedToken == AgentVoiceSelection.profile(it.id))) }
        providerVoices.forEach {
            val token = AgentVoiceSelection.provider(it.provider, it.providerVoiceId)
            add(AgentEditOption(token, it.displayName, selectedToken == token))
        }
    }.toImmutableList()
}

private fun sandboxRenderModel(
    mode: AgentSandboxMode,
    initMethod: AgentSandboxInitMethod,
    sandboxStatus: AgentSandboxStatus?,
    sandboxBusy: Boolean,
    sandboxMessage: String?,
    labels: AgentEditRenderLabels = AgentEditRenderLabels(),
): AgentSandboxRenderModel {
    return AgentSandboxRenderModel(
        modeLabel = mode.displayLabel(labels),
        modeOptions = AgentSandboxMode.entries.map {
            AgentEditOption(id = it.name, label = it.displayLabel(labels), isSelected = it == mode)
        }.toImmutableList(),
        description = if (mode == AgentSandboxMode.PerUser) {
            labels.runtimePerUserDescription
        } else {
            labels.runtimeDedicatedDescription
        },
        warning = if (mode == AgentSandboxMode.AgentDedicated) {
            labels.runtimeDedicatedWarning
        } else {
            null
        },
        initOptions = listOf(
            AgentSandboxInitOptionRenderModel(
                method = AgentSandboxInitMethod.Empty,
                title = labels.runtimeCreateEmpty,
                subtitle = labels.runtimeCreateEmptySubtitle,
                isSelected = initMethod == AgentSandboxInitMethod.Empty,
            ),
            AgentSandboxInitOptionRenderModel(
                method = AgentSandboxInitMethod.CloneOwner,
                title = labels.runtimeCloneOwner,
                subtitle = labels.runtimeCloneOwnerSubtitle,
                isSelected = initMethod == AgentSandboxInitMethod.CloneOwner,
            ),
        ).toImmutableList(),
        statusLabel = sandboxStatus?.let {
            if (it.sourceUserId != null) labels.runtimeConfiguredClone else labels.runtimeConfiguredEmpty
        },
        isBusy = sandboxBusy,
        message = sandboxMessage,
    )
}
