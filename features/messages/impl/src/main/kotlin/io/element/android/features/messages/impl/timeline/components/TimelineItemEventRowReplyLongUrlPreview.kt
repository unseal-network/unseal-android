/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineItemReactions
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetails
import io.element.android.libraries.matrix.ui.messages.reply.aProfileDetailsReady

/**
 * Reproduction for the reply-misalignment bug: an incoming message that replies to a quoted
 * message whose body contains a long, unbreakable URL. The quoted preview's large minimum
 * intrinsic width is what triggers the layout overflow that shoves the whole message off its
 * avatar-column axis.
 */
private val aLongUrlReply: InReplyToDetails
    get() {
        val body = "在 https://play.google.com/console/developers/app/create 创建 Android 应用"
        return InReplyToDetails.Ready(
            eventId = EventId("\$repro-event"),
            senderId = UserId("@sender:domain"),
            senderProfile = aProfileDetailsReady(displayName = "Rayson"),
            eventContent = MessageContent(
                body = body,
                inReplyTo = null,
                isEdited = false,
                threadInfo = null,
                type = TextMessageType(body, null),
            ),
            textContent = body,
        )
    }

@PreviewsDayNight
@Composable
internal fun TimelineItemEventRowReplyLongUrlPreview() = ElementPreview {
    Column {
        sequenceOf(false, true).forEach { isMine ->
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    isMine = isMine,
                    timelineItemReactions = aTimelineItemReactions(count = 0),
                    content = aTimelineItemTextContent(body = "1"),
                    inReplyTo = aLongUrlReply,
                    groupPosition = TimelineItemGroupPosition.First,
                ),
            )
        }
    }
}

/**
 * Alignment check: a regular (non-reply) incoming message immediately followed by an incoming
 * reply message, both from the same sender. Their body text left edges must share the same
 * content column; the reply preview card must line up with that column too.
 */
@PreviewsDayNight
@Composable
internal fun TimelineItemEventRowReplyAlignmentPreview() = ElementPreview {
    Column {
        sequenceOf(false, true).forEach { isMine ->
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    isMine = isMine,
                    timelineItemReactions = aTimelineItemReactions(count = 0),
                    content = aTimelineItemTextContent(body = if (isMine) "Regular own message" else "Regular message body here"),
                    groupPosition = TimelineItemGroupPosition.First,
                ),
            )
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    isMine = isMine,
                    timelineItemReactions = aTimelineItemReactions(count = 0),
                    content = aTimelineItemTextContent(body = if (isMine) "Reply own message" else "Reply message body here"),
                    inReplyTo = aLongUrlReply,
                    groupPosition = TimelineItemGroupPosition.First,
                ),
            )
        }
    }
}
