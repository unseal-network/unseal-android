/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class AiStreamRenderModel(
    val streamId: String?,
    val schemaVersion: Int,
    val streamStatus: String?,
    val updatedAtMs: Long?,
    val completedAtMs: Long?,
    val streamError: String?,
    val renderVersion: String?,
    val isStreaming: Boolean,
    val isTerminal: Boolean,
    val cursorMode: AiStreamCursorMode,
    val markdownBlocks: ImmutableList<AiMarkdownBlock>,
    val thinkingSteps: ImmutableList<AiThinkingStep>,
    val toolCalls: ImmutableList<AiToolCall>,
    val sources: ImmutableList<AiSource>,
    val quickActions: ImmutableList<AiQuickAction>,
    val parts: ImmutableList<AiStreamPart> = persistentListOf(),
    val renderableToolParts: ImmutableList<AiToolStreamPart> = persistentListOf(),
    val toolCardEntries: ImmutableList<AiToolCardEntry> = persistentListOf(),
    val toolCallRoot: ToolCallRootRenderModel? = null,
    val passthroughParts: ImmutableList<AiStreamPart> = persistentListOf(),
    val visibleParts: ImmutableList<AiStreamPart> = persistentListOf(),
    val firstToolPartIndex: Int? = null,
    val lastPartIsStreamingText: Boolean = false,
) {
    val body: String
        get() = markdownBlocks.joinToString(separator = "\n\n") { it.text }

    fun toTimelineContent(isEdited: Boolean, sender: String?): TimelineItemAiContent {
        return TimelineItemAiContent(
            body = body,
            isEdited = isEdited,
            isStreaming = isStreaming,
            isTerminal = isTerminal,
            streamId = streamId,
            schemaVersion = schemaVersion,
            streamStatus = streamStatus,
            updatedAtMs = updatedAtMs,
            completedAtMs = completedAtMs,
            streamError = streamError,
            renderVersion = renderVersion,
            sender = sender,
            roomId = null,
            eventId = null,
            thinkingSteps = thinkingSteps,
            toolCalls = toolCalls,
            sources = sources,
            quickActions = quickActions,
            parts = parts,
            renderableToolParts = renderableToolParts,
            toolCardEntries = toolCardEntries,
            toolCallRoot = toolCallRoot,
            passthroughParts = passthroughParts,
            visibleParts = visibleParts,
            firstToolPartIndex = firstToolPartIndex,
            lastPartIsStreamingText = lastPartIsStreamingText,
        )
    }
}

enum class AiStreamCursorMode {
    None,
    Loading,
    TrailingCursor,
}

@Immutable
data class AiMarkdownBlock(
    val id: String,
    val text: String,
    val state: String,
)

/**
 * Native (degraded) rendering of an Unseal AI/assistant "stream" message.
 *
 * Mirrors the iOS AIMessageContent parsing (RoomTimelineItemFactory.parseAIMessageContentSync):
 * the rich parts live in the event's original JSON under custom keys, not in the typed Matrix
 * message content. The markdown rendering of [body] is intentionally plain text here — the
 * iOS rich markdown renderer depends on the MarkdownUI / UnsealUI component libraries which are
 * out of scope for this native first version.
 */
