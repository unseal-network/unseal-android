/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.edit

sealed interface AgentEditEvents {
    data object OnAppear : AgentEditEvents
    data object RefreshProviders : AgentEditEvents
    data class BotNameChanged(val value: String) : AgentEditEvents
    data class DisplayNameChanged(val value: String) : AgentEditEvents
    data class DescriptionChanged(val value: String) : AgentEditEvents
    data class AvatarUrlChanged(val value: String) : AgentEditEvents
    data class AvatarPicked(val data: ByteArray, val mimeType: String) : AgentEditEvents
    data class IsPublicChanged(val value: Boolean) : AgentEditEvents
    data class AutoJoinChanged(val value: Boolean) : AgentEditEvents
    data class ProviderChanged(val providerId: String) : AgentEditEvents
    data class ModelChanged(val value: String) : AgentEditEvents
    data class ApiKeyChanged(val value: String) : AgentEditEvents
    data class BaseUrlChanged(val value: String) : AgentEditEvents
    data class SoulChanged(val value: String) : AgentEditEvents
    data class SandboxModeChanged(val mode: io.element.android.libraries.chatbot.api.model.agent.AgentSandboxMode) : AgentEditEvents
    data class SandboxInitMethodChanged(val method: AgentSandboxInitMethod) : AgentEditEvents
    data class SandboxActionRequested(val method: AgentSandboxInitMethod) : AgentEditEvents
    data object SandboxActionConfirmed : AgentEditEvents
    data object SandboxActionDismissed : AgentEditEvents
    data object AddVaultEntry : AgentEditEvents
    data class UpdateVaultEntry(val index: Int, val key: String, val value: String, val description: String) : AgentEditEvents
    data class RemoveVaultEntry(val index: Int) : AgentEditEvents
    data class ToggleVaultKey(val key: String) : AgentEditEvents
    data class ToggleSkill(val skillId: String) : AgentEditEvents
    data class VoiceSelectionChanged(val token: String) : AgentEditEvents
    data object Submit : AgentEditEvents
    data object GoToChat : AgentEditEvents
    data object CreateAnother : AgentEditEvents
    data object ClearError : AgentEditEvents
}
