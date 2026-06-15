/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice

sealed interface VoiceLibraryEvents {
    data object OnAppear : VoiceLibraryEvents
    data object Refresh : VoiceLibraryEvents
    data class SelectTab(val tab: VoiceLibraryTab) : VoiceLibraryEvents
    data class SearchChanged(val query: String) : VoiceLibraryEvents
    data class SaveVoice(val voice: ChatbotProviderVoice) : VoiceLibraryEvents
    data class RequestDelete(val profileId: String) : VoiceLibraryEvents
    data object ConfirmDelete : VoiceLibraryEvents
    data object CancelDelete : VoiceLibraryEvents
    data class ShareVoice(val profileId: String) : VoiceLibraryEvents
    data class ImportShareChanged(val value: String) : VoiceLibraryEvents
    data object ImportShare : VoiceLibraryEvents
    data object ShowCreateVoice : VoiceLibraryEvents
    data object DismissCreateVoice : VoiceLibraryEvents
    data class RecordingNameChanged(val value: String) : VoiceLibraryEvents
    data object StartRecording : VoiceLibraryEvents
    data object RecordingStarted : VoiceLibraryEvents
    data class RecordingReady(val sample: VoiceLibraryRecordingSample) : VoiceLibraryEvents
    data class RecordingFailed(val reason: String) : VoiceLibraryEvents
    data object ToggleRecordingPreview : VoiceLibraryEvents
    data object RecordingPreviewPlaying : VoiceLibraryEvents
    data object RecordingPreviewStopped : VoiceLibraryEvents
    data class RecordingPreviewFailed(val reason: String) : VoiceLibraryEvents
    data class RecordingPreviewProgress(val positionMillis: Long, val durationMillis: Long) : VoiceLibraryEvents
    data class SeekRecordingPreview(val progress: Float) : VoiceLibraryEvents
    data class RecordingPreviewScrubbing(val isScrubbing: Boolean) : VoiceLibraryEvents
    data object CancelRecording : VoiceLibraryEvents
    data object DiscardRecording : VoiceLibraryEvents
    data object UploadCurrentRecording : VoiceLibraryEvents
    data object ClearError : VoiceLibraryEvents
    data object ClearShareId : VoiceLibraryEvents
    data object ClearDeleteNotice : VoiceLibraryEvents
    data class TogglePreview(val item: VoiceLibraryPreviewItem) : VoiceLibraryEvents
    data class PreviewPlaying(val itemId: String) : VoiceLibraryEvents
    data object PreviewStopped : VoiceLibraryEvents
    data class PreviewFailed(val itemId: String, val reason: String) : VoiceLibraryEvents
    data class UploadRecording(val sample: VoiceLibraryRecordingSample) : VoiceLibraryEvents
    data object Dismiss : VoiceLibraryEvents
}
