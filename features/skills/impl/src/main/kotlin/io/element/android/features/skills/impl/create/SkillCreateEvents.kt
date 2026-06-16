/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.create

import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility

sealed interface SkillCreateEvents {
    data class NameChanged(val value: String) : SkillCreateEvents
    data class DescriptionChanged(val value: String) : SkillCreateEvents
    data class VisibilityChanged(val visibility: ChatbotSkillVisibility) : SkillCreateEvents

    data object AddFile : SkillCreateEvents
    data class DeleteFile(val id: String) : SkillCreateEvents
    data class StartEditingFile(val id: String) : SkillCreateEvents
    data object CancelEditingFile : SkillCreateEvents
    data class FileEdited(val id: String, val path: String, val content: String) : SkillCreateEvents
    data object DismissFileConflict : SkillCreateEvents
    data object KeepBothConflictingFile : SkillCreateEvents
    data object OverwriteConflictingFile : SkillCreateEvents

    /** A text file was picked via the system file picker (raw bytes + display name). */
    data class FilePicked(val fileName: String, val bytes: ByteArray) : SkillCreateEvents {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is FilePicked) return false
            return fileName == other.fileName && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
    }

    /** A zip archive was picked via the system file picker. */
    data class ZipPicked(val fileName: String, val bytes: ByteArray) : SkillCreateEvents {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ZipPicked) return false
            return fileName == other.fileName && bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int = 31 * fileName.hashCode() + bytes.contentHashCode()
    }

    data object Submit : SkillCreateEvents
    data object ViewDetail : SkillCreateEvents
    data object BackToList : SkillCreateEvents
    data object ClearError : SkillCreateEvents
}
