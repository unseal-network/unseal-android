/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

data class VoiceLibraryState(
    val selectedTab: VoiceLibraryTab,
    val profiles: ImmutableList<ChatbotVoiceProfile>,
    val catalog: ImmutableList<ChatbotProviderVoice>,
    val searchQuery: String,
    val importShareId: String,
    val isPresentingCreateVoice: Boolean,
    val recordingName: String,
    val recordingValidationMessage: String?,
    val isStartingRecording: Boolean,
    val recordingState: VoiceLibraryRecordingState,
    val recordingSample: VoiceLibraryRecordingSample?,
    val isRecordingPreviewPlaying: Boolean,
    val isRecordingPreviewScrubbing: Boolean,
    val recordingPreviewProgress: Float,
    val recordingPreviewPositionMillis: Long,
    val isLoading: Boolean,
    val busyId: String?,
    val deleteConfirmationProfileId: String?,
    val lastShareId: String?,
    val deleteNotice: String?,
    val previewTarget: VoiceLibraryPreviewItem?,
    val loadingPreviewId: String?,
    val remotePreviewId: String?,
    val error: String?,
    val eventSink: (VoiceLibraryEvents) -> Unit,
) {
    /**
     * The user's saved voices, filtered client-side by [searchQuery]. Mirrors iOS `filteredProfiles`:
     * matches against displayName/description/provider/providerVoiceId/sourceType. No API call.
     */
    val filteredProfiles: ImmutableList<ChatbotVoiceProfile>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return profiles
            return profiles.filter { profile ->
                listOfNotNull(
                    profile.displayName,
                    profile.description,
                    profile.provider,
                    profile.providerVoiceId,
                    profile.sourceType,
                ).any { it.contains(query, ignoreCase = true) }
            }.toImmutableList()
        }

    /**
     * The provider catalog, filtered client-side by [searchQuery]. Mirrors iOS `filteredCatalog`:
     * matches against displayName/description/provider/providerVoiceId. No API call.
     */
    val filteredCatalog: ImmutableList<ChatbotProviderVoice>
        get() {
            val query = searchQuery.trim()
            if (query.isEmpty()) return catalog
            return catalog.filter { voice ->
                listOfNotNull(
                    voice.displayName,
                    voice.description,
                    voice.provider,
                    voice.providerVoiceId,
                ).any { it.contains(query, ignoreCase = true) }
            }.toImmutableList()
    }

    val canUploadRecording: Boolean
        get() = recordingSample != null &&
            recordingValidationMessage == null &&
            busyId != VoiceLibraryBusyIds.RecordingUpload &&
            !isStartingRecording &&
            recordingName.trim().isNotEmpty()
}

enum class VoiceLibraryRecordingState {
    Idle,
    Recording,
    Recorded,
}

object VoiceLibraryBusyIds {
    const val RecordingUpload = "recording-upload"
}

data class VoiceLibraryPreviewItem(
    val id: String,
    val title: String,
    val previewUrl: String?,
) {
    val hasPreview: Boolean = !previewUrl.isNullOrBlank()

    companion object {
        fun fromProfile(profile: ChatbotVoiceProfile) = VoiceLibraryPreviewItem(
            id = "profile:${profile.id}",
            title = profile.displayName,
            previewUrl = profile.previewUrl,
        )

        fun fromProviderVoice(voice: ChatbotProviderVoice) = VoiceLibraryPreviewItem(
            id = "catalog:${voice.provider}:${voice.providerVoiceId}",
            title = voice.displayName,
            previewUrl = voice.previewUrl,
        )
    }
}

data class VoiceLibraryRecordingSample(
    val displayName: String,
    val description: String? = null,
    val audioBase64: String,
    val filename: String = "voice.m4a",
    val mimeType: String = "audio/m4a",
    val removeBackgroundNoise: Boolean = true,
    val durationMillis: Long = 0,
    val fileSizeBytes: Long = 0,
    val localFilePath: String? = null,
    val waveform: ImmutableList<Float> = persistentListOf(),
) {
    val normalizedDisplayName: String = displayName.trim().ifEmpty { "Recorded voice" }
}

object VoiceCloneRecordingLimits {
    const val MaximumUploadBytes: Long = 25_000_000
    const val MaximumDurationMillis: Long = 10 * 60 * 1000

    fun validationMessage(fileSizeBytes: Long, durationMillis: Long): String? {
        if (fileSizeBytes <= 0 || durationMillis <= 0) {
            return "Recording was empty. Please try again."
        }
        if (fileSizeBytes > MaximumUploadBytes) {
            return "Recording must be 25 MB or smaller."
        }
        if (durationMillis > MaximumDurationMillis) {
            return "Recording must be 10 minutes or shorter."
        }
        return null
    }
}
