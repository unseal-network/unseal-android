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
import io.element.android.features.messages.impl.timeline.components.receipt.aReadReceiptData
import io.element.android.features.messages.impl.timeline.model.TimelineItemReadReceipts
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.api.core.EventId
import kotlinx.collections.immutable.toImmutableList

/**
 * Verification preview for "receipts inline with the timestamp" on self media messages:
 * an uncaptioned image (overlay timestamp) and a captioned image (aligned timestamp), both
 * with read receipts. The receipt avatars should sit next to the timestamp, not on a separate
 * row below the bubble.
 */
@PreviewsDayNight
@Composable
internal fun TimelineItemEventRowMediaReceiptPreview() = ElementPreview {
    val receipts = TimelineItemReadReceipts(List(3) { aReadReceiptData(it) }.toImmutableList())
    Column {
        // Uncaptioned image from me -> overlay timestamp + inline receipt.
        ATimelineItemEventRow(
            event = aTimelineItemEvent(
                isMine = true,
                sendState = LocalEventSendState.Sent(EventId("\$eventId")),
                content = aTimelineItemImageContent(aspectRatio = 2.5f),
                timelineItemReactions = aTimelineItemReactions(count = 0),
                readReceiptState = receipts,
            ),
            renderReadReceipts = true,
            isLastOutgoingMessage = true,
        )
        // Captioned image from me -> aligned timestamp + inline receipt.
        ATimelineItemEventRow(
            event = aTimelineItemEvent(
                isMine = true,
                sendState = LocalEventSendState.Sent(EventId("\$eventId")),
                content = aTimelineItemImageContent(
                    aspectRatio = 2.5f,
                    filename = "image.jpg",
                    caption = "A captioned image from me.",
                ),
                timelineItemReactions = aTimelineItemReactions(count = 0),
                readReceiptState = receipts,
            ),
            renderReadReceipts = true,
            isLastOutgoingMessage = true,
        )
    }
}
