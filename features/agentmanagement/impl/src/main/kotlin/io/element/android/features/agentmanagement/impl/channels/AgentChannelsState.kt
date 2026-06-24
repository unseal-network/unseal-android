/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.channels

import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelPlatform
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary
import kotlinx.collections.immutable.ImmutableList

const val WECOM_AES_KEY_LENGTH = 43
const val WECOM_TOKEN_LENGTH = 16

/** Sub-state for the add/edit bottom sheet — null in [AgentChannelsState] means the sheet is closed. */
data class ChannelSheetState(
    val editInstallationId: String?,
    val platform: ChatbotChannelPlatform,
    val botToken: String,
    val wecomToken: String,
    val wecomAesKey: String,
    val busy: Boolean,
    val error: String?,
    /** Non-null ⇒ show the WeCom callback panel (created/loaded). */
    val callbackUrl: String?,
    val installationId: String?,
    val connectedToken: String,
    val connectedAesKey: String,
) {
    val isEditMode: Boolean get() = editInstallationId != null
    val credsChanged: Boolean
        get() = callbackUrl != null && (wecomToken != connectedToken || wecomAesKey != connectedAesKey)
    val canConnect: Boolean
        get() = if (platform == ChatbotChannelPlatform.Telegram) {
            botToken.isNotBlank()
        } else {
            wecomToken.isNotBlank() && wecomAesKey.length == WECOM_AES_KEY_LENGTH
        }
}

data class AgentChannelsState(
    val channels: ImmutableList<ChatbotChannelSummary>,
    val isLoading: Boolean,
    val error: String?,
    val sheet: ChannelSheetState?,
    val pendingDelete: ChatbotChannelSummary?,
    val eventSink: (AgentChannelsEvents) -> Unit,
)
