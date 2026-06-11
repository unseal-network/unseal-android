/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault

import io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem
import kotlinx.collections.immutable.ImmutableList

data class VaultManagementState(
    val items: ImmutableList<ChatbotVaultItem>,
    val isLoading: Boolean,
    val error: String?,
    val pendingDelete: ChatbotVaultItem?,
    val isDeleting: Boolean,
    val eventSink: (VaultManagementEvents) -> Unit,
) {
    val isEmpty: Boolean = items.isEmpty() && !isLoading
}
