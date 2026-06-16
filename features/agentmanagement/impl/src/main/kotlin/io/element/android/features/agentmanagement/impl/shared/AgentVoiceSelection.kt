/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.shared

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentVoiceConfigResolution
import io.element.android.libraries.chatbot.api.model.agent.ChatbotSetAgentVoiceConfigRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile

/**
 * Encodes the agent voice picker selection as an opaque token (mirrors iOS `AgentVoiceSelection`):
 * `default`, `profile:<voiceProfileId>`, or `provider:<provider>:<providerVoiceId>`.
 */
object AgentVoiceSelection {
    const val DEFAULT = "default"
    private const val PROFILE_PREFIX = "profile:"
    private const val PROVIDER_PREFIX = "provider:"

    fun profile(voiceProfileId: String): String = "$PROFILE_PREFIX$voiceProfileId"

    fun provider(provider: String, providerVoiceId: String): String = "$PROVIDER_PREFIX$provider:$providerVoiceId"

    fun request(token: String): ChatbotSetAgentVoiceConfigRequest? {
        if (token == DEFAULT) return null
        if (token.startsWith(PROFILE_PREFIX)) {
            val id = token.removePrefix(PROFILE_PREFIX).takeIf { it.isNotEmpty() } ?: return null
            return ChatbotSetAgentVoiceConfigRequest(sourceType = "personal_library", voiceProfileId = id)
        }
        if (token.startsWith(PROVIDER_PREFIX)) {
            val payload = token.removePrefix(PROVIDER_PREFIX)
            val sep = payload.indexOf(':')
            if (sep <= 0 || sep == payload.lastIndex) return null
            return ChatbotSetAgentVoiceConfigRequest(
                sourceType = "provider_catalog",
                provider = payload.substring(0, sep),
                providerVoiceId = payload.substring(sep + 1),
            )
        }
        return null
    }

    fun fromConfig(resolution: ChatbotAgentVoiceConfigResolution): String {
        val config = resolution.config?.takeIf { resolution.hasConfig } ?: return DEFAULT
        return if (config.sourceType == "personal_library" && !config.voiceProfileId.isNullOrEmpty()) {
            profile(config.voiceProfileId!!)
        } else {
            provider(config.provider, config.providerVoiceId)
        }
    }

    fun label(
        token: String,
        profiles: List<ChatbotVoiceProfile>,
        providerVoices: List<ChatbotProviderVoice>,
    ): String {
        val request = request(token) ?: return "Use server default"
        return if (request.sourceType == "personal_library") {
            profiles.firstOrNull { it.id == request.voiceProfileId }?.displayName ?: "Personal voice"
        } else {
            providerVoices.firstOrNull { it.provider == request.provider && it.providerVoiceId == request.providerVoiceId }?.displayName
                ?: "Provider voice"
        }
    }
}
