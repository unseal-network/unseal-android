/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StreamSnapshotUpdatePolicyTest {
    @Test
    fun `state changes emit immediately while same-state patches are coalesced`() {
        val policy = StreamSnapshotUpdatePolicy(patchCoalesceMs = 500)

        assertThat(policy.accept(streamingText("Hel", TextPartState.Streaming), nowMs = 0))
            .isEqualTo(StreamSnapshotUpdateDecision.Emit(streamingText("Hel", TextPartState.Streaming)))

        assertThat(policy.accept(streamingText("Hello", TextPartState.Streaming), nowMs = 10))
            .isEqualTo(StreamSnapshotUpdateDecision.Pending)
        assertThat(policy.nextFlushDelayMs(nowMs = 10)).isEqualTo(490)

        assertThat(policy.flushPending(nowMs = 500))
            .isEqualTo(streamingText("Hello", TextPartState.Streaming))

        val complete = completedText("Hello!")
        assertThat(policy.accept(complete, nowMs = 501))
            .isEqualTo(StreamSnapshotUpdateDecision.Emit(complete))
    }

    @Test
    fun `unchanged snapshots are skipped`() {
        val policy = StreamSnapshotUpdatePolicy()
        val snapshot = streamingText("Hello", TextPartState.Streaming)

        assertThat(policy.accept(snapshot, nowMs = 0))
            .isEqualTo(StreamSnapshotUpdateDecision.Emit(snapshot))
        assertThat(policy.accept(snapshot, nowMs = 10))
            .isEqualTo(StreamSnapshotUpdateDecision.Skip)
        assertThat(policy.flushPending(nowMs = 500)).isNull()
    }

    private fun streamingText(
        text: String,
        state: TextPartState,
    ): StreamSnapshot {
        return snapshot(
            status = StreamStatus.Streaming,
            parts = listOf(StreamPart.Text(id = "text-1", text = text, textState = state)),
        )
    }

    private fun completedText(text: String): StreamSnapshot {
        return snapshot(
            status = StreamStatus.Completed,
            parts = listOf(StreamPart.Text(id = "text-1", text = text, textState = TextPartState.Complete)),
        )
    }

    private fun snapshot(
        status: StreamStatus,
        parts: List<StreamPart>,
    ): StreamSnapshot {
        return StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = "stream-1",
            status = status,
            parts = parts,
            rawEvents = emptyList(),
            updatedAtMs = 1L,
            completedAtMs = if (status == StreamStatus.Completed) 1L else null,
            error = null,
        )
    }
}
