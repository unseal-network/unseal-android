/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class StreamSnapshotJsonCodecTest {
    @Test
    fun `encodes and decodes completed snapshot`() {
        val codec = StreamSnapshotJsonCodec()
        val snapshot = StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = "stream-1",
            status = StreamStatus.Completed,
            parts = listOf(
                StreamPart.Text(
                    id = "text-1",
                    text = "Done",
                    textState = TextPartState.Complete.wireValue,
                ),
                StreamPart.Tool(
                    id = "tool-1",
                    toolName = "weather",
                    toolCallId = "call-1",
                    toolState = ToolPartState.OutputAvailable.wireValue,
                    input = buildJsonObject {
                        put("city", JsonPrimitive("Paris"))
                    },
                    output = buildJsonObject {
                        put("temperature", JsonPrimitive(21))
                    },
                    rawInput = buildJsonObject {
                        put("rawCity", JsonPrimitive("Paris, France"))
                    },
                ),
                StreamPart.Custom(
                    id = "custom-1",
                    customType = "custom-widget",
                    payload = buildJsonObject {
                        put("component", JsonPrimitive("weather-card"))
                    },
                ),
            ),
            rawEvents = listOf(
                RawStreamEvent(
                    sequence = 3,
                    eventType = "snapshot",
                    partId = "text-1",
                    payload = buildJsonObject {
                        put("ok", JsonPrimitive(true))
                    },
                    receivedAtMs = 222L,
                )
            ),
            updatedAtMs = 111L,
            completedAtMs = 222L,
            error = null,
        )

        val decoded = codec.decode(codec.encode(snapshot))

        assertEquals(AGENT_STREAM_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals("stream-1", decoded.streamId)
        assertEquals(StreamStatus.Completed, decoded.status)
        assertEquals(111L, decoded.updatedAtMs)
        assertEquals(222L, decoded.completedAtMs)

        val text = decoded.parts[0] as StreamPart.Text
        assertEquals("Done", text.text)
        assertEquals(TextPartState.Complete.wireValue, text.textState)

        val tool = decoded.parts[1] as StreamPart.Tool
        assertEquals("weather", tool.toolName)
        assertEquals("call-1", tool.toolCallId)
        assertEquals(ToolPartState.OutputAvailable.wireValue, tool.toolState)
        assertEquals(JsonPrimitive("Paris"), tool.input?.jsonObject?.get("city"))
        assertEquals(JsonPrimitive("Paris, France"), tool.rawInput?.jsonObject?.get("rawCity"))
        assertEquals(JsonPrimitive(21), tool.output?.jsonObject?.get("temperature"))

        val custom = decoded.parts[2] as StreamPart.Custom
        assertEquals("custom-widget", custom.customType)
        assertEquals(JsonPrimitive("weather-card"), custom.payload.jsonObject.getValue("component"))
        assertEquals(setOf("component"), custom.payload.jsonObject.keys)

        assertEquals(3L, decoded.rawEvents.single().sequence)
        assertEquals("snapshot", decoded.rawEvents.single().eventType)
        assertEquals("text-1", decoded.rawEvents.single().partId)
        assertEquals(JsonPrimitive(true), decoded.rawEvents.single().payload.jsonObject.getValue("ok"))
    }

    @Test
    fun `preserves unknown states across codec round trip`() {
        val codec = StreamSnapshotJsonCodec()
        val snapshot = StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = "stream-1",
            status = StreamStatus.Streaming,
            parts = listOf(
                StreamPart.Text(
                    id = "text-1",
                    text = "Blocked",
                    textState = "blocked-waiting",
                ),
                StreamPart.Reasoning(
                    id = "reason-1",
                    text = "Still thinking",
                    reasoningState = "blocked-waiting",
                ),
                StreamPart.Tool(
                    id = "tool-1",
                    toolState = "output-streaming",
                ),
            ),
            rawEvents = emptyList(),
            updatedAtMs = 111L,
            completedAtMs = null,
            error = null,
        )

        val decoded = codec.decode(codec.encode(snapshot))

        assertEquals("blocked-waiting", (decoded.parts[0] as StreamPart.Text).textState)
        assertEquals("blocked-waiting", (decoded.parts[1] as StreamPart.Reasoning).reasoningState)
        assertEquals("output-streaming", (decoded.parts[2] as StreamPart.Tool).toolState)
    }

    @Test
    fun `error raw payload is idempotent across codec round trips`() {
        val codec = StreamSnapshotJsonCodec()
        val snapshot = StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = "stream-1",
            status = StreamStatus.Failed,
            parts = listOf(
                StreamPart.Tool(
                    id = "tool-1",
                    toolState = ToolPartState.OutputError.wireValue,
                    error = StreamError(
                        message = "Tool exploded",
                        code = "E_TOOL",
                        raw = buildJsonObject {
                            put("detail", JsonPrimitive("tool detail"))
                        },
                    ),
                ),
                StreamPart.Error(
                    id = "error-1",
                    error = StreamError(
                        message = "Part exploded",
                        code = "E_PART",
                        raw = buildJsonObject {
                            put("detail", JsonPrimitive("part detail"))
                        },
                    ),
                ),
            ),
            rawEvents = emptyList(),
            updatedAtMs = 111L,
            completedAtMs = null,
            error = StreamError(
                message = "Snapshot exploded",
                code = "E_SNAPSHOT",
                raw = buildJsonObject {
                    put("detail", JsonPrimitive("snapshot detail"))
                },
            ),
        )

        val encoded = codec.encode(snapshot)
        val reEncoded = codec.encode(codec.decode(encoded))
        val twiceReEncoded = codec.encode(codec.decode(reEncoded))

        assertEquals(Json.parseToJsonElement(encoded), Json.parseToJsonElement(reEncoded))
        assertEquals(Json.parseToJsonElement(encoded), Json.parseToJsonElement(twiceReEncoded))
    }
}
