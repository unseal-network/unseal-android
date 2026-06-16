/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.list

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotInitiateConnectionResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListToolkitCategoriesResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListToolkitsResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkit
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkitCategory
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aChatbotToolkit
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ConnectorListPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads toolkits once on appear`() = runTest {
        var calls = 0
        val service = FakeChatbotApiService().apply {
            listToolkitsResult = { _, _, _, _ ->
                calls++
                Result.success(ChatbotListToolkitsResponse(items = listOf(toolkit("gmail")), nextCursor = "cursor"))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(ConnectorListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.toolkits.size == 1 }
            assertThat(loaded.toolkits.single().slug).isEqualTo("gmail")
            assertThat(loaded.hasMore).isTrue()
            assertThat(calls).isEqualTo(1)
            loaded.eventSink(ConnectorListEvents.OnAppear)
            assertThat(calls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - search reloads with query forwarded to api`() = runTest {
        val searches = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            listToolkitsResult = { search, _, _, _ ->
                searches += search
                Result.success(ChatbotListToolkitsResponse(items = listOf(toolkit("slack"))))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(ConnectorListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.toolkits.isNotEmpty() }
            loaded.eventSink(ConnectorListEvents.SearchChanged("  gmail  "))
            awaitStateWhere { !it.isLoading && it.searchQuery == "  gmail  " && searches.size == 2 }
            assertThat(searches).containsExactly(null, "gmail").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - loads categories on appear`() = runTest {
        val service = FakeChatbotApiService().apply {
            listToolkitsResult = { _, _, _, _ -> Result.success(ChatbotListToolkitsResponse(items = listOf(toolkit("gmail")))) }
            listToolkitCategoriesResult = { _, _ ->
                Result.success(ChatbotListToolkitCategoriesResponse(items = listOf(ChatbotToolkitCategory(id = "comms", name = "Communication"))))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(ConnectorListEvents.OnAppear)
            val loaded = awaitStateWhere { it.categories.isNotEmpty() }
            assertThat(loaded.categories.single().id).isEqualTo("comms")
            assertThat(loaded.selectedCategoryId).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - select category reloads with category forwarded to api`() = runTest {
        val categoriesSent = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            listToolkitsResult = { _, category, _, _ ->
                categoriesSent += category
                Result.success(ChatbotListToolkitsResponse(items = listOf(toolkit("gmail"))))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(ConnectorListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.toolkits.isNotEmpty() }
            loaded.eventSink(ConnectorListEvents.SelectCategory("comms"))
            val filtered = awaitStateWhere { it.selectedCategoryId == "comms" && categoriesSent.size == 2 }
            assertThat(filtered.selectedCategoryId).isEqualTo("comms")
            assertThat(categoriesSent).containsExactly(null, "comms").inOrder()
            // Selecting "All" again clears the filter back to null.
            filtered.eventSink(ConnectorListEvents.SelectCategory(""))
            awaitStateWhere { it.selectedCategoryId.isEmpty() && categoriesSent.size == 3 }
            assertThat(categoriesSent).containsExactly(null, "comms", null).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - connect initiates connection and opens url`() = runTest {
        val connects = mutableListOf<Pair<String, String>>()
        val service = FakeChatbotApiService().apply {
            listToolkitsResult = { _, _, _, _ -> Result.success(ChatbotListToolkitsResponse(items = listOf(toolkit("gmail")))) }
            initiateConnectionResult = { toolkit, redirect ->
                connects += toolkit to redirect
                Result.success(ChatbotInitiateConnectionResponse(connectUrl = "https://connect.example/gmail"))
            }
        }
        val navigator = FakeConnectorListNavigator()
        val presenter = createPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(ConnectorListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.toolkits.isNotEmpty() }
            loaded.eventSink(ConnectorListEvents.Connect(loaded.toolkits.single()))
            awaitStateWhere { it.connectingSlug == null && navigator.openedUrls.isNotEmpty() }
            assertThat(connects).containsExactly("gmail" to "io.element.android.x://composio-callback?toolkit=gmail")
            assertThat(navigator.openedUrls).containsExactly("https://connect.example/gmail")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - manage navigates with slug and name`() = runTest {
        val service = FakeChatbotApiService().apply {
            listToolkitsResult = { _, _, _, _ ->
                Result.success(ChatbotListToolkitsResponse(items = listOf(toolkit("gmail", name = "Gmail", connected = true))))
            }
        }
        val navigator = FakeConnectorListNavigator()
        val presenter = createPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(ConnectorListEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.toolkits.isNotEmpty() }
            loaded.eventSink(ConnectorListEvents.Manage(loaded.toolkits.single()))
            assertThat(navigator.managed).containsExactly("gmail" to "Gmail")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - load failure exposes error`() = runTest {
        val service = FakeChatbotApiService().apply {
            listToolkitsResult = { _, _, _, _ -> Result.failure(RuntimeException("network")) }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(ConnectorListEvents.OnAppear)
            val failed = awaitStateWhere { !it.isLoading && it.error?.contains("network") == true }
            assertThat(failed.toolkits).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: FakeConnectorListNavigator = FakeConnectorListNavigator(),
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
    ): ConnectorListPresenter {
        return ConnectorListPresenter(
            navigator = navigator,
            matrixClient = matrixClient,
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private fun toolkit(
    slug: String,
    name: String = "Toolkit $slug",
    connected: Boolean = false,
): ChatbotToolkit = aChatbotToolkit(slug = slug, name = name).copy(connected = connected)

private class FakeConnectorListNavigator : ConnectorListNavigator {
    val managed = mutableListOf<Pair<String, String>>()
    val openedUrls = mutableListOf<String>()
    var doneCalls = 0

    override fun onManageToolkit(toolkitSlug: String, toolkitName: String) {
        managed += toolkitSlug to toolkitName
    }

    override fun onOpenConnectUrl(url: String) {
        openedUrls += url
    }

    override fun onDone() {
        doneCalls++
    }
}

private suspend fun TurbineTestContext<ConnectorListState>.awaitStateWhere(
    predicate: (ConnectorListState) -> Boolean,
): ConnectorListState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}
