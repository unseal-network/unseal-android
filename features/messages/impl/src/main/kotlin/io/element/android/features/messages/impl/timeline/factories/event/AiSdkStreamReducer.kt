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
import io.element.android.features.messages.impl.timeline.model.event.AiQuickAction
import io.element.android.features.messages.impl.timeline.model.event.AiReasoningStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiSource
import io.element.android.features.messages.impl.timeline.model.event.AiSourceStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamCursorMode
import io.element.android.features.messages.impl.timeline.model.event.AiStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamRenderModel
import io.element.android.features.messages.impl.timeline.model.event.AiThinkingStep
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolCall
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamSnapshotParser
import io.element.android.libraries.agentstream.api.StreamStatus
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import timber.log.Timber

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
        Timber.tag(TAG).d(
            "AI snapshot stream=%s status=%s isStreaming=%s parts: %s",
            snapshot.streamId,
            snapshot.status,
            (snapshot.status == StreamStatus.Loading || snapshot.status == StreamStatus.Streaming),
            streamParts.joinToString(separator = ", ") { it.safeLogLabel() }
        )
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
        val partsSignature = parts.joinToString(separator = "|") { part ->
            listOf(
                part.id,
                part::class.simpleName.orEmpty(),
                part.state,
                part.contentSignature().hashCode().toString(16),
            ).joinToString(separator = "/")
        }.hashCode().toString(16)
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

    private fun AiStreamPart.contentSignature(): String {
        return when (this) {
            is AiTextStreamPart -> text
            is AiReasoningStreamPart -> text
            is AiToolStreamPart -> listOf(toolName, title, input, rawInput, output, errorText).joinToString()
            is AiSourceStreamPart -> listOf(sourceType, title, url, filename, mediaType).joinToString()
            is AiErrorStreamPart -> errorText
            is AiDataStreamPart -> listOf(type, payload).joinToString()
            is AiFileStreamPart -> listOf(mediaType, filename, url).joinToString()
            is AiCustomStreamPart -> listOf(type, payload).joinToString()
        }
    }

    private companion object {
        const val TAG = "AiSdkStreamReducer"
        const val DEFAULT_DONE_STATE = "done"
        const val DEFAULT_ERROR_STATE = "error"
        const val DEFAULT_STREAM_ERROR_MESSAGE = "Stream error"
        const val STREAMING_TEXT_STATE = "streaming"
    }
}

private fun AiStreamPart.safeLogLabel(): String {
    return when (this) {
        is AiToolStreamPart -> "tool(type=$toolName,state=$state,input=${input != null},rawInput=${rawInput != null},output=${output != null})"
        is AiDataStreamPart -> "data(type=$type,state=$state,payload=${payload.isNotBlank()})"
        is AiTextStreamPart -> "text(state=$state,len=${text.length})"
        is AiReasoningStreamPart -> "reasoning(state=$state,len=${text.length})"
        is AiSourceStreamPart -> "source(type=$sourceType,state=$state,url=${url != null})"
        is AiFileStreamPart -> "file(state=$state,url=${url != null})"
        is AiErrorStreamPart -> "error(state=$state,len=${errorText.length})"
        is AiCustomStreamPart -> "custom(type=$type,state=$state)"
    }
}
