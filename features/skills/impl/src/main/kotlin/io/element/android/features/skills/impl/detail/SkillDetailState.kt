/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

import io.element.android.features.skills.impl.shared.displayName
import io.element.android.libraries.chatbot.api.model.skills.ChatbotGetUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility

data class SkillDetailState(
    val id: String,
    val isOwner: Boolean,
    val response: ChatbotGetUserSkillResponse?,
    val isLoading: Boolean,
    val isSaving: Boolean,
    val isDeleting: Boolean,
    val isEditing: Boolean,
    val canApplyMetadataFilters: Boolean,
    val editName: String,
    val editDescription: String,
    val editVisibility: ChatbotSkillVisibility,
    val error: String?,
    val eventSink: (SkillDetailEvents) -> Unit,
) {
    val skill = response?.skill
    val title: String = skill?.name?.takeIf { it.isNotBlank() } ?: id
    val visibilityLabel: String? = skill?.visibility?.displayName()
    val canEdit: Boolean = isOwner && skill != null && !isLoading && !isSaving && !isDeleting
    val fileItems: List<SkillFileRenderModel> = buildSkillFileRenderModels(
        skillId = id,
        presignedUrls = response?.presignedUrls.orEmpty(),
        preuploadUrls = response?.preuploadUrls.orEmpty(),
    )
}
