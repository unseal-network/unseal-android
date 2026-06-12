/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.AiErrorStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiReasoningStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiSourceStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.libraries.agentstream.api.AGENT_STREAM_SCHEMA_VERSION
import io.element.android.libraries.agentstream.api.StreamError
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.json.JSONObject
import org.junit.Test

class AiSdkStreamReducerTest {
    private val reducer = AiSdkStreamReducer()

    @Test
    fun `maps stream snapshot into renderable content`() {
        val snapshot = snapshot(
            streamId = "stream-1",
            status = StreamStatus.Streaming,
            parts = listOf(
                StreamPart.Text(
                    id = "text-1",
                    text = "Weather result",
                    textState = "done",
                ),
                StreamPart.Text(
                    id = "text-2",
                    text = "Pack an umbrella.",
                    textState = "done",
                ),
                StreamPart.Tool(
                    id = "tool-1",
                    toolState = "output-available",
                    toolName = "weather",
                    input = Json.parseToJsonElement("""{"city":"Shanghai"}"""),
                    rawInput = JsonPrimitive("weather in Shanghai"),
                    output = JsonPrimitive("Sunny"),
                    title = "Weather",
                ),
                StreamPart.Reasoning(
                    id = "reason-1",
                    text = "Check the city forecast.",
                    reasoningState = "done",
                ),
                StreamPart.Source(
                    id = "source-1",
                    title = "Forecast API",
                    url = "https://example.com/weather",
                ),
            ),
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = true, sender = "@alice:example.org")

        assertThat(result.isEdited).isTrue()
        assertThat(result.isStreaming).isTrue()
        assertThat(result.streamId).isEqualTo("stream-1")
        assertThat(result.sender).isEqualTo("@alice:example.org")
        assertThat(result.body).isEqualTo("Weather result\n\nPack an umbrella.")
        assertThat(result.quickActions).isEmpty()
        assertThat(result.parts).hasSize(5)

        val firstText = result.parts[0] as AiTextStreamPart
        assertThat(firstText.text).isEqualTo("Weather result")
        assertThat(firstText.state).isEqualTo("done")

        val tool = result.parts[2] as AiToolStreamPart
        assertThat(tool.toolName).isEqualTo("weather")
        assertThat(tool.title).isEqualTo("Weather")
        assertThat(tool.state).isEqualTo("output-available")
        assertThat(tool.input).contains("Shanghai")
        assertThat(tool.rawInput).isEqualTo("weather in Shanghai")
        assertThat(tool.output).isEqualTo("Sunny")

        val reasoning = result.parts[3] as AiReasoningStreamPart
        assertThat(reasoning.text).isEqualTo("Check the city forecast.")
        assertThat(result.thinkingSteps.single().title).isEqualTo("Thinking 1")
        assertThat(result.thinkingSteps.single().description).isEqualTo("Check the city forecast.")
        assertThat(result.thinkingSteps.single().status).isEqualTo("done")

        val source = result.parts[4] as AiSourceStreamPart
        assertThat(source.sourceType).isEqualTo("url")
        assertThat(source.state).isEqualTo("done")
        assertThat(source.title).isEqualTo("Forecast API")
        assertThat(source.url).isEqualTo("https://example.com/weather")
        assertThat(result.sources).isEmpty()

        assertThat(result.toolCalls.single().name).isEqualTo("weather")
        assertThat(result.toolCalls.single().displayName).isEqualTo("Weather")
        assertThat(result.toolCalls.single().state).isEqualTo("output-available")
        assertThat(result.toolCalls.single().output).isEqualTo("Sunny")
        assertThat(result.toolCalls.single().error).isNull()
    }

    @Test
    fun `maps tool name from tool type when sdk toolName is null`() {
        val snapshot = snapshot(
            parts = listOf(
                StreamPart.Tool(
                    id = "call-1",
                    toolState = "output-error",
                    toolName = null,
                    input = Json.parseToJsonElement("""{"city":"Shanghai"}"""),
                    output = Json.parseToJsonElement("""{"temperature":"21C"}"""),
                    error = StreamError(message = "Tool failed"),
                    type = "tool-weather",
                ),
            ),
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)

        val tool = result.parts.single() as AiToolStreamPart
        assertThat(tool.toolName).isEqualTo("weather")
        assertThat(tool.input).contains("Shanghai")
        assertThat(tool.output).contains("21C")
        assertThat(tool.errorText).isEqualTo("Tool failed")
        assertThat(result.toolCalls.single().name).isEqualTo("weather")
        assertThat(result.toolCalls.single().displayName).isEqualTo("weather")
        assertThat(result.toolCalls.single().error).isEqualTo("Tool failed")
    }

