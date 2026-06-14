/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class TimelinePresentationReducerTest {
    @Test
    fun `reduce renders AI stream as standalone content with sender in direct room`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemAiContent(),
            isMine = false,
            groupPosition = TimelineItemGroupPosition.None,
            isDirectRoom = true,
        )

        assertThat(model.alignment).isEqualTo(TimelineItemAlignment.Start)
        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.showSenderInformation).isTrue()
        assertThat(model.reserveAvatarColumn).isTrue()
    }

    @Test
    fun `reduce renders regular direct-room text as standalone content with sender row`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemTextContent(),
            isMine = false,
            groupPosition = TimelineItemGroupPosition.None,
            isDirectRoom = true,
        )

        assertThat(model.alignment).isEqualTo(TimelineItemAlignment.Start)
        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.showSenderInformation).isTrue()
        assertThat(model.reserveAvatarColumn).isTrue()
    }

    @Test
    fun `reduce renders own direct-room text with the same standalone leading layout`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemTextContent(),
            isMine = true,
            groupPosition = TimelineItemGroupPosition.None,
            isDirectRoom = true,
        )

        assertThat(model.alignment).isEqualTo(TimelineItemAlignment.Start)
        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.showSenderInformation).isTrue()
        assertThat(model.reserveAvatarColumn).isTrue()
    }

    @Test
    fun `reduce keeps own non-direct-room text aligned to the end while using iOS plain style`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemTextContent(),
            isMine = true,
            groupPosition = TimelineItemGroupPosition.None,
            isDirectRoom = false,
        )

        assertThat(model.alignment).isEqualTo(TimelineItemAlignment.End)
        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.showSenderInformation).isTrue()
        assertThat(model.reserveAvatarColumn).isTrue()
    }

    private fun aTimelineItemAiContent(): TimelineItemAiContent {
        return TimelineItemAiContent(
            body = "",
            isEdited = false,
            isStreaming = false,
            isTerminal = true,
            thinkingSteps = persistentListOf(),
            toolCalls = persistentListOf(),
            sources = persistentListOf(),
            quickActions = persistentListOf(),
        )
    }
}
