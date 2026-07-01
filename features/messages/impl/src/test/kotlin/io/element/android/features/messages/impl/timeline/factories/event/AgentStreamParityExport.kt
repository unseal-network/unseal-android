/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import io.element.android.features.messages.impl.timeline.model.event.AiCustomStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiErrorStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiFileStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiPptWorkflowStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiReasoningStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiSourceStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import org.json.JSONArray
import org.json.JSONObject

internal object AgentStreamParityExport {
    fun renderJson(content: TimelineItemAiContent): JSONObject {
        return JSONObject()
            .put("streamId", content.streamId)
            .put("isStreaming", content.isStreaming)
            .put("isTerminal", content.isTerminal)
            .put("parts", JSONArray(content.visibleParts.map(::partJson)))
            .put("toolRoot", content.toolCallRoot?.let { root ->
                JSONObject()
                    .put("title", root.title)
                    .put("doneCount", root.doneCount)
                    .put("errorCount", root.errorCount)
                    .put("callingCount", root.callingCount)
                    .put("entries", JSONArray(root.entries.map { entry ->
                        JSONObject()
                            .put("id", entry.id)
                            .put("name", entry.name)
                            .put("cardType", entry.cardType)
                            .put("state", entry.state)
                            .put("propsKeys", sortedPropsKeys(entry.props))
                    }))
            } ?: JSONObject.NULL)
    }

    private fun partJson(part: AiStreamPart): JSONObject {
        return when (part) {
            is AiTextStreamPart -> JSONObject()
                .put("type", "text")
                .put("id", part.id)
                .put("state", part.state)
                .put("textLength", part.text.length)
            is AiReasoningStreamPart -> JSONObject()
                .put("type", "reasoning")
                .put("id", part.id)
                .put("state", part.state)
                .put("textLength", part.text.length)
            is AiToolStreamPart -> JSONObject()
                .put("type", "tool")
                .put("id", part.id)
                .put("state", part.state)
                .put("toolName", part.toolName)
            is AiDataStreamPart -> JSONObject()
                .put("type", part.type)
                .put("id", part.id)
                .put("state", part.state)
                .apply { putSuspendedSummary(part.payload) }
            is AiErrorStreamPart -> JSONObject()
                .put("type", "error")
                .put("id", part.id)
                .put("state", part.state)
                .put("message", part.errorText)
            is AiSourceStreamPart -> JSONObject()
                .put("type", "source")
                .put("id", part.id)
                .put("state", part.state)
                .put("sourceType", part.sourceType)
            is AiFileStreamPart -> JSONObject()
                .put("type", "file")
                .put("id", part.id)
                .put("state", part.state)
                .put("mediaType", part.mediaType)
            is AiPptWorkflowStreamPart -> JSONObject()
                .put("type", "ppt-workflow")
                .put("id", part.id)
                .put("state", part.state)
                .put("taskId", part.taskId)
                .put("totalSlides", part.totalSlides)
                .put("isStreaming", part.isStreaming)
            is AiCustomStreamPart -> JSONObject()
                .put("type", part.type)
                .put("id", part.id)
                .put("state", part.state)
        }
    }

    private fun sortedPropsKeys(props: String): JSONArray {
        val json = runCatching { JSONObject(props) }.getOrNull() ?: return JSONArray()
        return JSONArray(json.keys().asSequence().toList().sorted())
    }

    private fun JSONObject.putSuspendedSummary(payload: String) {
        val root = runCatching { JSONObject(payload) }.getOrNull() ?: return
        put("toolName", root.optString("toolName").takeIf { it.isNotBlank() })
        put("title", root.optString("title").takeIf { it.isNotBlank() })
        put("reason", root.optString("reason").takeIf { it.isNotBlank() })
        val suspendPayload = root.optJSONObject("suspendPayload")
        val kind = suspendPayload?.optString("kind")?.takeIf { it.isNotBlank() }
            ?: root.optString("toolName").takeIf { it.isNotBlank() }
        put("suspendedKind", kind)
        put("payloadKeys", JSONArray((suspendPayload ?: root).keys().asSequence().toList().sorted()))
        if (kind == "moltbookRegister") {
            put("fields", JSONArray(listOf("name", "verificationCode", "claimUrl")))
            put("submitLabel", root.optString("submitLabel").takeIf { it.isNotBlank() } ?: "Continue")
        }
    }
}
