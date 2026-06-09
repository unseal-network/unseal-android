/*
 * Copyright (c) 2026 New Vector Ltd.
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
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Parses an Unseal AI/assistant "stream" message from a Matrix event's original JSON.
 *
 * Mirrors iOS `RoomTimelineItemFactory.checkAgentMessage` + `parseAIMessageContentSync`.
 * Detection: `content.msgtype == "m.aisdk.protocol"` OR (`content.msgtype == "m.text"` and a
 * `content.stream` object is present). The rich parts are read from custom keys under `content`.
 *
 * Returns null when the JSON is absent/invalid or the event is not an AI message, so callers can
 * fall back to normal message rendering.
 */
@Inject
class AiMessageContentParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(originalJson: String?, isEdited: Boolean): TimelineItemAiContent? {
        val raw = originalJson?.takeIf { it.isNotBlank() } ?: return null
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val content = root["content"] as? JsonObject ?: return null

        val msgType = content.string("msgtype")
        val isStream = content["stream"] is JsonObject
        val isAiMessage = msgType == MSGTYPE_AISDK || (msgType == MSGTYPE_TEXT && isStream)
        if (!isAiMessage) return null

        return TimelineItemAiContent(
            body = content.string("content") ?: content.string("body").orEmpty(),
            isEdited = isEdited,
            isStreaming = isStream,
            thinkingSteps = content.objectArray("thinking_process").mapNotNull { it.toThinkingStep() }.toImmutableList(),
            toolCalls = content.objectArray("tool_calls").mapNotNull { it.toToolCall() }.toImmutableList(),
            sources = content.objectArray("sources").mapNotNull { it.toSource() }.toImmutableList(),
            quickActions = content.objectArray("quick_actions").mapNotNull { it.toQuickAction() }.toImmutableList(),
        )
    }

    private fun JsonObject.toThinkingStep(): AiThinkingStep? {
        val title = string("title") ?: return null
        return AiThinkingStep(
            title = title,
            description = string("description").orEmpty(),
            status = string("status") ?: "complete",
        )
    }

    private fun JsonObject.toToolCall(): AiToolCall? {
        val name = string("name") ?: return null
        return AiToolCall(
            name = name,
            displayName = string("display_name") ?: name,
            state = string("state") ?: "completed",
            output = string("output"),
            error = string("error"),
        )
    }

    private fun JsonObject.toSource(): AiSource? {
        val title = string("title") ?: return null
        return AiSource(
            title = title,
            url = string("url"),
            snippet = string("snippet"),
        )
    }

    private fun JsonObject.toQuickAction(): AiQuickAction? {
        val label = string("label") ?: return null
        return AiQuickAction(
            label = label,
            action = string("action").orEmpty(),
        )
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.objectArray(key: String): List<JsonObject> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    private companion object {
        const val MSGTYPE_AISDK = "m.aisdk.protocol"
        const val MSGTYPE_TEXT = "m.text"
    }
}
