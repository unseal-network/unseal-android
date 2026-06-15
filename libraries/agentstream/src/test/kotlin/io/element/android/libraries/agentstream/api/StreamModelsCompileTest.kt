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
            textState = TextPartState.Streaming.wireValue,
        )
        val toolPart = StreamPart.Tool(
            id = "tool-1",
            toolName = "search",
            toolCallId = "call-1",
            input = JsonPrimitive("query"),
            output = JsonPrimitive("result"),
            toolState = ToolPartState.InputAvailable.wireValue,
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
        assertEquals(TextPartState.Streaming.wireValue, textPart.textState)
        assertEquals(TextPartState.Streaming, textPart.textPartState)
        assertEquals(ToolPartState.InputAvailable.wireValue, toolPart.state)
        assertEquals(ToolPartState.InputAvailable.wireValue, toolPart.toolState)
        assertEquals(ToolPartState.InputAvailable, toolPart.toolPartState)
    }

    @Test
    fun `text part copy keeps generic state aligned with named wire state`() {
        val textPart = StreamPart.Text(
            id = "text-1",
            text = "Hello",
            textState = TextPartState.Streaming.wireValue,
        )

        val copied = textPart.copy(textState = TextPartState.Complete.wireValue)

        assertEquals(TextPartState.Complete.wireValue, copied.state)
        assertEquals(TextPartState.Complete.wireValue, copied.textState)
    }

    @Test
    fun `tool part copy keeps generic state aligned with named wire state`() {
        val toolPart = StreamPart.Tool(
            id = "tool-1",
            toolName = "search",
            toolCallId = "call-1",
            input = JsonPrimitive("query"),
            toolState = ToolPartState.InputAvailable.wireValue,
        )

        val copied = toolPart.copy(
            output = JsonPrimitive("result"),
            toolState = ToolPartState.OutputAvailable.wireValue,
        )

        assertEquals(ToolPartState.OutputAvailable.wireValue, copied.state)
        assertEquals(ToolPartState.OutputAvailable.wireValue, copied.toolState)
    }

    @Test
    fun `tool part states include ai sdk approval and denied states`() {
        assertEquals(ToolPartState.InputStreaming, ToolPartState.fromWire("input-streaming"))
        assertEquals(ToolPartState.InputAvailable, ToolPartState.fromWire("input-available"))
        assertEquals(ToolPartState.OutputAvailable, ToolPartState.fromWire("output-available"))
        assertEquals(ToolPartState.ApprovalRequested, ToolPartState.fromWire("approval-requested"))
        assertEquals(ToolPartState.ApprovalResponded, ToolPartState.fromWire("approval-responded"))
        assertEquals(ToolPartState.OutputError, ToolPartState.fromWire("output-error"))
        assertEquals(ToolPartState.OutputDenied, ToolPartState.fromWire("output-denied"))
    }
}
