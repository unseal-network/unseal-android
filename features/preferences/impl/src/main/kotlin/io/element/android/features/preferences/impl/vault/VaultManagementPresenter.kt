/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.preferences.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@AssistedInject
class VaultManagementPresenter(
    @Assisted private val navigator: VaultManagementNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<VaultManagementState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: VaultManagementNavigator): VaultManagementPresenter
    }

    @Composable
    override fun present(): VaultManagementState {
        val coroutineScope = rememberCoroutineScope()
        var items by remember { mutableStateOf(emptyList<ChatbotVaultItem>()) }
        var searchQuery by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var successMessage by remember { mutableStateOf<String?>(null) }
        var pendingDelete by remember { mutableStateOf<ChatbotVaultItem?>(null) }
        var isDeleting by remember { mutableStateOf(false) }
        val unknownError = stringResource(R.string.screen_vault_error_unknown)
        val loadFailedTemplate = stringResource(R.string.screen_vault_error_load_failed)
        val deleteFailedTemplate = stringResource(R.string.screen_vault_error_delete_failed)

        fun failureMessage(failure: Throwable, fallback: String): String =
            failure.message ?: failure::class.simpleName ?: fallback

        // Personal vault lives on the AI-stream (homeserver) base URL, mirroring iOS VaultService
        // which builds its requests against the Matrix homeserver base (api.unseal.network).
        suspend fun api() = chatbotApiServiceFactory.createForAiStream(matrixClient)

        fun load() {
            coroutineScope.launch {
                isLoading = true
                api().listVault()
                    .onSuccess {
                        items = it
                        error = null
                    }
                    .onFailure { error = loadFailedTemplate.format(failureMessage(it, unknownError)) }
                isLoading = false
            }
        }

        fun deleteConfirmed() {
            val target = pendingDelete ?: return
            coroutineScope.launch {
                isDeleting = true
                // iOS deletes by key (VaultService.deleteVault(key:) forwards the key as the vault id).
                api().deleteVaultEntry(target.key)
                    .onSuccess {
                        pendingDelete = null
                        error = null
                        successMessage = "Vault entry deleted successfully"
                        load()
                    }
                    .onFailure { error = deleteFailedTemplate.format(failureMessage(it, unknownError)) }
                isDeleting = false
            }
        }

        fun handleEvent(event: VaultManagementEvents) {
            when (event) {
                VaultManagementEvents.OnAppear -> load()
                VaultManagementEvents.Refresh -> load()
                VaultManagementEvents.Retry -> load()
                VaultManagementEvents.AddEntry -> navigator.onAddEntry()
                is VaultManagementEvents.EditEntry -> navigator.onEditEntry(event.item)
                is VaultManagementEvents.SearchQueryChanged -> searchQuery = event.query
                is VaultManagementEvents.ConfirmDelete -> pendingDelete = event.item
                VaultManagementEvents.DismissDelete -> pendingDelete = null
                VaultManagementEvents.DeleteConfirmed -> deleteConfirmed()
                VaultManagementEvents.ClearError -> error = null
                VaultManagementEvents.ClearSuccess -> successMessage = null
            }
        }

        return VaultManagementState(
            items = items.toImmutableList(),
            filteredItems = items.filteredBy(searchQuery).toImmutableList(),
            searchQuery = searchQuery,
            isLoading = isLoading,
            error = error,
            successMessage = successMessage,
            pendingDelete = pendingDelete,
            isDeleting = isDeleting,
            eventSink = ::handleEvent,
        )
    }
}

private fun List<ChatbotVaultItem>.filteredBy(query: String): List<ChatbotVaultItem> {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return this
    return filter { item ->
        item.key.lowercase().contains(normalized) ||
            item.description.orEmpty().lowercase().contains(normalized)
    }
}
