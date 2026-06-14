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

object TimelinePresentationReducer {
    fun reduce(
        content: TimelineItemEventContent,
        isMine: Boolean,
        groupPosition: TimelineItemGroupPosition,
        isDirectRoom: Boolean,
    ): TimelinePresentationModel {
        val usesPlainTimelineStyle = content is TimelineItemAiContent ||
            content is TimelineItemTextBasedContent ||
            content is TimelineItemRedactedContent
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
        val showSenderInformation = groupPosition.isNew() && (!isDirectRoom || usesPlainTimelineStyle || !isMine)
        val reserveAvatarColumn = !isDirectRoom || usesPlainTimelineStyle || !isMine
        return TimelinePresentationModel(
            alignment = alignment,
            bubblePolicy = bubblePolicy,
            showSenderInformation = showSenderInformation,
            reserveAvatarColumn = reserveAvatarColumn,
        )
    }
}