    @Test
    fun `reducer does not mutate sdk part states for completed snapshots`() {
        val snapshot = snapshot(
            status = StreamStatus.Completed,
            parts = listOf(
                StreamPart.Tool(
                    id = "call-1",
                    toolState = "input-available",
                    toolName = "GMAIL_FETCH_EMAILS",
                    input = Json.parseToJsonElement("""{"query":"from:alice@example.com"}"""),
                    rawInput = JsonPrimitive("Find Alice emails"),
                    output = Json.parseToJsonElement("""{"successful":true}"""),
                ),
            ),
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)

        val tool = result.parts.single() as AiToolStreamPart
        assertThat(tool.state).isEqualTo("input-available")
        assertThat(tool.input).contains("alice@example.com")
        assertThat(tool.rawInput).isEqualTo("Find Alice emails")
        assertThat(tool.output).contains("successful")
    }

    @Test
    fun `failed snapshot with top-level error maps to error part`() {
        val snapshot = snapshot(
            streamId = "",
            status = StreamStatus.Failed,
            parts = emptyList(),
            error = StreamError(message = "Stream failed"),
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)

        assertThat(result.isStreaming).isFalse()
        val error = result.parts.single() as AiErrorStreamPart
        assertThat(error.id).isEqualTo("stream-error")
        assertThat(error.state).isEqualTo("error")
        assertThat(error.errorText).isEqualTo("Stream failed")
    }

    @Test
    fun `source snapshot part stays in parts without filling legacy sources`() {
        val snapshot = snapshot(
            parts = listOf(
                StreamPart.Source(
                    id = "source-1",
                    title = "Docs",
                    url = "https://example.com/docs",
                ),
            ),
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)

        val source = result.parts.single() as AiSourceStreamPart
        assertThat(source.title).isEqualTo("Docs")
        assertThat(source.url).isEqualTo("https://example.com/docs")
        assertThat(result.sources).isEmpty()
    }

    @Test
    fun `render model preserves sdk snapshot metadata`() {
        val snapshot = StreamSnapshot(
            schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
            streamId = "stream-meta",
            status = StreamStatus.Completed,
            parts = listOf(StreamPart.Text(id = "text-1", text = "Done", textState = "done")),
            rawEvents = emptyList(),
            updatedAtMs = 42L,
            completedAtMs = 64L,
            error = null,
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = "@alice:example.org")

        assertThat(result.streamId).isEqualTo("stream-meta")
        assertThat(result.schemaVersion).isEqualTo(AGENT_STREAM_SCHEMA_VERSION)
        assertThat(result.streamStatus).isEqualTo(StreamStatus.Completed.name)
        assertThat(result.updatedAtMs).isEqualTo(42L)
        assertThat(result.completedAtMs).isEqualTo(64L)
        assertThat(result.renderVersion).isEqualTo("stream-meta:1:42:64:Completed")
    }

    @Test
    fun `reducer inserts one tool root card at first registered tool candidate`() {
        val snapshot = snapshot(
            status = StreamStatus.Streaming,
            parts = listOf(
                StreamPart.Text(id = "text-before", text = "Before", textState = "done"),
                StreamPart.Tool(id = "ignored", toolState = "input-available", toolName = "COMPOSIO_SEARCH_TOOLS"),
                StreamPart.Tool(
                    id = "gmail",
                    toolState = "output-available",
                    toolName = "GMAIL_FETCH_EMAILS",
                    output = Json.parseToJsonElement("""{"successful":true,"data":{"messages":[{"subject":"Hello"}]}}"""),
                ),
                StreamPart.Text(id = "text-after", text = "After", textState = "streaming"),
            ),
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)

        assertThat(result.visibleParts.map { it.id }).containsExactly("text-before", "ignored", "gmail", "text-after").inOrder()
        assertThat(result.toolCardEntries).hasSize(1)
        assertThat(result.firstToolPartIndex).isEqualTo(1)
        assertThat(result.passthroughParts.map { it.id }).containsExactly("text-before", "text-after").inOrder()
        assertThat(result.lastPartIsStreamingText).isTrue()
    }

