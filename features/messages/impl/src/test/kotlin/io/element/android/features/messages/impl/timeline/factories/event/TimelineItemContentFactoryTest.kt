/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.fixtures.aTimelineItemContentFactory
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnknownContent
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimelineItemContentFactoryTest {
    @Test
    fun `create parses top level stream event before falling back to non-message content`() = runTest {
        val originalJson = """
            {
              "type": "m.stream.start",
              "content": {
                "msgtype": "m.stream.start",
                "stream_id": "stream-1",
                "body": "stream-1"
              }
            }
        """.trimIndent()
        val factory = aTimelineItemContentFactory()
        val event = anEventTimelineItem(
            content = UnknownContent,
            sender = A_USER_ID,
            debugInfoProvider = { aTimelineItemDebugInfo(originalJson = originalJson) },
        )

        val content = factory.create(event)

        assertThat(content).isInstanceOf(TimelineItemAiContent::class.java)
        val aiContent = content as TimelineItemAiContent
        assertThat(aiContent.streamId).isEqualTo("stream-1")
        assertThat(aiContent.sender).isEqualTo(A_USER_ID.value)
        assertThat(aiContent.isStreaming).isTrue()
    }
}
