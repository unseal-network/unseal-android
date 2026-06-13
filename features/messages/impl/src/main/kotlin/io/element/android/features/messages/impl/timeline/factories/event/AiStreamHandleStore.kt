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
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.StreamStorageProvider
import io.element.android.libraries.agentstream.api.StreamSubscription
import io.element.android.libraries.di.RoomScope
import java.io.Closeable
import timber.log.Timber

@SingleIn(RoomScope::class)
@Inject
class AiStreamHandleStore(
    private val client: AgentStreamClient,
    private val storageProvider: StreamStorageProvider,
) {
    private val handles = linkedMapOf<String, StreamHandle>()
    private val snapshots = linkedMapOf<String, StreamSnapshot>()
    private val lock = Any()

    fun cachedSnapshot(streamId: String): StreamSnapshot? {
        return synchronized(lock) {
            handles[streamId]?.snapshot()?.takeIf { it.hasUsableContent() }
                ?: snapshots[streamId]
        }.also { snapshot ->
            Timber.tag(DBG).d(
                "room cache %s stream=%s status=%s parts=%d",
                if (snapshot == null) "MISS" else "HIT",
                streamId,
                snapshot?.status,
                snapshot?.parts?.size ?: 0,
            )
        }
    }

    suspend fun cachedCompletedSnapshot(streamId: String): StreamSnapshot? {
        cachedSnapshot(streamId)
            ?.takeIf { it.status == StreamStatus.Completed && it.hasUsableContent() }
            ?.let { return it }

        val storedSnapshot = storageProvider.load(streamId)
            ?.takeIf { it.status == StreamStatus.Completed && it.hasUsableContent() }
            ?: return null
        synchronized(lock) {
            rememberSnapshotLocked(streamId, storedSnapshot)
        }
        return storedSnapshot
    }

    fun bind(
        request: StreamRequest,
        onSnapshot: (StreamSnapshot) -> Unit,
    ): Binding {
        val handle = synchronized(lock) {
            val cached = handles[request.streamId]
            if (cached != null && cached.snapshot().status.isRetryableTerminalCache()) {
                handles.remove(request.streamId)
            }
            handles.getOrPut(request.streamId) { client.getStream(request) }
        }
        val initialSnapshot = handle.snapshot()
        synchronized(lock) {
            rememberSnapshotLocked(request.streamId, initialSnapshot)
        }
        onSnapshot(initialSnapshot)
        val subscription = handle.subscribe(StreamListener { snapshot ->
            synchronized(lock) {
                rememberSnapshotLocked(request.streamId, snapshot)
            }
            onSnapshot(snapshot)
        })
        return Binding(
            streamId = request.streamId,
            subscription = subscription,
        )
    }

    fun refresh(streamId: String) {
        synchronized(lock) { handles[streamId] }?.refresh()
    }

    fun cancelStream(streamId: String) {
        synchronized(lock) { handles.remove(streamId) }?.cancel()
    }

    class Binding(
        val streamId: String,
        private val subscription: StreamSubscription,
    ) : Closeable {
        override fun close() {
            subscription.cancel()
        }
    }

    private fun rememberSnapshotLocked(
        streamId: String,
        snapshot: StreamSnapshot,
    ) {
        if (!snapshot.hasUsableContent()) {
            return
        }
        snapshots[streamId] = snapshot
        Timber.tag(DBG).d("room cache PUT stream=%s status=%s parts=%d", streamId, snapshot.status, snapshot.parts.size)
        while (snapshots.size > MAX_CACHED_SNAPSHOTS) {
            val eldestKey = snapshots.keys.firstOrNull() ?: return
            snapshots.remove(eldestKey)
        }
    }

    private companion object {
        const val DBG = "AiStreamDbg"
        const val MAX_CACHED_SNAPSHOTS = 256
    }
}

private fun StreamStatus.isRetryableTerminalCache(): Boolean {
    return this == StreamStatus.Failed || this == StreamStatus.Cancelled
}

private fun StreamSnapshot.hasUsableContent(): Boolean {
    return parts.isNotEmpty() || isTerminal
}
