/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentstream.api.RawStreamEvent
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.TextPartState
import io.element.android.libraries.agentstream.api.ToolPartState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

class AgentStreamParityReplayTest {
    @Test
    fun `write android render json for all parity fixtures`() {
        fixtureIds.forEach { fixtureId ->
            val content = replayFixture(fixtureId)
            val exported = AgentStreamParityExport.renderJson(content)
            val out = artifactFile(fixtureId, "android-render-completed.json")
            out.parentFile?.mkdirs()
            out.writeText(exported.toString(2))

            assertThat(out.isFile).isTrue()
            assertThat(exported.getString("streamId")).isEqualTo(fixtureId)
            assertThat(exported.getBoolean("isTerminal")).isTrue()
        }
    }

    @Test
    fun `compose email fixture exports android root card entry`() {
        val exported = AgentStreamParityExport.renderJson(replayFixture("compose-email-list"))
        val entries = exported.getJSONObject("toolRoot").getJSONArray("entries")

        assertThat(entries.length()).isEqualTo(1)
        assertThat(entries.getJSONObject(0).getString("cardType")).isEqualTo("composeEmail")
    }

    private fun replayFixture(id: String) = AiSdkStreamReducer().mapSnapshot(
        snapshot = FixtureSnapshotReducer(id).reduce(),
        isEdited = false,
        sender = "@agent:unseal.ai",
    )

    private fun artifactFile(fixtureId: String, name: String): File {
        return repoRoot().resolve("docs/agent-stream-fixtures/artifacts/$fixtureId/$name")
    }

    private class FixtureSnapshotReducer(
        private val fixtureId: String,
    ) {
        private val file = repoRoot().resolve("docs/agent-stream-fixtures/fixtures/$fixtureId.sse.jsonl")
        private val textParts = linkedMapOf<String, TextBuilder>()
        private val toolParts = linkedMapOf<String, ToolBuilder>()
        private val dataParts = mutableListOf<StreamPart.Data>()
        private val rawEvents = mutableListOf<RawStreamEvent>()
        private var status = StreamStatus.Streaming

        fun reduce(): StreamSnapshot {
            require(file.isFile) { "Missing fixture: ${file.absolutePath}" }
            file.readLines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEachIndexed { index, line ->
                    val event = Json.parseToJsonElement(line).jsonObject
                    apply(event)
                    rawEvents += RawStreamEvent(
                        sequence = index.toLong(),
                        eventType = event.string("type").orEmpty(),
                        partId = event.string("id", "toolCallId"),
                        payload = event,
                        receivedAtMs = 1000L + index,
                    )
                }

            val parts = buildList {
                addAll(textParts.values.map { it.toPart() })
                addAll(toolParts.values.map { it.toPart() })
                addAll(dataParts)
            }
            return StreamSnapshot(
                schemaVersion = 1,
                streamId = fixtureId,
                status = status,
                parts = parts,
                rawEvents = rawEvents,
                updatedAtMs = 1000L + rawEvents.size,
                completedAtMs = if (status == StreamStatus.Completed) 1000L + rawEvents.size else null,
                error = null,
            )
        }

        private fun apply(event: JsonObject) {
            when (event.string("type")) {
                "start" -> status = StreamStatus.Streaming
                "text-start" -> textParts[event.string("id").orEmpty()] = TextBuilder(event.string("id").orEmpty())
                "text-delta" -> textParts.getOrPut(event.string("id").orEmpty()) { TextBuilder(event.string("id").orEmpty()) }
                    .append(event.string("delta").orEmpty())
                "text-end" -> textParts.getOrPut(event.string("id").orEmpty()) { TextBuilder(event.string("id").orEmpty()) }
                    .state = TextPartState.Done.wireValue
                "tool-input-start" -> toolBuilder(event).state = ToolPartState.InputStreaming.wireValue
                "tool-input-available" -> toolBuilder(event).apply {
                    state = ToolPartState.InputAvailable.wireValue
                    input = event["input"]
                }
                "tool-output-available" -> toolBuilder(event).apply {
                    state = ToolPartState.OutputAvailable.wireValue
                    output = event["output"]
                }
                "data-tool-call-suspended" -> dataParts += StreamPart.Data(
                    id = event.string("id").orEmpty(),
                    data = event["data"] ?: event,
                    type = "data-tool-call-suspended",
                    state = "done",
                )
                "finish" -> status = StreamStatus.Completed
            }
        }

        private fun toolBuilder(event: JsonObject): ToolBuilder {
            val id = event.string("toolCallId").orEmpty()
            return toolParts.getOrPut(id) {
                ToolBuilder(
                    id = id,
                    toolName = event.string("toolName").orEmpty(),
                    title = event.string("title"),
                )
            }.also { builder ->
                event.string("toolName")?.takeIf { it.isNotBlank() }?.let { builder.toolName = it }
                event.string("title")?.let { builder.title = it }
            }
        }

        private fun JsonObject.string(vararg keys: String): String? {
            return keys.firstNotNullOfOrNull { key ->
                (this[key] as? JsonPrimitive)?.jsonPrimitive?.contentOrNull
            }
        }
    }

    private class TextBuilder(
        private val id: String,
    ) {
        private val text = StringBuilder()
        var state: String = TextPartState.Streaming.wireValue

        fun append(delta: String) {
            text.append(delta)
        }

        fun toPart(): StreamPart.Text {
            return StreamPart.Text(
                id = id,
                text = text.toString(),
                textState = state,
            )
        }
    }

    private class ToolBuilder(
        private val id: String,
        var toolName: String,
        var title: String?,
    ) {
        var state: String = ToolPartState.InputStreaming.wireValue
        var input: JsonElement? = null
        var output: JsonElement? = null

        fun toPart(): StreamPart.Tool {
            return StreamPart.Tool(
                id = id,
                toolState = state,
                toolName = toolName,
                toolCallId = id,
                input = input,
                output = output,
                title = title,
            )
        }
    }

    private companion object {
        val fixtureIds = listOf(
            "compose-email-list",
            "weather-current-forecast",
            "moltbook-register",
            "hotel-booking-gallery",
            "file-attachment-list",
        )

        fun repoRoot(): File {
            val userDir = requireNotNull(System.getProperty("user.dir")) { "Missing user.dir" }
            return generateSequence(File(userDir).absoluteFile) { it.parentFile }
                .first { it.resolve("settings.gradle.kts").isFile }
        }
    }
}
