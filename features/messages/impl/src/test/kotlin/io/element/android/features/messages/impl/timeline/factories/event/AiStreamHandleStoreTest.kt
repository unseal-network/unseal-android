/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.RawStreamEvent
import io.element.android.libraries.agentstream.api.StreamError
import io.element.android.libraries.agentstream.api.StreamHandle
import io.element.android.libraries.agentstream.api.StreamListener
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamSubscription
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiStreamHandleStoreTest {
    @Test
    fun `same stream id reuses one sdk handle`() {
        val client = FakeAgentStreamClient()
        val store = AiStreamHandleStore(client)
        val request = StreamRequest(streamId = "stream-1", sender = "@a:b", roomId = "!room:b", eventId = "event-1")

        val first = store.bind(request) { }
        val second = store.bind(request) { }

        assertThat(client.requests).hasSize(1)
        assertThat(first.streamId).isEqualTo("stream-1")
        assertThat(second.streamId).isEqualTo("stream-1")
    }

    @Test
    fun `unbind cancels listener subscription but does not cancel sdk handle`() {
        val client = FakeAgentStreamClient()
        val store = AiStreamHandleStore(client)
        val binding = store.bind(StreamRequest("stream-1", "@a:b", "!room:b", "event-1")) { }

        binding.close()

        assertThat(client.handle.cancelCount).isEqualTo(0)
        assertThat(client.handle.subscriptionCancelCount).isEqualTo(1)
    }

    @Test
    fun `refresh delegates to existing sdk handle`() {
        val client = FakeAgentStreamClient()
        val store = AiStreamHandleStore(client)
        store.bind(StreamRequest("stream-1", "@a:b", "!room:b", "event-1")) { }

        store.refresh("stream-1")

        assertThat(client.handle.refreshCount).isEqualTo(1)
    }

    @Test
    fun `failed handle is not reused on next bind`() {
        val client = FakeAgentStreamClient(
            handles = ArrayDeque(
                listOf(
                    FakeStreamHandle(status = StreamStatus.Failed),
                    FakeStreamHandle(status = StreamStatus.Loading),
                )
            )
        )
        val store = AiStreamHandleStore(client)
        val request = StreamRequest("stream-1", "@a:b", "!room:b", "event-1")

        store.bind(request) { }.close()
        store.bind(request) { }.close()

        assertThat(client.requests).hasSize(2)
    }

    @Test
    fun `concurrent binds for same stream id reuse one sdk handle`() {
        val client = FakeAgentStreamClient()
        val store = AiStreamHandleStore(client)
        val request = StreamRequest(streamId = "stream-1", sender = "@a:b", roomId = "!room:b", eventId = "event-1")
        val ready = CountDownLatch(8)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)

        repeat(8) {
            executor.execute {
                ready.countDown()
                start.await(1, TimeUnit.SECONDS)
                store.bind(request) { }.close()
            }
        }
        ready.await(1, TimeUnit.SECONDS)
        start.countDown()
        executor.shutdown()
        executor.awaitTermination(5, TimeUnit.SECONDS)

        assertThat(client.requests).hasSize(1)
    }

    @Test
    fun `cached snapshot returns latest usable snapshot after binding`() {
        val client = FakeAgentStreamClient()
        val store = AiStreamHandleStore(client)
        val request = StreamRequest(streamId = "stream-1", sender = "@a:b", roomId = "!room:b", eventId = "event-1")

        store.bind(request) { }.close()

        assertThat(store.cachedSnapshot("stream-1")?.status).isEqualTo(StreamStatus.Completed)
    }
}

private class FakeAgentStreamClient(
    private val handles: ArrayDeque<FakeStreamHandle> = ArrayDeque(listOf(FakeStreamHandle())),
) : AgentStreamClient {
    val requests = mutableListOf<StreamRequest>()
    val handle: FakeStreamHandle
        get() = issuedHandles.first()
    private val issuedHandles = mutableListOf<FakeStreamHandle>()

    override fun getStream(request: StreamRequest): StreamHandle {
        return synchronized(handles) {
            requests += request
            (handles.removeFirstOrNull() ?: FakeStreamHandle()).also { issuedHandles += it }
        }
    }
}

private class FakeStreamHandle(
    private val status: StreamStatus = StreamStatus.Completed,
) : StreamHandle {
    var refreshCount = 0
    var cancelCount = 0
    var subscriptionCancelCount = 0
    private val current = StreamSnapshot(
        schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
        streamId = "stream-1",
        status = status,
        parts = emptyList<StreamPart>(),
        rawEvents = emptyList<RawStreamEvent>(),
        updatedAtMs = 1L,
        completedAtMs = 1L,
        error = null as StreamError?,
    )

    override fun snapshot(): StreamSnapshot = current

    override fun subscribe(listener: StreamListener): StreamSubscription {
        listener.onSnapshot(current)
        return object : StreamSubscription {
            override fun cancel() {
                subscriptionCancelCount += 1
            }
        }
    }

    override fun refresh() {
        refreshCount += 1
    }

    override fun cancel() {
        cancelCount += 1
    }
}
