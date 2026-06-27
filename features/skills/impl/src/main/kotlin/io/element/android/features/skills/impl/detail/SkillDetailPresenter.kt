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
import io.element.android.features.skills.impl.shared.apiValue
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.skills.ChatbotGetUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@AssistedInject
class SkillDetailPresenter(
    @Assisted private val id: String,
    @Assisted private val isOwner: Boolean,
    @Assisted private val canApplyMetadataFilters: Boolean,
    @Assisted private val navigator: SkillDetailNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<SkillDetailState> {
    @AssistedFactory
    interface Factory {
        fun create(
            id: String,
            isOwner: Boolean,
            canApplyMetadataFilters: Boolean,
            navigator: SkillDetailNavigator,
        ): SkillDetailPresenter
    }

    @Composable
    override fun present(): SkillDetailState {
        val coroutineScope = rememberCoroutineScope()
        var response by remember { mutableStateOf<ChatbotGetUserSkillResponse?>(null) }
        var isLoading by remember { mutableStateOf(false) }
        var isSaving by remember { mutableStateOf(false) }
        var isDeleting by remember { mutableStateOf(false) }
        var isEditing by remember { mutableStateOf(false) }
        var editName by remember { mutableStateOf("") }
        var editDescription by remember { mutableStateOf("") }
        var editVisibility by remember { mutableStateOf(ChatbotSkillVisibility.Private) }
        var error by remember { mutableStateOf<String?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var loadRequestId by remember { mutableStateOf(0) }
        val loadError = stringResource(R.string.skill_detail_error_load)
        val updateError = stringResource(R.string.skill_detail_error_update)
        val deleteError = stringResource(R.string.skill_detail_error_delete)

        suspend fun api() = chatbotApiServiceFactory.createForHomeserver(matrixClient)

        fun failureMessage(fallback: String): String = fallback

        fun load(isInitial: Boolean) {
            if (isInitial && hasLoadedOnce) return
            val requestId = ++loadRequestId
            coroutineScope.launch {
                isLoading = true
                val result = api().getUserSkill(id)
                if (requestId != loadRequestId) return@launch
                result
                    .onSuccess {
                        response = it
                        error = null
                    }
                    .onFailure { error = failureMessage(loadError) }
                isLoading = false
                hasLoadedOnce = true
            }
        }

        fun startEditing() {
            if (!isOwner) return
            val skill = response?.skill ?: return
            editName = skill.name
            editDescription = skill.description.orEmpty()
            editVisibility = skill.visibility ?: ChatbotSkillVisibility.Private
            isEditing = true
        }

        fun saveEditing() {
            if (!isOwner) return
            coroutineScope.launch {
                isSaving = true
                val body = buildJsonObject {
                    put("name", JsonPrimitive(editName))
                    put("description", JsonPrimitive(editDescription))
                    put("visibility", JsonPrimitive(editVisibility.apiValue()))
                }
                api().updateUserSkill(id, body)
                    .onSuccess { result ->
                        result.skill?.let { response = ChatbotGetUserSkillResponse(skill = it) }
                        isEditing = false
                        error = null
                    }
                    .onFailure { error = failureMessage(updateError) }
                isSaving = false
            }
        }

        fun deleteSkill() {
            if (!isOwner) return
            coroutineScope.launch {
                isDeleting = true
                api().deleteUserSkill(id)
                    .onSuccess {
                        error = null
                        navigator.onDeleted(id)
                    }
                    .onFailure { error = failureMessage(deleteError) }
                isDeleting = false
            }
        }

        fun handleEvent(event: SkillDetailEvents) {
            when (event) {
                SkillDetailEvents.OnAppear -> load(isInitial = true)
                SkillDetailEvents.Refresh -> load(isInitial = false)
                SkillDetailEvents.StartEditing -> startEditing()
                SkillDetailEvents.CancelEditing -> isEditing = false
                is SkillDetailEvents.EditNameChanged -> editName = event.name
                is SkillDetailEvents.EditDescriptionChanged -> editDescription = event.description
                is SkillDetailEvents.EditVisibilityChanged -> editVisibility = event.visibility
                SkillDetailEvents.SaveEditing -> saveEditing()
                is SkillDetailEvents.OpenFile -> navigator.onOpenFile(event.file)
                is SkillDetailEvents.ApplyFilterToken -> if (canApplyMetadataFilters) navigator.onApplyMetadataFilter(event.token)
                SkillDetailEvents.Delete -> deleteSkill()
                SkillDetailEvents.ClearError -> error = null
            }
        }

        return SkillDetailState(
            id = id,
            isOwner = isOwner,
            response = response,
            isLoading = isLoading,
            isSaving = isSaving,
            isDeleting = isDeleting,
            isEditing = isEditing,
            canApplyMetadataFilters = canApplyMetadataFilters,
            editName = editName,
            editDescription = editDescription,
            editVisibility = editVisibility,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}
