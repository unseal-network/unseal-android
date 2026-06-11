/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import io.element.android.libraries.agentstream.jni.UnsealAgentStreamNative
import java.io.Closeable

/**
 * Thin Android facade over the shared Rust AI SDK stream reducer.
 *
 * The Android client owns network, scheduling, storage, and rendering. This
 * session only consumes SSE text chunks and returns the latest stream snapshot
 * JSON using the shared Rust parts schema.
 */
class AgentStreamSession(
    streamId: String,
    includeRawEvents: Boolean = false,
    includeNormalizedEvents: Boolean = true,
) : Closeable {
    private var nativeSession: Long = UnsealAgentStreamNative.nativeCreateSession(
        streamId = streamId,
        includeRawEvents = includeRawEvents,
        includeNormalizedEvents = includeNormalizedEvents,
    )

    init {
        check(nativeSession != 0L) { "Unable to create native agent stream session" }
    }

    val isClosed: Boolean
        get() = nativeSession == 0L

    fun applySseChunk(chunk: String): String {
        val session = requireOpenSession()
        return UnsealAgentStreamNative.nativeApplySseChunk(session, chunk)
    }

    fun finish(): String {
        val session = requireOpenSession()
        return UnsealAgentStreamNative.nativeFinish(session)
    }

    fun snapshot(): String {
        val session = requireOpenSession()
        return UnsealAgentStreamNative.nativeSnapshot(session)
    }

    override fun close() {
        val session = nativeSession
        if (session != 0L) {
            nativeSession = 0L
            UnsealAgentStreamNative.nativeDestroySession(session)
        }
    }

    private fun requireOpenSession(): Long {
        val session = nativeSession
        check(session != 0L) { "Agent stream session is closed" }
        return session
    }

    companion object {
        fun isNativeAvailable(): Boolean = UnsealAgentStreamNative.nativeIsAvailable()
    }
}
