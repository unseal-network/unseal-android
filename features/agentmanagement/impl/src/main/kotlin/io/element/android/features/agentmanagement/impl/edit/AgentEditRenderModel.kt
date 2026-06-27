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

fun AgentEditState.toRenderModel(): AgentEditRenderModel {
    val isSubmitting = phase is AgentEditPhase.Submitting
    return AgentEditRenderModel(
        title = if (isCreate) "Create agent" else "Edit agent",
        isCreate = isCreate,
        submitLabel = if (isCreate) "Create" else "Save changes",
        isSubmitting = isSubmitting,
        submittingLabel = (phase as? AgentEditPhase.Submitting)?.step?.displayLabel(),
        sectionLabels = AgentEditSectionLabels(),
        botNameLabel = if (isCreate) "Identifier (required)" else "Identifier",
        botNameHelper = if (isCreate) "The identifier cannot be changed after creation. Use lowercase letters and numbers." else null,
        botNameAvailabilityLabel = nameAvailability.displayLabel(),
        providerOptions = providers.toProviderOptions(form.providerId),
        selectedProviderLabel = selectedProvider?.displayLabel() ?: "—",
        modelOptions = availableModels.toModelOptions(form.model),
        selectedModelLabel = availableModels.firstOrNull { it.id == form.model }?.displayLabel() ?: form.model.ifEmpty { "—" },
        needsApiKey = needsApiKey,
        supportsBaseUrl = supportsBaseUrl,
        voiceOptions = voiceOptions(voiceSelection, voiceProfiles, providerVoices),
        selectedVoiceLabel = AgentVoiceSelection.label(voiceSelection, voiceProfiles, providerVoices),
        sandbox = sandboxRenderModel(form.sandboxMode, form.sandboxInitMethod, sandboxStatus, sandboxBusy, sandboxMessage),
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
        vaultPickerLabel = if (personalVaultKeys.isEmpty()) "No vault entries." else "Choose from my vault",
        skillPickerLabel = if (availableSkills.isEmpty()) "No skills available" else "Add skill",
    )
}

fun AgentNameAvailability.displayLabel(): String? = when (this) {
    AgentNameAvailability.Unknown -> null
    AgentNameAvailability.Checking -> "Checking..."
    AgentNameAvailability.Available -> "Available"
    AgentNameAvailability.Taken -> "Taken"
}

fun AgentEditSubmittingStep.displayLabel(): String = when (this) {
    AgentEditSubmittingStep.CreateAgent -> "Saving agent…"
    AgentEditSubmittingStep.CreateDM -> "Creating direct chat…"
}

fun ChatbotAgentProvider.displayLabel(): String = displayName ?: info?.displayName ?: id

fun ChatbotProviderModel.displayLabel(): String = displayName ?: id

fun AgentSandboxMode.displayLabel(): String = when (this) {
    AgentSandboxMode.PerUser -> "Separate runtime per user"
    AgentSandboxMode.AgentDedicated -> "Dedicated agent runtime"
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
): ImmutableList<AgentEditOption> {
    return buildList {
        add(AgentEditOption(AgentVoiceSelection.DEFAULT, "Use server default", selectedToken == AgentVoiceSelection.DEFAULT))
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
): AgentSandboxRenderModel {
    return AgentSandboxRenderModel(
        modeLabel = mode.displayLabel(),
        modeOptions = AgentSandboxMode.entries.map {
            AgentEditOption(id = it.name, label = it.displayLabel(), isSelected = it == mode)
        }.toImmutableList(),
        description = if (mode == AgentSandboxMode.PerUser) {
            "Each user uses their own runtime when chatting with the agent. Data stays separate."
        } else {
            "All users share the agent's dedicated runtime."
        },
        warning = if (mode == AgentSandboxMode.AgentDedicated) {
            "All users will share this runtime. Make sure it does not contain private information, or use it only in trusted groups."
        } else {
            null
        },
        initOptions = listOf(
            AgentSandboxInitOptionRenderModel(
                method = AgentSandboxInitMethod.Empty,
                title = "Create empty runtime",
                subtitle = "Start from scratch with a fresh runtime for this agent",
                isSelected = initMethod == AgentSandboxInitMethod.Empty,
            ),
            AgentSandboxInitOptionRenderModel(
                method = AgentSandboxInitMethod.CloneOwner,
                title = "Copy my runtime",
                subtitle = "Copy your current runtime as a starting point; future changes stay separate",
                isSelected = initMethod == AgentSandboxInitMethod.CloneOwner,
            ),
        ).toImmutableList(),
        statusLabel = sandboxStatus?.let {
            "Configured · ${if (it.sourceUserId != null) "Source: copied from user runtime" else "Source: empty runtime"}"
        },
        isBusy = sandboxBusy,
        message = sandboxMessage,
    )
}
