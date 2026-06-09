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
    data object ClearError : VoiceLibraryEvents
    data object ClearShareId : VoiceLibraryEvents
    data object Dismiss : VoiceLibraryEvents
}
