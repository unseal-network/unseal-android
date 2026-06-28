/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class TimelineItemAiViewLogicTest {
    @Test
    fun `stream placeholder body is not rendered as markdown fallback`() {
        assertThat(aiContent(body = "thinking", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
        assertThat(aiContent(body = "Thinking...", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
        assertThat(aiContent(body = "Thinking…", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
        assertThat(aiContent(body = "loading", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
        assertThat(aiContent(body = "stream-1", streamId = "stream-1").shouldRenderBodyFallback()).isFalse()
    }

    @Test
    fun `real stream body is rendered as markdown fallback before parts arrive`() {
        assertThat(aiContent(body = "Let me check Gmail.", streamId = "stream-1").shouldRenderBodyFallback()).isTrue()
    }

    @Test
    fun `non stream body still renders as markdown fallback`() {
        assertThat(aiContent(body = "thinking", streamId = null).shouldRenderBodyFallback()).isTrue()
    }

    @Test
    fun `duplicate tool card payload text is detected without hiding summaries`() {
        val jsonPayload = """
            {
              "cards": [
                { "hotel_name": "Upper House Chengdu", "rating": 4.8 }
              ],
              "component": "HotelBookingCard"
            }
        """.trimIndent()
        val fencedPayload = """
            ```json
            $jsonPayload
            ```
        """.trimIndent()

        assertThat(jsonPayload.looksLikeDuplicateToolCardPayload()).isTrue()
        assertThat(fencedPayload.looksLikeDuplicateToolCardPayload()).isTrue()
        assertThat("Here are the current market quotes for Apple and NVIDIA.".looksLikeDuplicateToolCardPayload()).isFalse()
    }

    @Test
    fun `streaming reveal advances in small adaptive steps`() {
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 12, totalTextUnits = 120)).isEqualTo(1)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 60, totalTextUnits = 120)).isEqualTo(2)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 180, totalTextUnits = 240)).isEqualTo(4)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 400, totalTextUnits = 600)).isEqualTo(8)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 900, totalTextUnits = 1_000)).isEqualTo(16)
        assertThat(streamingRevealCodePointStep(remainingTextUnits = 1_800, totalTextUnits = 2_000)).isEqualTo(32)
    }

    @Test
    fun `streaming reveal caps frame rate for long markdown`() {
        assertThat(streamingRevealFrameDelayMs(120)).isEqualTo(28L)
        assertThat(streamingRevealFrameDelayMs(800)).isEqualTo(40L)
        assertThat(streamingRevealFrameDelayMs(1_600)).isEqualTo(56L)
    }

    @Test
    fun `streaming reveal does not split surrogate pairs`() {
        val text = "A\uD83E\uDD16B"

        val first = nextStreamingRevealEndIndex(currentEndIndex = 0, text = text)
        val second = nextStreamingRevealEndIndex(currentEndIndex = first, text = text)

        assertThat(text.substring(0, first)).isEqualTo("A")
        assertThat(text.substring(0, second)).isEqualTo("A\uD83E\uDD16")
    }

    @Test
    fun `streaming reveal catches up large snapshots without showing the whole paragraph at once`() {
        val text = "x".repeat(320)

        val nextIndex = nextStreamingRevealEndIndex(currentEndIndex = 0, text = text)

        assertThat(nextIndex).isEqualTo(8)
    }

    @Test
    fun `streaming reveal catches up very large snapshots in bounded chunks`() {
        val text = "x".repeat(2_000)

        val nextIndex = nextStreamingRevealEndIndex(currentEndIndex = 0, text = text)

        assertThat(nextIndex).isEqualTo(32)
    }

    private fun aiContent(body: String, streamId: String?): TimelineItemAiContent {
        return TimelineItemAiContent(
            body = body,
            isEdited = false,
            isStreaming = true,
            streamId = streamId,
            thinkingSteps = persistentListOf(),
            toolCalls = persistentListOf(),
            sources = persistentListOf(),
            quickActions = persistentListOf(),
        )
    }
}
