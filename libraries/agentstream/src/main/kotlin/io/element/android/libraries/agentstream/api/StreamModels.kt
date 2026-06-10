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
        val textState: TextPartState,
        override val type: String = "text",
        override val state: String = textState.wireValue,
    ) : StreamPart

    data class Reasoning(
        override val id: String,
        val text: String,
        val reasoningState: TextPartState,
        override val type: String = "reasoning",
        override val state: String = reasoningState.wireValue,
    ) : StreamPart

    data class Tool(
        override val id: String,
        val toolState: ToolPartState,
        val toolName: String? = null,
        val toolCallId: String? = null,
        val input: JsonElement? = null,
        val output: JsonElement? = null,
        val error: StreamError? = null,
        val title: String? = null,
        override val type: String = toolName?.let { "tool-$it" } ?: "tool",
        override val state: String = toolState.wireValue,
    ) : StreamPart

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
    OutputError("output-error"),
    ;

    companion object {
        fun fromWire(value: String?): ToolPartState? = entries.firstOrNull { it.wireValue == value }
    }
}
