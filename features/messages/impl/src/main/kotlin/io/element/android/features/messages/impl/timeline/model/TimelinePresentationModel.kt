/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model

import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRedactedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent

@Immutable
data class TimelinePresentationModel(
    val alignment: TimelineItemAlignment,
    val bubblePolicy: TimelineBubblePolicy,
    val contentKind: TimelineContentKind,
    val editedPolicy: TimelineEditedPolicy,
    val showSenderInformation: Boolean,
    val reserveAvatarColumn: Boolean,
) {
    val isStandalone: Boolean
        get() = bubblePolicy == TimelineBubblePolicy.Standalone
}

enum class TimelineItemAlignment {
    Start,
    End,
}

enum class TimelineBubblePolicy {
    StandardBubble,
    Standalone,
}

enum class TimelineContentKind {
    AiStream,
    PlainText,
    Redacted,
    RichEvent,
}

enum class TimelineEditedPolicy {
    ShowWhenEdited,
    Hide,
}

object TimelinePresentationReducer {
    fun reduce(
        content: TimelineItemEventContent,
        isMine: Boolean,
        groupPosition: TimelineItemGroupPosition,
        isDirectRoom: Boolean,
    ): TimelinePresentationModel {
        val contentKind = content.kind()
        val usesPlainTimelineStyle = contentKind == TimelineContentKind.AiStream ||
            contentKind == TimelineContentKind.PlainText ||
            contentKind == TimelineContentKind.Redacted
        val alignment = if (usesPlainTimelineStyle && isDirectRoom) {
            TimelineItemAlignment.Start
        } else if (isMine) {
            TimelineItemAlignment.End
        } else {
            TimelineItemAlignment.Start
        }
        val bubblePolicy = if (usesPlainTimelineStyle) {
            TimelineBubblePolicy.Standalone
        } else {
            TimelineBubblePolicy.StandardBubble
        }
        val editedPolicy = editedPolicy(content)
        val showSenderInformation = groupPosition.isNew() && (!isDirectRoom || usesPlainTimelineStyle || !isMine)
        val reserveAvatarColumn = !isDirectRoom || usesPlainTimelineStyle || !isMine
        return TimelinePresentationModel(
            alignment = alignment,
            bubblePolicy = bubblePolicy,
            contentKind = contentKind,
            editedPolicy = editedPolicy,
            showSenderInformation = showSenderInformation,
            reserveAvatarColumn = reserveAvatarColumn,
        )
    }

    fun editedPolicy(content: TimelineItemEventContent): TimelineEditedPolicy {
        val aiContent = content as? TimelineItemAiContent ?: return TimelineEditedPolicy.ShowWhenEdited
        return if (aiContent.streamId.isNullOrBlank() && !aiContent.hasRichParts) {
            TimelineEditedPolicy.ShowWhenEdited
        } else {
            TimelineEditedPolicy.Hide
        }
    }

    private fun TimelineItemEventContent.kind(): TimelineContentKind {
        return when (this) {
            is TimelineItemAiContent -> TimelineContentKind.AiStream
            is TimelineItemTextBasedContent -> TimelineContentKind.PlainText
            is TimelineItemRedactedContent -> TimelineContentKind.Redacted
            else -> TimelineContentKind.RichEvent
        }
    }
}
