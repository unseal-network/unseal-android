/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkit
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkitCategory
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 20
private const val CATEGORY_PAGE_SIZE = 50

// Mirror iOS: only query the API when the search text is at least this long,
// debounced to avoid a request on every keystroke.
private const val MIN_SEARCH_LENGTH = 3
private const val SEARCH_DEBOUNCE_MS = 300L

@AssistedInject
class ConnectorListPresenter(
    @Assisted private val navigator: ConnectorListNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<ConnectorListState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: ConnectorListNavigator): ConnectorListPresenter
    }

    @OptIn(FlowPreview::class)
    @Composable
    override fun present(): ConnectorListState {
        val coroutineScope = rememberCoroutineScope()
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var toolkits by remember { mutableStateOf(emptyList<ChatbotToolkit>()) }
        var categories by remember { mutableStateOf(emptyList<ChatbotToolkitCategory>()) }
        // Mirror iOS `selectedCategoryID`: empty string means the "All" chip is selected.
        var selectedCategoryId by remember { mutableStateOf("") }
        var searchQuery by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var isLoadingMore by remember { mutableStateOf(false) }
        var nextCursor by remember { mutableStateOf<String?>(null) }
        var connectingSlug by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        var successMessage by remember { mutableStateOf<String?>(null) }

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        fun errorMessage(throwable: Throwable, fallback: String): String {
            return throwable.message ?: throwable::class.simpleName ?: fallback
        }

        // Mirror iOS: only send the search term once it reaches the minimum length, otherwise null.
        fun searchParam(): String? {
            val trimmed = searchQuery.trim()
            return if (trimmed.length >= MIN_SEARCH_LENGTH) trimmed else null
        }

        // Mirror iOS: empty selection ("All") maps to null so no category filter is sent.
        fun categoryParam(): String? = selectedCategoryId.takeIf { it.isNotEmpty() }

        // Mirror iOS loadCategories(): populate the chip row from the dedicated
        // toolkit-categories endpoint. Failures are swallowed so the list still renders.
        fun loadCategories() = coroutineScope.launch {
            api().listToolkitCategories(cursor = null, limit = CATEGORY_PAGE_SIZE)
                .onSuccess { categories = it.items }
        }

        fun loadToolkits(showConnectionSuccess: Boolean = false) = coroutineScope.launch {
            isLoading = true
            val previousConnectedSlugs = toolkits.asSequence()
                .filter { it.connected }
                .map { it.slug }
                .toSet()
            api().listToolkits(
                search = searchParam(),
                category = categoryParam(),
                cursor = null,
                limit = PAGE_SIZE,
            )
                .onSuccess {
                    if (showConnectionSuccess && toolkits.isNotEmpty()) {
                        it.items.firstOrNull { toolkit -> toolkit.connected && toolkit.slug !in previousConnectedSlugs }?.let { toolkit ->
                            successMessage = "${toolkit.name} 已连接"
                        }
                    }
                    toolkits = it.items
                    nextCursor = it.nextCursor
                    error = null
                }
                .onFailure {
                    error = errorMessage(it, "Failed to load connectors")
                }
            isLoading = false
        }

        fun loadMore() = coroutineScope.launch {
            val cursor = nextCursor ?: return@launch
            if (isLoadingMore) return@launch
            isLoadingMore = true
            api().listToolkits(
                search = searchParam(),
                category = categoryParam(),
                cursor = cursor,
                limit = PAGE_SIZE,
            )
                .onSuccess {
                    toolkits = toolkits + it.items
                    nextCursor = it.nextCursor
                    error = null
                }
                .onFailure {
                    error = errorMessage(it, "Failed to load more connectors")
                }
            isLoadingMore = false
        }

        fun connect(toolkit: ChatbotToolkit) = coroutineScope.launch {
            connectingSlug = toolkit.slug
            val redirectUrl = "network.unseal.android://composio-callback?toolkit=${toolkit.slug}"
            api().initiateConnection(toolkit.slug, redirectUrl)
                .onSuccess {
                    navigator.onOpenConnectUrl(it.connectUrl)
                    error = null
                }
                .onFailure {
                    error = errorMessage(it, "Failed to connect")
                }
            connectingSlug = null
        }

        fun handleEvent(event: ConnectorListEvents) {
            when (event) {
                ConnectorListEvents.OnAppear -> if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    loadCategories()
                    loadToolkits()
                }
                // Silent refresh (e.g. on resume / after returning from the connect flow):
                // re-fetch without clearing the existing list so the skeleton is not shown again.
                ConnectorListEvents.Refresh -> loadToolkits(showConnectionSuccess = true)
                is ConnectorListEvents.SearchChanged -> {
                    // Just update the query; the debounced effect below triggers the actual load.
                    searchQuery = event.query
                }
                is ConnectorListEvents.SelectCategory -> {
                    // Mirror iOS: update the selection then reload toolkits with the new category.
                    selectedCategoryId = event.categoryId
                    loadToolkits()
                }
                ConnectorListEvents.LoadMore -> loadMore()
                is ConnectorListEvents.Connect -> connect(event.toolkit)
                is ConnectorListEvents.Manage -> navigator.onManageToolkit(event.toolkit.slug, event.toolkit.name)
                ConnectorListEvents.ClearError -> error = null
                ConnectorListEvents.ClearSuccess -> successMessage = null
                ConnectorListEvents.Dismiss -> navigator.onDone()
            }
        }

        // Mirror iOS setupSearchDebounce(): observe search text, drop the initial value,
        // de-duplicate, debounce 300ms, then reload (length threshold is applied in searchParam()).
        LaunchedEffect(Unit) {
            snapshotFlow { searchQuery }
                .drop(1)
                .distinctUntilChanged()
                .debounce(SEARCH_DEBOUNCE_MS)
                .collect { loadToolkits() }
        }

        return ConnectorListState(
            toolkits = toolkits.toImmutableList(),
            categories = categories.toImmutableList(),
            selectedCategoryId = selectedCategoryId,
            searchQuery = searchQuery,
            isLoading = isLoading,
            isLoadingMore = isLoadingMore,
            hasMore = nextCursor != null,
            connectingSlug = connectingSlug,
            error = error,
            successMessage = successMessage,
            eventSink = ::handleEvent,
        )
    }
}
