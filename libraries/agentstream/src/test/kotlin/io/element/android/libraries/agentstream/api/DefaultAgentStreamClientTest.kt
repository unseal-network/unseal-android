/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAgentStreamClientTest {
    private val parser = StreamSnapshotParser(clock = { 1000L })

    @Test
    fun `storage completed hit publishes cached snapshot without network`() = runTest {
        val cached = completedSnapshot("stream-1", "cached")
        val storage = FakeStreamStorageProvider(loadResult = cached)
        val http = FakeStreamHttpClient()
        val client = createClient(storage = storage, http = http)
        val snapshots = mutableListOf<StreamSnapshot>()

        client.getStream(request("stream-1")).subscribe { snapshots += it }
        advanceUntilIdle()

        assertEquals(cached, snapshots.last())
        assertEquals(0, http.openCount)
    }

    @Test
    fun `same stream id dedupes in flight network request and fans out streaming update`() = runTest {
        val storage = FakeStreamStorageProvider()
        val http = FakeStreamHttpClient(
            chunks = listOf(streamingJson("stream-1", "hi")),
        )
        val client = createClient(storage = storage, http = http)
        val first = mutableListOf<StreamSnapshot>()
        val second = mutableListOf<StreamSnapshot>()

        val firstHandle = client.getStream(request("stream-1"))
        val secondHandle = client.getStream(request("stream-1"))
        firstHandle.subscribe { first += it }
        secondHandle.subscribe { second += it }
        advanceUntilIdle()

        assertSame(firstHandle, secondHandle)
        assertEquals(1, http.openCount)
        assertTrue(first.any { it.status == StreamStatus.Streaming && it.text() == "hi" })
        assertTrue(second.any { it.status == StreamStatus.Streaming && it.text() == "hi" })
    }

    @Test
    fun `completed stream saves final snapshot and publishes completed final parts`() = runTest {
        val storage = FakeStreamStorageProvider()
        val http = FakeStreamHttpClient(
            chunks = listOf(streamingJson("stream-1", "hi")),
        )
        val client = createClient(storage = storage, http = http)
        val snapshots = mutableListOf<StreamSnapshot>()

        client.getStream(request("stream-1")).subscribe { snapshots += it }
        advanceUntilIdle()

        val finalSnapshot = snapshots.last()
        assertEquals(StreamStatus.Completed, finalSnapshot.status)
        assertEquals("hi", finalSnapshot.text())
        assertEquals(TextPartState.Complete.wireValue, (finalSnapshot.parts.single() as StreamPart.Text).textState)
        assertEquals(finalSnapshot, storage.savedSnapshots.single())
    }

    @Test
    fun `network failure publishes failed snapshot and does not replace existing completed cache or storage`() = runTest {
        val completed = completedSnapshot("stream-1", "old")
        val storage = FakeStreamStorageProvider(loadResult = null).apply {
            savedSnapshots += completed
        }
        val http = FakeStreamHttpClient(error = IllegalStateException("boom"))
        val client = createClient(storage = storage, http = http)
        val snapshots = mutableListOf<StreamSnapshot>()

        client.getStream(request("stream-1")).subscribe { snapshots += it }
        advanceUntilIdle()

        assertEquals(StreamStatus.Failed, snapshots.last().status)
        assertTrue(snapshots.last().error?.message.orEmpty().contains("boom"))
        assertEquals(listOf(completed), storage.savedSnapshots)
    }

    @Test
    fun `subscribe immediately emits current snapshot`() = runTest {
        val release = CompletableDeferred<Unit>()
        val client = createClient(http = FakeStreamHttpClient(waitForRelease = release))
        val snapshots = mutableListOf<StreamSnapshot>()
        val handle = client.getStream(request("stream-1"))

        handle.subscribe { snapshots += it }

        assertEquals(StreamStatus.Loading, snapshots.single().status)
        assertEquals("stream-1", snapshots.single().streamId)
        assertTrue(snapshots.single().parts.isEmpty())
        release.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `cancel publishes cancelled and cancels task and session if still running`() = runTest {
        val release = CompletableDeferred<Unit>()
        val factory = FakeStreamReducerSessionFactory()
        val runner = TestStreamTaskRunner(this)
        val client = createClient(
            http = FakeStreamHttpClient(waitForRelease = release),
            runner = runner,
            reducerSessionFactory = factory,
        )
        val snapshots = mutableListOf<StreamSnapshot>()
        val handle = client.getStream(request("stream-1"))
        handle.subscribe { snapshots += it }
        advanceUntilIdle()

        handle.cancel()
        advanceUntilIdle()
        release.complete(Unit)

        assertEquals(StreamStatus.Cancelled, snapshots.last().status)
        assertTrue(runner.tasks.single().cancelled)
        assertTrue(factory.sessions.single().closed)
    }

    @Test
    fun `listener callbacks can unsubscribe and cancel without deadlocking`() = runTest {
        val http = FakeStreamHttpClient(chunks = listOf(streamingJson("stream-1", "hi")))
        val client = createClient(http = http)
        val handle = client.getStream(request("stream-1"))
        val subscription = AtomicReference<StreamSubscription>()
        val snapshots = mutableListOf<StreamSnapshot>()

        subscription.set(handle.subscribe { snapshot ->
            snapshots += snapshot
            subscription.get()?.cancel()
            handle.cancel()
        })
        advanceUntilIdle()

        assertTrue(snapshots.isNotEmpty())
        assertEquals(StreamStatus.Cancelled, handle.snapshot().status)
    }

    private fun TestScope.createClient(
        storage: FakeStreamStorageProvider = FakeStreamStorageProvider(),
        http: FakeStreamHttpClient = FakeStreamHttpClient(),
        runner: TestStreamTaskRunner = TestStreamTaskRunner(this),
        reducerSessionFactory: FakeStreamReducerSessionFactory = FakeStreamReducerSessionFactory(),
    ): DefaultAgentStreamClient {
        return DefaultAgentStreamClient(
            storageProvider = storage,
            httpClient = http,
            taskRunner = runner,
            reducerSessionFactory = reducerSessionFactory,
            snapshotParser = parser,
            clock = { 1000L },
        )
    }

    private fun request(streamId: String) = StreamRequest(
        streamId = streamId,
        sender = "@bot:keepsecret.io",
        roomId = "!room:keepsecret.io",
        eventId = "\$event",
    )

    private fun streamingJson(streamId: String, text: String): String {
        return """
            {"streamId":"$streamId","status":"streaming","updatedAtMs":999,"parts":[{"type":"text","id":"text","state":"streaming","text":"$text"}]}
        """.trimIndent()
    }

    private fun completedSnapshot(streamId: String, text: String) = StreamSnapshot(
        schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
        streamId = streamId,
        status = StreamStatus.Completed,
        parts = listOf(StreamPart.Text("text", text, TextPartState.Complete)),
        rawEvents = emptyList(),
        updatedAtMs = 1L,
        completedAtMs = 1L,
        error = null,
    )

    private fun StreamSnapshot.text(): String {
        return (parts.singleOrNull() as? StreamPart.Text)?.text.orEmpty()
    }

    private class FakeStreamStorageProvider(
        private val loadResult: StreamSnapshot? = null,
    ) : StreamStorageProvider {
        val savedSnapshots = mutableListOf<StreamSnapshot>()

        override suspend fun load(streamId: String): StreamSnapshot? = loadResult

        override suspend fun save(snapshot: StreamSnapshot) {
            if (savedSnapshots.any { it.streamId == snapshot.streamId && it.status == StreamStatus.Completed } &&
                snapshot.status == StreamStatus.Failed
            ) {
                return
            }
            savedSnapshots += snapshot
        }

        override suspend fun delete(streamId: String) = Unit
    }

    private class FakeStreamHttpClient(
        private val chunks: List<String> = emptyList(),
        private val error: Throwable? = null,
        private val waitForRelease: CompletableDeferred<Unit>? = null,
    ) : StreamHttpClient {
        var openCount = 0

        override suspend fun openStream(
            request: StreamRequest,
            onChunk: suspend (String) -> Unit,
        ) {
            openCount++
            error?.let { throw it }
            chunks.forEach { onChunk(it) }
            waitForRelease?.await()
        }
    }

    private class TestStreamTaskRunner(
        private val scope: TestScope,
    ) : StreamTaskRunner {
        val tasks = mutableListOf<TestStreamTask>()

        override fun run(
            key: String,
            block: suspend () -> Unit,
        ): StreamTask {
            val task = TestStreamTask(scope.launch { block() })
            tasks += task
            return task
        }
    }

    private class TestStreamTask(
        private val job: Job,
    ) : StreamTask {
        var cancelled = false

        override fun cancel() {
            cancelled = true
            job.cancel()
        }

        suspend fun join() = job.cancelAndJoin()
    }

    private class FakeStreamReducerSessionFactory : StreamReducerSessionFactory {
        val sessions = mutableListOf<FakeStreamReducerSession>()

        override fun create(
            streamId: String,
            includeRawEvents: Boolean,
        ): StreamReducerSession {
            return FakeStreamReducerSession(streamId).also { sessions += it }
        }
    }

    private class FakeStreamReducerSession(
        streamId: String,
    ) : StreamReducerSession {
        private var latest = """{"streamId":"$streamId","status":"streaming","parts":[]}"""
        var closed = false

        override fun applySseChunk(chunk: String): String {
            latest = chunk
            return chunk
        }

        override fun finish(): String = latest

        override fun snapshot(): String = latest

        override fun close() {
            closed = true
        }
    }
}
