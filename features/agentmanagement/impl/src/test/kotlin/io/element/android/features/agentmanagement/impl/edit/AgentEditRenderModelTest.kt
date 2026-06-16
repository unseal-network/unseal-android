/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.edit

import com.google.common.truth.Truth.assertThat
import io.element.android.features.agentmanagement.impl.shared.AgentVoiceSelection
import io.element.android.libraries.chatbot.api.model.agent.AgentSandboxMode
import io.element.android.libraries.chatbot.api.model.agent.AgentSandboxStatus
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProviderInfo
import io.element.android.libraries.chatbot.api.model.agent.ChatbotProviderModel
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Test

class AgentEditRenderModelTest {
    @Test
    fun `toRenderModel - exposes iOS style form options and section labels`() {
        val state = AgentEditState(
            mode = AgentEditMode.Create,
            form = AgentEditFormState(
                botName = "planner",
                providerId = "openai",
                model = "gpt-4o",
                sandboxMode = AgentSandboxMode.AgentDedicated,
                sandboxInitMethod = AgentSandboxInitMethod.CloneOwner,
            ),
            providers = persistentListOf(
                provider("unseal", "Unseal", models = listOf(model("agent-default", "Agent Default"))),
                provider("openai", "OpenAI", supportsBaseUrl = true, models = listOf(model("gpt-4o", "GPT-4o"))),
            ),
            nameAvailability = AgentNameAvailability.Available,
            phase = AgentEditPhase.Submitting(AgentEditSubmittingStep.CreateAgent),
            isLoading = false,
            error = null,
            availableSkills = persistentListOf(
                ChatbotUserSkill(id = "calendar", name = "Calendar", description = "Reads events"),
                ChatbotUserSkill(id = "search", name = "Search"),
            ),
            selectedSkillIds = persistentSetOf("calendar"),
            voiceProfiles = persistentListOf(ChatbotVoiceProfile(id = "voice-profile", provider = "elevenlabs", providerVoiceId = "p1", displayName = "My Voice")),
            providerVoices = persistentListOf(ChatbotProviderVoice(provider = "elevenlabs", providerVoiceId = "v1", displayName = "Rachel")),
            voiceSelection = AgentVoiceSelection.profile("voice-profile"),
            personalVaultKeys = persistentListOf(ChatbotVaultItem(id = "1", key = "GMAIL_TOKEN", description = "Gmail")),
            selectedVaultKeys = persistentSetOf("GMAIL_TOKEN"),
            sandboxStatus = AgentSandboxStatus(sourceUserId = "@me:server"),
            sandboxBusy = true,
            sandboxMessage = "Runtime cloned",
            eventSink = {},
        )

        val model = state.toRenderModel()

        assertThat(model.title).isEqualTo("创建 Agent")
        assertThat(model.submitLabel).isEqualTo("创建")
        assertThat(model.submittingLabel).isEqualTo("正在保存 Agent…")
        assertThat(model.botNameAvailabilityLabel).isEqualTo("可用")
        assertThat(model.providerOptions.map { it.label }).containsExactly("Unseal", "OpenAI").inOrder()
        assertThat(model.providerOptions.single { it.id == "openai" }.isSelected).isTrue()
        assertThat(model.selectedProviderLabel).isEqualTo("OpenAI")
        assertThat(model.modelOptions.single()).isEqualTo(AgentEditOption("gpt-4o", "GPT-4o", true))
        assertThat(model.selectedModelLabel).isEqualTo("GPT-4o")
        assertThat(model.needsApiKey).isTrue()
        assertThat(model.supportsBaseUrl).isTrue()
        assertThat(model.voiceOptions.map { it.label }).containsExactly("使用服务器默认", "My Voice", "Rachel").inOrder()
        assertThat(model.selectedVoiceLabel).isEqualTo("My Voice")

        assertThat(model.sandbox.modeLabel).isEqualTo("Agent 专属环境")
        assertThat(model.sandbox.warning).contains("所有用户将共享")
        assertThat(model.sandbox.initOptions.single { it.method == AgentSandboxInitMethod.CloneOwner }.isSelected).isTrue()
        assertThat(model.sandbox.statusLabel).contains("从用户环境复制")
        assertThat(model.sandbox.isBusy).isTrue()
        assertThat(model.sandbox.message).isEqualTo("Runtime cloned")

        assertThat(model.selectedSkills.single()).isEqualTo(AgentEditChipRenderModel("calendar", "Calendar", "Reads events"))
        assertThat(model.selectedVaultKeys.single()).isEqualTo(AgentEditChipRenderModel("GMAIL_TOKEN", "GMAIL_TOKEN", "Gmail"))
        assertThat(model.vaultPickerLabel).isEqualTo("从我的密钥库选择")
        assertThat(model.skillPickerLabel).isEqualTo("添加技能")
    }

    @Test
    fun `toRenderModel - edit mode labels and empty picker labels`() {
        val model = AgentEditState(
            mode = AgentEditMode.Edit("planner"),
            form = AgentEditFormState(botName = "planner"),
            providers = persistentListOf(),
            nameAvailability = AgentNameAvailability.Unknown,
            phase = AgentEditPhase.Editing,
            isLoading = false,
            error = null,
            eventSink = {},
        ).toRenderModel()

        assertThat(model.title).isEqualTo("编辑 Agent")
        assertThat(model.submitLabel).isEqualTo("保存更改")
        assertThat(model.botNameLabel).isEqualTo("标识符")
        assertThat(model.botNameHelper).isNull()
        assertThat(model.botNameAvailabilityLabel).isNull()
        assertThat(model.vaultPickerLabel).isEqualTo("暂无密钥配置。")
        assertThat(model.skillPickerLabel).isEqualTo("暂无可用技能")
    }
}

private fun provider(
    id: String,
    displayName: String,
    supportsBaseUrl: Boolean = false,
    models: List<ChatbotProviderModel> = emptyList(),
): ChatbotAgentProvider {
    return ChatbotAgentProvider(
        id = id,
        displayName = displayName,
        info = ChatbotAgentProviderInfo(displayName = displayName, supportsBaseUrl = supportsBaseUrl, models = models),
    )
}

private fun model(id: String, displayName: String): ChatbotProviderModel {
    return ChatbotProviderModel(id = id, displayName = displayName)
}
