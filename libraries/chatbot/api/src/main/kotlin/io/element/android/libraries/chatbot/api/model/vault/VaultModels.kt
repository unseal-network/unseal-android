/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.vault

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A personal vault entry (`/chatbot/v1/vault`). Mirrors iOS `VaultItem`. */
@Serializable
data class ChatbotVaultItem(
    val id: String = "",
    val key: String,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("updated_at")
    val updatedAt: String? = null,
    val description: String? = null,
    val value: String? = null,
)

@Serializable
data class ChatbotVaultListResponse(
    val items: List<ChatbotVaultItem> = emptyList(),
)

@Serializable
data class ChatbotVaultDetailResponse(
    val item: ChatbotVaultItem? = null,
    val value: String? = null,
)
