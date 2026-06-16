/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility

sealed interface SkillDetailEvents {
    data object OnAppear : SkillDetailEvents
    data object Refresh : SkillDetailEvents
    data object StartEditing : SkillDetailEvents
    data object CancelEditing : SkillDetailEvents
    data class EditNameChanged(val name: String) : SkillDetailEvents
    data class EditDescriptionChanged(val description: String) : SkillDetailEvents
    data class EditVisibilityChanged(val visibility: ChatbotSkillVisibility) : SkillDetailEvents
    data object SaveEditing : SkillDetailEvents
    data class OpenFile(val file: SkillFileRenderModel) : SkillDetailEvents
    data object Delete : SkillDetailEvents
    data object ClearError : SkillDetailEvents
}
