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
import io.element.android.libraries.chatbot.api.model.voices.ChatbotUploadVoiceProfileRequest
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
            assertThat(after.deleteNotice).isEqualTo("Deleted voice.")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - delete notice includes cleared agent voice binding count`() = runTest {
        val service = FakeChatbotApiService().apply {
            listVoiceProfilesResult = { _, _, _, _, _ ->
                Result.success(listOf(aChatbotVoiceProfile(id = "p1")))
            }
            deleteVoiceProfileResult = {
                Result.success(
                    io.element.android.libraries.chatbot.api.model.voices.ChatbotDeleteVoiceProfileResponse(
                        deleted = true,
                        deletedAgentVoiceConfigs = 2,
                    )
                )
            }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.profiles.singleOrNull()?.id == "p1" }
            loaded.eventSink(VoiceLibraryEvents.RequestDelete("p1"))
            awaitStateWhere { it.deleteConfirmationProfileId == "p1" }.eventSink(VoiceLibraryEvents.ConfirmDelete)

            val deleted = awaitStateWhere { it.deleteNotice?.contains("2 agent voice bindings") == true }
            assertThat(deleted.deleteNotice).isEqualTo("Deleted voice and cleared 2 agent voice bindings.")
            deleted.eventSink(VoiceLibraryEvents.ClearDeleteNotice)
            assertThat(awaitStateWhere { it.deleteNotice == null }.deleteNotice).isNull()
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
    fun `event - import share trims id switches to mine and reloads profiles`() = runTest {
        val importedShares = mutableListOf<String>()
        var profileLoads = 0
        val service = FakeChatbotApiService().apply {
            listProviderVoicesResult = { _, _, _, _, _ -> Result.success(listOf(aChatbotProviderVoice(displayName = "Public Voice"))) }
            listVoiceProfilesResult = { _, _, _, _, _ ->
                profileLoads++
                Result.success(listOf(aChatbotVoiceProfile(id = "imported", displayName = "Imported Voice")))
            }
            importVoiceShareResult = {
                importedShares += it
                Result.success(aChatbotVoiceProfile(id = "imported"))
            }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.SelectTab(VoiceLibraryTab.Public))
            val publicState = awaitStateWhere { it.selectedTab == VoiceLibraryTab.Public && !it.isLoading }
            publicState.eventSink(VoiceLibraryEvents.ImportShareChanged("  share-123  "))
            awaitStateWhere { it.importShareId == "  share-123  " }.eventSink(VoiceLibraryEvents.ImportShare)

            val importedState = awaitStateWhere {
                it.selectedTab == VoiceLibraryTab.Mine &&
                    it.importShareId.isEmpty() &&
                    it.profiles.singleOrNull()?.displayName == "Imported Voice"
            }
            assertThat(importedState.busyId).isNull()
            assertThat(importedShares).containsExactly("share-123")
            assertThat(profileLoads).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - preview state is presenter-owned and mirrors iOS remote preview ids`() = runTest {
        val service = FakeChatbotApiService().apply {
            listVoiceProfilesResult = { _, _, _, _, _ ->
                Result.success(
                    listOf(
                        aChatbotVoiceProfile(id = "p1", displayName = "Aria")
                            .copy(previewUrl = "https://example.com/aria.mp3")
                    )
                )
            }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.profiles.singleOrNull()?.id == "p1" }
            val item = VoiceLibraryPreviewItem.fromProfile(loaded.profiles.single())
            loaded.eventSink(VoiceLibraryEvents.TogglePreview(item))

            val loading = awaitStateWhere { it.loadingPreviewId == item.id && it.previewTarget == item }
            assertThat(loading.remotePreviewId).isNull()
            loading.eventSink(VoiceLibraryEvents.PreviewPlaying(item.id))

            val playing = awaitStateWhere { it.remotePreviewId == item.id && it.loadingPreviewId == null }
            playing.eventSink(VoiceLibraryEvents.TogglePreview(item))
            val stopped = awaitStateWhere { it.previewTarget == null && it.remotePreviewId == null && it.loadingPreviewId == null }
            assertThat(stopped.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - preview without url exposes iOS no preview error`() = runTest {
        val presenter = createPresenter()

        presenter.test {
            val initial = awaitItem()
            initial.eventSink(
                VoiceLibraryEvents.TogglePreview(
                    VoiceLibraryPreviewItem(
                        id = "profile:p1",
                        title = "No Preview",
                        previewUrl = null,
                    )
                )
            )
            val failed = awaitStateWhere { it.error == "No preview audio is available for this voice." }
            assertThat(failed.previewTarget).isNull()
            assertThat(failed.loadingPreviewId).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - upload recording sends iOS clone request switches to mine and reloads`() = runTest {
        val uploads = mutableListOf<ChatbotUploadVoiceProfileRequest>()
        var profileLoads = 0
        val service = FakeChatbotApiService().apply {
            uploadVoiceProfileResult = {
                uploads += it
                Result.success(aChatbotVoiceProfile(id = "uploaded", displayName = it.displayName))
            }
            listVoiceProfilesResult = { _, _, _, _, _ ->
                profileLoads++
                Result.success(listOf(aChatbotVoiceProfile(id = "uploaded", displayName = "Recorded voice")))
            }
        }
        val presenter = createPresenter(service)

        presenter.test {
            val initial = awaitItem()
            initial.eventSink(VoiceLibraryEvents.SelectTab(VoiceLibraryTab.Public))
            val publicState = awaitStateWhere { it.selectedTab == VoiceLibraryTab.Public && !it.isLoading }
            publicState.eventSink(
                VoiceLibraryEvents.UploadRecording(
                    VoiceLibraryRecordingSample(
                        displayName = "  ",
                        description = null,
                        audioBase64 = "YWJj",
                    )
                )
            )

            val uploaded = awaitStateWhere {
                it.selectedTab == VoiceLibraryTab.Mine &&
                    it.busyId == null &&
                    it.profiles.singleOrNull()?.id == "uploaded"
            }
            assertThat(uploaded.error).isNull()
            assertThat(uploads).hasSize(1)
            assertThat(uploads.single().displayName).isEqualTo("Recorded voice")
            assertThat(uploads.single().audioBase64).isEqualTo("YWJj")
            assertThat(uploads.single().filename).isEqualTo("voice.m4a")
            assertThat(uploads.single().mimeType).isEqualTo("audio/m4a")
            assertThat(uploads.single().removeBackgroundNoise).isTrue()
            assertThat(profileLoads).isAtLeast(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - recording flow exposes iOS create voice state and can upload`() = runTest {
        val presenter = createPresenter()

        presenter.test {
            val initial = awaitItem()
            initial.eventSink(VoiceLibraryEvents.ShowCreateVoice)

            val presenting = awaitStateWhere { it.isPresentingCreateVoice && it.recordingName == "Recorded voice" }
            assertThat(presenting.recordingState).isEqualTo(VoiceLibraryRecordingState.Idle)
            assertThat(presenting.canUploadRecording).isFalse()

            presenting.eventSink(VoiceLibraryEvents.StartRecording)
            val starting = awaitStateWhere { it.isStartingRecording }
            assertThat(starting.recordingState).isEqualTo(VoiceLibraryRecordingState.Idle)

            starting.eventSink(VoiceLibraryEvents.RecordingStarted)
            val recording = awaitStateWhere { it.recordingState == VoiceLibraryRecordingState.Recording && !it.isStartingRecording }

            recording.eventSink(
                VoiceLibraryEvents.RecordingReady(
                    VoiceLibraryRecordingSample(
                        displayName = "",
                        audioBase64 = "YWJj",
                        durationMillis = 2_000,
                        fileSizeBytes = 32_000,
                    )
                )
            )
            val ready = awaitStateWhere { it.recordingState == VoiceLibraryRecordingState.Recorded && it.recordingSample != null }
            assertThat(ready.recordingValidationMessage).isNull()
            assertThat(ready.canUploadRecording).isTrue()

            ready.eventSink(VoiceLibraryEvents.DismissCreateVoice)
            val dismissed = awaitStateWhere { !it.isPresentingCreateVoice && it.recordingSample == null }
            assertThat(dismissed.recordingName).isEmpty()
            assertThat(dismissed.recordingState).isEqualTo(VoiceLibraryRecordingState.Idle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - recorded playback is presenter-owned and separate from remote preview`() = runTest {
        val presenter = createPresenter()

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.ShowCreateVoice)
            val presenting = awaitStateWhere { it.isPresentingCreateVoice }
            presenting.eventSink(
                VoiceLibraryEvents.RecordingReady(
                    VoiceLibraryRecordingSample(
                        displayName = "Recorded voice",
                        audioBase64 = "YWJj",
                        durationMillis = 2_000,
                        fileSizeBytes = 32_000,
                        localFilePath = "/tmp/voice.m4a",
                    )
                )
            )
            val ready = awaitStateWhere { it.recordingSample?.localFilePath == "/tmp/voice.m4a" }
            ready.eventSink(VoiceLibraryEvents.ToggleRecordingPreview)

            val playing = awaitStateWhere { it.isRecordingPreviewPlaying }
            assertThat(playing.remotePreviewId).isNull()
            assertThat(playing.loadingPreviewId).isNull()

            playing.eventSink(VoiceLibraryEvents.RecordingPreviewProgress(positionMillis = 500, durationMillis = 2_000))
            val progressed = awaitStateWhere { it.recordingPreviewPositionMillis == 500L && it.recordingPreviewProgress > 0f }
            assertThat(progressed.recordingPreviewProgress).isWithin(0.001f).of(0.25f)

            progressed.eventSink(VoiceLibraryEvents.RecordingPreviewScrubbing(true))
            val scrubbing = awaitStateWhere { it.isRecordingPreviewScrubbing }
            scrubbing.eventSink(VoiceLibraryEvents.SeekRecordingPreview(0.75f))
            val seeked = awaitStateWhere { it.recordingPreviewPositionMillis == 1_500L }
            seeked.eventSink(VoiceLibraryEvents.RecordingPreviewProgress(positionMillis = 600, durationMillis = 2_000))
            seeked.eventSink(VoiceLibraryEvents.RecordingPreviewScrubbing(false))
            val noLongerScrubbing = awaitStateWhere { !it.isRecordingPreviewScrubbing && it.recordingPreviewPositionMillis == 1_500L }
            assertThat(noLongerScrubbing.recordingPreviewProgress).isWithin(0.001f).of(0.75f)
            noLongerScrubbing.eventSink(VoiceLibraryEvents.RecordingPreviewProgress(positionMillis = 1_800, durationMillis = 2_000))
            val resumedProgress = awaitStateWhere { it.recordingPreviewPositionMillis == 1_800L && it.recordingPreviewProgress > 0.75f }
            assertThat(resumedProgress.recordingPreviewProgress).isWithin(0.001f).of(0.9f)

            resumedProgress.eventSink(VoiceLibraryEvents.RecordingPreviewStopped)
            val stopped = awaitStateWhere {
                !it.isRecordingPreviewPlaying &&
                    it.recordingSample != null &&
                    it.recordingPreviewProgress == 0f
            }
            assertThat(stopped.error).isNull()
            assertThat(stopped.recordingPreviewProgress).isEqualTo(0f)

            stopped.eventSink(VoiceLibraryEvents.RecordingPreviewFailed("Recording preview is not available. Please record again."))
            val failed = awaitStateWhere { it.error == "Recording preview is not available. Please record again." }
            assertThat(failed.isRecordingPreviewPlaying).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - recording validation blocks current recording upload`() = runTest {
        var uploadCalls = 0
        val service = FakeChatbotApiService().apply {
            uploadVoiceProfileResult = {
                uploadCalls++
                Result.success(aChatbotVoiceProfile())
            }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.ShowCreateVoice)
            val presenting = awaitStateWhere { it.isPresentingCreateVoice }
            presenting.eventSink(
                VoiceLibraryEvents.RecordingReady(
                    VoiceLibraryRecordingSample(
                        displayName = "Too large",
                        audioBase64 = "YWJj",
                        durationMillis = 1_000,
                        fileSizeBytes = VoiceCloneRecordingLimits.MaximumUploadBytes + 1,
                    )
                )
            )
            val invalid = awaitStateWhere { it.recordingValidationMessage == "Recording must be 25 MB or smaller." }
            assertThat(invalid.canUploadRecording).isFalse()
            invalid.eventSink(VoiceLibraryEvents.UploadCurrentRecording)

            val failed = awaitStateWhere { it.error == "Recording must be 25 MB or smaller." }
            assertThat(failed.busyId).isNull()
            assertThat(uploadCalls).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - upload current recording uses recording name and clears create voice state`() = runTest {
        val uploads = mutableListOf<ChatbotUploadVoiceProfileRequest>()
        val service = FakeChatbotApiService().apply {
            uploadVoiceProfileResult = {
                uploads += it
                Result.success(aChatbotVoiceProfile(id = "recording", displayName = it.displayName))
            }
            listVoiceProfilesResult = { _, _, _, _, _ ->
                Result.success(listOf(aChatbotVoiceProfile(id = "recording", displayName = "My Clone")))
            }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(VoiceLibraryEvents.ShowCreateVoice)
            val presenting = awaitStateWhere { it.isPresentingCreateVoice }
            presenting.eventSink(VoiceLibraryEvents.RecordingNameChanged("My Clone"))
            val named = awaitStateWhere { it.recordingName == "My Clone" }
            named.eventSink(
                VoiceLibraryEvents.RecordingReady(
                    VoiceLibraryRecordingSample(
                        displayName = "Ignored sample name",
                        audioBase64 = "YWJj",
                        durationMillis = 3_000,
                        fileSizeBytes = 42_000,
                    )
                )
            )
            val ready = awaitStateWhere { it.canUploadRecording }
            ready.eventSink(VoiceLibraryEvents.UploadCurrentRecording)

            val uploaded = awaitStateWhere {
                !it.isPresentingCreateVoice &&
                    it.recordingSample == null &&
                    it.profiles.singleOrNull()?.displayName == "My Clone"
            }
            assertThat(uploaded.recordingName).isEmpty()
            assertThat(uploads).hasSize(1)
            assertThat(uploads.single().displayName).isEqualTo("My Clone")
            assertThat(uploads.single().audioBase64).isEqualTo("YWJj")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - upload recording rejects empty audio before API call`() = runTest {
        var uploadCalls = 0
        val service = FakeChatbotApiService().apply {
            uploadVoiceProfileResult = {
                uploadCalls++
                Result.success(aChatbotVoiceProfile())
            }
        }
        val presenter = createPresenter(service)

        presenter.test {
            awaitItem().eventSink(
                VoiceLibraryEvents.UploadRecording(
                    VoiceLibraryRecordingSample(
                        displayName = "Voice",
                        audioBase64 = " ",
                    )
                )
            )
            val failed = awaitStateWhere { it.error == "Recording was empty. Please try again." }
            assertThat(failed.busyId).isNull()
            assertThat(uploadCalls).isEqualTo(0)
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
