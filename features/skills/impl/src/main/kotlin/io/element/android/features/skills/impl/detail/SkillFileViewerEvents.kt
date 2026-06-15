/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

sealed interface SkillFileViewerEvents {
    data object OnAppear : SkillFileViewerEvents
    data object RetryLoad : SkillFileViewerEvents
    data class ContentChanged(val content: String) : SkillFileViewerEvents
    data object Save : SkillFileViewerEvents
    data object ClearSaveError : SkillFileViewerEvents
    data object HideSavedToast : SkillFileViewerEvents
}
