/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault

import io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem

sealed interface VaultManagementEvents {
    data object OnAppear : VaultManagementEvents
    data object Refresh : VaultManagementEvents
    data object Retry : VaultManagementEvents
    data object AddEntry : VaultManagementEvents
    data class EditEntry(val item: ChatbotVaultItem) : VaultManagementEvents
    data class SearchQueryChanged(val query: String) : VaultManagementEvents
    data class ConfirmDelete(val item: ChatbotVaultItem) : VaultManagementEvents
    data object DismissDelete : VaultManagementEvents
    data object DeleteConfirmed : VaultManagementEvents
    data object ClearError : VaultManagementEvents
    data object ClearSuccess : VaultManagementEvents
}
