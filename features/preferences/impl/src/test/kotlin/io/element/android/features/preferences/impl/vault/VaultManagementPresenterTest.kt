/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.vault.ChatbotVaultItem
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class VaultManagementPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads vault items and filters by key or description`() = runTest {
        val service = FakeChatbotApiService().apply {
            listVaultResult = {
                Result.success(
                    listOf(
                        aVaultItem(key = "OPENAI_API_KEY", description = "OpenAI service token"),
                        aVaultItem(key = "STRIPE_SECRET", description = "Billing"),
                        aVaultItem(key = "GITHUB_TOKEN", description = "Repo access"),
                    )
                )
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(VaultManagementEvents.OnAppear)
            val loadedState = awaitStateWhere { it.items.size == 3 && !it.isLoading }

            loadedState.eventSink(VaultManagementEvents.SearchQueryChanged(" billing "))
            val billingState = awaitStateWhere { it.searchQuery == " billing " }
            assertThat(billingState.filteredItems.map { it.key }).containsExactly("STRIPE_SECRET")

            billingState.eventSink(VaultManagementEvents.SearchQueryChanged("github"))
            val githubState = awaitStateWhere { it.searchQuery == "github" }
            assertThat(githubState.filteredItems.map { it.key }).containsExactly("GITHUB_TOKEN")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - load failure with no items exposes full screen error state`() = runTest {
        val service = FakeChatbotApiService().apply {
            listVaultResult = { Result.failure(IllegalStateException("network down")) }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(VaultManagementEvents.OnAppear)
            val failedState = awaitStateWhere { it.error?.contains("network down") == true && !it.isLoading }

            assertThat(failedState.isFullScreenError).isTrue()
            assertThat(failedState.isEmpty).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - delete uses vault key then reloads and emits success`() = runTest {
        val deletedKeys = mutableListOf<String>()
        var listCalls = 0
        val service = FakeChatbotApiService().apply {
            listVaultResult = {
                listCalls++
                Result.success(
                    if (listCalls == 1) {
                        listOf(aVaultItem(id = "id-1", key = "SECRET_KEY"))
                    } else {
                        emptyList()
                    }
                )
            }
            deleteVaultEntryResult = {
                deletedKeys += it
                Result.success(Unit)
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(VaultManagementEvents.OnAppear)
            val loadedState = awaitStateWhere { it.items.singleOrNull()?.key == "SECRET_KEY" && !it.isLoading }

            loadedState.eventSink(VaultManagementEvents.ConfirmDelete(loadedState.items.single()))
            val confirmState = awaitStateWhere { it.pendingDelete?.key == "SECRET_KEY" }
            confirmState.eventSink(VaultManagementEvents.DeleteConfirmed)

            val deletedState = awaitStateWhere { it.successMessage == "Vault entry deleted successfully" && it.items.isEmpty() && !it.isDeleting }
            assertThat(deletedKeys).containsExactly("SECRET_KEY")
            assertThat(listCalls).isEqualTo(2)

            deletedState.eventSink(VaultManagementEvents.ClearSuccess)
            val clearedState = awaitStateWhere { it.successMessage == null && it.items.isEmpty() }
            assertThat(clearedState.successMessage).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: VaultManagementNavigator = FakeVaultManagementNavigator(),
    ): VaultManagementPresenter {
        return VaultManagementPresenter(
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private class FakeVaultManagementNavigator : VaultManagementNavigator {
    override fun onAddEntry() = Unit
    override fun onEditEntry(item: ChatbotVaultItem) = Unit
}

private fun aVaultItem(
    id: String = "",
    key: String,
    description: String? = null,
): ChatbotVaultItem {
    return ChatbotVaultItem(
        id = id,
        key = key,
        description = description,
        createdAt = "2026-06-01T00:00:00Z",
    )
}

private suspend fun TurbineTestContext<VaultManagementState>.awaitStateWhere(
    predicate: (VaultManagementState) -> Boolean,
): VaultManagementState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}
