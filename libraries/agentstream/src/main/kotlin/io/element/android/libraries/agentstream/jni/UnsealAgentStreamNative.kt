/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.jni

internal object UnsealAgentStreamNative {
    init {
        System.loadLibrary("unseal_agent_stream")
    }

    @JvmStatic
    external fun nativeCreateSession(
        streamId: String,
        includeRawEvents: Boolean,
        includeNormalizedEvents: Boolean,
    ): Long

    @JvmStatic
    external fun nativeApplySseChunk(
        session: Long,
        chunk: String,
    ): String

    @JvmStatic
    external fun nativeFinish(session: Long): String

    @JvmStatic
    external fun nativeSnapshot(session: Long): String

    @JvmStatic
    external fun nativeDestroySession(session: Long)

    @JvmStatic
    external fun nativeIsAvailable(): Boolean
}