    @Test
    fun `schedule create tool props mirror ios friendly cron formatting`() {
        val snapshot = snapshot(
            parts = listOf(
                StreamPart.Tool(
                    id = "schedule-1",
                    toolState = "input-available",
                    toolName = "createSchedule",
                    input = Json.parseToJsonElement(
                        """
                        {
                          "name": " Daily sync ",
                          "cron": "0 9 * * *",
                          "timezone": "Asia/Shanghai",
                          "action": "Send a summary"
                        }
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)
        val props = JSONObject(result.toolCardEntries.single().props)

        assertThat(result.toolCardEntries.single().state).isEqualTo("calling")
        assertThat(props.getString("_cardType")).isEqualTo("createSchedule")
        assertThat(props.getString("name")).isEqualTo("Daily sync")
        assertThat(props.getString("cadence")).isEqualTo("Every day at 09:00")
        assertThat(props.getBoolean("cadenceProvided")).isTrue()
        assertThat(props.getString("timezone")).isEqualTo("Asia/Shanghai")
        assertThat(props.getString("action")).isEqualTo("Send a summary")
    }

    @Test
    fun `schedule status tool props mirror ios names summary formatting`() {
        val snapshot = snapshot(
            parts = listOf(
                StreamPart.Tool(
                    id = "schedule-status-1",
                    toolState = "input-available",
                    toolName = "updateScheduleStatus",
                    input = Json.parseToJsonElement(
                        """
                        {
                          "names": [" Morning report ", "Inbox sweep"],
                          "status": "disabled"
                        }
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)
        val props = JSONObject(result.toolCardEntries.single().props)

        assertThat(props.getString("_cardType")).isEqualTo("updateScheduleStatus")
        assertThat(props.getString("summary")).isEqualTo("2 schedules")
        assertThat(props.getJSONArray("names").getString(0)).isEqualTo("Morning report")
        assertThat(props.getJSONArray("names").getString(1)).isEqualTo("Inbox sweep")
        assertThat(props.getBoolean("isEnable")).isFalse()
    }

    @Test
    fun `schedule update without action mirrors ios empty action fallback`() {
        val snapshot = snapshot(
            parts = listOf(
                StreamPart.Tool(
                    id = "schedule-update-1",
                    toolState = "input-available",
                    toolName = "updateSchedule",
                    input = Json.parseToJsonElement(
                        """
                        {
                          "name": " Morning report ",
                          "cron": "30 8 * * 1-5"
                        }
                        """.trimIndent(),
                    ),
                ),
            ),
        )

        val result = reducer.mapSnapshot(snapshot, isEdited = false, sender = null)
        val props = JSONObject(result.toolCardEntries.single().props)

        assertThat(props.getString("_cardType")).isEqualTo("updateSchedule")
        assertThat(props.getString("name")).isEqualTo("Morning report")
        assertThat(props.getString("cadence")).isEqualTo("Every weekday at 08:30")
        assertThat(props.getBoolean("cadenceChanged")).isTrue()
        assertThat(props.getString("timezone")).isEmpty()
        assertThat(props.getString("action")).isEmpty()
    }

    @Test
    fun `transitional parseSnapshot uses sdk parser`() {
        val snapshotJson = """
            {
              "streamId": "stream-2",
              "status": "done",
              "parts": [
                { "type": "text", "id": "text-1", "state": "done", "text": "Done" },
                {
                  "type": "tool-weather",
                  "id": "call-1",
                  "state": "output-available",
                  "input": { "city": "Shanghai" },
                  "output": { "temperature": "21C" }
                }
              ]
            }
        """.trimIndent()

        val result = reducer.parseSnapshot(snapshotJson, isEdited = false)

        assertThat(result.streamId).isEqualTo("stream-2")
        assertThat(result.isStreaming).isFalse()
        assertThat(result.body).isEqualTo("Done")
        assertThat((result.parts[1] as AiToolStreamPart).toolName).isEqualTo("weather")
    }

    private fun snapshot(
        streamId: String = "stream-1",
        status: StreamStatus = StreamStatus.Completed,
        parts: List<StreamPart>,
        error: StreamError? = null,
    ) = StreamSnapshot(
        schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
        streamId = streamId,
        status = status,
        parts = parts,
        rawEvents = emptyList(),
        updatedAtMs = 1L,
        completedAtMs = null,
        error = error,
    )
}
