/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineRoomInfo
import io.element.android.features.messages.impl.timeline.model.event.aStaticLocationMode
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemLocationContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.protection.aTimelineProtectionState
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.tests.testutils.setSafeContent
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TimelineItemEventRowTest {
    @Test
    fun `location events let their content handle clicks`() {
        val event = aTimelineItemEvent(
            content = aTimelineItemLocationContent(mode = aStaticLocationMode())
        )

        assertThat(event.isWholeContentClickable).isFalse()
    }

    @Test
    fun `clicking an interactive standalone child does not trigger row click`() = runAndroidComposeUiTest<ComponentActivity> {
        var rowClicks = 0
        var childClicks = 0

        setSafeContent {
            TimelineItemEventRow(
                event = aTimelineItemEvent(content = aTimelineItemTextContent()),
                timelineMode = Timeline.Mode.Live,
                timelineRoomInfo = aTimelineRoomInfo(),
                timelineProtectionState = aTimelineProtectionState(),
                renderReadReceipts = false,
                isLastOutgoingMessage = false,
                displayThreadSummaries = false,
                onEventClick = { rowClicks++ },
                onLongClick = {},
                onLinkClick = {},
                onLinkLongClick = {},
                onUserDataClick = {},
                inReplyToClick = {},
                onReactionClick = { _, _ -> },
                onReactionLongClick = { _, _ -> },
                onMoreReactionsClick = {},
                onReadReceiptClick = {},
                onSwipeToReply = {},
                eventSink = {},
                eventContentView = { contentModifier ->
                    Box(
                        modifier = contentModifier
                            .size(64.dp)
                            .testTag(INTERACTIVE_CHILD_TAG)
                            .clickable { childClicks++ },
                    )
                },
            )
        }

        onNodeWithTag(INTERACTIVE_CHILD_TAG, useUnmergedTree = true).performTouchInput {
            down(center)
            up()
        }

        assertThat(childClicks).isEqualTo(1)
        assertThat(rowClicks).isEqualTo(0)
    }

    private companion object {
        const val INTERACTIVE_CHILD_TAG = "timeline-item-row-interactive-child"
    }
}
