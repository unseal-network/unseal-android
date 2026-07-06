/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.fixtures.aTimelineItemContentFactory
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamHandleStore
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamContentCache
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemLocationContent
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamStorageProvider
import io.element.android.libraries.matrix.api.room.location.AssetType
import io.element.android.libraries.matrix.api.timeline.item.event.LiveLocationContent
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

    @Test
    fun `create hydrates completed stream snapshot before timeline item reaches Compose`() = runTest {
        val originalJson = """
            {
              "type": "m.room.message",
              "content": {
                "msgtype": "m.stream.start",
                "stream_id": "stream-1",
                "body": "stream-1"
              }
            }
        """.trimIndent()
        val storage = HydratingStreamStorageProvider(
            StreamSnapshot(
                schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
                streamId = "stream-1",
                status = StreamStatus.Completed,
                parts = listOf(StreamPart.Text(id = "text-1", text = "Loaded from cache", textState = "done")),
                rawEvents = emptyList(),
                updatedAtMs = 1L,
                completedAtMs = 2L,
                error = null,
            )
        )
        val factory = aTimelineItemContentFactory(
            aiStreamHandleStore = AiStreamHandleStore(NoopAgentStreamClient, storage),
        )
        val event = anEventTimelineItem(
            content = UnknownContent,
            sender = A_USER_ID,
            debugInfoProvider = { aTimelineItemDebugInfo(originalJson = originalJson) },
        )

        val content = factory.create(event)

        assertThat(storage.loadCount).isEqualTo(1)
        assertThat(content).isInstanceOf(TimelineItemAiContent::class.java)
        val aiContent = content as TimelineItemAiContent
        assertThat(aiContent.isTerminal).isTrue()
        assertThat(aiContent.body).isEqualTo("Loaded from cache")
        assertThat(aiContent.visibleParts.map { it.id }).containsExactly("text-1")
    }

    @Test
    fun `create keeps matrix body when completed stream snapshot has no renderable parts`() = runTest {
        val originalJson = """
            {
              "type": "m.room.message",
              "content": {
                "msgtype": "m.text",
                "stream": { "id": "stream-1" },
                "body": "Final assistant text from Matrix"
              }
            }
        """.trimIndent()
        val storage = HydratingStreamStorageProvider(
            StreamSnapshot(
                schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
                streamId = "stream-1",
                status = StreamStatus.Completed,
                parts = emptyList(),
                rawEvents = emptyList(),
                updatedAtMs = 1L,
                completedAtMs = 2L,
                error = null,
            )
        )
        val factory = aTimelineItemContentFactory(
            aiStreamHandleStore = AiStreamHandleStore(NoopAgentStreamClient, storage),
        )
        val event = anEventTimelineItem(
            content = UnknownContent,
            sender = A_USER_ID,
            debugInfoProvider = { aTimelineItemDebugInfo(originalJson = originalJson) },
        )

        val content = factory.create(event)

        assertThat(content).isInstanceOf(TimelineItemAiContent::class.java)
        val aiContent = content as TimelineItemAiContent
        assertThat(aiContent.body).isEqualTo("Final assistant text from Matrix")
        assertThat(aiContent.visibleParts).isEmpty()
        assertThat(aiContent.isTerminal).isTrue()
    }

    @Test
    fun `create uses latest matrix body when cached stream content only has fallback body`() = runTest {
        val contentCache = AiStreamContentCache().apply {
            put(
                TimelineItemAiContent(
                    body = "Old assistant text",
                    isEdited = false,
                    isStreaming = false,
                    isTerminal = true,
                    streamId = "stream-1",
                    thinkingSteps = kotlinx.collections.immutable.persistentListOf(),
                    toolCalls = kotlinx.collections.immutable.persistentListOf(),
                    sources = kotlinx.collections.immutable.persistentListOf(),
                    quickActions = kotlinx.collections.immutable.persistentListOf(),
                )
            )
        }
        val originalJson = """
            {
              "type": "m.room.message",
              "content": {
                "msgtype": "m.text",
                "stream": { "id": "stream-1" },
                "body": "Edited assistant text"
              }
            }
        """.trimIndent()
        val factory = aTimelineItemContentFactory(aiStreamContentCache = contentCache)
        val event = anEventTimelineItem(
            content = UnknownContent,
            sender = A_USER_ID,
            debugInfoProvider = { aTimelineItemDebugInfo(originalJson = originalJson) },
        )

        val content = factory.create(event)

        assertThat(content).isInstanceOf(TimelineItemAiContent::class.java)
        val aiContent = content as TimelineItemAiContent
        assertThat(aiContent.body).isEqualTo("Edited assistant text")
    }

    @Test
    fun `create parses live location from beacon info state location fallback`() = runTest {
        val originalJson = """
            {
              "type": "org.matrix.msc3672.beacon_info",
              "origin_server_ts": 1782901612517,
              "content": {
                "description": "Live location",
                "live": true,
                "timeout": 900000,
                "m.asset": { "type": "m.self" },
                "org.matrix.msc3488.location": {
                  "uri": "geo:22.27292352,113.52691051;u=35.497997"
                }
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

        assertThat(content).isInstanceOf(TimelineItemLocationContent::class.java)
        val locationContent = content as TimelineItemLocationContent
        val mode = locationContent.mode as TimelineItemLocationContent.Mode.Live
        assertThat(mode.lastKnownLocation?.lat).isEqualTo(22.27292352)
        assertThat(mode.lastKnownLocation?.lon).isEqualTo(113.52691051)
    }

    @Test
    fun `create does not let raw beacon info override SDK live location content`() = runTest {
        val originalJson = """
            {
              "type": "org.matrix.msc3672.beacon_info",
              "origin_server_ts": 1782901612517,
              "content": {
                "description": "Live location ended",
                "live": false,
                "timeout": 0,
                "m.asset": { "type": "m.self" }
              }
            }
        """.trimIndent()
        val factory = aTimelineItemContentFactory()
        val event = anEventTimelineItem(
            content = LiveLocationContent(
                isLive = true,
                description = "Live location",
                startTimestamp = 1782901612517,
                timeout = 900000,
                assetType = AssetType.SENDER,
                locations = emptyList(),
            ),
            sender = A_USER_ID,
            debugInfoProvider = { aTimelineItemDebugInfo(originalJson = originalJson) },
        )

        val content = factory.create(event)

        assertThat(content).isInstanceOf(TimelineItemLocationContent::class.java)
        val locationContent = content as TimelineItemLocationContent
        val mode = locationContent.mode as TimelineItemLocationContent.Mode.Live
        assertThat(mode.isActive).isTrue()
        assertThat(locationContent.description).isEqualTo("Live location")
    }
}

private object NoopAgentStreamClient : io.element.android.libraries.agentstream.api.AgentStreamClient {
    override fun getStream(request: io.element.android.libraries.agentstream.api.StreamRequest): io.element.android.libraries.agentstream.api.StreamHandle {
        error("Stream network should not be opened for completed cache hydration")
    }
}

private class HydratingStreamStorageProvider(
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
