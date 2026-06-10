/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

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
        assertEquals(JsonPrimitive(21), tool.output?.jsonObject?.get("temperature"))

        assertEquals(3L, decoded.rawEvents.single().sequence)
        assertEquals("snapshot", decoded.rawEvents.single().eventType)
        assertEquals("text-1", decoded.rawEvents.single().partId)
        assertEquals(JsonPrimitive(true), decoded.rawEvents.single().payload.jsonObject.getValue("ok"))
    }
}
