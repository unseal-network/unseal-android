/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamContentCache
import io.element.android.features.messages.impl.timeline.factories.event.AiStreamHandleStore
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
import io.element.android.libraries.agentstream.api.StreamStorageProvider
import io.element.android.libraries.agentstream.api.StreamSubscription
import io.element.android.libraries.agentstream.api.TextPartState
import io.element.android.libraries.agentstream.api.ToolPartState
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.cards.ChatbotCardResponseResult
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.miniapp.api.MiniAppConfig
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineItemAiPresenterTest {
    @Test
    fun `present - uses sdk stream id and maps subscribed snapshots`() = runTest {
        val client = FakeAgentStreamClient()
        val presenter = createPresenter(
            content = aTimelineItemAiContent(
                streamId = "stream-1",
                sender = "@bot:keepsecret.io",
                roomId = "!room:keepsecret.io",
                eventId = "\$event-1",
            ),
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
                roomId = "!room:keepsecret.io",
                eventId = "\$event-1",
                includeRawEvents = false,
            )
        )
    }

    @Test
    fun `present - keeps matrix body while stream snapshot has no renderable parts`() = runTest {
        val client = FakeAgentStreamClient(
            initialSnapshot = snapshot(
                streamId = "stream-1",
                status = StreamStatus.Loading,
            )
        )
        val presenter = createPresenter(
            content = aTimelineItemAiContent(
                body = "Final assistant text from Matrix",
                streamId = "stream-1",
                sender = "@bot:keepsecret.io",
            ),
            agentStreamClient = client,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            assertThat(awaitItem().content.body).isEqualTo("Final assistant text from Matrix")

            client.handle.emit(
                snapshot(
                    streamId = "stream-1",
                    status = StreamStatus.Completed,
                    parts = emptyList(),
                )
            )

            val updated = awaitItem().content
            assertThat(updated.body).isEqualTo("Final assistant text from Matrix")
            assertThat(updated.visibleParts).isEmpty()
            assertThat(updated.isTerminal).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - subscribes to sdk stream when initial parts are populated`() = runTest {
        val client = FakeAgentStreamClient()
        val content = aTimelineItemAiContent(
            streamId = "stream-1",
            parts = persistentListOf(
                AiTextStreamPart(id = "initial", state = "streaming", text = "Running tool..."),
            ),
        )
        val presenter = createPresenter(
            content = content,
            agentStreamClient = client,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            assertThat(awaitItem().content).isEqualTo(content)

            client.handle.emit(
                snapshot(
                    streamId = "stream-1",
                    status = StreamStatus.Completed,
                    parts = listOf(
                        StreamPart.Tool(
                            id = "tool-1",
                            toolState = ToolPartState.OutputAvailable,
                            toolName = "mail",
                        ),
                        StreamPart.Text(id = "text-1", text = "Done", textState = TextPartState.Complete),
                    ),
                )
            )

            val updated = awaitItem().content
            assertThat(updated.body).isEqualTo("Done")
            assertThat(updated.parts.map { it.state }).contains("output-available")
            assertThat(updated.isStreaming).isFalse()
            assertThat(client.requests).containsExactly(
                StreamRequest(
                    streamId = "stream-1",
                    sender = "",
                    roomId = "",
                    eventId = "",
                    includeRawEvents = false,
                )
            )

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

    @Test
    fun `present - terminal snapshot updates even when non-terminal burst is throttled`() = runTest {
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
                    status = StreamStatus.Streaming,
                    parts = listOf(
                        StreamPart.Text(id = "text-1", text = "intermediate", textState = TextPartState.Streaming),
                    ),
                )
            )
            client.handle.emit(
                snapshot(
                    streamId = "stream-1",
                    status = StreamStatus.Completed,
                    parts = listOf(
                        StreamPart.Text(id = "text-1", text = "final", textState = TextPartState.Complete),
                    ),
                )
            )

            val firstUpdate = awaitItem().content
            val updated = if (firstUpdate.body == "final") {
                firstUpdate
            } else {
                assertThat(firstUpdate.body).isEqualTo("intermediate")
                awaitItem().content
            }
            assertThat(updated.body).isEqualTo("final")
            assertThat(updated.isStreaming).isFalse()
            assertThat(client.handle.cancelledSubscriptions).isEqualTo(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - coalesces same-state content patches and emits state changes immediately`() = runTest {
        val client = FakeAgentStreamClient()
        val presenter = createPresenter(
            content = aTimelineItemAiContent(streamId = "stream-1"),
            agentStreamClient = client,
            dispatchers = testCoroutineDispatchers(),
        )

        presenter.test {
            assertThat(awaitItem().content.body).isEmpty()
            advanceUntilIdle()

            client.handle.emit(
                snapshot(
                    streamId = "stream-1",
                    status = StreamStatus.Streaming,
                    parts = listOf(
                        StreamPart.Text(id = "text-1", text = "Hel", textState = TextPartState.Streaming),
                    ),
                )
            )
            runCurrent()
            assertThat(awaitItem().content.body).isEqualTo("Hel")

            client.handle.emit(
                snapshot(
                    streamId = "stream-1",
                    status = StreamStatus.Streaming,
                    parts = listOf(
                        StreamPart.Text(id = "text-1", text = "Hello", textState = TextPartState.Streaming),
                    ),
                )
            )
            runCurrent()
            expectNoEvents()

            advanceTimeBy(500)
            advanceUntilIdle()
            assertThat(awaitItem().content.body).isEqualTo("Hello")

            client.handle.emit(
                snapshot(
                    streamId = "stream-1",
                    status = StreamStatus.Completed,
                    parts = listOf(
                        StreamPart.Text(id = "text-1", text = "Hello!", textState = TextPartState.Complete),
                    ),
                )
            )
            runCurrent()

            val completed = awaitItem().content
            assertThat(completed.body).isEqualTo("Hello!")
            assertThat(completed.isStreaming).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - terminal immediate subscription snapshot exits collection`() = runTest {
        val client = FakeAgentStreamClient(
            initialSnapshot = snapshot(
                streamId = "stream-1",
                status = StreamStatus.Completed,
                parts = listOf(
                    StreamPart.Text(id = "text-1", text = "cached final", textState = TextPartState.Complete),
                ),
            )
        )
        val presenter = createPresenter(
            content = aTimelineItemAiContent(streamId = "stream-1"),
            agentStreamClient = client,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            val firstUpdate = awaitItem().content
            val updated = if (firstUpdate.body == "cached final") {
                firstUpdate
            } else {
                assertThat(firstUpdate.body).isEmpty()
                awaitItem().content
            }

            assertThat(updated.body).isEqualTo("cached final")
            assertThat(updated.isStreaming).isFalse()
            assertThat(client.handle.cancelledSubscriptions).isEqualTo(1)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - uses reduced content cache as initial state after recycled timeline item rebinds`() = runTest {
        val client = FakeAgentStreamClient(
            initialSnapshot = snapshot(
                streamId = "stream-1",
                status = StreamStatus.Completed,
                parts = listOf(
                    StreamPart.Text(id = "text-1", text = "already loaded", textState = TextPartState.Complete),
                ),
            )
        )
        val streamHandleStore = AiStreamHandleStore(client, FakeStreamStorageProvider())
        streamHandleStore.bind(
            StreamRequest(
                streamId = "stream-1",
                sender = "@bot:keepsecret.io",
                roomId = "",
                eventId = "",
                includeRawEvents = false,
            )
        ) { }.close()
        val contentCache = AiStreamContentCache().apply {
            put(
                AiSdkStreamReducer().mapSnapshot(
                    snapshot = client.handle.snapshot(),
                    isEdited = false,
                    sender = "@bot:keepsecret.io",
                )
            )
        }
        val presenter = createPresenter(
            content = aTimelineItemAiContent(
                streamId = "stream-1",
                sender = "@bot:keepsecret.io",
                roomId = "!room:keepsecret.io",
                eventId = "event-1",
            ),
            agentStreamClient = client,
            streamHandleStore = streamHandleStore,
            streamContentCache = contentCache,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            val initial = awaitItem().content

            assertThat(initial.body).isEqualTo("already loaded")
            assertThat(initial.isStreaming).isFalse()
            assertThat(initial.sender).isEqualTo("@bot:keepsecret.io")
            assertThat(initial.roomId).isEqualTo("!room:keepsecret.io")
            assertThat(initial.eventId).isEqualTo("event-1")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - terminal cached content does not rebind sdk stream after recycled timeline item rebinds`() = runTest {
        val client = FakeAgentStreamClient(
            initialSnapshot = snapshot(
                streamId = "stream-1",
                status = StreamStatus.Loading,
            )
        )
        val contentCache = AiStreamContentCache().apply {
            put(
                AiSdkStreamReducer().mapSnapshot(
                    snapshot = snapshot(
                        streamId = "stream-1",
                        status = StreamStatus.Completed,
                        parts = listOf(
                            StreamPart.Text(id = "text-1", text = "cached final", textState = TextPartState.Complete),
                        ),
                    ),
                    isEdited = false,
                    sender = "@bot:keepsecret.io",
                )
            )
        }
        val presenter = createPresenter(
            content = aTimelineItemAiContent(streamId = "stream-1", sender = "@bot:keepsecret.io"),
            agentStreamClient = client,
            streamContentCache = contentCache,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            val initial = awaitItem().content

            assertThat(initial.body).isEqualTo("cached final")
            assertThat(initial.isStreaming).isFalse()
            assertThat(client.requests).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - cached fallback body does not override edited matrix body`() = runTest {
        val client = FakeAgentStreamClient(
            initialSnapshot = snapshot(
                streamId = "stream-1",
                status = StreamStatus.Loading,
            )
        )
        val contentCache = AiStreamContentCache().apply {
            put(
                aTimelineItemAiContent(
                    body = "Old assistant text",
                    streamId = "stream-1",
                ).copy(
                    isStreaming = false,
                    isTerminal = true,
                )
            )
        }
        val presenter = createPresenter(
            content = aTimelineItemAiContent(
                body = "Edited assistant text",
                streamId = "stream-1",
                sender = "@bot:keepsecret.io",
            ),
            agentStreamClient = client,
            streamContentCache = contentCache,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            val initial = awaitItem().content

            assertThat(initial.body).isEqualTo("Edited assistant text")
            assertThat(initial.isTerminal).isTrue()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - completed durable snapshot renders before binding sdk stream`() = runTest {
        val client = FakeAgentStreamClient(
            initialSnapshot = snapshot(
                streamId = "stream-1",
                status = StreamStatus.Loading,
            )
        )
        val streamHandleStore = AiStreamHandleStore(
            client = client,
            storageProvider = FakeStreamStorageProvider(
                loadResult = snapshot(
                    streamId = "stream-1",
                    status = StreamStatus.Completed,
                    parts = listOf(
                        StreamPart.Text(id = "text-1", text = "stored final", textState = TextPartState.Complete),
                    ),
                )
            ),
        )
        val presenter = createPresenter(
            content = aTimelineItemAiContent(streamId = "stream-1", sender = "@bot:keepsecret.io"),
            agentStreamClient = client,
            streamHandleStore = streamHandleStore,
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            val first = awaitItem().content
            val updated = if (first.body == "stored final") first else awaitItem().content

            assertThat(updated.body).isEqualTo("stored final")
            assertThat(updated.isStreaming).isFalse()
            assertThat(client.requests).isEmpty()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - send card response calls homeserver service`() = runTest {
        val service = FakeChatbotApiService()
        val requests = mutableListOf<Triple<String, String, String>>()
        service.sendCardResponseResult = { roomId, eventId, actionId ->
            requests += Triple(roomId, eventId, actionId)
            Result.success(
                ChatbotCardResponseResult(
                    eventId = eventId,
                    roomId = roomId,
                    actionId = actionId,
                    duplicate = false,
                )
            )
        }
        val presenter = createPresenter(
            content = aTimelineItemAiContent(
                roomId = "!room:keepsecret.io",
                eventId = "\$event-1",
            ),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            val state = awaitItem()

            assertThat(state.onSendCardResponse("\$event-1", "continue")).isTrue()
            assertThat(requests).containsExactly(Triple("!room:keepsecret.io", "\$event-1", "continue"))

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - send card response fails closed without room context`() = runTest {
        val service = FakeChatbotApiService()
        var requestCount = 0
        service.sendCardResponseResult = { roomId, eventId, actionId ->
            requestCount++
            Result.success(ChatbotCardResponseResult(eventId = eventId, roomId = roomId, actionId = actionId, duplicate = false))
        }
        val presenter = createPresenter(
            content = aTimelineItemAiContent(eventId = "\$event-1"),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            val state = awaitItem()

            assertThat(state.onSendCardResponse("\$event-1", "continue")).isFalse()
            assertThat(requestCount).isEqualTo(0)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - send card response returns false when service fails`() = runTest {
        val service = FakeChatbotApiService()
        service.sendCardResponseResult = { _, _, _ -> Result.failure(IllegalStateException("boom")) }
        val presenter = createPresenter(
            content = aTimelineItemAiContent(
                roomId = "!room:keepsecret.io",
                eventId = "\$event-1",
            ),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
            dispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
        )

        presenter.test {
            val state = awaitItem()

            assertThat(state.onSendCardResponse("\$event-1", "continue")).isFalse()

            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        content: TimelineItemAiContent,
        agentStreamClient: AgentStreamClient = FakeAgentStreamClient(),
        streamHandleStore: AiStreamHandleStore = AiStreamHandleStore(agentStreamClient, FakeStreamStorageProvider()),
        streamContentCache: AiStreamContentCache = AiStreamContentCache(),
        dispatchers: CoroutineDispatchers,
        workflowProgressProvider: WorkflowProgressProvider = FakeWorkflowProgressProvider(),
        matrixClient: MatrixClient = FakeMatrixClient(),
        chatbotApiServiceFactory: ChatbotApiServiceFactory = FakeChatbotApiServiceFactory(),
    ): TimelineItemAiPresenter {
        return TimelineItemAiPresenter(
            content = content,
            aiStreamHandleStore = streamHandleStore,
            aiStreamContentCache = streamContentCache,
            aiSdkStreamReducer = AiSdkStreamReducer(),
            dispatchers = dispatchers,
            workflowProgressManager = workflowProgressProvider,
            workflowTaskStore = FakeWorkflowTaskStore(),
            miniAppDocumentLauncher = FakeMiniAppDocumentLauncher(),
            matrixClient = matrixClient,
            chatbotApiServiceFactory = chatbotApiServiceFactory,
        )
    }

    private class FakeWorkflowProgressProvider : WorkflowProgressProvider {
        override fun progressFlow(taskId: String, wsBaseUrl: String?) = kotlinx.coroutines.flow.flowOf<WorkflowMessage>()
    }

    private class FakeWorkflowTaskStore : WorkflowTaskStore {
        override suspend fun load(taskId: String): WorkflowTaskRecord? = null
        override suspend fun save(record: WorkflowTaskRecord) = Unit
        override suspend fun loadSlides(taskId: String): List<String> = emptyList()
        override suspend fun saveSlides(taskId: String, taskType: String, slides: List<String>, status: String, knownTotal: Int?) = Unit
    }

    private class FakeMiniAppDocumentLauncher : MiniAppDocumentLauncher {
        override suspend fun buildConfig(appId: Long, options: Map<String, Any>): MiniAppConfig {
            return MiniAppConfig(appId = appId, url = "", options = options)
        }

        override fun okHttpClient() = okhttp3.OkHttpClient()
    }

    private class FakeAgentStreamClient(
        initialSnapshot: StreamSnapshot = snapshot(streamId = "stream-1"),
    ) : AgentStreamClient {
        val requests = mutableListOf<StreamRequest>()
        val handle = FakeStreamHandle(initialSnapshot)

        override fun getStream(request: StreamRequest): StreamHandle {
            requests += request
            return handle
        }
    }

    private class FakeStreamHandle(
        private val initialSnapshot: StreamSnapshot,
    ) : StreamHandle {
        private val listeners = mutableListOf<StreamListener>()
        var cancelledSubscriptions = 0
            private set
        var cancelledHandles = 0
            private set

        override fun snapshot(): StreamSnapshot = initialSnapshot

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

    private class FakeStreamStorageProvider(
        private val loadResult: StreamSnapshot? = null,
    ) : StreamStorageProvider {
        override suspend fun load(streamId: String): StreamSnapshot? = loadResult

        override suspend fun save(snapshot: StreamSnapshot) = Unit

        override suspend fun delete(streamId: String) = Unit
    }

    private companion object {
        fun aTimelineItemAiContent(
            body: String = "",
            streamId: String? = null,
            sender: String? = null,
            roomId: String? = null,
            eventId: String? = null,
            parts: ImmutableList<AiStreamPart> = persistentListOf(),
        ): TimelineItemAiContent {
            return TimelineItemAiContent(
                body = body,
                isEdited = false,
                isStreaming = true,
                streamId = streamId,
                sender = sender,
                roomId = roomId,
                eventId = eventId,
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
