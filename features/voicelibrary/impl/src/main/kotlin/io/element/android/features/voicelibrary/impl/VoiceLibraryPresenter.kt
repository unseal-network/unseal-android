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
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.voices.ChatbotCreateVoiceProfileRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotCreateVoiceShareRequest
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

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
        var isLoading by remember { mutableStateOf(false) }
        var busyId by remember { mutableStateOf<String?>(null) }
        var deleteConfirmationProfileId by remember { mutableStateOf<String?>(null) }
        var lastShareId by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        fun errorMessage(throwable: Throwable, fallback: String): String {
            return throwable.message ?: throwable::class.simpleName ?: fallback
        }

        fun search() = searchQuery.trim().ifEmpty { null }

        fun loadProfiles() = coroutineScope.launch {
            isLoading = true
            api().listVoiceProfiles(provider = VOICE_PROVIDER, status = "available", search = search(), limit = PAGE_LIMIT, offset = null)
                .onSuccess {
                    profiles = it
                    error = null
                }
                .onFailure { error = errorMessage(it, "Failed to load voices") }
            isLoading = false
        }

        fun loadCatalog() = coroutineScope.launch {
            isLoading = true
            api().listProviderVoices(provider = VOICE_PROVIDER, availabilityStatus = "available", search = search(), limit = PAGE_LIMIT, offset = null)
                .onSuccess {
                    catalog = it
                    error = null
                }
                .onFailure { error = errorMessage(it, "Failed to load public voices") }
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
                .onFailure { error = errorMessage(it, "Failed to save voice") }
            busyId = null
        }

        fun deleteConfirmed() = coroutineScope.launch {
            val profileId = deleteConfirmationProfileId ?: return@launch
            busyId = profileId
            deleteConfirmationProfileId = null
            api().deleteVoiceProfile(profileId)
                .onSuccess {
                    profiles = profiles.filterNot { it.id == profileId }
                    error = null
                }
                .onFailure { error = errorMessage(it, "Failed to delete voice") }
            busyId = null
        }

        fun shareVoice(profileId: String) = coroutineScope.launch {
            busyId = profileId
            api().createVoiceShare(ChatbotCreateVoiceShareRequest(voiceProfileId = profileId))
                .onSuccess {
                    lastShareId = it.id
                    error = null
                }
                .onFailure { error = errorMessage(it, "Failed to create share link") }
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
                .onFailure { error = errorMessage(it, "Failed to import shared voice") }
            busyId = null
        }

        fun handleEvent(event: VoiceLibraryEvents) {
            when (event) {
                VoiceLibraryEvents.OnAppear -> if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    loadProfiles()
                }
                VoiceLibraryEvents.Refresh -> loadCurrentTab()
                is VoiceLibraryEvents.SelectTab -> {
                    selectedTab = event.tab
                    loadCurrentTab()
                }
                is VoiceLibraryEvents.SearchChanged -> {
                    searchQuery = event.query
                    loadCurrentTab()
                }
                is VoiceLibraryEvents.SaveVoice -> saveVoice(event.voice)
                is VoiceLibraryEvents.RequestDelete -> deleteConfirmationProfileId = event.profileId
                VoiceLibraryEvents.ConfirmDelete -> deleteConfirmed()
                VoiceLibraryEvents.CancelDelete -> deleteConfirmationProfileId = null
                is VoiceLibraryEvents.ShareVoice -> shareVoice(event.profileId)
                is VoiceLibraryEvents.ImportShareChanged -> importShareId = event.value
                VoiceLibraryEvents.ImportShare -> importShare()
                VoiceLibraryEvents.ClearError -> error = null
                VoiceLibraryEvents.ClearShareId -> lastShareId = null
                VoiceLibraryEvents.Dismiss -> navigator.onDone()
            }
        }

        return VoiceLibraryState(
            selectedTab = selectedTab,
            profiles = profiles.toImmutableList(),
            catalog = catalog.toImmutableList(),
            searchQuery = searchQuery,
            importShareId = importShareId,
            isLoading = isLoading,
            busyId = busyId,
            deleteConfirmationProfileId = deleteConfirmationProfileId,
            lastShareId = lastShareId,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}
