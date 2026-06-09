/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.voices.ChatbotCreateVoiceProfileRequest
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aChatbotProviderVoice
import io.element.android.libraries.chatbot.test.aChatbotVoiceProfile
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class VoiceLibraryPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads profiles once on appear`() = runTest {
        var calls = 0
        val service = FakeChatbotApiService().apply {
            listVoiceProfilesResult = { _, _, _, _, _ ->
                calls++
                Result.success(listOf(aChatbotVoiceProfile(id = "p1", displayName = "Alpha")))
            }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.profiles.size == 1 }
            assertThat(loaded.profiles.single().displayName).isEqualTo("Alpha")
            assertThat(calls).isEqualTo(1)
            loaded.eventSink(VoiceLibraryEvents.OnAppear)
            assertThat(calls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - select public tab loads catalog`() = runTest {
        val service = FakeChatbotApiService().apply {
            listProviderVoicesResult = { _, _, _, _, _ -> Result.success(listOf(aChatbotProviderVoice(displayName = "Catalog A"))) }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading }
            loaded.eventSink(VoiceLibraryEvents.SelectTab(VoiceLibraryTab.Public))
            val publicState = awaitStateWhere { !it.isLoading && it.selectedTab == VoiceLibraryTab.Public && it.catalog.size == 1 }
            assertThat(publicState.catalog.single().displayName).isEqualTo("Catalog A")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - save voice creates profile and reloads`() = runTest {
        val created = mutableListOf<ChatbotCreateVoiceProfileRequest>()
        val service = FakeChatbotApiService().apply {
            createVoiceProfileResult = {
                created += it
                Result.success(aChatbotVoiceProfile(displayName = it.displayName))
            }
            listVoiceProfilesResult = { _, _, _, _, _ -> Result.success(listOf(aChatbotVoiceProfile(id = "saved", displayName = "Saved Voice"))) }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading }
            loaded.eventSink(VoiceLibraryEvents.SaveVoice(aChatbotProviderVoice(providerVoiceId = "v9", displayName = "Cool Voice")))
            awaitStateWhere { it.busyId == null && created.isNotEmpty() }
            assertThat(created.single().providerVoiceId).isEqualTo("v9")
            assertThat(created.single().displayName).isEqualTo("Cool Voice")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - delete requires confirmation then removes profile`() = runTest {
        val deleted = mutableListOf<String>()
        val service = FakeChatbotApiService().apply {
            listVoiceProfilesResult = { _, _, _, _, _ ->
                Result.success(listOf(aChatbotVoiceProfile(id = "p1"), aChatbotVoiceProfile(id = "p2")))
            }
            deleteVoiceProfileResult = {
                deleted += it
                Result.success(io.element.android.libraries.chatbot.api.model.voices.ChatbotDeleteVoiceProfileResponse(deleted = true))
            }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.profiles.size == 2 }
            loaded.eventSink(VoiceLibraryEvents.RequestDelete("p1"))
            val confirming = awaitStateWhere { it.deleteConfirmationProfileId == "p1" }
            assertThat(deleted).isEmpty()
            confirming.eventSink(VoiceLibraryEvents.ConfirmDelete)
            val after = awaitStateWhere { it.busyId == null && it.profiles.size == 1 }
            assertThat(deleted).containsExactly("p1")
            assertThat(after.profiles.single().id).isEqualTo("p2")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - share voice exposes share id`() = runTest {
        val service = FakeChatbotApiService().apply {
            listVoiceProfilesResult = { _, _, _, _, _ -> Result.success(listOf(aChatbotVoiceProfile(id = "p1"))) }
            createVoiceShareResult = { Result.success(io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceShare(id = "share-xyz", voiceProfileId = it.voiceProfileId)) }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.profiles.isNotEmpty() }
            loaded.eventSink(VoiceLibraryEvents.ShareVoice("p1"))
            val shared = awaitStateWhere { it.lastShareId == "share-xyz" }
            assertThat(shared.lastShareId).isEqualTo("share-xyz")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - load failure exposes error`() = runTest {
        val service = FakeChatbotApiService().apply {
            listVoiceProfilesResult = { _, _, _, _, _ -> Result.failure(RuntimeException("network")) }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.OnAppear)
            val failed = awaitStateWhere { !it.isLoading && it.error?.contains("network") == true }
            assertThat(failed.profiles).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        navigator: FakeVoiceLibraryNavigator = FakeVoiceLibraryNavigator(),
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
    ): VoiceLibraryPresenter {
        return VoiceLibraryPresenter(
            navigator = navigator,
            matrixClient = matrixClient,
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }
}

private class FakeVoiceLibraryNavigator : VoiceLibraryNavigator {
    var doneCalls = 0
    override fun onDone() {
        doneCalls++
    }
}

private suspend fun TurbineTestContext<VoiceLibraryState>.awaitStateWhere(
    predicate: (VoiceLibraryState) -> Boolean,
): VoiceLibraryState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}
