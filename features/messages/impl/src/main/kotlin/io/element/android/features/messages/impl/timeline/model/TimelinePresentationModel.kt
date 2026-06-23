/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model

import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEncryptedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRedactedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStickerContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent

@Immutable
data class TimelinePresentationModel(
    val alignment: TimelineItemAlignment,
    val bubblePolicy: TimelineBubblePolicy,
    val contentKind: TimelineContentKind,
    val avatarPolicy: TimelineAvatarPolicy,
    val senderLabelPolicy: TimelineSenderLabelPolicy,
    val timestampPolicy: TimelineTimestampPolicy,
    val editedPolicy: TimelineEditedPolicy,
    val replySwipePolicy: TimelineReplySwipePolicy,
    val contentWidthPolicy: TimelineContentWidthPolicy,
    val rightGutterPolicy: TimelineRightGutterPolicy,
    val supplementaryPolicy: TimelineSupplementaryPolicy,
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
    RoomKeyRecovery,
    Redacted,
    /** Full-bleed media (uncaptioned image/video/sticker) — rendered without a bubble card or notch. */
    Media,
    RichEvent,
}

enum class TimelineAvatarPolicy {
    Show,
    ReserveSpace,
    Hidden,
}

enum class TimelineSenderLabelPolicy {
    Show,
    Hide,
}

enum class TimelineTimestampPolicy {
    ContentManaged,
    Below,
    Hidden,
}

enum class TimelineEditedPolicy {
    ShowWhenEdited,
    Hide,
}

enum class TimelineReplySwipePolicy {
    Enabled,
    Disabled,
}

enum class TimelineContentWidthPolicy {
    StandardBubble,
    StandaloneAdaptive,
}

enum class TimelineRightGutterPolicy {
    Standard,
    Standalone,
}

enum class TimelineSupplementaryPolicy {
    None,
    Decorated,
}

