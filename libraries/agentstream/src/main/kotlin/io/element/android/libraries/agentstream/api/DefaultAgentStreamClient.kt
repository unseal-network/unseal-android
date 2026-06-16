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
                if (isActiveRun(runId)) {
                    completedBeforeRefresh?.let(::rememberCompleted)
                }
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
                        val snapshot = storedSnapshot
                            .withStreamIdFallback(request.streamId)
                            .normalizedForTerminalState()
                        // Only a Completed snapshot is a durable cache hit. Genuine stream-level
                        // errors arrive as SSE data and are stored as Completed (with an error part),
                        // so they stay cached. A stored Failed/Cancelled is a transient transport
                        // failure (network/5xx) — ignore it and re-open the stream so it retries.
                        if (snapshot.status == StreamStatus.Completed) {
                            if (!publishIfActive(runId, snapshot)) {
                                return
                            }
                            if (isActiveRun(runId)) {
                                rememberCompleted(snapshot)
                            }
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
                        if (!publishIfActive(runId, snapshot)) {
                            throw CancellationException("Stale stream run")
                        }
                    }
                    val finalSnapshot = snapshotParser
                        .parseOrFailed(activeSession.finish(), request.streamId)
                        .withStreamIdFallback(request.streamId)
                        .asCompleted(clock())
                            .normalizedForTerminalState()
                    if (!isActiveRun(runId)) {
                        return
                    }
                    rememberCompleted(finalSnapshot)
                    try {
                        storageProvider.save(finalSnapshot)
                    } catch (throwable: CancellationException) {
                        throw throwable
                    } catch (_: Throwable) {
                        // Completion is already terminal for the UI; persistence can retry on a future path.
                    }
                    if (!publishIfActive(runId, finalSnapshot)) {
                        return
                    }
                    finishInFlight(runId)
                }
            } catch (throwable: CancellationException) {
                finishInFlight(runId)
            } catch (throwable: Throwable) {
                try {
                    val previousSnapshot = synchronized(lock) {
                        currentSnapshot.takeIf { isActiveRunLocked(runId) }
                    }
                    if (previousSnapshot != null) {
                        val failed = failedSnapshot(request.streamId, throwable, previousSnapshot)
                        // A thrown failure is transport-level (network blip, timeout, 5xx, HTTP error)
                        // — never persist it, so re-opening the stream retries instead of serving a
                        // stale failure. Genuine stream-level errors arrive as SSE data and are stored
                        // via the Completed path, so they remain cached.
                        if (publishIfActive(runId, failed)) {
                            if (!isActiveRun(runId)) {
                                return
                            }
                            completedBeforeRefresh?.let(::rememberCompleted)
                        }
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

        private fun publishIfActive(runId: Long, snapshot: StreamSnapshot): Boolean {
            val snapshotToPublish = snapshot.normalizedForTerminalState()
            val callbacks = synchronized(lock) {
                if (!isActiveRunLocked(runId)) {
                    return false
                }
                // Terminal Completed is monotonic: once a stream has completed, a stale non-completed
                // snapshot (e.g. a late streaming chunk or a replayed run) must NOT overwrite it —
                // otherwise the tool card regresses from done back to "Running tool…".
                if (currentSnapshot.status == StreamStatus.Completed && snapshotToPublish.status != StreamStatus.Completed) {
                    return false
                }
                currentSnapshot = snapshotToPublish
                listeners.values.toList()
            }
            callbacks.forEach { listener ->
                listener.onSnapshot(snapshotToPublish)
            }
            return true
        }

        private fun publish(snapshot: StreamSnapshot) {
            val snapshotToPublish = snapshot.normalizedForTerminalState()
            val callbacks = synchronized(lock) {
                currentSnapshot = snapshotToPublish
                listeners.values.toList()
            }
            callbacks.forEach { listener ->
                listener.onSnapshot(snapshotToPublish)
            }
        }

        private fun rememberCompleted(snapshot: StreamSnapshot) {
            val normalized = snapshot.normalizedForTerminalState()
            if (normalized.status == StreamStatus.Completed) {
                synchronized(lock) {
                    completedCache[normalized.streamId] = normalized
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

        private fun isActiveRun(runId: Long): Boolean {
            return synchronized(lock) {
                isActiveRunLocked(runId)
            }
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
            updatedAtMs = now,
            completedAtMs = completedAtMs ?: now,
            error = null,
        )
    }

    private companion object {
        private const val MAX_MEMORY_ENTRIES = 128
        private const val CACHE_LOAD_FACTOR = 0.75f
    }
}
