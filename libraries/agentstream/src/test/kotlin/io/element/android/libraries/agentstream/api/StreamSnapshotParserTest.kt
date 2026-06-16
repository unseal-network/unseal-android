/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamSnapshotParserTest {
    @Test
    fun `parses mixed standard and custom parts`() {
        val parser = StreamSnapshotParser(clock = { 987L })

        val snapshot = parser.parse(
            """
            {
              "schemaVersion": 7,
              "stream_id": "stream-1",
              "status": "streaming",
              "updatedAtMs": 123,
              "parts": [
                {"type": "text", "id": "text-1", "state": "complete", "text": "Hello"},
                {"type": "reasoning", "id": "reason-1", "text": "Thinking"},
                {"type": "tool", "toolCallId": "call-1", "toolName": "search", "state": "output-available", "input": {"query": "matrix"}, "rawInput": {"rawQuery": "matrix raw"}, "output": {"result": 1}, "errorText": "ignored", "display_name": "Search"},
                {"type": "tool-weather", "id": "weather-1", "state": "input-available", "input": {"city": "Paris"}},
                {"type": "source-url", "id": "source-1", "sourceType": "url", "title": "Docs", "url": "https://example.com", "filename": "docs.html", "media_type": "text/html"},
                {"type": "data-ui-spec", "id": "data-1", "data": {"component": "card"}},
                {"type": "file", "id": "file-1", "filename": "report.pdf", "mediaType": "application/pdf", "url": "mxc://file", "data": {"size": 42}},
                {"type": "step-start", "id": "step-1", "title": "Plan", "extra": true},
                {"type": "error", "id": "error-1", "error": {"message": "Boom", "code": "E_BANG", "details": {"line": 1}}},
                {"type": "custom-x", "id": "custom-1", "custom": true}
              ],
              "rawEvents": [
                {"type": "text-delta", "id": "text-1", "payload": {"delta": "Hi"}, "receivedAtMs": 555},
                {"eventType": "tool-call", "sequence": 9, "partId": "call-1", "foo": "bar"}
              ]
            }
            """.trimIndent()
        )

        assertEquals(7, snapshot.schemaVersion)
        assertEquals("stream-1", snapshot.streamId)
        assertEquals(StreamStatus.Streaming, snapshot.status)
        assertEquals(123L, snapshot.updatedAtMs)
        assertNull(snapshot.completedAtMs)

        val text = snapshot.parts[0] as StreamPart.Text
        assertEquals("text-1", text.id)
        assertEquals("Hello", text.text)
        assertEquals(TextPartState.Complete.wireValue, text.textState)
        assertEquals(TextPartState.Complete, text.textPartState)

        val reasoning = snapshot.parts[1] as StreamPart.Reasoning
        assertEquals("reason-1", reasoning.id)
        assertEquals("Thinking", reasoning.text)
        assertEquals(TextPartState.Streaming.wireValue, reasoning.reasoningState)

        val tool = snapshot.parts[2] as StreamPart.Tool
        assertEquals("call-1", tool.id)
        assertEquals("search", tool.toolName)
        assertEquals("call-1", tool.toolCallId)
        assertEquals(ToolPartState.OutputAvailable.wireValue, tool.toolState)
        assertEquals(ToolPartState.OutputAvailable, tool.toolPartState)
        assertEquals(JsonPrimitive("matrix"), tool.input?.jsonObject?.get("query"))
        assertEquals(JsonPrimitive("matrix raw"), tool.rawInput?.jsonObject?.get("rawQuery"))
        assertEquals(JsonPrimitive(1), tool.output?.jsonObject?.get("result"))
        assertEquals("ignored", tool.error?.message)
        assertEquals("Search", tool.title)

        val prefixedTool = snapshot.parts[3] as StreamPart.Tool
        assertEquals("weather", prefixedTool.toolName)
        assertEquals(ToolPartState.InputAvailable.wireValue, prefixedTool.toolState)

        val source = snapshot.parts[4] as StreamPart.Source
        assertEquals("url", source.sourceType)
        assertEquals("Docs", source.title)
        assertEquals("https://example.com", source.url)
        assertEquals("source-url", source.type)
        assertEquals(JsonPrimitive("docs.html"), source.payload?.jsonObject?.get("filename"))
        assertEquals(JsonPrimitive("text/html"), source.payload?.jsonObject?.get("media_type"))

        val data = snapshot.parts[5] as StreamPart.Data
        assertEquals("data-ui-spec", data.type)
        assertEquals(JsonPrimitive("card"), data.data.jsonObject.getValue("component"))

        val file = snapshot.parts[6] as StreamPart.File
        assertEquals("report.pdf", file.filename)
        assertEquals("application/pdf", file.mediaType)
        assertEquals("mxc://file", file.url)
        assertEquals(JsonPrimitive(42), file.data?.jsonObject?.get("size"))

        val step = snapshot.parts[7] as StreamPart.Step
        assertEquals("Plan", step.title)
        assertEquals("step-start", step.type)
        assertEquals(JsonPrimitive(true), step.payload?.jsonObject?.get("extra"))

        val error = snapshot.parts[8] as StreamPart.Error
        assertEquals("Boom", error.error.message)
        assertEquals("E_BANG", error.error.code)
        assertTrue(error.error.raw is JsonObject)

        val custom = snapshot.parts[9] as StreamPart.Custom
        assertEquals("custom-x", custom.customType)
        assertEquals(JsonPrimitive(true), custom.payload.jsonObject.getValue("custom"))

        assertEquals(2, snapshot.rawEvents.size)
        assertEquals(0L, snapshot.rawEvents[0].sequence)
        assertEquals("text-delta", snapshot.rawEvents[0].eventType)
        assertEquals("text-1", snapshot.rawEvents[0].partId)
        assertEquals(JsonPrimitive("Hi"), snapshot.rawEvents[0].payload.jsonObject.getValue("delta"))
        assertEquals(555L, snapshot.rawEvents[0].receivedAtMs)
        assertEquals(9L, snapshot.rawEvents[1].sequence)
        assertEquals("tool-call", snapshot.rawEvents[1].eventType)
        assertEquals("call-1", snapshot.rawEvents[1].partId)
        assertEquals(JsonPrimitive("bar"), snapshot.rawEvents[1].payload.jsonObject.getValue("foo"))
        assertEquals(987L, snapshot.rawEvents[1].receivedAtMs)
    }

    @Test
    fun `maps terminal status aliases and completed clock default`() {
        val parser = StreamSnapshotParser(clock = { 456L })

        assertEquals(StreamStatus.Completed, parser.parse("""{"status":"done"}""").status)
        assertEquals(StreamStatus.Completed, parser.parse("""{"status":"completed"}""").status)
        assertEquals(StreamStatus.Completed, parser.parse("""{"status":"complete"}""").status)
        assertEquals(456L, parser.parse("""{"status":"completed"}""").completedAtMs)
        assertEquals(StreamStatus.Failed, parser.parse("""{"status":"error"}""").status)
        assertEquals(StreamStatus.Failed, parser.parse("""{"status":"failed"}""").status)
        assertEquals(StreamStatus.Cancelled, parser.parse("""{"status":"canceled"}""").status)
        assertEquals(StreamStatus.Cancelled, parser.parse("""{"status":"cancelled"}""").status)
        assertEquals(StreamStatus.Idle, parser.parse("""{"status":"unexpected"}""").status)
    }

    @Test
    fun `keeps raw input separate when tool input is absent`() {
        val parser = StreamSnapshotParser(clock = { 987L })

        val snapshot = parser.parse(
            """
            {
              "parts": [
                {"type": "dynamic-tool", "id": "tool-1", "state": "input-streaming", "raw_input": {"query": "matrix"}}
              ]
            }
            """.trimIndent()
        )

        val tool = snapshot.parts.single() as StreamPart.Tool
        assertNull(tool.input)
        assertEquals(JsonPrimitive("matrix"), tool.rawInput?.jsonObject?.get("query"))
    }

    @Test
    fun `parser preserves approval and denied tool states`() {
        val snapshot = StreamSnapshotParser().parseOrFailed(
            """
            {
              "streamId": "stream-approval",
              "status": "done",
              "parts": [
                {
                  "type": "tool-mail",
                  "id": "tool-approval",
                  "state": "approval-requested",
                  "input": { "subject": "Confirm" }
                },
                {
                  "type": "tool-mail",
                  "id": "tool-denied",
                  "state": "output-denied",
                  "errorText": "User denied"
                }
              ]
            }
            """.trimIndent()
        )

        val approval = snapshot.parts[0] as StreamPart.Tool
        val denied = snapshot.parts[1] as StreamPart.Tool
        assertEquals("approval-requested", approval.toolState)
        assertEquals(ToolPartState.ApprovalRequested, approval.toolPartState)
        assertEquals("output-denied", denied.toolState)
        assertEquals(ToolPartState.OutputDenied, denied.toolPartState)
    }

    @Test
    fun `preserves unknown present part states and only defaults absent states`() {
        val parser = StreamSnapshotParser(clock = { 987L })

        val snapshot = parser.parse(
            """
            {
              "parts": [
                {"type": "text", "id": "text-1", "state": "blocked-waiting", "text": "Blocked"},
                {"type": "text", "id": "text-2", "text": "Default text"},
                {"type": "reasoning", "id": "reason-1", "reasoningState": "blocked-waiting", "text": "Blocked reasoning"},
                {"type": "reasoning", "id": "reason-2", "text": "Default reasoning"},
                {"type": "tool", "id": "tool-1", "toolState": "output-streaming"},
                {"type": "tool", "id": "tool-2"}
              ]
            }
            """.trimIndent()
        )

        assertEquals("blocked-waiting", (snapshot.parts[0] as StreamPart.Text).textState)
        assertEquals(TextPartState.Streaming.wireValue, (snapshot.parts[1] as StreamPart.Text).textState)
        assertEquals("blocked-waiting", (snapshot.parts[2] as StreamPart.Reasoning).reasoningState)
        assertEquals(TextPartState.Streaming.wireValue, (snapshot.parts[3] as StreamPart.Reasoning).reasoningState)
        assertEquals("output-streaming", (snapshot.parts[4] as StreamPart.Tool).toolState)
        assertEquals(ToolPartState.InputStreaming.wireValue, (snapshot.parts[5] as StreamPart.Tool).toolState)
    }

    @Test
    fun `parseOrFailed returns failed snapshot for invalid json`() {
        val parser = StreamSnapshotParser(clock = { 1234L })

        val snapshot = parser.parseOrFailed("not json", streamId = "stream-1")

        assertEquals(AGENT_STREAM_SCHEMA_VERSION, snapshot.schemaVersion)
        assertEquals("stream-1", snapshot.streamId)
        assertEquals(StreamStatus.Failed, snapshot.status)
        assertTrue(snapshot.parts.isEmpty())
        assertTrue(snapshot.rawEvents.isEmpty())
        assertEquals(1234L, snapshot.updatedAtMs)
        assertNull(snapshot.completedAtMs)
        assertTrue(snapshot.error?.message.orEmpty().isNotBlank())
    }

    @Test
    fun `parseOrFailed returns failed snapshot for non object root`() {
        val parser = StreamSnapshotParser(clock = { 5678L })

        val snapshot = parser.parseOrFailed("""["not", "an", "object"]""", streamId = "stream-2")

        assertEquals("stream-2", snapshot.streamId)
        assertEquals(StreamStatus.Failed, snapshot.status)
        assertTrue(snapshot.parts.isEmpty())
        assertTrue(snapshot.rawEvents.isEmpty())
        assertEquals(5678L, snapshot.updatedAtMs)
        assertTrue(snapshot.error?.message.orEmpty().isNotBlank())
    }
}
