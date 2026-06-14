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
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.AiStream)
        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.Hide)
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
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.PlainText)
        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.ShowWhenEdited)
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
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.PlainText)
        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.ShowWhenEdited)
        assertThat(model.showSenderInformation).isTrue()
        assertThat(model.reserveAvatarColumn).isTrue()
    }

    @Test
    fun `reduce renders own non-direct-room text with the same standalone leading layout`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemTextContent(),
            isMine = true,
            groupPosition = TimelineItemGroupPosition.None,
            isDirectRoom = false,
        )

        assertThat(model.alignment).isEqualTo(TimelineItemAlignment.Start)
        assertThat(model.bubblePolicy).isEqualTo(TimelineBubblePolicy.Standalone)
        assertThat(model.contentKind).isEqualTo(TimelineContentKind.PlainText)
        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.ShowWhenEdited)
        assertThat(model.showSenderInformation).isTrue()
        assertThat(model.reserveAvatarColumn).isTrue()
    }

    @Test
    fun `edited policy still shows edited for inline AI content without stream parts`() {
        val model = TimelinePresentationReducer.reduce(
            content = aTimelineItemAiContent(streamId = null),
            isMine = false,
            groupPosition = TimelineItemGroupPosition.None,
            isDirectRoom = true,
        )

        assertThat(model.editedPolicy).isEqualTo(TimelineEditedPolicy.ShowWhenEdited)
    }

    private fun aTimelineItemAiContent(
        streamId: String? = "stream-id",
    ): TimelineItemAiContent {
        return TimelineItemAiContent(
            body = "",
            isEdited = false,
            isStreaming = false,
            isTerminal = true,
            streamId = streamId,
            thinkingSteps = persistentListOf(),
            toolCalls = persistentListOf(),
            sources = persistentListOf(),
            quickActions = persistentListOf(),
        )
    }
}
