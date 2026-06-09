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

data class VoiceLibraryState(
    val selectedTab: VoiceLibraryTab,
    val profiles: ImmutableList<ChatbotVoiceProfile>,
    val catalog: ImmutableList<ChatbotProviderVoice>,
    val searchQuery: String,
    val importShareId: String,
    val isLoading: Boolean,
    val busyId: String?,
    val deleteConfirmationProfileId: String?,
    val lastShareId: String?,
    val error: String?,
    val eventSink: (VoiceLibraryEvents) -> Unit,
)
