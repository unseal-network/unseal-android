/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.create

import androidx.compose.runtime.Immutable
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import kotlinx.collections.immutable.ImmutableList

/** A single editable file in the manual skill editor. Mirrors iOS `ManualSkillFile`. */
@Immutable
data class ManualSkillFile(
    val id: String,
    val path: String,
    val content: String,
)

@Immutable
data class SkillCreateFileConflict(
    val existingFileId: String,
    val incomingFile: ManualSkillFile,
)

/** Phase of the create flow. Mirrors iOS `SkillCreateScreenPhase`. */
@Immutable
sealed interface SkillCreatePhase {
    data object Editing : SkillCreatePhase
    data object Submitting : SkillCreatePhase
    data class Success(val id: String, val name: String) : SkillCreatePhase
}

@Immutable
data class SkillCreateState(
    val phase: SkillCreatePhase,
    val name: String,
    val description: String,
    val skillContent: String,
    val visibility: ChatbotSkillVisibility,
    val manualFiles: ImmutableList<ManualSkillFile>,
    /** File currently being edited in the bottom sheet, or null when the sheet is closed. */
    val editingFile: ManualSkillFile?,
    val pendingFileConflict: SkillCreateFileConflict?,
    val error: String?,
    val eventSink: (SkillCreateEvents) -> Unit,
) {
    val isSubmitting: Boolean = phase is SkillCreatePhase.Submitting
}
