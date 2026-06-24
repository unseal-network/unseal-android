/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.channels

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ChatbotChannelPlatform {
    @SerialName("telegram")
    Telegram,

    @SerialName("wecom")
    WeCom,
}

/** Public channel summary — never contains secrets. */
@Serializable
data class ChatbotChannelSummary(
    val installationId: String,
    val platform: ChatbotChannelPlatform,
    val status: String,
    val label: String,
    val callbackUrl: String? = null,
)

@Serializable
data class ChatbotListChannelsResponse(
    val channels: List<ChatbotChannelSummary> = emptyList(),
)

@Serializable
data class ChatbotConnectChannelResponse(
    val installationId: String,
    val platform: ChatbotChannelPlatform,
    val botUsername: String? = null,
    val callbackUrl: String? = null,
)

/** Owner-only credential reveal — used to prefill the WeCom edit dialog. */
@Serializable
data class ChatbotChannelCredentials(
    val installationId: String,
    val platform: ChatbotChannelPlatform,
    val token: String,
    val encodingAESKey: String,
    val receiveId: String? = null,
    val aibotid: String? = null,
    val callbackUrl: String? = null,
)

/** Connect payload — platform-discriminated; serialised manually in the API impl. */
sealed interface ChatbotChannelConnectBody {
    data class Telegram(val botToken: String) : ChatbotChannelConnectBody
    data class WeCom(val token: String, val encodingAESKey: String) : ChatbotChannelConnectBody
}
