/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import dev.zacsweers.metro.Inject
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
        val streamParts = snapshot.parts.map { it.toAiStreamPart() }
        Timber.tag(TAG).d(
            "AI stream parts: %s",
            streamParts.joinToString(separator = ", ") { it.safeLogLabel() }
        )
        val textParts = streamParts.filterIsInstance<AiTextStreamPart>()
        val reasoningParts = streamParts.filterIsInstance<AiReasoningStreamPart>()
        val toolParts = streamParts.filterIsInstance<AiToolStreamPart>()
        val sourceParts = streamParts.filterIsInstance<AiSourceStreamPart>()

        return TimelineItemAiContent(
            body = textParts.joinToString(separator = "\n\n") { it.text },
            isEdited = isEdited,
            isStreaming = snapshot.status == StreamStatus.Loading || snapshot.status == StreamStatus.Streaming,
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
            sources = sourceParts.map { part ->
                AiSource(
                    title = part.title,
                    url = part.url,
                    snippet = null,
                )
            }.toImmutableList(),
            quickActions = emptyList<AiQuickAction>().toImmutableList(),
            parts = streamParts.toImmutableList(),
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
                    input = input?.asDisplayString() ?: rawInput?.asDisplayString(),
                    output = output?.asDisplayString(),
                    errorText = error?.message,
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

    private companion object {
        const val TAG = "AiSdkStreamReducer"
        const val DEFAULT_DONE_STATE = "done"
        const val DEFAULT_ERROR_STATE = "error"
    }
}

private fun AiStreamPart.safeLogLabel(): String {
    return when (this) {
        is AiToolStreamPart -> "tool(type=$toolName,state=$state,input=${input != null},output=${output != null})"
        is AiDataStreamPart -> "data(type=$type,state=$state,payload=${payload.isNotBlank()})"
        is AiTextStreamPart -> "text(state=$state,len=${text.length})"
        is AiReasoningStreamPart -> "reasoning(state=$state,len=${text.length})"
        is AiSourceStreamPart -> "source(type=$sourceType,state=$state,url=${url != null})"
        is AiFileStreamPart -> "file(state=$state,url=${url != null})"
        is AiErrorStreamPart -> "error(state=$state,len=${errorText.length})"
        is AiCustomStreamPart -> "custom(type=$type,state=$state)"
    }
}
