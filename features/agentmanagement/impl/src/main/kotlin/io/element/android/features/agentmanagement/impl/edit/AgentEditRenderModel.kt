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
    val avatar: String = "头像",
    val basicInfo: String = "基本信息",
    val accessControl: String = "访问控制",
    val aiEngine: String = "AI 引擎",
    val voice: String = "语音",
    val soul: String = "角色设定",
    val runtime: String = "运行环境",
    val vault: String = "密钥变量",
    val skills: String = "拥有的技能",
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
        title = if (isCreate) "创建 Agent" else "编辑 Agent",
        isCreate = isCreate,
        submitLabel = if (isCreate) "创建" else "保存更改",
        isSubmitting = isSubmitting,
        submittingLabel = (phase as? AgentEditPhase.Submitting)?.step?.displayLabel(),
        sectionLabels = AgentEditSectionLabels(),
        botNameLabel = if (isCreate) "唯一标识符（必填）" else "标识符",
        botNameHelper = if (isCreate) "标识符创建后不可更改。请使用小写字母和数字。" else null,
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
        vaultPickerLabel = if (personalVaultKeys.isEmpty()) "暂无密钥配置。" else "从我的密钥库选择",
        skillPickerLabel = if (availableSkills.isEmpty()) "暂无可用技能" else "添加技能",
    )
}

fun AgentNameAvailability.displayLabel(): String? = when (this) {
    AgentNameAvailability.Unknown -> null
    AgentNameAvailability.Checking -> "检查中..."
    AgentNameAvailability.Available -> "可用"
    AgentNameAvailability.Taken -> "已被占用"
}

fun AgentEditSubmittingStep.displayLabel(): String = when (this) {
    AgentEditSubmittingStep.CreateAgent -> "正在保存 Agent…"
    AgentEditSubmittingStep.CreateDM -> "正在创建私聊…"
}

fun ChatbotAgentProvider.displayLabel(): String = displayName ?: info?.displayName ?: id

fun ChatbotProviderModel.displayLabel(): String = displayName ?: id

fun AgentSandboxMode.displayLabel(): String = when (this) {
    AgentSandboxMode.PerUser -> "每用户各自的环境"
    AgentSandboxMode.AgentDedicated -> "Agent 专属环境"
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
        add(AgentEditOption(AgentVoiceSelection.DEFAULT, "使用服务器默认", selectedToken == AgentVoiceSelection.DEFAULT))
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
            "每个用户与 Agent 对话时使用自己的运行环境，数据互不影响。"
        } else {
            "所有用户共享 Agent 的独立运行环境。"
        },
        warning = if (mode == AgentSandboxMode.AgentDedicated) {
            "所有用户将共享此环境，请确保不包含私有信息，或仅在信任的群组中使用。"
        } else {
            null
        },
        initOptions = listOf(
            AgentSandboxInitOptionRenderModel(
                method = AgentSandboxInitMethod.Empty,
                title = "创建空白环境",
                subtitle = "从零开始，Agent 拥有全新的运行空间",
                isSelected = initMethod == AgentSandboxInitMethod.Empty,
            ),
            AgentSandboxInitOptionRenderModel(
                method = AgentSandboxInitMethod.CloneOwner,
                title = "从我的环境复制",
                subtitle = "复制你当前的运行环境作为起点，后续互不影响",
                isSelected = initMethod == AgentSandboxInitMethod.CloneOwner,
            ),
        ).toImmutableList(),
        statusLabel = sandboxStatus?.let {
            "已配置 · ${if (it.sourceUserId != null) "来源: 从用户环境复制" else "来源: 空白环境"}"
        },
        isBusy = sandboxBusy,
        message = sandboxMessage,
    )
}
