/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.fixtures.aTimelineItemsFactory
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamHandleStore
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.StreamHandle
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamStorageProvider
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.OtherState
import io.element.android.libraries.matrix.api.timeline.item.event.StateContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnknownContent
import io.element.android.libraries.matrix.test.A_UNIQUE_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimelineItemsFactoryTest {
    @Test
    fun `replaceWith keeps cached event instance when member update cannot affect rendering`() = runTest {
        val factory = aTimelineItemsFactory(
            config = TimelineItemsFactoryConfig(
                computeReadReceipts = true,
                computeReactions = true,
                roomId = "!room:keepsecret.io",
            )
        )
        val event = MatrixTimelineItem.Event(
            A_UNIQUE_ID,
            anEventTimelineItem(
                sender = A_USER_ID,
            )
        )

        factory.replaceWith(listOf(event), roomMembers = emptyList())
        val initialEvent = factory.timelineItems.first().single() as TimelineItem.Event

        factory.replaceWith(listOf(event), roomMembers = listOf(aRoomMember(userId = A_USER_ID)))
        val updatedEvent = factory.timelineItems.first().single() as TimelineItem.Event

        assertThat(updatedEvent).isSameInstanceAs(initialEvent)
    }

    @Test
    fun `replaceWith keeps cached encrypted event instance when recovery state does not change`() = runTest {
        val factory = aTimelineItemsFactory(
            config = TimelineItemsFactoryConfig(
                computeReadReceipts = true,
                computeReactions = true,
                roomId = "!room:keepsecret.io",
            )
        )
        val event = MatrixTimelineItem.Event(
            A_UNIQUE_ID,
            anEventTimelineItem(
                content = UnableToDecryptContent(
                    data = UnableToDecryptContent.Data.Unknown,
                    threadInfo = null,
                ),
                sender = A_USER_ID,
            )
        )

        factory.replaceWith(listOf(event), roomMembers = emptyList())
        val initialEvent = factory.timelineItems.first().single() as TimelineItem.Event

        factory.replaceWith(listOf(event), roomMembers = listOf(aRoomMember(userId = A_USER_ID)))
        val updatedEvent = factory.timelineItems.first().single() as TimelineItem.Event

        assertThat(updatedEvent).isSameInstanceAs(initialEvent)
    }

    @Test
    fun `replaceWith keeps cached ai stream content when only member dependent data updates`() = runTest {
        val storage = CountingStreamStorageProvider(
            snapshot = StreamSnapshot(
                schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
                streamId = "stream-1",
                status = StreamStatus.Completed,
                parts = listOf(StreamPart.Text(id = "text-1", text = "Stored stream", textState = "done")),
                rawEvents = emptyList(),
                updatedAtMs = 1L,
                completedAtMs = 2L,
                error = null,
            )
        )
        val factory = aTimelineItemsFactory(
            config = TimelineItemsFactoryConfig(
                computeReadReceipts = true,
                computeReactions = true,
                roomId = "!room:keepsecret.io",
            ),
            aiStreamHandleStore = AiStreamHandleStore(NoopAgentStreamClient, storage),
        )
        val event = MatrixTimelineItem.Event(
            A_UNIQUE_ID,
            anEventTimelineItem(
                content = UnknownContent,
                sender = A_USER_ID,
                debugInfoProvider = { aTimelineItemDebugInfo(originalJson = streamEventJson("stream-1")) },
            )
        )

        factory.replaceWith(listOf(event), roomMembers = emptyList())
        val initialContent = factory.timelineItems.first().singleAiContent()

        factory.replaceWith(listOf(event), roomMembers = listOf(aRoomMember(userId = A_USER_ID)))
        val updatedContent = factory.timelineItems.first().singleAiContent()

        assertThat(initialContent.body).isEqualTo("Stored stream")
        assertThat(updatedContent).isSameInstanceAs(initialContent)
        assertThat(storage.loadCount).isEqualTo(1)
    }

    @Test
    fun `replaceWith hides beacon info state events`() = runTest {
        val factory = aTimelineItemsFactory(
            config = TimelineItemsFactoryConfig(
                computeReadReceipts = true,
                computeReactions = true,
                roomId = "!room:keepsecret.io",
            )
        )
        val event = MatrixTimelineItem.Event(
            A_UNIQUE_ID,
            anEventTimelineItem(
                content = StateContent(
                    stateKey = A_USER_ID.value,
                    content = OtherState.Custom("org.matrix.msc3672.beacon_info"),
                ),
                sender = A_USER_ID,
            )
        )

        factory.replaceWith(listOf(event), roomMembers = emptyList())

        assertThat(factory.timelineItems.first()).isEmpty()
    }
}

private fun List<TimelineItem>.singleAiContent(): TimelineItemAiContent {
    val event = single() as TimelineItem.Event
    return event.content as TimelineItemAiContent
}

private fun streamEventJson(streamId: String): String {
    return """
        {
          "type": "m.room.message",
          "content": {
            "msgtype": "m.stream.start",
            "stream_id": "$streamId",
            "body": "$streamId"
          }
        }
    """.trimIndent()
}

private object NoopAgentStreamClient : AgentStreamClient {
    override fun getStream(request: StreamRequest): StreamHandle {
        error("Stream network should not be opened for completed cache hydration")
    }
}

private class CountingStreamStorageProvider(
    private val snapshot: StreamSnapshot?,
) : StreamStorageProvider {
    var loadCount = 0
        private set

    override suspend fun load(streamId: String): StreamSnapshot? {
        loadCount++
        return snapshot?.takeIf { it.streamId == streamId }
    }

    override suspend fun save(snapshot: StreamSnapshot) = Unit
    override suspend fun delete(streamId: String) = Unit
}
