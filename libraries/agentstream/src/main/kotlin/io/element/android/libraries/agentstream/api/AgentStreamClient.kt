/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import java.io.Closeable

interface AgentStreamClient {
    fun getStream(request: StreamRequest): StreamHandle
}

interface StreamHandle {
    fun snapshot(): StreamSnapshot
    fun subscribe(listener: StreamListener): StreamSubscription
    fun refresh()
    fun cancel()
}

fun interface StreamListener {
    fun onSnapshot(snapshot: StreamSnapshot)
}

interface StreamSubscription {
    fun cancel()
}

/**
 * Stores SDK snapshot models; adapters should persist them through the SDK JSON codec once wired.
 */
interface StreamStorageProvider {
    suspend fun load(streamId: String): StreamSnapshot?
    suspend fun save(snapshot: StreamSnapshot)
    suspend fun delete(streamId: String)
}

interface StreamHttpClient {
    suspend fun openStream(
        request: StreamRequest,
        onChunk: suspend (String) -> Unit,
    )
}

/**
 * Thrown by a [StreamHttpClient] to classify a stream fetch failure.
 *
 * When [retryable] is true (network/transport blips, timeouts, 5xx, rate limiting) the failure is
 * transient: the client surfaces it to the UI but does NOT persist it, so re-opening the stream
 * retries. When false (e.g. a 4xx / genuine stream-level error) the failure is durable and is
 * cached/persisted like a terminal result.
 */
class StreamTransportException(
    message: String,
    val retryable: Boolean,
    cause: Throwable? = null,
) : Exception(message, cause)

interface StreamTaskRunner {
    fun run(
        key: String,
        block: suspend () -> Unit,
    ): StreamTask
}

interface StreamTask {
    fun cancel()
}

interface StreamReducerSession : Closeable {
    fun applySseChunk(chunk: String): String
    fun finish(): String
    fun snapshot(): String
}

interface StreamReducerSessionFactory {
    fun create(
        streamId: String,
        includeRawEvents: Boolean,
    ): StreamReducerSession
}

class NativeStreamReducerSessionFactory : StreamReducerSessionFactory {
    override fun create(
        streamId: String,
        includeRawEvents: Boolean,
    ): StreamReducerSession {
        return NativeStreamReducerSession(
            AgentStreamSession(
                streamId = streamId,
                includeRawEvents = includeRawEvents,
                includeNormalizedEvents = true,
            )
        )
    }

    private class NativeStreamReducerSession(
        private val delegate: AgentStreamSession,
    ) : StreamReducerSession {
        override fun applySseChunk(chunk: String): String = delegate.applySseChunk(chunk)

        override fun finish(): String = delegate.finish()

        override fun snapshot(): String = delegate.snapshot()

        override fun close() = delegate.close()
    }
}
