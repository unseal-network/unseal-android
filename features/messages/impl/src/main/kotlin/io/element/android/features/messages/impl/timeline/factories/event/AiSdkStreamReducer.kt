/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import dev.zacsweers.metro.Inject
import io.element.android.features.messages.impl.timeline.components.event.isHiddenStreamPart
import io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCallRootCardAdapter
import io.element.android.features.messages.impl.timeline.components.event.toolcards.isRegisteredToolName
import io.element.android.features.messages.impl.timeline.model.event.AiCustomStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiErrorStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiFileStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiMarkdownBlock
import io.element.android.features.messages.impl.timeline.model.event.AiPptWorkflowStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiQuickAction
import io.element.android.features.messages.impl.timeline.model.event.AiReasoningStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiSource
import io.element.android.features.messages.impl.timeline.model.event.AiSourceStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamCursorMode
import io.element.android.features.messages.impl.timeline.model.event.AiStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamRenderModel
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiThinkingStep
import io.element.android.features.messages.impl.timeline.model.event.AiToolCall
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSnapshotParser
import io.element.android.libraries.agentstream.api.StreamStatus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Adapts SDK AI stream snapshots to the existing Android AI timeline renderer.
 */
@Inject
class AiSdkStreamReducer {
    fun mapSnapshot(
        snapshot: StreamSnapshot,
        isEdited: Boolean,
        sender: String?,
    ): TimelineItemAiContent {
        return mapRenderModel(snapshot).toTimelineContent(
            isEdited = isEdited,
            sender = sender,
        )
    }

    fun mapRenderModel(snapshot: StreamSnapshot): AiStreamRenderModel {
        val mappedParts = snapshot.parts.map { it.toAiStreamPart() }
        val streamParts = mappedParts.withSnapshotErrorIfNeeded(snapshot)
        val textParts = streamParts.filterIsInstance<AiTextStreamPart>()
        val reasoningParts = streamParts.filterIsInstance<AiReasoningStreamPart>()
        val toolParts = streamParts.filterIsInstance<AiToolStreamPart>()

        // Mirror iOS ToolCallRootCardAdapter: parse/expand tool parts off the Compose thread so the
        // view only renders precomputed lists.
        // Mirror iOS ToolGroupUtils.isHiddenPart: internal/noise tool & data parts are not rendered.
        val visible = streamParts.filterNot { it.isHiddenStreamPart }
        val visibleToolCandidates = visible.filterIsInstance<AiToolStreamPart>()
            .filter { it.toolName.isRegisteredToolName }
        val firstToolPartIndex = visible.indexOfFirst { part ->
            part is AiToolStreamPart && part.toolName.isRegisteredToolName
        }.takeIf { it >= 0 }
        val renderableToolParts = ToolCallRootCardAdapter.renderableToolParts(visibleToolCandidates)
            .toImmutableList()
        val toolCardEntries = ToolCallRootCardAdapter.toolCallEntries(visibleToolCandidates)
            .toImmutableList()
        val toolCallRoot = ToolCallRootCardAdapter.rootModel(toolCardEntries)
        val passthroughParts = visible.filterNot { part ->
            part is AiToolStreamPart && part.toolName.isRegisteredToolName
        }.toImmutableList()
        val lastPartIsStreamingText = visible.lastOrNull().let { part ->
            part is AiTextStreamPart && part.state == STREAMING_TEXT_STATE
        }
        val isStreaming = snapshot.status == StreamStatus.Loading || snapshot.status == StreamStatus.Streaming
        val isTerminal = snapshot.status == StreamStatus.Completed ||
            snapshot.status == StreamStatus.Failed ||
            snapshot.status == StreamStatus.Cancelled

        return AiStreamRenderModel(
            streamId = snapshot.streamId,
            schemaVersion = snapshot.schemaVersion,
            streamStatus = snapshot.status.name,
            updatedAtMs = snapshot.updatedAtMs,
            completedAtMs = snapshot.completedAtMs,
            streamError = snapshot.error?.message,
            isStreaming = isStreaming,
            isTerminal = isTerminal,
            cursorMode = when {
                !isStreaming -> AiStreamCursorMode.None
                visible.isEmpty() -> AiStreamCursorMode.Loading
                lastPartIsStreamingText -> AiStreamCursorMode.None
                else -> AiStreamCursorMode.TrailingCursor
            },
            renderVersion = snapshot.renderVersion(streamParts),
            markdownBlocks = textParts.map {
                AiMarkdownBlock(
                    id = it.id,
                    text = it.text,
                    state = it.state,
                )
            }.toImmutableList(),
            thinkingSteps = reasoningParts.mapIndexed { index, part ->
                AiThinkingStep(
                    title = "Thinking ${index + 1}",
                    description = part.text,
                    status = part.state,
                )
            }.toImmutableList(),
            toolCalls = toolParts.map { part ->
                AiToolCall(
                    name = part.toolName,
                    displayName = part.title ?: part.toolName,
                    state = part.state,
                    output = part.output,
                    error = part.errorText,
                )
            }.toImmutableList(),
            sources = emptyList<AiSource>().toImmutableList(),
            quickActions = emptyList<AiQuickAction>().toImmutableList(),
            parts = streamParts.toImmutableList(),
            renderableToolParts = renderableToolParts,
            toolCardEntries = toolCardEntries,
            toolCallRoot = toolCallRoot,
            passthroughParts = passthroughParts,
            visibleParts = visible.toImmutableList(),
            firstToolPartIndex = firstToolPartIndex,
            lastPartIsStreamingText = lastPartIsStreamingText,
        )
    }

