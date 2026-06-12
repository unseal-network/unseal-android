/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.agentstream.api.AgentStreamClient
import io.element.android.libraries.agentstream.api.StreamHandle
import io.element.android.libraries.agentstream.api.StreamListener
import io.element.android.libraries.agentstream.api.StreamRequest
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSubscription
import io.element.android.libraries.di.RoomScope
import java.io.Closeable

@SingleIn(RoomScope::class)
@Inject
class AiStreamHandleStore(
    private val client: AgentStreamClient,
) {
    private val handles = linkedMapOf<String, StreamHandle>()

    fun bind(
        request: StreamRequest,
        onSnapshot: (StreamSnapshot) -> Unit,
    ): Binding {
        val handle = handles.getOrPut(request.streamId) { client.getStream(request) }
        onSnapshot(handle.snapshot())
        val subscription = handle.subscribe(StreamListener { snapshot -> onSnapshot(snapshot) })
        return Binding(
            streamId = request.streamId,
            subscription = subscription,
        )
    }

    fun refresh(streamId: String) {
        handles[streamId]?.refresh()
    }

    fun cancelStream(streamId: String) {
        handles.remove(streamId)?.cancel()
    }

    class Binding(
        val streamId: String,
        private val subscription: StreamSubscription,
    ) : Closeable {
        override fun close() {
            subscription.cancel()
        }
    }
}
