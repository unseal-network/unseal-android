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
    fun `reduce keeps regular direct-room text in a standard bubble without sender row`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemTextContent(),
            isMine = false,
            groupPosition = TimelineItemGroupPosition.None,
            isDirectRoom = true,
        )

        assertThat(model.alignment).isEqualTo(TimelineItemAlignment.Start)
        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.StandardBubble)
        assertThat(model.showSenderInformation).isFalse()
        assertThat(model.reserveAvatarColumn).isFalse()
    }

    @Test
    fun `reduce aligns own messages to the end`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemTextContent(),
            isMine = true,
            groupPosition = TimelineItemGroupPosition.None,
            isDirectRoom = false,
        )

        assertThat(model.alignment).isEqualTo(TimelineItemAlignment.End)
        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.StandardBubble)
        assertThat(model.showSenderInformation).isFalse()
        assertThat(model.reserveAvatarColumn).isFalse()
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
