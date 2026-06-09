/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

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
    val thinkingSteps: ImmutableList<AiThinkingStep>,
    val toolCalls: ImmutableList<AiToolCall>,
    val sources: ImmutableList<AiSource>,
    val quickActions: ImmutableList<AiQuickAction>,
) : TimelineItemEventContent, TimelineItemEventMutableContent {
    override val type: String = "TimelineItemAiContent"

    val hasRichParts: Boolean
        get() = thinkingSteps.isNotEmpty() || toolCalls.isNotEmpty() || sources.isNotEmpty() || quickActions.isNotEmpty()
}

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
