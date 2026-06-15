/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.vault.edit

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class VaultEditPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - edit mode loads value once on appear`() = runTest {
        val loadedKeys = mutableListOf<String>()
        val service = FakeChatbotApiService().apply {
            getVaultValueResult = {
                loadedKeys += it
                Result.success("secret-value")
            }
        }
        val presenter = createPresenter(
            mode = VaultEditMode.Edit(key = "API_KEY", description = "OpenAI token"),
            service = service,
        )

        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.isEditingExisting).isTrue()
            assertThat(initialState.key).isEqualTo("API_KEY")
            assertThat(initialState.description).isEqualTo("OpenAI token")
            assertThat(initialState.isLoadingValue).isTrue()

            initialState.eventSink(VaultEditEvents.OnAppear)
            val loadedState = awaitStateWhere { it.value == "secret-value" && !it.isLoadingValue }
            loadedState.eventSink(VaultEditEvents.OnAppear)

            assertThat(loadedKeys).containsExactly("API_KEY")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - edit mode saves updated value with key`() = runTest {
        val updates = mutableListOf<Triple<String, String, String?>>()
        val navigator = FakeVaultEditNavigator()
        val service = FakeChatbotApiService().apply {
            getVaultValueResult = { Result.success("old-value") }
            updateVaultEntryResult = { key, value, description ->
                updates += Triple(key, value, description)
                Result.success(Unit)
            }
        }
        val presenter = createPresenter(
            mode = VaultEditMode.Edit(key = "API_KEY", description = "Old desc"),
            service = service,
            navigator = navigator,
        )

        presenter.test {
            awaitItem().eventSink(VaultEditEvents.OnAppear)
            val loadedState = awaitStateWhere { it.value == "old-value" && !it.isLoadingValue }
            loadedState.eventSink(VaultEditEvents.ValueChanged("new-value"))
            awaitStateWhere { it.value == "new-value" }.eventSink(VaultEditEvents.DescriptionChanged(""))
            awaitStateWhere { it.description.isEmpty() }.eventSink(VaultEditEvents.Save)
            awaitStateWhere { navigator.completed }

            assertThat(updates).containsExactly(Triple("API_KEY", "new-value", null))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - create mode creates entry and completes`() = runTest {
        val creates = mutableListOf<Triple<String, String, String?>>()
        val navigator = FakeVaultEditNavigator()
        val service = FakeChatbotApiService().apply {
            createVaultEntryResult = { key, value, description ->
                creates += Triple(key, value, description)
                Result.success(Unit)
            }
        }
        val presenter = createPresenter(
            mode = VaultEditMode.Create,
            service = service,
            navigator = navigator,
        )

        presenter.test {
            awaitItem().eventSink(VaultEditEvents.KeyChanged("API_KEY"))
            awaitStateWhere { it.key == "API_KEY" }.eventSink(VaultEditEvents.ValueChanged("secret"))
            awaitStateWhere { it.value == "secret" }.eventSink(VaultEditEvents.DescriptionChanged("desc"))
            awaitStateWhere { it.description == "desc" }.eventSink(VaultEditEvents.Save)
            awaitStateWhere { navigator.completed }

            assertThat(creates).containsExactly(Triple("API_KEY", "secret", "desc"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - save validates empty key and value before calling api`() = runTest {
        var createCalls = 0
        val service = FakeChatbotApiService().apply {
            createVaultEntryResult = { _, _, _ ->
                createCalls++
                Result.success(Unit)
            }
        }
        val presenter = createPresenter(mode = VaultEditMode.Create, service = service)

        presenter.test {
            val initialState = awaitItem()
            initialState.eventSink(VaultEditEvents.Save)
            val emptyKeyState = awaitStateWhere { it.error == "Key不能为空。" }
            emptyKeyState.eventSink(VaultEditEvents.KeyChanged("API_KEY"))
            awaitStateWhere { it.key == "API_KEY" }.eventSink(VaultEditEvents.Save)
            val emptyValueState = awaitStateWhere { it.error == "Value不能为空。" }

            assertThat(emptyValueState.isSaving).isFalse()
            assertThat(createCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        mode: VaultEditMode,
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: FakeVaultEditNavigator = FakeVaultEditNavigator(),
    ): VaultEditPresenter {
        return VaultEditPresenter(
            mode = mode,
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private class FakeVaultEditNavigator : VaultEditNavigator {
    var completed: Boolean = false
        private set

    override fun onComplete() {
        completed = true
    }
}

private suspend fun TurbineTestContext<VaultEditState>.awaitStateWhere(
    predicate: (VaultEditState) -> Boolean,
): VaultEditState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}
