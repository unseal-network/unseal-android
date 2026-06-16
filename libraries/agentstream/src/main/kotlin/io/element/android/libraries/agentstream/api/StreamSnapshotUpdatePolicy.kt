/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlin.math.max

class StreamSnapshotUpdatePolicy(
    private val patchCoalesceMs: Long = DEFAULT_PATCH_COALESCE_MS,
) {
    private var lastEmittedSnapshot: StreamSnapshot? = null
    private var pendingPatchSnapshot: StreamSnapshot? = null
    private var lastPatchEmittedAtMs = 0L

    fun accept(
        snapshot: StreamSnapshot,
        nowMs: Long,
    ): StreamSnapshotUpdateDecision {
        if (snapshot.parts.isEmpty() && !snapshot.isTerminal && snapshot.error == null) {
            return StreamSnapshotUpdateDecision.Skip
        }
        if (snapshot.hasSameContentAs(lastEmittedSnapshot)) {
            return StreamSnapshotUpdateDecision.Skip
        }
        val hasStateChange = !snapshot.hasSameStateAs(lastEmittedSnapshot)
        return if (snapshot.isTerminal || hasStateChange) {
            pendingPatchSnapshot = null
            recordEmitted(snapshot, nowMs)
            StreamSnapshotUpdateDecision.Emit(snapshot)
        } else {
            pendingPatchSnapshot = snapshot
            StreamSnapshotUpdateDecision.Pending
        }
    }

    fun nextFlushDelayMs(nowMs: Long): Long? {
        pendingPatchSnapshot ?: return null
        return max(0L, patchCoalesceMs - (nowMs - lastPatchEmittedAtMs))
    }

    fun flushPending(nowMs: Long): StreamSnapshot? {
        val snapshot = pendingPatchSnapshot ?: return null
        pendingPatchSnapshot = null
        return if (snapshot.hasSameContentAs(lastEmittedSnapshot)) {
            null
        } else {
            recordEmitted(snapshot, nowMs)
            snapshot
        }
    }

    private fun recordEmitted(
        snapshot: StreamSnapshot,
        nowMs: Long,
    ) {
        lastEmittedSnapshot = snapshot
        lastPatchEmittedAtMs = nowMs
    }

    companion object {
        const val DEFAULT_PATCH_COALESCE_MS = 500L
    }
}

sealed interface StreamSnapshotUpdateDecision {
    data object Skip : StreamSnapshotUpdateDecision
    data object Pending : StreamSnapshotUpdateDecision
    data class Emit(val snapshot: StreamSnapshot) : StreamSnapshotUpdateDecision
}

private fun StreamSnapshot.hasSameStateAs(other: StreamSnapshot?): Boolean {
    return stateSignature() == other?.stateSignature()
}

private fun StreamSnapshot.hasSameContentAs(other: StreamSnapshot?): Boolean {
    return contentSignature() == other?.contentSignature()
}

private fun StreamSnapshot.stateSignature(): SnapshotStateSignature {
    return SnapshotStateSignature(
        status = status.name,
        parts = parts.map { part ->
            PartStateSignature(
                id = part.id,
                type = part.type,
                state = part.state,
                toolName = (part as? StreamPart.Tool)?.toolName,
                toolCallId = (part as? StreamPart.Tool)?.toolCallId,
            )
        },
        error = error?.message,
    )
}

private fun StreamSnapshot.contentSignature(): SnapshotContentSignature {
    return SnapshotContentSignature(
        status = status.name,
        parts = parts.map { part ->
            PartContentSignature(
                id = part.id,
                type = part.type,
                state = part.state,
                content = when (part) {
                    is StreamPart.Text -> part.text
                    is StreamPart.Reasoning -> part.text
                    is StreamPart.Tool -> listOf(part.input, part.rawInput, part.output, part.error).joinToString()
                    is StreamPart.Data -> part.data.toString()
                    is StreamPart.Source -> listOf(part.sourceType, part.title, part.url, part.payload).joinToString()
                    is StreamPart.File -> listOf(part.mediaType, part.filename, part.url, part.data).joinToString()
                    is StreamPart.Step -> listOf(part.title, part.payload).joinToString()
                    is StreamPart.Error -> part.error.toString()
                    is StreamPart.Custom -> part.payload.toString()
                },
            )
        },
        error = error?.message,
    )
}

private data class SnapshotStateSignature(
    val status: String,
    val parts: List<PartStateSignature>,
    val error: String?,
)

private data class PartStateSignature(
    val id: String,
    val type: String,
    val state: String?,
    val toolName: String?,
    val toolCallId: String?,
)

private data class SnapshotContentSignature(
    val status: String,
    val parts: List<PartContentSignature>,
    val error: String?,
)

private data class PartContentSignature(
    val id: String,
    val type: String,
    val state: String?,
    val content: String,
)
