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

    @Test
    fun `parity fixtures expose every registered root card entry`() {
        expectedRootCardTypes.forEach { (fixtureId, cardTypes) ->
            val exported = AgentStreamParityExport.renderJson(replayFixture(fixtureId))
            val entries = exported.getJSONObject("toolRoot").getJSONArray("entries")
            val exportedTypes = (0 until entries.length()).map {
                entries.getJSONObject(it).getString("cardType")
            }
            assertThat(exportedTypes).containsAtLeastElementsIn(cardTypes)
        }

        val mixed = AgentStreamParityExport.renderJson(replayFixture("stream-mixed-parts"))
        val types = (0 until mixed.getJSONArray("parts").length()).map {
            mixed.getJSONArray("parts").getJSONObject(it).getString("type")
        }
        assertThat(types).containsAtLeast("text", "reasoning", "source", "file", "data-error-card")

        val suspended = AgentStreamParityExport.renderJson(replayFixture("moltbook-register"))
        val suspendedParts = suspended.getJSONArray("parts")
        val suspendedKinds = (0 until suspendedParts.length()).mapNotNull {
            suspendedParts.getJSONObject(it).optString("suspendedKind").takeIf { kind -> kind.isNotBlank() }
        }
        assertThat(suspendedKinds).contains("moltbookRegister")
    }

    @Test
    fun `moltbook register fixture exports interactive fields`() {
        val exported = AgentStreamParityExport.renderJson(replayFixture("moltbook-register"))
        val parts = exported.getJSONArray("parts")
        val suspended = (0 until parts.length())
            .map { parts.getJSONObject(it) }
            .first { it.optString("suspendedKind") == "moltbookRegister" }
        val fields = suspended.getJSONArray("fields").let { array ->
            (0 until array.length()).map { array.getString(it) }
        }

        assertThat(fields).containsAtLeast("name", "verificationCode", "claimUrl")
        assertThat(suspended.getString("submitLabel")).isEqualTo("Continue")
    }

    @Test
    fun `hotel fixture exports images and amenities separately`() {
        val exported = AgentStreamParityExport.renderJson(replayFixture("hotel-booking-gallery"))
        val entries = exported.getJSONObject("toolRoot").getJSONArray("entries")
        val entry = entries.getJSONObject(0)
        val propsKeys = entry.getJSONArray("propsKeys").let { array ->
            (0 until array.length()).map { array.getString(it) }
        }

        assertThat(entry.getString("cardType")).isEqualTo("hotelBooking")
        assertThat(propsKeys).containsAtLeast("hotels", "images", "amenities")
    }

    @Test
    fun `file attachment fixture exports file rows`() {
        val exported = AgentStreamParityExport.renderJson(replayFixture("file-attachment-list"))
        val entries = exported.getJSONObject("toolRoot").getJSONArray("entries")
        val entry = entries.getJSONObject(0)
        val propsKeys = entry.getJSONArray("propsKeys").let { array ->
            (0 until array.length()).map { array.getString(it) }
        }

        assertThat(entry.getString("cardType")).isEqualTo("fileAttachment")
        assertThat(propsKeys).containsAtLeast("files", "title")
    }

    @Test
    fun `root card exports partial failure counts`() {
        val exported = AgentStreamParityExport.renderJson(replayFixture("stream-mixed-parts"))
        val toolRoot = exported.optJSONObject("toolRoot")
        if (toolRoot != null) {
            assertThat(toolRoot.getInt("doneCount")).isAtLeast(0)
            assertThat(toolRoot.getInt("errorCount")).isAtLeast(0)
            assertThat(toolRoot.getInt("callingCount")).isAtLeast(0)
        }
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
        private val reasoningParts = linkedMapOf<String, TextBuilder>()
        private val toolParts = linkedMapOf<String, ToolBuilder>()
        private val dataParts = mutableListOf<StreamPart.Data>()
        private val sourceParts = mutableListOf<StreamPart.Source>()
        private val fileParts = mutableListOf<StreamPart.File>()
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
                addAll(reasoningParts.values.map { it.toReasoningPart() })
                addAll(toolParts.values.map { it.toPart() })
                addAll(sourceParts)
                addAll(fileParts)
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
                "reasoning-start" -> reasoningParts[event.string("id").orEmpty()] = TextBuilder(event.string("id").orEmpty())
                "reasoning-delta" -> reasoningParts.getOrPut(event.string("id").orEmpty()) { TextBuilder(event.string("id").orEmpty()) }
                    .append(event.string("delta").orEmpty())
                "reasoning-end" -> reasoningParts.getOrPut(event.string("id").orEmpty()) { TextBuilder(event.string("id").orEmpty()) }
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
                "data-error", "data-error-card" -> dataParts += StreamPart.Data(
                    id = event.string("id").orEmpty(),
                    data = event["data"] ?: event,
                    type = event.string("type").orEmpty(),
                    state = "done",
                )
                "source-url", "source-document" -> sourceParts += StreamPart.Source(
                    id = event.string("sourceId", "id").orEmpty(),
                    sourceType = if (event.string("type") == "source-document") "document" else "url",
                    title = event.string("title") ?: event.string("filename"),
                    url = event.string("url"),
                    payload = event,
                    type = event.string("type").orEmpty(),
                    state = "done",
                )
                "file" -> fileParts += StreamPart.File(
                    id = event.string("id").orEmpty(),
                    mediaType = event.string("mediaType"),
                    filename = event.string("filename"),
                    url = event.string("url"),
                    data = event["data"],
                    type = "file",
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

        fun toReasoningPart(): StreamPart.Reasoning {
            return StreamPart.Reasoning(
                id = id,
                text = text.toString(),
                reasoningState = state,
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
            "composio-search-cards",
            "github-primary-cards",
            "github-activity-cards",
            "linear-twitter-schedule-cards",
            "meta-subagent-cards",
            "stream-mixed-parts",
        )

        val expectedRootCardTypes = mapOf(
            "compose-email-list" to listOf("composeEmail"),
            "weather-current-forecast" to listOf("weather"),
            "hotel-booking-gallery" to listOf("hotelBooking"),
            "file-attachment-list" to listOf("fileAttachment"),
            "composio-search-cards" to listOf(
                "flightAlert",
                "headlineList",
                "imageGrid",
                "productList",
                "finance",
                "eventList",
                "placeList",
                "urlContent",
            ),
            "github-primary-cards" to listOf(
                "githubIssue",
                "githubIssuesList",
                "repoList",
                "release",
            ),
            "github-activity-cards" to listOf(
                "orgsList",
                "contributors",
                "checkRuns",
                "commitComparison",
                "deployments",
                "notifications",
                "secretAlerts",
                "workflows",
                "commentThread",
            ),
            "linear-twitter-schedule-cards" to listOf(
                "linearIssue",
                "linearIssuesList",
                "socialPostFeed",
                "createSchedule",
                "updateSchedule",
                "updateScheduleStatus",
            ),
            "meta-subagent-cards" to listOf(
                "weather",
                "githubIssuesList",
                "createSchedule",
            ),
        )

        fun repoRoot(): File {
            val userDir = requireNotNull(System.getProperty("user.dir")) { "Missing user.dir" }
            return generateSequence(File(userDir).absoluteFile) { it.parentFile }
                .first { it.resolve("settings.gradle.kts").isFile }
        }
    }
}
