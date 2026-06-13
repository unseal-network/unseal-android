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