@Immutable
data class TimelineItemAiContent(
    val body: String,
    override val isEdited: Boolean,
    val isStreaming: Boolean,
    /** True once the stream reached a terminal status (Completed/Failed/Cancelled). */
    val isTerminal: Boolean = false,
    val streamId: String? = null,
    val schemaVersion: Int = 1,
    val streamStatus: String? = null,
    val updatedAtMs: Long? = null,
    val completedAtMs: Long? = null,
    val streamError: String? = null,
    val renderVersion: String? = null,
    val sender: String? = null,
    val roomId: String? = null,
    val eventId: String? = null,
    val targetUserId: String? = null,
    val thinkingSteps: ImmutableList<AiThinkingStep>,
    val toolCalls: ImmutableList<AiToolCall>,
    val sources: ImmutableList<AiSource>,
    val quickActions: ImmutableList<AiQuickAction>,
    val parts: ImmutableList<AiStreamPart> = persistentListOf(),
    val renderableToolParts: ImmutableList<AiToolStreamPart> = persistentListOf(),
    val toolCardEntries: ImmutableList<AiToolCardEntry> = persistentListOf(),
    val toolCallRoot: ToolCallRootRenderModel? = null,
    val passthroughParts: ImmutableList<AiStreamPart> = persistentListOf(),
    /** Ordered, hidden-filtered parts (tool markers kept) — the render source, mirrors iOS groupedParts. */
    val visibleParts: ImmutableList<AiStreamPart> = persistentListOf(),
    val firstToolPartIndex: Int? = null,
    val lastPartIsStreamingText: Boolean = false,
) : TimelineItemEventContent, TimelineItemEventMutableContent {
    override val type: String = "TimelineItemAiContent"

    val hasRichParts: Boolean
        get() = parts.isNotEmpty() || thinkingSteps.isNotEmpty() || toolCalls.isNotEmpty() || sources.isNotEmpty() || quickActions.isNotEmpty()
}

@Immutable
sealed interface AiStreamPart {
    val id: String
    val state: String
}

@Immutable
data class AiTextStreamPart(
    override val id: String,
    override val state: String,
    val text: String,
) : AiStreamPart

@Immutable
data class AiReasoningStreamPart(
    override val id: String,
    override val state: String,
    val text: String,
) : AiStreamPart

@Immutable
data class AiToolStreamPart(
    override val id: String,
    override val state: String,
    val toolName: String,
    val title: String?,
    val input: String?,
    val output: String?,
    val errorText: String?,
    val rawInput: String? = null,
) : AiStreamPart

@Immutable
data class AiToolCardEntry(
    val id: String,
    val name: String,
    /** Stable renderer key matching iOS ToolCallEntry.props._cardType. */
    val cardType: String,
    /** Mirrors iOS CardToolState: "calling", "done", or "error". */
    val state: String,
    /** JSON object string matching iOS ToolCallEntry.props, including `_cardType`. */
    val props: String,
)

@Immutable
data class ToolCallRootRenderModel(
    val id: String,
    val title: String,
    val entries: ImmutableList<AiToolCardEntry>,
    val selectedIndex: Int,
    val doneCount: Int,
    val errorCount: Int,
    val callingCount: Int,
    val allFinished: Boolean,
    val isSingleTool: Boolean,
    val expandedByDefault: Boolean,
) {
    val selectedEntry: AiToolCardEntry?
        get() = entries.getOrNull(selectedIndex)
}

@Immutable
data class AiSourceStreamPart(
    override val id: String,
    override val state: String,
    val sourceType: String,
    val title: String,
    val url: String?,
    val filename: String?,
    val mediaType: String?,
) : AiStreamPart

@Immutable
data class AiErrorStreamPart(
    override val id: String,
    override val state: String,
    val errorText: String,
) : AiStreamPart

@Immutable
data class AiDataStreamPart(
    override val id: String,
    override val state: String,
    val type: String,
    val payload: String,
) : AiStreamPart

@Immutable
data class AiFileStreamPart(
    override val id: String,
    override val state: String,
    val mediaType: String?,
    val filename: String?,
    val url: String?,
) : AiStreamPart

@Immutable
data class AiCustomStreamPart(
    override val id: String,
    override val state: String,
    val type: String,
    val payload: String,
) : AiStreamPart

@Immutable
data class AiPptWorkflowStreamPart(
    override val id: String,
    override val state: String,
    val taskId: String,
    val totalSlides: Int,
    val websocketUrl: String?,
    val isStreaming: Boolean,
) : AiStreamPart

@Immutable
data class AiThinkingStep(
    val title: String,
    val description: String,
    val status: String,
)

@Immutable
data class AiToolCall(
    val name: String,
    val displayName: String,
    val state: String,
    val output: String?,
    val error: String?,
)

@Immutable
data class AiSource(
    val title: String,
    val url: String?,
    val snippet: String?,
)

@Immutable
data class AiQuickAction(
    val label: String,
    val action: String,
)
