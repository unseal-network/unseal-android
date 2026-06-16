/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.serialization.json.JsonElement

const val AGENT_STREAM_SCHEMA_VERSION = 1

data class StreamRequest(
    val streamId: String,
    val sender: String,
    val roomId: String,
    val eventId: String,
    val includeRawEvents: Boolean = true,
)

data class StreamSnapshot(
    val schemaVersion: Int,
    val streamId: String,
    val status: StreamStatus,
    val parts: List<StreamPart>,
    val rawEvents: List<RawStreamEvent>,
    val updatedAtMs: Long,
    val completedAtMs: Long?,
    val error: StreamError?,
) {
    val isTerminal: Boolean
        get() = status in setOf(StreamStatus.Completed, StreamStatus.Failed, StreamStatus.Cancelled)
}

/**
 * Returns a display/storage safe completed snapshot.
 *
 * The AI SDK stream is a patch state machine: in well-formed streams, each part receives its own
 * terminal state. Some historic server/cache payloads completed the stream without sending an
 * explicit tool/text/reasoning end event, leaving parts stuck at input/streaming states. Completed
 * snapshots are terminal for UI and storage, so non-terminal part states must be closed here rather
 * than leaving every client renderer to guess.
 */
fun StreamSnapshot.normalizedForTerminalState(): StreamSnapshot {
    if (status != StreamStatus.Completed) return this
    return copy(parts = parts.map { it.normalizedCompletedPart() })
}

enum class StreamStatus {
    Idle,
    Loading,
    Streaming,
    Completed,
    Failed,
    Cancelled,
}

data class StreamError(
    val message: String,
    val code: String? = null,
    val raw: JsonElement? = null,
)

data class RawStreamEvent(
    val sequence: Long,
    val eventType: String,
    val partId: String?,
    val payload: JsonElement,
    val receivedAtMs: Long,
)

sealed interface StreamPart {
    val id: String
    val type: String
    val state: String?

    data class Text(
        override val id: String,
        val text: String,
        val textState: String,
        override val type: String = "text",
    ) : StreamPart {
        constructor(
            id: String,
            text: String,
            textState: TextPartState,
            type: String = "text",
        ) : this(
            id = id,
            text = text,
            textState = textState.wireValue,
            type = type,
        )

        override val state: String
            get() = textState

        val textPartState: TextPartState?
            get() = TextPartState.fromWire(textState)
    }

    data class Reasoning(
        override val id: String,
        val text: String,
        val reasoningState: String,
        override val type: String = "reasoning",
    ) : StreamPart {
        constructor(
            id: String,
            text: String,
            reasoningState: TextPartState,
            type: String = "reasoning",
        ) : this(
            id = id,
            text = text,
            reasoningState = reasoningState.wireValue,
            type = type,
        )

        override val state: String
            get() = reasoningState

        val reasoningPartState: TextPartState?
            get() = TextPartState.fromWire(reasoningState)
    }

    data class Tool(
        override val id: String,
        val toolState: String,
        val toolName: String? = null,
        val toolCallId: String? = null,
        val input: JsonElement? = null,
        val rawInput: JsonElement? = null,
        val output: JsonElement? = null,
        val error: StreamError? = null,
        val title: String? = null,
        override val type: String = toolName?.let { "tool-$it" } ?: "tool",
    ) : StreamPart {
        constructor(
            id: String,
            toolState: ToolPartState,
            toolName: String? = null,
            toolCallId: String? = null,
            input: JsonElement? = null,
            rawInput: JsonElement? = null,
            output: JsonElement? = null,
            error: StreamError? = null,
            title: String? = null,
            type: String = toolName?.let { "tool-$it" } ?: "tool",
        ) : this(
            id = id,
            toolState = toolState.wireValue,
            toolName = toolName,
            toolCallId = toolCallId,
            input = input,
            rawInput = rawInput,
            output = output,
            error = error,
            title = title,
            type = type,
        )

        override val state: String
            get() = toolState

        val toolPartState: ToolPartState?
            get() = ToolPartState.fromWire(toolState)
    }

    data class Data(
        override val id: String,
        val data: JsonElement,
        override val type: String = "data",
        override val state: String? = null,
    ) : StreamPart

    data class Source(
        override val id: String,
        val sourceType: String? = null,
        val title: String? = null,
        val url: String? = null,
        val payload: JsonElement? = null,
        override val type: String = "source",
        override val state: String? = null,
    ) : StreamPart

    data class File(
        override val id: String,
        val mediaType: String? = null,
        val filename: String? = null,
        val url: String? = null,
        val data: JsonElement? = null,
        override val type: String = "file",
        override val state: String? = null,
    ) : StreamPart

    data class Step(
        override val id: String,
        val title: String? = null,
        val payload: JsonElement? = null,
        override val type: String = "step",
        override val state: String? = null,
    ) : StreamPart

    data class Error(
        override val id: String,
        val error: StreamError,
        override val type: String = "error",
        override val state: String? = null,
    ) : StreamPart

    data class Custom(
        override val id: String,
        val customType: String,
        val payload: JsonElement,
        override val type: String = customType,
        override val state: String? = null,
    ) : StreamPart
}

enum class TextPartState(val wireValue: String) {
    Streaming("streaming"),
    Complete("complete"),
    Done("done"),
    ;

    companion object {
        fun fromWire(value: String?): TextPartState? = entries.firstOrNull { it.wireValue == value }
    }
}

enum class ToolPartState(val wireValue: String) {
    InputStreaming("input-streaming"),
    InputAvailable("input-available"),
    OutputAvailable("output-available"),
    ApprovalRequested("approval-requested"),
    ApprovalResponded("approval-responded"),
    OutputError("output-error"),
    OutputDenied("output-denied"),
    ;

    companion object {
        fun fromWire(value: String?): ToolPartState? = entries.firstOrNull { it.wireValue == value }
    }
}

private fun StreamPart.normalizedCompletedPart(): StreamPart {
    return when (this) {
        is StreamPart.Text -> if (textState == TextPartState.Done.wireValue) this else copy(textState = TextPartState.Done.wireValue)
        is StreamPart.Reasoning -> if (reasoningState == TextPartState.Done.wireValue) this else copy(reasoningState = TextPartState.Done.wireValue)
        is StreamPart.Tool -> if (toolState in TERMINAL_TOOL_STATES) this else copy(toolState = ToolPartState.OutputAvailable.wireValue)
        else -> this
    }
}

private val TERMINAL_TOOL_STATES = setOf(
    ToolPartState.OutputAvailable.wireValue,
    ToolPartState.ApprovalRequested.wireValue,
    ToolPartState.ApprovalResponded.wireValue,
    ToolPartState.OutputError.wireValue,
    ToolPartState.OutputDenied.wireValue,
)
