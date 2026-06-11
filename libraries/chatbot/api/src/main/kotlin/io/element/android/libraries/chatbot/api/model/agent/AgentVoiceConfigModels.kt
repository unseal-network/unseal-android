/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatbotAgentVoiceConfig(
    val id: String = "",
    @SerialName("agent_id")
    val agentId: String = "",
    val provider: String = "",
    @SerialName("provider_voice_id")
    val providerVoiceId: String = "",
    @SerialName("display_name")
    val displayName: String = "",
    @SerialName("source_type")
    val sourceType: String = "",
    @SerialName("voice_profile_id")
    val voiceProfileId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
)

@Serializable
data class ChatbotAgentVoiceConfigResolution(
    @SerialName("has_config")
    val hasConfig: Boolean = false,
    val source: String? = null,
    val config: ChatbotAgentVoiceConfig? = null,
)

@Serializable
data class ChatbotSetAgentVoiceConfigRequest(
    @SerialName("source_type")
    val sourceType: String,
    val provider: String? = null,
    @SerialName("provider_voice_id")
    val providerVoiceId: String? = null,
    @SerialName("voice_profile_id")
    val voiceProfileId: String? = null,
)
