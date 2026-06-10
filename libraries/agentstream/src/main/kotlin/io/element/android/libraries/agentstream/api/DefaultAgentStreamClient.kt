/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlin.coroutines.cancellation.CancellationException
import java.util.LinkedHashMap
import java.util.UUID

class DefaultAgentStreamClient(
    private val storageProvider: StreamStorageProvider,
    private val httpClient: StreamHttpClient,
    private val taskRunner: StreamTaskRunner,
    private val reducerSessionFactory: StreamReducerSessionFactory = NativeStreamReducerSessionFactory(),
    private val snapshotParser: StreamSnapshotParser = StreamSnapshotParser(),
    private val clock: () -> Long = { System.currentTimeMillis() },
) : AgentStreamClient {
    private val lock = Any()
    private val handles = mutableMapOf<String, DefaultStreamHandle>()
    private val completedCache = object : LinkedHashMap<String, StreamSnapshot>(MAX_MEMORY_ENTRIES, CACHE_LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, StreamSnapshot>?): Boolean {
            return size > MAX_MEMORY_ENTRIES
        }
    }

    override fun getStream(request: StreamRequest): StreamHandle {
        val handle = synchronized(lock) {
            completedCache[request.streamId]?.let { return CachedStreamHandle(it) }
            handles.getOrPut(request.streamId) {
                DefaultStreamHandle(request)
            }
        }
        handle.startIfNeeded()
        return handle
    }

    private inner class DefaultStreamHandle(
        private val request: StreamRequest,
    ) : StreamHandle {
        private val listeners = mutableMapOf<String, StreamListener>()
        private var currentSnapshot = loadingSnapshot(request.streamId)
        private var task: StreamTask? = null
        private var session: StreamReducerSession? = null

        override fun snapshot(): StreamSnapshot {
            return synchronized(lock) { currentSnapshot }
        }

        override fun subscribe(listener: StreamListener): StreamSubscription {
            val id = UUID.randomUUID().toString()
            val snapshot = synchronized(lock) {
                listeners[id] = listener
                currentSnapshot
            }
            listener.onSnapshot(snapshot)
            return object : StreamSubscription {
                override fun cancel() {
                    synchronized(lock) {
                        listeners.remove(id)
                    }
                }
            }
        }

        override fun refresh() {
            val snapshot = synchronized(lock) {
                completedCache[request.streamId]
            }
            if (snapshot != null) {
                publish(snapshot)
                return
            }
            startIfNeeded()
        }

        override fun cancel() {
            val snapshotToPublish = synchronized(lock) {
                task?.cancel()
                task = null
                session?.close()
                session = null
                handles.remove(request.streamId)
                if (currentSnapshot.isTerminal) {
                    null
                } else {
                    cancelledSnapshot(request.streamId, currentSnapshot)
                }
            }
            snapshotToPublish?.let(::publish)
        }

        fun startIfNeeded() {
            val shouldStart = synchronized(lock) {
                if (task != null || currentSnapshot.status == StreamStatus.Completed) {
                    false
                } else {
                    currentSnapshot = loadingSnapshot(request.streamId)
                    true
                }
            }
            if (!shouldStart) return

            val streamTask = taskRunner.run(request.streamId) {
                runStream()
            }
            var cancelNewTask = false
            synchronized(lock) {
                if (task == null && handles[request.streamId] === this) {
                    task = streamTask
                } else {
                    cancelNewTask = true
                }
            }
            if (cancelNewTask) {
                streamTask.cancel()
            }
        }

        private suspend fun runStream() {
            try {
                storageProvider.load(request.streamId)?.let { storedSnapshot ->
                    publish(storedSnapshot.withStreamIdFallback(request.streamId))
                    if (storedSnapshot.isTerminal) {
                        rememberCompleted(storedSnapshot.withStreamIdFallback(request.streamId))
                        finishInFlight()
                        return
                    }
                }

                val reducerSession = reducerSessionFactory.create(
                    streamId = request.streamId,
                    includeRawEvents = request.includeRawEvents,
                )
                synchronized(lock) {
                    session = reducerSession
                }
                reducerSession.use { activeSession ->
                    httpClient.openStream(request) { chunk ->
                        val snapshot = snapshotParser
                            .parseOrFailed(activeSession.applySseChunk(chunk), request.streamId)
                            .withStreamIdFallback(request.streamId)
                        publish(snapshot)
                    }
                    val finalSnapshot = snapshotParser
                        .parseOrFailed(activeSession.finish(), request.streamId)
                        .withStreamIdFallback(request.streamId)
                        .asCompleted(clock())
                    publish(finalSnapshot)
                    rememberCompleted(finalSnapshot)
                    storageProvider.save(finalSnapshot)
                    finishInFlight()
                }
            } catch (throwable: CancellationException) {
                finishInFlight()
            } catch (throwable: Throwable) {
                val failed = failedSnapshot(request.streamId, throwable, snapshot())
                publish(failed)
                if (!hasCompletedSnapshot(request.streamId)) {
                    storageProvider.save(failed)
                }
                finishInFlight()
            } finally {
                synchronized(lock) {
                    session = null
                }
            }
        }

        private fun publish(snapshot: StreamSnapshot) {
            val callbacks = synchronized(lock) {
                currentSnapshot = snapshot
                listeners.values.toList()
            }
            callbacks.forEach { listener ->
                listener.onSnapshot(snapshot)
            }
        }

        private fun rememberCompleted(snapshot: StreamSnapshot) {
            if (snapshot.status == StreamStatus.Completed) {
                synchronized(lock) {
                    completedCache[snapshot.streamId] = snapshot
                }
            }
        }

        private fun finishInFlight() {
            synchronized(lock) {
                if (handles[request.streamId] === this) {
                    handles.remove(request.streamId)
                }
                task = null
            }
        }
    }

    private class CachedStreamHandle(
        private val snapshot: StreamSnapshot,
    ) : StreamHandle {
        override fun snapshot(): StreamSnapshot = snapshot

        override fun subscribe(listener: StreamListener): StreamSubscription {
            listener.onSnapshot(snapshot)
            return object : StreamSubscription {
                override fun cancel() = Unit
            }
        }

        override fun refresh() = Unit

        override fun cancel() = Unit
    }

    private fun hasCompletedSnapshot(streamId: String): Boolean {
        return synchronized(lock) {
            completedCache[streamId]?.status == StreamStatus.Completed
        }
    }

    private fun loadingSnapshot(streamId: String): StreamSnapshot {
        return StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = streamId,
            status = StreamStatus.Loading,
            parts = emptyList(),
            rawEvents = emptyList(),
            updatedAtMs = clock(),
            completedAtMs = null,
            error = null,
        )
    }

    private fun cancelledSnapshot(
        streamId: String,
        previous: StreamSnapshot,
    ): StreamSnapshot {
        return previous.copy(
            streamId = previous.streamId.ifBlank { streamId },
            status = StreamStatus.Cancelled,
            updatedAtMs = clock(),
            completedAtMs = null,
            error = null,
        )
    }

    private fun failedSnapshot(
        streamId: String,
        throwable: Throwable,
        previous: StreamSnapshot,
    ): StreamSnapshot {
        val error = StreamError(
            message = throwable.message.orEmpty().ifBlank { "Failed to load stream." },
        )
        return previous.copy(
            streamId = previous.streamId.ifBlank { streamId },
            status = StreamStatus.Failed,
            updatedAtMs = clock(),
            completedAtMs = null,
            error = error,
        )
    }

    private fun StreamSnapshot.withStreamIdFallback(streamId: String): StreamSnapshot {
        return if (this.streamId.isBlank()) {
            copy(streamId = streamId)
        } else {
            this
        }
    }

    private fun StreamSnapshot.asCompleted(now: Long): StreamSnapshot {
        return copy(
            status = StreamStatus.Completed,
            parts = parts.map { it.asCompletedPart() },
            updatedAtMs = now,
            completedAtMs = completedAtMs ?: now,
            error = null,
        )
    }

    private fun StreamPart.asCompletedPart(): StreamPart {
        return when (this) {
            is StreamPart.Text -> copy(textState = TextPartState.Complete.wireValue)
            is StreamPart.Reasoning -> copy(reasoningState = TextPartState.Complete.wireValue)
            else -> this
        }
    }

    private companion object {
        private const val MAX_MEMORY_ENTRIES = 128
        private const val CACHE_LOAD_FACTOR = 0.75f
    }
}
