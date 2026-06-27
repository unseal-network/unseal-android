/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

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
import io.element.android.features.voicelibrary.impl.R
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.voices.ChatbotCreateVoiceProfileRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotCreateVoiceShareRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotUploadVoiceProfileRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.io.File

private const val VOICE_PROVIDER = "elevenlabs"
private const val PAGE_LIMIT = 100

@AssistedInject
class VoiceLibraryPresenter(
    @Assisted private val navigator: VoiceLibraryNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<VoiceLibraryState> {
    @AssistedFactory
    interface Factory {
        fun create(navigator: VoiceLibraryNavigator): VoiceLibraryPresenter
    }

    @Composable
    override fun present(): VoiceLibraryState {
        val coroutineScope = rememberCoroutineScope()
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(VoiceLibraryTab.Mine) }
        var profiles by remember { mutableStateOf(emptyList<ChatbotVoiceProfile>()) }
        var catalog by remember { mutableStateOf(emptyList<ChatbotProviderVoice>()) }
        var searchQuery by remember { mutableStateOf("") }
        var importShareId by remember { mutableStateOf("") }
        var isPresentingCreateVoice by remember { mutableStateOf(false) }
        var recordingName by remember { mutableStateOf("") }
        var recordingValidationMessage by remember { mutableStateOf<String?>(null) }
        var isStartingRecording by remember { mutableStateOf(false) }
        var recordingState by remember { mutableStateOf(VoiceLibraryRecordingState.Idle) }
        var recordingSample by remember { mutableStateOf<VoiceLibraryRecordingSample?>(null) }
        var isRecordingPreviewPlaying by remember { mutableStateOf(false) }
        var isRecordingPreviewScrubbing by remember { mutableStateOf(false) }
        var recordingPreviewPositionMillis by remember { mutableStateOf(0L) }
        var recordingPreviewProgress by remember { mutableStateOf(0f) }
        var isLoading by remember { mutableStateOf(false) }
        var busyId by remember { mutableStateOf<String?>(null) }
        var deleteConfirmationProfileId by remember { mutableStateOf<String?>(null) }
        var lastShareId by remember { mutableStateOf<String?>(null) }
        var deleteNotice by remember { mutableStateOf<String?>(null) }
        var previewTarget by remember { mutableStateOf<VoiceLibraryPreviewItem?>(null) }
        var loadingPreviewId by remember { mutableStateOf<String?>(null) }
        var remotePreviewId by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }
        val loadError = stringResource(R.string.voice_library_error_load)
        val loadPublicError = stringResource(R.string.voice_library_error_load_public)
        val saveError = stringResource(R.string.voice_library_error_save)
        val deleteError = stringResource(R.string.voice_library_error_delete)
        val shareError = stringResource(R.string.voice_library_error_share)
        val importError = stringResource(R.string.voice_library_error_import)
        val uploadError = stringResource(R.string.voice_library_error_upload)

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        fun errorMessage(throwable: Throwable, fallback: String): String {
            return throwable.message ?: throwable::class.simpleName ?: fallback
        }

        fun loadProfiles() = coroutineScope.launch {
            isLoading = true
            // Search is applied client-side (see VoiceLibraryState.filteredProfiles), mirroring iOS
            // which passes search = nil and filters the already-loaded list in memory.
            api().listVoiceProfiles(provider = VOICE_PROVIDER, status = "available", search = null, limit = PAGE_LIMIT, offset = null)
                .onSuccess {
                    profiles = it
                    error = null
                }
                .onFailure { error = errorMessage(it, loadError) }
            isLoading = false
        }

        fun loadCatalog() = coroutineScope.launch {
            isLoading = true
            // Search is applied client-side (see VoiceLibraryState.filteredCatalog), mirroring iOS.
            api().listProviderVoices(provider = VOICE_PROVIDER, availabilityStatus = "available", search = null, limit = PAGE_LIMIT, offset = null)
                .onSuccess {
                    catalog = it
                    error = null
                }
                .onFailure { error = errorMessage(it, loadPublicError) }
            isLoading = false
        }

        fun loadCurrentTab() = when (selectedTab) {
            VoiceLibraryTab.Mine -> loadProfiles()
            VoiceLibraryTab.Public -> loadCatalog()
        }

        fun saveVoice(voice: ChatbotProviderVoice) = coroutineScope.launch {
            busyId = voice.providerVoiceId
            api().createVoiceProfile(
                ChatbotCreateVoiceProfileRequest(
                    provider = voice.provider,
                    providerVoiceId = voice.providerVoiceId,
                    displayName = voice.displayName,
                    description = voice.description,
                    previewUrl = voice.previewUrl,
                )
            )
                .onSuccess {
                    error = null
                    loadProfiles()
                }
                .onFailure { error = errorMessage(it, saveError) }
            busyId = null
        }

        fun deleteConfirmed() = coroutineScope.launch {
            val profileId = deleteConfirmationProfileId ?: return@launch
            busyId = profileId
            deleteConfirmationProfileId = null
            api().deleteVoiceProfile(profileId)
                .onSuccess { response ->
                    profiles = profiles.filterNot { it.id == profileId }
                    deleteNotice = if (response.deletedAgentVoiceConfigs > 0) {
                        val suffix = if (response.deletedAgentVoiceConfigs == 1) "" else "s"
                        "Deleted voice and cleared ${response.deletedAgentVoiceConfigs} agent voice binding$suffix."
                    } else {
                        "Deleted voice."
                    }
                    error = null
                }
                .onFailure { error = errorMessage(it, deleteError) }
            busyId = null
        }

        fun shareVoice(profileId: String) = coroutineScope.launch {
            busyId = profileId
            deleteNotice = null
            api().createVoiceShare(ChatbotCreateVoiceShareRequest(voiceProfileId = profileId))
                .onSuccess {
                    lastShareId = it.id
                    error = null
                }
                .onFailure { error = errorMessage(it, shareError) }
            busyId = null
        }

        fun importShare() = coroutineScope.launch {
            val shareId = importShareId.trim().ifEmpty { return@launch }
            busyId = "import"
            api().importVoiceShare(shareId)
                .onSuccess {
                    importShareId = ""
                    selectedTab = VoiceLibraryTab.Mine
                    error = null
                    loadProfiles()
                }
                .onFailure { error = errorMessage(it, importError) }
            busyId = null
        }

        fun clearRecording() {
            recordingSample?.localFilePath
                ?.let(::File)
                ?.takeIf { it.exists() }
                ?.delete()
            recordingValidationMessage = null
            isStartingRecording = false
            recordingState = VoiceLibraryRecordingState.Idle
            isRecordingPreviewPlaying = false
            isRecordingPreviewScrubbing = false
            recordingPreviewPositionMillis = 0L
            recordingPreviewProgress = 0f
            recordingSample = null
        }

        fun uploadRecording(sample: VoiceLibraryRecordingSample) = coroutineScope.launch {
            if (sample.audioBase64.isBlank()) {
                error = "Recording was empty. Please try again."
                return@launch
            }
            val namedSample = sample.copy(displayName = recordingName.ifBlank { sample.displayName })
            busyId = VoiceLibraryBusyIds.RecordingUpload
            deleteNotice = null
            lastShareId = null
            api().uploadVoiceProfile(
                ChatbotUploadVoiceProfileRequest(
                    displayName = namedSample.normalizedDisplayName,
                    description = namedSample.description,
                    audioBase64 = namedSample.audioBase64,
                    filename = namedSample.filename,
                    mimeType = namedSample.mimeType,
                    removeBackgroundNoise = namedSample.removeBackgroundNoise,
                )
            )
                .onSuccess {
                    selectedTab = VoiceLibraryTab.Mine
                    searchQuery = ""
                    isPresentingCreateVoice = false
                    recordingName = ""
                    clearRecording()
                    error = null
                    loadProfiles()
                }
                .onFailure { error = errorMessage(it, uploadError) }
            busyId = null
        }

        fun stopPreview() {
            previewTarget = null
            loadingPreviewId = null
            remotePreviewId = null
        }

        fun handleEvent(event: VoiceLibraryEvents) {
            when (event) {
                VoiceLibraryEvents.OnAppear -> if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    loadProfiles()
                }
                VoiceLibraryEvents.Refresh -> loadCurrentTab()
                is VoiceLibraryEvents.SelectTab -> if (selectedTab != event.tab) {
                    selectedTab = event.tab
                    // iOS selectTab resets the search query and only loads when the target tab is empty.
                    searchQuery = ""
                    val needsLoad = when (event.tab) {
                        VoiceLibraryTab.Mine -> profiles.isEmpty()
                        VoiceLibraryTab.Public -> catalog.isEmpty()
                    }
                    if (needsLoad) loadCurrentTab()
                }
                is VoiceLibraryEvents.SearchChanged -> {
                    // Filtering happens client-side in VoiceLibraryState; no API call, no loading spinner.
                    searchQuery = event.query
                }
                is VoiceLibraryEvents.SaveVoice -> saveVoice(event.voice)
                is VoiceLibraryEvents.RequestDelete -> deleteConfirmationProfileId = event.profileId
                VoiceLibraryEvents.ConfirmDelete -> deleteConfirmed()
                VoiceLibraryEvents.CancelDelete -> deleteConfirmationProfileId = null
                is VoiceLibraryEvents.ShareVoice -> shareVoice(event.profileId)
                is VoiceLibraryEvents.ImportShareChanged -> importShareId = event.value
                VoiceLibraryEvents.ImportShare -> importShare()
                VoiceLibraryEvents.ShowCreateVoice -> {
                    isPresentingCreateVoice = true
                    if (recordingName.isBlank()) {
                        recordingName = "Recorded voice"
                    }
                    error = null
                }
                VoiceLibraryEvents.DismissCreateVoice -> {
                    isPresentingCreateVoice = false
                    recordingName = ""
                    clearRecording()
                }
                is VoiceLibraryEvents.RecordingNameChanged -> {
                    recordingName = event.value
                    error = null
                }
                VoiceLibraryEvents.StartRecording -> {
                    recordingSample?.localFilePath
                        ?.let(::File)
                        ?.takeIf { it.exists() }
                        ?.delete()
                    isStartingRecording = true
                    recordingValidationMessage = null
                    recordingState = VoiceLibraryRecordingState.Idle
                    isRecordingPreviewPlaying = false
                    isRecordingPreviewScrubbing = false
                    recordingPreviewPositionMillis = 0L
                    recordingPreviewProgress = 0f
                    recordingSample = null
                    error = null
                }
                VoiceLibraryEvents.RecordingStarted -> {
                    isStartingRecording = false
                    recordingState = VoiceLibraryRecordingState.Recording
                    error = null
                }
                is VoiceLibraryEvents.RecordingReady -> {
                    isStartingRecording = false
                    recordingState = VoiceLibraryRecordingState.Recorded
                    recordingSample = event.sample
                    isRecordingPreviewPlaying = false
                    isRecordingPreviewScrubbing = false
                    recordingPreviewPositionMillis = 0L
                    recordingPreviewProgress = 0f
                    recordingValidationMessage = VoiceCloneRecordingLimits.validationMessage(
                        fileSizeBytes = event.sample.fileSizeBytes,
                        durationMillis = event.sample.durationMillis,
                    )
                    error = null
                }
                is VoiceLibraryEvents.RecordingFailed -> {
                    isStartingRecording = false
                    recordingState = VoiceLibraryRecordingState.Idle
                    recordingSample = null
                    isRecordingPreviewPlaying = false
                    isRecordingPreviewScrubbing = false
                    recordingPreviewPositionMillis = 0L
                    recordingPreviewProgress = 0f
                    recordingValidationMessage = event.reason
                    error = event.reason
                }
                VoiceLibraryEvents.ToggleRecordingPreview -> {
                    val sample = recordingSample
                    when {
                        isRecordingPreviewPlaying -> isRecordingPreviewPlaying = false
                        sample?.localFilePath.isNullOrBlank() -> {
                            error = "Recording preview is not available. Please record again."
                        }
                        else -> {
                            isRecordingPreviewPlaying = true
                            error = null
                        }
                    }
                }
                VoiceLibraryEvents.RecordingPreviewPlaying -> {
                    isRecordingPreviewPlaying = true
                    error = null
                }
                VoiceLibraryEvents.RecordingPreviewStopped -> {
                    isRecordingPreviewPlaying = false
                    isRecordingPreviewScrubbing = false
                    recordingPreviewPositionMillis = 0L
                    recordingPreviewProgress = 0f
                }
                is VoiceLibraryEvents.RecordingPreviewFailed -> {
                    isRecordingPreviewPlaying = false
                    isRecordingPreviewScrubbing = false
                    error = event.reason
                }
                is VoiceLibraryEvents.RecordingPreviewProgress -> if (!isRecordingPreviewScrubbing) {
                    val duration = event.durationMillis.takeIf { it > 0 } ?: recordingSample?.durationMillis ?: 0
                    recordingPreviewPositionMillis = event.positionMillis.coerceIn(0, duration.coerceAtLeast(0))
                    recordingPreviewProgress = if (duration > 0) {
                        (recordingPreviewPositionMillis.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                }
                is VoiceLibraryEvents.SeekRecordingPreview -> {
                    val duration = recordingSample?.durationMillis ?: 0
                    recordingPreviewProgress = event.progress.coerceIn(0f, 1f)
                    recordingPreviewPositionMillis = (duration * recordingPreviewProgress).toLong()
                }
                is VoiceLibraryEvents.RecordingPreviewScrubbing -> {
                    isRecordingPreviewScrubbing = event.isScrubbing
                }
                VoiceLibraryEvents.CancelRecording,
                VoiceLibraryEvents.DiscardRecording -> clearRecording()
                VoiceLibraryEvents.UploadCurrentRecording -> {
                    val sample = recordingSample
                    when {
                        sample == null -> {
                            recordingValidationMessage = "Recording was empty. Please try again."
                            error = recordingValidationMessage
                        }
                        recordingValidationMessage != null -> {
                            error = recordingValidationMessage
                        }
                        recordingName.trim().isEmpty() -> {
                            error = "Enter a name to enable upload"
                        }
                        else -> uploadRecording(sample)
                    }
                }
                VoiceLibraryEvents.ClearError -> error = null
                VoiceLibraryEvents.ClearShareId -> lastShareId = null
                VoiceLibraryEvents.ClearDeleteNotice -> deleteNotice = null
                is VoiceLibraryEvents.TogglePreview -> {
                    val item = event.item
                    if (!item.hasPreview) {
                        error = "No preview audio is available for this voice."
                    } else if (loadingPreviewId == item.id || remotePreviewId == item.id) {
                        stopPreview()
                    } else {
                        previewTarget = item
                        loadingPreviewId = item.id
                        remotePreviewId = null
                        error = null
                    }
                }
                is VoiceLibraryEvents.PreviewPlaying -> if (previewTarget?.id == event.itemId) {
                    loadingPreviewId = null
                    remotePreviewId = event.itemId
                    error = null
                }
                VoiceLibraryEvents.PreviewStopped -> stopPreview()
                is VoiceLibraryEvents.PreviewFailed -> if (previewTarget?.id == event.itemId || loadingPreviewId == event.itemId) {
                    stopPreview()
                    error = event.reason
                }
                is VoiceLibraryEvents.UploadRecording -> uploadRecording(event.sample)
                VoiceLibraryEvents.Dismiss -> navigator.onDone()
            }
        }

        return VoiceLibraryState(
            selectedTab = selectedTab,
            profiles = profiles.toImmutableList(),
            catalog = catalog.toImmutableList(),
            searchQuery = searchQuery,
            importShareId = importShareId,
            isPresentingCreateVoice = isPresentingCreateVoice,
            recordingName = recordingName,
            recordingValidationMessage = recordingValidationMessage,
            isStartingRecording = isStartingRecording,
            recordingState = recordingState,
            recordingSample = recordingSample,
            isRecordingPreviewPlaying = isRecordingPreviewPlaying,
            isRecordingPreviewScrubbing = isRecordingPreviewScrubbing,
            recordingPreviewProgress = recordingPreviewProgress,
            recordingPreviewPositionMillis = recordingPreviewPositionMillis,
            isLoading = isLoading,
            busyId = busyId,
            deleteConfirmationProfileId = deleteConfirmationProfileId,
            lastShareId = lastShareId,
            deleteNotice = deleteNotice,
            previewTarget = previewTarget,
            loadingPreviewId = loadingPreviewId,
            remotePreviewId = remotePreviewId,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}
