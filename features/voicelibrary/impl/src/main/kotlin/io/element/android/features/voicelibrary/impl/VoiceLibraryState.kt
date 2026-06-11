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
import kotlinx.collections.immutable.toImmutableList

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
}
