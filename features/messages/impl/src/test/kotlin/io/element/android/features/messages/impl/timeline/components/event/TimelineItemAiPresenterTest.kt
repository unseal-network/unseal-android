/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.factories.event.AiSdkStreamReducer
import io.element.android.features.messages.impl.timeline.model.event.AiStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.StreamHandle
import io.element.android.libraries.agentstream.api.StreamListener
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamSubscription
import io.element.android.libraries.agentstream.api.TextPartState
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TimelineItemAiPresenterTest {
    @Test
    fun `present - uses sdk stream id and maps subscribed snapshots`() = runTest {
        val client = FakeAgentStreamClient()
        val presenter = createPresenter(
            content = aTimelineItemAiContent(streamId = "stream-1", sender = "@bot:keepsecret.io"),
            agentStreamClient = client,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            assertThat(awaitItem().content.body).isEmpty()

            client.handle.emit(
                snapshot(
                    streamId = "stream-1",
                    status = StreamStatus.Completed,
                    parts = listOf(
                        StreamPart.Text(id = "text-1", text = "Hello from SDK", textState = TextPartState.Complete),
                    ),
                )
            )

            val updated = awaitItem().content
            assertThat(updated.body).isEqualTo("Hello from SDK")
            assertThat((updated.parts.single() as AiTextStreamPart).text).isEqualTo("Hello from SDK")
            assertThat(updated.sender).isEqualTo("@bot:keepsecret.io")
            assertThat(updated.isStreaming).isFalse()

            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.requests).containsExactly(
            StreamRequest(
                streamId = "stream-1",
                sender = "@bot:keepsecret.io",
                roomId = "",
                eventId = "",
                includeRawEvents = false,
            )
        )
    }

    @Test
    fun `present - does not request sdk stream when initial parts are populated`() = runTest {
        val client = FakeAgentStreamClient()
        val content = aTimelineItemAiContent(
            streamId = "stream-1",
            parts = persistentListOf(AiTextStreamPart(id = "initial", state = "done", text = "Already parsed")),
        )
        val presenter = createPresenter(
            content = content,
            agentStreamClient = client,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            val state = awaitItem()

            assertThat(state.content).isEqualTo(content)
            assertThat(client.requests).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - cancels sdk subscription when disposed`() = runTest {
        val client = FakeAgentStreamClient()
        val presenter = createPresenter(
            content = aTimelineItemAiContent(streamId = "stream-1"),
            agentStreamClient = client,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            assertThat(awaitItem().content.body).isEmpty()
            client.handle.emit(
                snapshot(
                    streamId = "stream-1",
                    status = StreamStatus.Completed,
                    parts = listOf(
                        StreamPart.Text(id = "text-1", text = "Done", textState = TextPartState.Complete),
                    ),
                )
            )
            assertThat(awaitItem().content.body).isEqualTo("Done")

            cancelAndIgnoreRemainingEvents()
        }

        assertThat(client.handle.cancelledSubscriptions).isEqualTo(1)
        assertThat(client.handle.cancelledHandles).isEqualTo(0)
    }

    private fun createPresenter(
        content: TimelineItemAiContent,
        agentStreamClient: AgentStreamClient = FakeAgentStreamClient(),
        dispatchers: CoroutineDispatchers,
    ): TimelineItemAiPresenter {
        return TimelineItemAiPresenter(
            content = content,
            agentStreamClient = agentStreamClient,
            aiSdkStreamReducer = AiSdkStreamReducer(),
            dispatchers = dispatchers,
        )
    }

    private class FakeAgentStreamClient : AgentStreamClient {
        val requests = mutableListOf<StreamRequest>()
        val handle = FakeStreamHandle()

        override fun getStream(request: StreamRequest): StreamHandle {
            requests += request
            return handle
        }
    }

    private class FakeStreamHandle : StreamHandle {
        private val listeners = mutableListOf<StreamListener>()
        var cancelledSubscriptions = 0
            private set
        var cancelledHandles = 0
            private set

        override fun snapshot(): StreamSnapshot = snapshot(streamId = "stream-1")

        override fun subscribe(listener: StreamListener): StreamSubscription {
            listeners += listener
            listener.onSnapshot(snapshot())
            return object : StreamSubscription {
                override fun cancel() {
                    listeners -= listener
                    cancelledSubscriptions++
                }
            }
        }

        override fun refresh() = Unit

        override fun cancel() {
            cancelledHandles++
        }

        fun emit(snapshot: StreamSnapshot) {
            listeners.toList().forEach { it.onSnapshot(snapshot) }
        }
    }

    private companion object {
        fun aTimelineItemAiContent(
            streamId: String? = null,
            sender: String? = null,
            parts: ImmutableList<AiStreamPart> = persistentListOf(),
        ): TimelineItemAiContent {
            return TimelineItemAiContent(
                body = "",
                isEdited = false,
                isStreaming = true,
                streamId = streamId,
                sender = sender,
                thinkingSteps = persistentListOf(),
                toolCalls = persistentListOf(),
                sources = persistentListOf(),
                quickActions = persistentListOf(),
                parts = parts,
            )
        }

        fun snapshot(
            streamId: String,
            status: StreamStatus = StreamStatus.Loading,
            parts: List<StreamPart> = emptyList(),
        ): StreamSnapshot {
            return StreamSnapshot(
                schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
                streamId = streamId,
                status = status,
                parts = parts,
                rawEvents = emptyList(),
                updatedAtMs = 1L,
                completedAtMs = if (status == StreamStatus.Completed) 1L else null,
                error = null,
            )
        }
    }
}
