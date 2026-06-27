/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

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
import io.element.android.features.skills.impl.R
import io.element.android.libraries.architecture.Presenter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AssistedInject
class SkillFileViewerPresenter(
    @Assisted private val fileName: String,
    @Assisted private val presignedUrl: String,
    @Assisted private val preuploadUrl: String?,
    private val client: SkillFileClient,
) : Presenter<SkillFileViewerState> {
    @AssistedFactory
    interface Factory {
        fun create(fileName: String, presignedUrl: String, preuploadUrl: String?): SkillFileViewerPresenter
    }

    @Composable
    override fun present(): SkillFileViewerState {
        val coroutineScope = rememberCoroutineScope()
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var content by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var loadError by remember { mutableStateOf<String?>(null) }
        var saveError by remember { mutableStateOf<String?>(null) }
        var showSavedToast by remember { mutableStateOf(false) }
        var loadRequestId by remember { mutableStateOf(0) }
        val loadFileError = stringResource(R.string.skill_file_load_failed)
        val saveFileError = stringResource(R.string.skill_file_save_failed_full)

        fun errorMessage(fallback: String): String = fallback

        fun load(force: Boolean = false) {
            if (hasLoadedOnce && !force) return
            val requestId = ++loadRequestId
            coroutineScope.launch {
                isLoading = true
                loadError = null
                val result = client.load(presignedUrl)
                if (requestId != loadRequestId) return@launch
                result
                    .onSuccess {
                        content = it
                        hasLoadedOnce = true
                    }
                    .onFailure {
                        loadError = errorMessage(loadFileError)
                    }
                isLoading = false
            }
        }

        fun save() {
            val uploadUrl = preuploadUrl ?: return
            coroutineScope.launch {
                isSaving = true
                saveError = null
                client.save(uploadUrl, fileName, content)
                    .onSuccess {
                        showSavedToast = true
                        load(force = true)
                        launch {
                            delay(2_000)
                            showSavedToast = false
                        }
                    }
                    .onFailure {
                        saveError = errorMessage(saveFileError)
                    }
                isSaving = false
            }
        }

        fun handleEvent(event: SkillFileViewerEvents) {
            when (event) {
                SkillFileViewerEvents.OnAppear -> load()
                SkillFileViewerEvents.RetryLoad -> load(force = true)
                is SkillFileViewerEvents.ContentChanged -> if (preuploadUrl != null) content = event.content
                SkillFileViewerEvents.Save -> save()
                SkillFileViewerEvents.ClearSaveError -> saveError = null
                SkillFileViewerEvents.HideSavedToast -> showSavedToast = false
            }
        }

        return SkillFileViewerState(
            fileName = fileName,
            isEditable = preuploadUrl != null,
            content = content,
            isLoading = isLoading,
            isSaving = isSaving,
            loadError = loadError,
            saveError = saveError,
            showSavedToast = showSavedToast,
            eventSink = ::handleEvent,
        )
    }
}