    fun parseSnapshot(
        snapshotJson: String,
        isEdited: Boolean,
    ): TimelineItemAiContent {
        return mapSnapshot(
            snapshot = StreamSnapshotParser().parseOrFailed(snapshotJson),
            isEdited = isEdited,
            sender = null,
        )
    }

    private fun StreamPart.toAiStreamPart(): AiStreamPart {
        return when (this) {
            is StreamPart.Text -> AiTextStreamPart(
                id = id,
                state = state,
                text = text,
            )
            is StreamPart.Reasoning -> AiReasoningStreamPart(
                id = id,
                state = state,
                text = text,
            )
            is StreamPart.Tool -> {
                val name = toolName ?: type.removePrefix("tool-").takeIf { it != type && it.isNotBlank() } ?: id
                if (name == TOOL_PPT) {
                    val outputObj = output as? JsonObject
                    val inputObj = input as? JsonObject
                    val outline = inputObj?.get("outline") as? JsonObject
                    val isOutputAvailable = state == "output-available"
                    val websocketUrl = (outputObj?.get("websocket_url") as? JsonPrimitive)?.contentOrNull
                    // Extract task_id from websocket_url path ("/ws/workflow/<task_id>") as authoritative fallback,
                    // since the output may omit task_id while the URL always encodes it.
                    val taskIdFromUrl = websocketUrl?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                    val taskId = if (isOutputAvailable) {
                        (outputObj?.get("task_id") as? JsonPrimitive)?.contentOrNull
                            ?: taskIdFromUrl
                            ?: (outline?.get("task_id") as? JsonPrimitive)?.contentOrNull
                            ?: id
                    } else {
                        (outline?.get("task_id") as? JsonPrimitive)?.contentOrNull
                            ?: (inputObj?.get("task_id") as? JsonPrimitive)?.contentOrNull
                            ?: id
                    }
                    val totalSlides = if (isOutputAvailable) {
                        (outputObj?.get("total_slides") as? JsonPrimitive)?.intOrNull
                            ?: (outline?.get("total_slides") as? JsonPrimitive)?.intOrNull
                            ?: 5
                    } else {
                        (outline?.get("total_slides") as? JsonPrimitive)?.intOrNull ?: 5
                    }
                    AiPptWorkflowStreamPart(
                        id = id,
                        state = state,
                        taskId = taskId,
                        totalSlides = totalSlides,
                        websocketUrl = websocketUrl,
                        isStreaming = !isOutputAvailable,
                    )
                } else {
                    AiToolStreamPart(
                        id = id,
                        state = state,
                        toolName = name,
                        title = title,
                        input = input?.asDisplayString(),
                        output = output?.asDisplayString(),
                        errorText = error?.message,
                        rawInput = rawInput?.asDisplayString(),
                    )
                }
            }
            is StreamPart.Source -> AiSourceStreamPart(
                id = id,
                state = state ?: DEFAULT_DONE_STATE,
                sourceType = sourceType ?: type.removePrefix("source-").takeIf { it != type && it.isNotBlank() } ?: "url",
                title = title ?: "Source",
                url = url,
                filename = null,
                mediaType = null,
            )
            is StreamPart.Data -> AiDataStreamPart(
                id = id,
                state = state ?: DEFAULT_DONE_STATE,
                type = type,
                payload = data.toString(),
            )
            is StreamPart.File -> AiFileStreamPart(
                id = id,
                state = state ?: DEFAULT_DONE_STATE,
                mediaType = mediaType,
                filename = filename,
                url = url,
            )
            is StreamPart.Step -> AiCustomStreamPart(
                id = id,
                state = state ?: DEFAULT_DONE_STATE,
                type = type,
                payload = payload?.toString() ?: title.orEmpty(),
            )
            is StreamPart.Error -> AiErrorStreamPart(
                id = id,
                state = state ?: DEFAULT_ERROR_STATE,
                errorText = error.message,
            )
            is StreamPart.Custom -> AiCustomStreamPart(
                id = id,
                state = state ?: DEFAULT_DONE_STATE,
                type = type,
                payload = payload.toString(),
            )
        }
    }

