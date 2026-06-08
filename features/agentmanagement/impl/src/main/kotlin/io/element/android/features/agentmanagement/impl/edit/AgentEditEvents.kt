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
    data class IsPublicChanged(val value: Boolean) : AgentEditEvents
    data class AutoJoinChanged(val value: Boolean) : AgentEditEvents
    data class ProviderChanged(val providerId: String) : AgentEditEvents
    data class ModelChanged(val value: String) : AgentEditEvents
    data class ApiKeyChanged(val value: String) : AgentEditEvents
    data class BaseUrlChanged(val value: String) : AgentEditEvents
    data class SoulChanged(val value: String) : AgentEditEvents
    data object Submit : AgentEditEvents
    data object GoToChat : AgentEditEvents
    data object CreateAnother : AgentEditEvents
    data object ClearError : AgentEditEvents
}
