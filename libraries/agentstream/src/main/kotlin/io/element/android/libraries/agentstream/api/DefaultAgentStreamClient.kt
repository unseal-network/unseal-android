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
            completedCache[request.streamId]?.let { return DefaultStreamHandle(request, it) }
            handles.getOrPut(request.streamId) {
                DefaultStreamHandle(request)
            }
        }
        handle.startIfNeeded()
        return handle
    }

    private inner class DefaultStreamHandle(
        private val request: StreamRequest,
        initialSnapshot: StreamSnapshot = loadingSnapshot(request.streamId),
    ) : StreamHandle {
        private val listeners = mutableMapOf<String, StreamListener>()
        private var currentSnapshot = initialSnapshot
        private var task: StreamTask? = null
        private var isStarting = false
        private var session: StreamReducerSession? = null
        private var activeRunId = 0L

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
            start(forceRefresh = true)
        }

        override fun cancel() {
            val taskToCancel: StreamTask?
            val sessionToClose: StreamReducerSession?
            val snapshotToPublish = synchronized(lock) {
                activeRunId++
                taskToCancel = task
                task = null
                isStarting = false
                sessionToClose = session
                session = null
                handles.remove(request.streamId)
                if (currentSnapshot.isTerminal) {
                    null
                } else {
                    cancelledSnapshot(request.streamId, currentSnapshot)
                }
            }
            taskToCancel?.cancel()
            sessionToClose?.close()
            snapshotToPublish?.let(::publish)
        }

        fun startIfNeeded() {
            start(forceRefresh = false)
        }

        private fun start(forceRefresh: Boolean) {
            val completedBeforeRefresh: StreamSnapshot?
            val loadingToPublish: StreamSnapshot?
            var runId: Long
            val shouldStart = synchronized(lock) {
                completedBeforeRefresh = completedCache[request.streamId] ?: currentSnapshot.takeIf { it.status == StreamStatus.Completed }
                if (task != null || isStarting || (!forceRefresh && currentSnapshot.status == StreamStatus.Completed)) {
                    loadingToPublish = null
                    runId = activeRunId
                    false
                } else if (handles[request.streamId] != null && handles[request.streamId] !== this) {
                    loadingToPublish = null
                    runId = activeRunId
                    false
                } else {
                    if (forceRefresh) {
                        completedCache.remove(request.streamId)
                    }
                    handles[request.streamId] = this
                    activeRunId++
                    runId = activeRunId
                    isStarting = true
                    currentSnapshot = loadingSnapshot(request.streamId)
                    loadingToPublish = currentSnapshot.takeIf { forceRefresh }
                    true
                }
            }
            if (!shouldStart) return
            loadingToPublish?.let(::publish)

            val streamTask = try {
                taskRunner.run(request.streamId) {
                    runStream(
                        runId = runId,
                        skipStorageLoad = forceRefresh,
                        completedBeforeRefresh = completedBeforeRefresh,
                    )
                }
            } catch (throwable: Throwable) {
                val shouldPublishFailed = synchronized(lock) {
                    if (isActiveRunLocked(runId)) {
                        isStarting = false
                        true
                    } else {
                        false
                    }
                }
                if (!shouldPublishFailed) {
                    return
                }
                val failed = failedSnapshot(request.streamId, throwable, snapshot())
                publish(failed)
                completedBeforeRefresh?.let(::rememberCompleted)
                finishInFlight(runId)
                return
            }
            var cancelNewTask = false
            synchronized(lock) {
                if (task == null && isStarting && isActiveRunLocked(runId)) {
                    task = streamTask
                    isStarting = false
                } else {
                    cancelNewTask = true
                }
            }
            if (cancelNewTask) {
                streamTask.cancel()
            }
        }

        private suspend fun runStream(
            runId: Long,
            skipStorageLoad: Boolean,
            completedBeforeRefresh: StreamSnapshot?,
        ) {
            var reducerSessionForRun: StreamReducerSession? = null
            try {
                if (!skipStorageLoad) {
                    storageProvider.load(request.streamId)?.let { storedSnapshot ->
                        publish(storedSnapshot.withStreamIdFallback(request.streamId))
                        if (storedSnapshot.isTerminal) {
                            rememberCompleted(storedSnapshot.withStreamIdFallback(request.streamId))
                            finishInFlight(runId)
                            return
                        }
                    }
                }

                val reducerSession = reducerSessionFactory.create(
                    streamId = request.streamId,
                    includeRawEvents = request.includeRawEvents,
                )
                reducerSessionForRun = reducerSession
                val shouldRun = synchronized(lock) {
                    if (isActiveRunLocked(runId)) {
                        session = reducerSession
                        true
                    } else {
                        false
                    }
                }
                if (!shouldRun) {
                    reducerSession.close()
                    return
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
                    try {
                        storageProvider.save(finalSnapshot)
                    } catch (throwable: CancellationException) {
                        throw throwable
                    } catch (_: Throwable) {
                        // Completion is already terminal for the UI; persistence can retry on a future path.
                    }
                    finishInFlight(runId)
                }
            } catch (throwable: CancellationException) {
                finishInFlight(runId)
            } catch (throwable: Throwable) {
                try {
                    val failed = failedSnapshot(request.streamId, throwable, snapshot())
                    publish(failed)
                    if (completedBeforeRefresh == null && !hasCompletedSnapshot(request.streamId)) {
                        try {
                            storageProvider.save(failed)
                        } catch (_: Throwable) {
                            // The failed snapshot is already visible; a persistence failure must not keep the stream in flight.
                        }
                    } else {
                        completedBeforeRefresh?.let(::rememberCompleted)
                    }
                } finally {
                    finishInFlight(runId)
                }
            } finally {
                synchronized(lock) {
                    if (activeRunId == runId && session === reducerSessionForRun) {
                        session = null
                    }
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

        private fun finishInFlight(runId: Long) {
            synchronized(lock) {
                if (!isActiveRunLocked(runId)) {
                    return
                }
                if (handles[request.streamId] === this) {
                    handles.remove(request.streamId)
                }
                task = null
                isStarting = false
            }
        }

        private fun isActiveRunLocked(runId: Long): Boolean {
            return activeRunId == runId && handles[request.streamId] === this
        }
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
        val errorPart = StreamPart.Error(
            id = "error-$streamId",
            error = error,
            state = "error",
        )
        var replacedErrorPart = false
        val parts = previous.parts.map { part ->
            if (part is StreamPart.Error && part.id == errorPart.id) {
                replacedErrorPart = true
                errorPart
            } else {
                part
            }
        }.let { updatedParts ->
            if (replacedErrorPart) {
                updatedParts
            } else {
                updatedParts + errorPart
            }
        }
        return previous.copy(
            streamId = previous.streamId.ifBlank { streamId },
            status = StreamStatus.Failed,
            parts = parts,
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