    private fun JsonElement.asDisplayString(): String? {
        return when (this) {
            is JsonPrimitive -> contentOrNull
            else -> toString()
        }
    }

    private fun List<AiStreamPart>.withSnapshotErrorIfNeeded(snapshot: StreamSnapshot): List<AiStreamPart> {
        val hasSnapshotError = snapshot.status == StreamStatus.Failed || snapshot.error != null
        if (!hasSnapshotError || any { it is AiErrorStreamPart }) {
            return this
        }
        val errorMessage = snapshot.error?.message?.takeIf { it.isNotBlank() } ?: DEFAULT_STREAM_ERROR_MESSAGE
        val streamId = snapshot.streamId.ifBlank { "stream" }
        return this + AiErrorStreamPart(
            id = "$streamId-error",
            state = DEFAULT_ERROR_STATE,
            errorText = errorMessage,
        )
    }

    private fun StreamSnapshot.renderVersion(parts: List<AiStreamPart>): String {
        val partsSignature = parts.fold(1) { acc, part ->
            31 * acc + part.signatureHash()
        }.toString(16)
        return listOf(
            streamId,
            schemaVersion.toString(),
            updatedAtMs.toString(),
            completedAtMs?.toString().orEmpty(),
            status.name,
            parts.size.toString(),
            partsSignature,
        ).joinToString(":")
    }

    private fun AiStreamPart.signatureHash(): Int {
        var result = id.hashCode()
        result = 31 * result + this::class.simpleName.orEmpty().hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + contentHash()
        return result
    }

    private fun AiStreamPart.contentHash(): Int {
        return when (this) {
            is AiTextStreamPart -> text.hashCode()
            is AiReasoningStreamPart -> text.hashCode()
            is AiToolStreamPart -> contentHashOf(toolName, title, input, rawInput, output, errorText)
            is AiSourceStreamPart -> contentHashOf(sourceType, title, url, filename, mediaType)
            is AiErrorStreamPart -> errorText.hashCode()
            is AiDataStreamPart -> contentHashOf(type, payload)
            is AiFileStreamPart -> contentHashOf(mediaType, filename, url)
            is AiCustomStreamPart -> contentHashOf(type, payload)
            is AiPptWorkflowStreamPart -> contentHashOf(taskId, totalSlides, websocketUrl, isStreaming)
        }
    }

    private fun contentHashOf(vararg values: Any?): Int {
        return values.fold(1) { acc, value ->
            31 * acc + (value?.hashCode() ?: 0)
        }
    }

    private companion object {
        const val TOOL_PPT = "generate_ppt_html_presentation"
        const val DEFAULT_DONE_STATE = "done"
        const val DEFAULT_ERROR_STATE = "error"
        const val DEFAULT_STREAM_ERROR_MESSAGE = "Stream error"
        const val STREAMING_TEXT_STATE = "streaming"
    }
}