object TimelinePresentationReducer {
    fun reduce(
        content: TimelineItemEventContent,
        isMine: Boolean,
        groupPosition: TimelineItemGroupPosition,
        isDirectRoom: Boolean,
        hasReply: Boolean = false,
        hasReactions: Boolean = false,
        isPinned: Boolean = false,
        hasThreadSummary: Boolean = false,
    ): TimelinePresentationModel {
        val contentKind = content.kind()
        val usesPlainTimelineStyle = contentKind == TimelineContentKind.AiStream ||
            contentKind == TimelineContentKind.PlainText ||
            contentKind == TimelineContentKind.RoomKeyRecovery ||
            contentKind == TimelineContentKind.Redacted
        // AiStream and RoomKeyRecovery are always rendered on the incoming (left) side regardless of
        // ownership. PlainText and Redacted follow the normal isMine alignment.
        val alwaysIncoming = contentKind == TimelineContentKind.AiStream ||
            contentKind == TimelineContentKind.RoomKeyRecovery
        val alignment = when {
            alwaysIncoming -> TimelineItemAlignment.Start
            isMine -> TimelineItemAlignment.End
            else -> TimelineItemAlignment.Start
        }
        // Captioned image/video render bubble-less too (image + caption stacked, iOS-style); the
        // bubble looked out of place now that the rest of the timeline is bubble-free. They keep the
        // RichEvent content kind so swipe-to-reply and timestamp handling are unchanged.
        val isCaptionedMedia = when (content) {
            is TimelineItemImageContent -> content.caption != null || content.formattedCaption != null
            is TimelineItemVideoContent -> content.caption != null || content.formattedCaption != null
            else -> false
        }
        // Plain-style content, full-bleed media and captioned media all render without a bubble (no
        // card/notch); media keeps its normal alignment though (only plain style forces Start).
        val bubblePolicy = if (usesPlainTimelineStyle || contentKind == TimelineContentKind.Media || isCaptionedMedia) {
            TimelineBubblePolicy.Standalone
        } else {
            TimelineBubblePolicy.StandardBubble
        }
        val editedPolicy = editedPolicy(content)
        val replySwipePolicy = replySwipePolicy(contentKind)
        val showSenderInformation = groupPosition.isNew() && (!isDirectRoom || alwaysIncoming || !isMine)
        val reserveAvatarColumn = !isDirectRoom || alwaysIncoming || !isMine
        val avatarPolicy = when {
            showSenderInformation -> TimelineAvatarPolicy.Show
            reserveAvatarColumn -> TimelineAvatarPolicy.ReserveSpace
            else -> TimelineAvatarPolicy.Hidden
        }
        val senderLabelPolicy = if (showSenderInformation) {
            TimelineSenderLabelPolicy.Show
        } else {
            TimelineSenderLabelPolicy.Hide
        }
        val timestampPolicy = when (contentKind) {
            TimelineContentKind.AiStream -> TimelineTimestampPolicy.Hidden
            TimelineContentKind.Media,
            TimelineContentKind.RichEvent -> TimelineTimestampPolicy.ContentManaged
            TimelineContentKind.PlainText,
            TimelineContentKind.RoomKeyRecovery,
            TimelineContentKind.Redacted -> TimelineTimestampPolicy.Below
        }
        val contentWidthPolicy = if (bubblePolicy == TimelineBubblePolicy.Standalone) {
            TimelineContentWidthPolicy.StandaloneAdaptive
        } else {
            TimelineContentWidthPolicy.StandardBubble
        }
        val rightGutterPolicy = if (bubblePolicy == TimelineBubblePolicy.Standalone) {
            TimelineRightGutterPolicy.Standalone
        } else {
            TimelineRightGutterPolicy.Standard
        }
        val supplementaryPolicy = if (hasReply || hasReactions || isPinned || hasThreadSummary) {
            TimelineSupplementaryPolicy.Decorated
        } else {
            TimelineSupplementaryPolicy.None
        }
        return TimelinePresentationModel(
            alignment = alignment,
            bubblePolicy = bubblePolicy,
            contentKind = contentKind,
            avatarPolicy = avatarPolicy,
            senderLabelPolicy = senderLabelPolicy,
            timestampPolicy = timestampPolicy,
            editedPolicy = editedPolicy,
            replySwipePolicy = replySwipePolicy,
            contentWidthPolicy = contentWidthPolicy,
            rightGutterPolicy = rightGutterPolicy,
            supplementaryPolicy = supplementaryPolicy,
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

    private fun replySwipePolicy(contentKind: TimelineContentKind): TimelineReplySwipePolicy {
        return when (contentKind) {
            TimelineContentKind.RichEvent -> TimelineReplySwipePolicy.Enabled
            TimelineContentKind.PlainText,
            TimelineContentKind.AiStream,
            TimelineContentKind.RoomKeyRecovery,
            TimelineContentKind.Redacted,
            TimelineContentKind.Media -> TimelineReplySwipePolicy.Disabled
        }
    }

    private fun TimelineItemEventContent.kind(): TimelineContentKind {
        return when (this) {
            is TimelineItemAiContent -> TimelineContentKind.AiStream
            is TimelineItemEncryptedContent -> if (recovery != null) TimelineContentKind.RoomKeyRecovery else TimelineContentKind.RichEvent
            is TimelineItemTextBasedContent -> TimelineContentKind.PlainText
            is TimelineItemRedactedContent -> TimelineContentKind.Redacted
            // Uncaptioned media renders full-bleed (no bubble card / corner notch). Captioned media
            // keeps the bubble so the caption has a background.
            is TimelineItemImageContent -> if (caption == null && formattedCaption == null) TimelineContentKind.Media else TimelineContentKind.RichEvent
            is TimelineItemVideoContent -> if (caption == null && formattedCaption == null) TimelineContentKind.Media else TimelineContentKind.RichEvent
            is TimelineItemStickerContent -> TimelineContentKind.Media
            else -> TimelineContentKind.RichEvent
        }
    }
}
