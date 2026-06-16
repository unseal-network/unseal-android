/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

data class SkillFileViewerState(
    val fileName: String,
    val isEditable: Boolean,
    val content: String,
    val isLoading: Boolean,
    val isSaving: Boolean,
    val loadError: String?,
    val saveError: String?,
    val showSavedToast: Boolean,
    val eventSink: (SkillFileViewerEvents) -> Unit,
) {
    val canSave: Boolean = isEditable && !isLoading && !isSaving && loadError == null
}
