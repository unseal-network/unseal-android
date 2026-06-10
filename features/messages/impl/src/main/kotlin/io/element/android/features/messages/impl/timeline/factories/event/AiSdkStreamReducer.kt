/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import dev.zacsweers.metro.Inject
import io.element.android.features.messages.impl.timeline.model.event.AiQuickAction
import io.element.android.features.messages.impl.timeline.model.event.AiSource
import io.element.android.features.messages.impl.timeline.model.event.AiThinkingStep
import io.element.android.features.messages.impl.timeline.model.event.AiToolCall
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.agentstream.api.AgentStreamSession
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Bridges the shared Rust AI SDK stream reducer to the existing Android AI
 * timeline renderer.
 *
 * Network and scheduling stay in Android. Callers feed SSE chunks from their
 * own stream task, then render the returned [TimelineItemAiContent].
 */
@Inject
class AiSdkStreamReducer {
    private val json = Json { ignoreUnknownKeys = true }

    fun reduceSseChunks(
        streamId: String,
        chunks: Iterable<String>,
        isEdited: Boolean,
    ): TimelineItemAiContent {
        AgentStreamSession(
            streamId = streamId,
            includeRawEvents = false,
            includeNormalizedEvents = true,
        ).use { session ->
            chunks.forEach(session::applySseChunk)
            return parseSnapshot(session.finish(), isEdited)
        }
    }

    fun parseSnapshot(
        snapshotJson: String,
        isEdited: Boolean,
    ): TimelineItemAiContent {
        val snapshot = json.parseToJsonElement(snapshotJson).jsonObject
        val parts = snapshot.objectArray("parts")
        val textParts = parts.filter { it.string("type") == "text" }
        val reasoningParts = parts.filter { it.string("type") == "reasoning" }
        val toolParts = parts.filter { it.string("type") == "tool" }
        val sourceParts = parts.filter { it.string("type") == "source" }

        return TimelineItemAiContent(
            body = textParts.joinToString(separator = "\n\n") { it.string("text").orEmpty() },
            isEdited = isEdited,
            isStreaming = snapshot.string("status") in streamingStatuses,
            thinkingSteps = reasoningParts.mapIndexed { index, part ->
                AiThinkingStep(
                    title = "Thinking ${index + 1}",
                    description = part.string("text").orEmpty(),
                    status = part.string("state") ?: "complete",
                )
            }.toImmutableList(),
            toolCalls = toolParts.map { part ->
                val name = part.string("toolName") ?: part.string("id").orEmpty()
                AiToolCall(
                    name = name,
                    displayName = part.string("title") ?: name,
                    state = part.string("state") ?: "input-streaming",
                    output = part["output"]?.asDisplayString(),
                    error = part.string("errorText"),
                )
            }.toImmutableList(),
            sources = sourceParts.map { part ->
                AiSource(
                    title = part.string("title") ?: part.string("filename") ?: part.string("url") ?: "Source",
                    url = part.string("url"),
                    snippet = null,
                )
            }.toImmutableList(),
            quickActions = emptyList<AiQuickAction>().toImmutableList(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.objectArray(key: String): List<JsonObject> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    private fun JsonElement.asDisplayString(): String? {
        return when (this) {
            is JsonPrimitive -> contentOrNull
            else -> toString()
        }
    }

    private companion object {
        val streamingStatuses = setOf("loading", "streaming")
    }
}
