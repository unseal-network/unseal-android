/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import dev.zacsweers.metro.Inject
import io.element.android.features.messages.impl.timeline.components.event.isHiddenStreamPart
import io.element.android.features.messages.impl.timeline.components.event.isRegisteredToolName
import io.element.android.features.messages.impl.timeline.components.event.toRenderableToolParts
import io.element.android.features.messages.impl.timeline.model.event.AiCustomStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiErrorStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiFileStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiQuickAction
import io.element.android.features.messages.impl.timeline.model.event.AiReasoningStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiSource
import io.element.android.features.messages.impl.timeline.model.event.AiSourceStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamPart
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
        val mappedParts = snapshot.parts.map { it.toAiStreamPart() }
            .finalizeIfCompleted(snapshot)
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
        // Mirror iOS: only registered tools render (as cards). Unregistered tool parts render
        // nothing (no generic card), so drop ALL tool parts from the pass-through list.
        val renderableToolParts = visible.filterIsInstance<AiToolStreamPart>()
            .filter { it.toolName.isRegisteredToolName }
            .flatMap { it.toRenderableToolParts() }
            .toImmutableList()
        val passthroughParts = visible.filterNot { it is AiToolStreamPart }.toImmutableList()

        return TimelineItemAiContent(
            body = textParts.joinToString(separator = "\n\n") { it.text },
            isEdited = isEdited,
            isStreaming = snapshot.status == StreamStatus.Loading || snapshot.status == StreamStatus.Streaming,
            isTerminal = snapshot.status == StreamStatus.Completed ||
                snapshot.status == StreamStatus.Failed ||
                snapshot.status == StreamStatus.Cancelled,
            streamId = snapshot.streamId,
            sender = sender,
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
            passthroughParts = passthroughParts,
            visibleParts = visible.toImmutableList(),
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

    /**
     * Mirrors iOS: when the SDK stream reaches its terminal `Completed` state, every part is
     * finalized — a completed stream cannot have a part still "streaming"/"calling". Coercing the
     * states here (before the renderable lists are computed) makes:
     *  - reasoning parts render "Thought" instead of a stuck "Thinking…",
     *  - tool parts stop showing "Running tool…",
     *  - sub-agent / multi-execute parts become `isDone`, so their results expand into cards.
     *
     * It also repairs persisted snapshots loaded on room re-entry, which may have been stored with
     * in-progress part states. Failed/Cancelled streams are left untouched (their parts may
     * legitimately be incomplete).
     */
    private fun List<AiStreamPart>.finalizeIfCompleted(snapshot: StreamSnapshot): List<AiStreamPart> {
        if (snapshot.status != StreamStatus.Completed) {
            return this
        }
        return map { part ->
            when (part) {
                is AiReasoningStreamPart -> if (part.state == "done") part else part.copy(state = "done")
                is AiTextStreamPart -> if (part.state == "done") part else part.copy(state = "done")
                is AiToolStreamPart -> if (part.state in FINALIZED_TOOL_STATES) part else part.copy(state = "output-available")
                else -> part
            }
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

    private companion object {
        const val TAG = "AiSdkStreamReducer"
        const val DEFAULT_DONE_STATE = "done"
        const val DEFAULT_ERROR_STATE = "error"
        const val DEFAULT_STREAM_ERROR_MESSAGE = "Stream error"
        // Tool states already considered terminal (done/error per AiToolCardLogic). Anything else on
        // a Completed stream is coerced to "output-available".
        val FINALIZED_TOOL_STATES = setOf(
            "output-available",
            "approval-requested",
            "approval-responded",
            "output-error",
            "output-denied",
        )
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
