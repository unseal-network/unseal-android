/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

class StreamSnapshotParser(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun parse(snapshotJson: String): StreamSnapshot {
        val root = Json.parseToJsonElement(snapshotJson).jsonObject
        val status = root.string("status").toStreamStatus()
        val completedAtMs = root.long("completedAtMs", "completed_at_ms")
            ?: if (status == StreamStatus.Completed) clock() else null

        return StreamSnapshot(
            schemaVersion = root.int("schemaVersion", "schema_version") ?: AGENT_STREAM_SCHEMA_VERSION,
            streamId = root.string("streamId", "stream_id").orEmpty(),
            status = status,
            parts = root.arrayObjects("parts").map(::parsePart),
            rawEvents = root.arrayObjects("rawEvents", "raw_events").mapIndexed(::parseRawEvent),
            updatedAtMs = root.long("updatedAtMs", "updated_at_ms") ?: clock(),
            completedAtMs = completedAtMs,
            error = root.error("error", "errorText", "error_text"),
        )
    }

    fun parseOrFailed(snapshotJson: String, streamId: String = ""): StreamSnapshot {
        return try {
            parse(snapshotJson)
        } catch (failure: Exception) {
            StreamSnapshot(
                schemaVersion = AGENT_STREAM_SCHEMA_VERSION,
                streamId = streamId,
                status = StreamStatus.Failed,
                parts = emptyList(),
                rawEvents = emptyList(),
                updatedAtMs = clock(),
                completedAtMs = null,
                error = StreamError(
                    message = failure.message.orEmpty().ifBlank { "Failed to parse stream snapshot." },
                ),
            )
        }
    }

    private fun parsePart(part: JsonObject): StreamPart {
        val type = part.string("type").orEmpty()
        val id = part.string("id", "toolCallId", "tool_call_id").ifBlank { type }
        val state = part.string("state")
        return when {
            type == "text" -> StreamPart.Text(
                id = id,
                text = part.string("text").orEmpty(),
                textState = part.string("state", "textState", "text_state")
                    ?: TextPartState.Streaming.wireValue,
                type = type,
            )
            type == "reasoning" -> StreamPart.Reasoning(
                id = id,
                text = part.string("text").orEmpty(),
                reasoningState = part.string("state", "reasoningState", "reasoning_state")
                    ?: TextPartState.Streaming.wireValue,
                type = type,
            )
            type == "tool" || type == "dynamic-tool" || type.startsWith("tool-") -> parseToolPart(
                part = part,
                type = type,
                id = id,
                state = part.string("state", "toolState", "tool_state"),
            )
            type == "source" || type == "source-url" || type == "source-document" -> StreamPart.Source(
                id = id,
                sourceType = part.string("sourceType", "source_type") ?: type.removePrefix("source-").takeIf { it != type },
                title = part.string("title"),
                url = part.string("url"),
                payload = part["payload"] ?: part.remainingObject(
                    "id",
                    "type",
                    "state",
                    "sourceType",
                    "source_type",
                    "title",
                    "url",
                ).takeIf { it.isNotEmpty() },
                type = type,
                state = state,
            )
            type == "file" -> StreamPart.File(
                id = id,
                mediaType = part.string("mediaType", "media_type"),
                filename = part.string("filename"),
                url = part.string("url"),
                data = part["data"],
                type = type,
                state = state,
            )
            type == "step-start" || type == "step" -> StreamPart.Step(
                id = id,
                title = part.string("title"),
                payload = part["payload"] ?: part.remainingObject("id", "type", "state", "title").takeIf { it.isNotEmpty() },
                type = type,
                state = state,
            )
            type == "error" || type == "data-error" -> StreamPart.Error(
                id = id,
                error = part.error("error", "message", "errorText", "error_text") ?: StreamError(message = "", raw = part),
                type = type,
                state = state,
            )
            type == "data" || type.startsWith("data-") -> StreamPart.Data(
                id = id,
                data = part["data"] ?: part.remainingObject("id", "type", "state"),
                type = type,
                state = state,
            )
            else -> StreamPart.Custom(
                id = id,
                customType = type,
                payload = part["payload"] ?: part["data"] ?: part.remainingObject("id", "type", "state"),
                state = state,
            )
        }
    }

    private fun parseToolPart(
        part: JsonObject,
        type: String,
        id: String,
        state: String?,
    ): StreamPart.Tool {
        val toolName = part.string("toolName", "tool_name", "name") ?: type.removePrefix("tool-").takeIf { it != type }
        return StreamPart.Tool(
            id = id,
            toolState = state ?: ToolPartState.InputStreaming.wireValue,
            toolName = toolName,
            toolCallId = part.string("toolCallId", "tool_call_id"),
            input = part["input"],
            rawInput = part["rawInput"] ?: part["raw_input"] ?: part["rawInputText"],
            output = part["output"],
            error = part.error("error", "errorText", "error_text"),
            title = part.string("title", "displayName", "display_name"),
            type = type,
        )
    }

    private fun parseRawEvent(
        index: Int,
        event: JsonObject,
    ): RawStreamEvent {
        return RawStreamEvent(
            sequence = event.long("sequence") ?: index.toLong(),
            eventType = event.string("eventType", "event_type", "type").orEmpty(),
            partId = event.string("partId", "part_id", "id"),
            payload = event["payload"] ?: event,
            receivedAtMs = event.long("receivedAtMs", "received_at_ms") ?: clock(),
        )
    }

    private fun String?.toStreamStatus(): StreamStatus {
        return when (this?.lowercase()) {
            "loading" -> StreamStatus.Loading
            "streaming" -> StreamStatus.Streaming
            "done", "completed", "complete" -> StreamStatus.Completed
            "failed", "error" -> StreamStatus.Failed
            "cancelled", "canceled" -> StreamStatus.Cancelled
            else -> StreamStatus.Idle
        }
    }
}

private fun JsonObject.string(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitiveOrNull()?.contentOrNull }
}

private fun JsonObject.int(vararg keys: String): Int? {
    return keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitiveOrNull()?.intOrNull }
}

private fun JsonObject.long(vararg keys: String): Long? {
    return keys.firstNotNullOfOrNull { key -> this[key]?.jsonPrimitiveOrNull()?.longOrNull }
}

private fun JsonObject.arrayObjects(vararg keys: String): List<JsonObject> {
    return keys.firstNotNullOfOrNull { key ->
        (this[key] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? JsonObject }
    }.orEmpty()
}

private fun JsonObject.error(vararg keys: String): StreamError? {
    for (key in keys) {
        when (val value = this[key]) {
            is JsonObject -> return StreamError(
                message = value.string("message", "error", "errorText", "error_text").orEmpty(),
                code = value.string("code"),
                raw = value["raw"] ?: value,
            )
            is JsonPrimitive -> value.contentOrNull?.let { return StreamError(message = it, raw = value) }
            else -> Unit
        }
    }
    return null
}

private fun JsonObject.remainingObject(vararg ignoredKeys: String): JsonObject {
    return JsonObject(filterKeys { it !in ignoredKeys })
}

private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

private fun String?.ifBlank(defaultValue: () -> String): String {
    return if (this.isNullOrBlank()) defaultValue() else this
}
