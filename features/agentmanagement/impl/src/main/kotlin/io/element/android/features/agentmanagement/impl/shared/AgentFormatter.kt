/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.shared

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent

fun ChatbotAgent.displayTitle(): String = displayName?.takeIf { it.isNotBlank() } ?: botName

fun ChatbotAgent.matrixId(): String? {
    val local = localpart?.takeIf { it.isNotBlank() } ?: return null
    val server = serverName?.takeIf { it.isNotBlank() } ?: return null
    return "@$local:$server"
}

fun ChatbotAgent.providerModelText(): String? {
    return listOfNotNull(provider?.trim(), model?.trim())
        .filter { it.isNotEmpty() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString(separator = " · ")
}

fun ChatbotAgent.agentMatrixUserId(): String? {
    matrixId()?.let { return it }
    val providerId = providerAgentId?.trim().orEmpty()
    return providerId.takeIf { it.startsWith("@") && it.contains(":") }
}

fun ChatbotAgent.copyableAgentId(): String = agentMatrixUserId() ?: matrixId() ?: botName
