/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.shared

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider

fun List<ChatbotAgentProvider>.iosOrderedProviders(): List<ChatbotAgentProvider> {
    val mutable = toMutableList()
    val unsealIndex = mutable.indexOfFirst { it.id == "unseal" }
    if (unsealIndex > 0) {
        val unseal = mutable.removeAt(unsealIndex)
        mutable.add(0, unseal)
    }
    return mutable
}

fun List<ChatbotAgentProvider>.iosOrderedProviderIds(): List<String> = iosOrderedProviders().map { it.id }

fun selectProviderId(providers: List<ChatbotAgentProvider>, currentProviderId: String?): String? {
    if (currentProviderId != null && providers.any { it.id == currentProviderId }) {
        return currentProviderId
    }
    return providers.firstOrNull()?.id
}

fun selectModelId(provider: ChatbotAgentProvider?, currentModelId: String): String {
    val models = provider?.info?.models.orEmpty()
    if (models.isEmpty()) return currentModelId
    if (currentModelId.isNotEmpty() && models.any { it.id == currentModelId }) return currentModelId
    return models.first().id
}

fun needsApiKey(providerId: String?): Boolean = providerId != null && providerId != "unseal"
