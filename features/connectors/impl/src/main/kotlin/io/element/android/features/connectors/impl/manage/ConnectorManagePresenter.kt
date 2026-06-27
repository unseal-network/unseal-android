/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.manage

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
import io.element.android.features.connectors.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@AssistedInject
class ConnectorManagePresenter(
    @Assisted private val toolkitSlug: String,
    @Assisted private val toolkitName: String,
    @Assisted private val navigator: ConnectorManageNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<ConnectorManageState> {
    @AssistedFactory
    interface Factory {
        fun create(
            toolkitSlug: String,
            toolkitName: String,
            navigator: ConnectorManageNavigator,
        ): ConnectorManagePresenter
    }

    @Composable
    override fun present(): ConnectorManageState {
        val coroutineScope = rememberCoroutineScope()
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var accounts by remember { mutableStateOf(emptyList<ChatbotConnectedAccount>()) }
        var isLoading by remember { mutableStateOf(false) }
        var connecting by remember { mutableStateOf(false) }
        var disconnectingId by remember { mutableStateOf<String?>(null) }
        var confirmingDisconnectId by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        val loadAccountsError = stringResource(R.string.connectors_error_load_accounts)
        val disconnectError = stringResource(R.string.connectors_error_disconnect)
        val connectError = stringResource(R.string.connectors_error_connect)

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        fun errorMessage(fallback: String): String = fallback

        fun loadAccounts() = coroutineScope.launch {
            isLoading = true
            api().listConnectedAccounts(toolkit = toolkitSlug.uppercase(), cursor = null, limit = null)
                .onSuccess {
                    accounts = it.items
                    error = null
                }
                .onFailure {
                    error = errorMessage(loadAccountsError)
                }
            isLoading = false
        }

        fun disconnect(accountId: String) = coroutineScope.launch {
            disconnectingId = accountId
            confirmingDisconnectId = null
            api().disconnectAccount(accountId)
                .onSuccess {
                    accounts = accounts.filterNot { it.id == accountId }
                    error = null
                }
                .onFailure {
                    error = errorMessage(disconnectError)
                }
            disconnectingId = null
        }

        fun connect() = coroutineScope.launch {
            connecting = true
            val redirectUrl = "network.unseal.android://composio-callback?toolkit=$toolkitSlug"
            api().initiateConnection(toolkitSlug, redirectUrl)
                .onSuccess {
                    navigator.onOpenConnectUrl(it.connectUrl)
                    error = null
                }
                .onFailure {
                    error = errorMessage(connectError)
                }
            connecting = false
        }

        fun handleEvent(event: ConnectorManageEvents) {
            when (event) {
                ConnectorManageEvents.OnAppear -> if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    loadAccounts()
                }
                ConnectorManageEvents.Refresh -> loadAccounts()
                is ConnectorManageEvents.ConfirmDisconnect -> confirmingDisconnectId = event.accountId
                ConnectorManageEvents.CancelDisconnect -> confirmingDisconnectId = null
                is ConnectorManageEvents.Disconnect -> disconnect(event.accountId)
                ConnectorManageEvents.Connect -> connect()
                ConnectorManageEvents.ClearError -> error = null
                ConnectorManageEvents.Dismiss -> navigator.onDone()
            }
        }

        return ConnectorManageState(
            toolkitName = toolkitName,
            accounts = accounts.toImmutableList(),
            isLoading = isLoading,
            connecting = connecting,
            disconnectingId = disconnectingId,
            confirmingDisconnectId = confirmingDisconnectId,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}
