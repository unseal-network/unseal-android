/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.channels

import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelPlatform
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary

sealed interface AgentChannelsEvents {
    data object OnAppear : AgentChannelsEvents
    data object Refresh : AgentChannelsEvents
    data object OpenAdd : AgentChannelsEvents
    data class OpenEdit(val installationId: String) : AgentChannelsEvents
    data object CloseSheet : AgentChannelsEvents
    data class RequestDelete(val channel: ChatbotChannelSummary) : AgentChannelsEvents
    data object CancelDelete : AgentChannelsEvents
    data class ConfirmDelete(val installationId: String) : AgentChannelsEvents

    // Add/edit sheet field edits + actions.
    data class SetPlatform(val platform: ChatbotChannelPlatform) : AgentChannelsEvents
    data class SetBotToken(val value: String) : AgentChannelsEvents
    data class SetWecomToken(val value: String) : AgentChannelsEvents
    data class SetWecomAesKey(val value: String) : AgentChannelsEvents
    data object GenerateToken : AgentChannelsEvents
    data object GenerateAesKey : AgentChannelsEvents
    data object Connect : AgentChannelsEvents
    data object UpdateCreds : AgentChannelsEvents
    data object FinishSheet : AgentChannelsEvents
    data class Copy(val value: String) : AgentChannelsEvents
    data object ClearError : AgentChannelsEvents
}
