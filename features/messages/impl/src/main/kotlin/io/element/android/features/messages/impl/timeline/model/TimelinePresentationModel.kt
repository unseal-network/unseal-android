/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model

import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAudioContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEncryptedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemFileContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGameContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemLocationContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRedactedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRtcNotificationContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStickerContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVoiceContent

@Immutable
data class TimelinePresentationModel(
    val bubblePolicy: TimelineBubblePolicy,
    val contentKind: TimelineContentKind,
    val contentPaddingPolicy: TimelineContentPaddingPolicy,
    val editedPolicy: TimelineEditedPolicy,
    val showSenderInformation: Boolean,
) {
    val isStandalone: Boolean
        get() = bubblePolicy == TimelineBubblePolicy.Standalone
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

enum class TimelineEditedPolicy {
    ShowWhenEdited,
    Hide,
}

enum class TimelineContentPaddingPolicy {
    Textual,
    Media,
    CaptionedMedia,
}

object TimelinePresentationReducer {
    fun reduce(
        content: TimelineItemEventContent,
        groupPosition: TimelineItemGroupPosition,
    ): TimelinePresentationModel {
        val contentKind = content.kind()
        val usesPlainTimelineStyle = contentKind == TimelineContentKind.AiStream ||
            contentKind == TimelineContentKind.PlainText ||
            contentKind == TimelineContentKind.RoomKeyRecovery ||
            contentKind == TimelineContentKind.Redacted
        // Captioned image/video render bubble-less too (image + caption stacked, iOS-style), but keep
        // a caption padding policy so text does not touch the media edge.
        val isCaptionedMedia = when (content) {
            is TimelineItemImageContent -> content.caption != null || content.formattedCaption != null
            is TimelineItemVideoContent -> content.caption != null || content.formattedCaption != null
            else -> false
        }
        // Plain-style content, full-bleed media and captioned media all render without a bubble.
        val bubblePolicy = if (usesPlainTimelineStyle || contentKind == TimelineContentKind.Media || isCaptionedMedia) {
            TimelineBubblePolicy.Standalone
        } else {
            TimelineBubblePolicy.StandardBubble
        }
        val contentPaddingPolicy = contentPaddingPolicy(contentKind, isCaptionedMedia)
        val editedPolicy = editedPolicy(content)
        val showSenderInformation = groupPosition.isNew()
        return TimelinePresentationModel(
            bubblePolicy = bubblePolicy,
            contentKind = contentKind,
            contentPaddingPolicy = contentPaddingPolicy,
            editedPolicy = editedPolicy,
            showSenderInformation = showSenderInformation,
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

    private fun contentPaddingPolicy(
        contentKind: TimelineContentKind,
        isCaptionedMedia: Boolean,
    ): TimelineContentPaddingPolicy {
        return when {
            isCaptionedMedia -> TimelineContentPaddingPolicy.CaptionedMedia
            contentKind == TimelineContentKind.Media ||
                contentKind == TimelineContentKind.RoomKeyRecovery ||
                contentKind == TimelineContentKind.AiStream -> TimelineContentPaddingPolicy.Media
            else -> TimelineContentPaddingPolicy.Textual
        }
    }

    private fun TimelineItemEventContent.kind(): TimelineContentKind {
        return when (this) {
            is TimelineItemAiContent -> TimelineContentKind.AiStream
            is TimelineItemEncryptedContent -> if (recovery != null) TimelineContentKind.RoomKeyRecovery else TimelineContentKind.RichEvent
            is TimelineItemTextBasedContent -> TimelineContentKind.PlainText
            is TimelineItemStateContent -> TimelineContentKind.PlainText
            is TimelineItemRedactedContent -> TimelineContentKind.Redacted
            // Uncaptioned media renders full-bleed. Captioned media is classified as rich content so
            // the reducer can give it the caption-specific padding policy above.
            is TimelineItemImageContent -> if (caption == null && formattedCaption == null) TimelineContentKind.Media else TimelineContentKind.RichEvent
            is TimelineItemVideoContent -> if (caption == null && formattedCaption == null) TimelineContentKind.Media else TimelineContentKind.RichEvent
            is TimelineItemStickerContent -> TimelineContentKind.Media
            is TimelineItemAudioContent,
            is TimelineItemFileContent,
            is TimelineItemLocationContent,
            is TimelineItemVoiceContent,
            is TimelineItemRtcNotificationContent,
            is TimelineItemGameContent -> TimelineContentKind.Media
            else -> TimelineContentKind.RichEvent
        }
    }
}
