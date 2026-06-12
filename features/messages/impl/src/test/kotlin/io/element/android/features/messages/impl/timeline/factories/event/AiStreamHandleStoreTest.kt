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
    var refreshCount = 0
    var cancelCount = 0
    var subscriptionCancelCount = 0
    private val current = StreamSnapshot(
        schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
        streamId = "stream-1",
        status = StreamStatus.Completed,
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
