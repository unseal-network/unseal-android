/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.manage

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotDisconnectAccountResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotInitiateConnectionResponse
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotListConnectedAccountsResponse
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ConnectorManagePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads connected accounts with uppercased toolkit`() = runTest {
        val queried = mutableListOf<String?>()
        val service = FakeChatbotApiService().apply {
            listConnectedAccountsResult = { toolkit, _, _ ->
                queried += toolkit
                Result.success(ChatbotListConnectedAccountsResponse(items = listOf(account("a-1"))))
            }
        }
        val presenter = createPresenter(service = service, toolkitSlug = "gmail")

        presenter.test {
            awaitItem().eventSink(ConnectorManageEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.accounts.size == 1 }
            assertThat(loaded.accounts.single().id).isEqualTo("a-1")
            assertThat(queried).containsExactly("GMAIL")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - disconnect requires confirmation then removes account`() = runTest {
        val disconnected = mutableListOf<String>()
        val service = FakeChatbotApiService().apply {
            listConnectedAccountsResult = { _, _, _ ->
                Result.success(ChatbotListConnectedAccountsResponse(items = listOf(account("a-1"), account("a-2"))))
            }
            disconnectAccountResult = {
                disconnected += it
                Result.success(ChatbotDisconnectAccountResponse(success = true))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(ConnectorManageEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.accounts.size == 2 }
            loaded.eventSink(ConnectorManageEvents.ConfirmDisconnect("a-1"))
            val confirming = awaitStateWhere { it.confirmingDisconnectId == "a-1" }
            assertThat(disconnected).isEmpty()
            confirming.eventSink(ConnectorManageEvents.Disconnect("a-1"))
            val after = awaitStateWhere { it.disconnectingId == null && it.accounts.size == 1 }
            assertThat(disconnected).containsExactly("a-1")
            assertThat(after.accounts.single().id).isEqualTo("a-2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - connect initiates connection and opens url`() = runTest {
        val connects = mutableListOf<Pair<String, String>>()
        val service = FakeChatbotApiService().apply {
            initiateConnectionResult = { toolkit, redirect ->
                connects += toolkit to redirect
                Result.success(ChatbotInitiateConnectionResponse(connectUrl = "https://connect.example/new"))
            }
        }
        val navigator = FakeConnectorManageNavigator()
        val presenter = createPresenter(service = service, navigator = navigator, toolkitSlug = "gmail")

        presenter.test {
            awaitItem().eventSink(ConnectorManageEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading }
            loaded.eventSink(ConnectorManageEvents.Connect)
            awaitStateWhere { !it.connecting && navigator.openedUrls.isNotEmpty() }
            assertThat(connects).containsExactly("gmail" to "io.element.android.x://composio-callback?toolkit=gmail")
            assertThat(navigator.openedUrls).containsExactly("https://connect.example/new")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: FakeConnectorManageNavigator = FakeConnectorManageNavigator(),
        toolkitSlug: String = "gmail",
        toolkitName: String = "Gmail",
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
    ): ConnectorManagePresenter {
        return ConnectorManagePresenter(
            toolkitSlug = toolkitSlug,
            toolkitName = toolkitName,
            navigator = navigator,
            matrixClient = matrixClient,
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private fun account(id: String): ChatbotConnectedAccount = ChatbotConnectedAccount(
    id = id,
    toolkit = "GMAIL",
    status = "ACTIVE",
    createdAt = "2026-01-01T00:00:00Z",
    updatedAt = "2026-01-01T00:00:00Z",
)

private class FakeConnectorManageNavigator : ConnectorManageNavigator {
    val openedUrls = mutableListOf<String>()
    var doneCalls = 0

    override fun onOpenConnectUrl(url: String) {
        openedUrls += url
    }

    override fun onDone() {
        doneCalls++
    }
}

private suspend fun TurbineTestContext<ConnectorManageState>.awaitStateWhere(
    predicate: (ConnectorManageState) -> Boolean,
): ConnectorManageState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}
