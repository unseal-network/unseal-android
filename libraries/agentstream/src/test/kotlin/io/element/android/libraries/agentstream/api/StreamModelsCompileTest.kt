/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamModelsCompileTest {
    @Test
    fun `snapshot exposes text and tool part wire states`() {
        val textPart = StreamPart.Text(
            id = "text-1",
            text = "Hello",
            textState = TextPartState.Streaming,
        )
        val toolPart = StreamPart.Tool(
            id = "tool-1",
            toolName = "search",
            toolCallId = "call-1",
            input = JsonPrimitive("query"),
            output = JsonPrimitive("result"),
            toolState = ToolPartState.InputAvailable,
        )

        val snapshot = StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = "stream-1",
            status = StreamStatus.Completed,
            parts = listOf(textPart, toolPart),
            rawEvents = listOf(
                RawStreamEvent(
                    sequence = 1,
                    eventType = "text-delta",
                    partId = "text-1",
                    payload = JsonPrimitive("Hello"),
                    receivedAtMs = 123L,
                )
            ),
            updatedAtMs = 456L,
            completedAtMs = 789L,
            error = null,
        )

        assertTrue(snapshot.isTerminal)
        assertEquals(TextPartState.Streaming.wireValue, textPart.state)
        assertEquals(ToolPartState.InputAvailable.wireValue, toolPart.state)
    }
}
