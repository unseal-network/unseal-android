/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.connectors

import kotlinx.serialization.Serializable

@Serializable
data class ChatbotToolkitCategory(
    val id: String,
    val name: String,
)

@Serializable
data class ChatbotToolkit(
    val name: String,
    val slug: String,
    val description: String? = null,
    val logo: String? = null,
    val categories: List<ChatbotToolkitCategory> = emptyList(),
    val authSchemes: List<String> = emptyList(),
    val noAuth: Boolean = false,
    val connected: Boolean = false,
    val connectedAccountId: String? = null,
)

@Serializable
data class ChatbotConnectedAccountProfile(
    val displayName: String? = null,
    val image: String? = null,
)

@Serializable
data class ChatbotConnectedAccount(
    val id: String,
    val toolkit: String,
    val status: String,
    val alias: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val profile: ChatbotConnectedAccountProfile? = null,
)

@Serializable
data class ChatbotListToolkitCategoriesResponse(
    val items: List<ChatbotToolkitCategory> = emptyList(),
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val nextCursor: String? = null,
)

@Serializable
data class ChatbotListToolkitsResponse(
    val items: List<ChatbotToolkit> = emptyList(),
    val totalItems: Int = 0,
    val totalPages: Int = 0,
    val nextCursor: String? = null,
)

@Serializable
data class ChatbotInitiateConnectionResponse(
    val connectUrl: String,
)

@Serializable
data class ChatbotConnectionStatusResponse(
    val connected: Boolean,
)

@Serializable
data class ChatbotListConnectedAccountsResponse(
    val items: List<ChatbotConnectedAccount> = emptyList(),
    val nextCursor: String? = null,
    val totalPages: Int = 0,
)

@Serializable
data class ChatbotDisconnectAccountResponse(
    val success: Boolean,
    val deletedTriggerCount: Int = 0,
)
