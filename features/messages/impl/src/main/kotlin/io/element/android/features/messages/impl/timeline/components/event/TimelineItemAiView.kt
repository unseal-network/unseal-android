/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.ui.layout.onSizeChanged
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.components.SelectedStatePill
import io.element.android.features.messages.impl.components.ShapedClickableSurface
import io.element.android.features.messages.impl.timeline.model.event.AiCustomStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiDataStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiErrorStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiFileStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiReasoningStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiSource
import io.element.android.features.messages.impl.timeline.model.event.AiSourceStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiThinkingStep
import io.element.android.features.messages.impl.timeline.model.event.AiTextStreamPart
import io.element.android.features.messages.impl.timeline.model.event.AiToolCardEntry
import io.element.android.features.messages.impl.timeline.model.event.AiToolCall
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCard
import io.element.android.features.messages.impl.timeline.components.event.toolcards.ToolCardFinalProps
import io.element.android.features.messages.impl.timeline.components.event.toolcards.resolveToolCardType
import io.element.android.features.messages.impl.timeline.components.event.toolcards.toCardDataJson
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.ToolCallRootRenderModel
import io.element.android.libraries.androidutils.text.LinkifyHelper
import io.element.android.libraries.textcomposer.ElementRichTextEditorStyle
import io.element.android.wysiwyg.compose.EditorStyledText
import io.element.android.wysiwyg.link.Link
import org.json.JSONObject

private val ToolCallContentMaxHeight = 360.dp

/**
 * Native (degraded) renderer for [TimelineItemAiContent]. Composes the AI stream sub-parts that
 * iOS renders with plain SwiftUI (thinking process, tool calls, sources). The markdown body is
 * rendered as plain text — rich markdown rendering depends on external component libraries that
 * are out of scope for this first version. Quick actions are shown as static chips (the iOS
 * "send action" wiring is not migrated).
 */
@Composable
fun TimelineItemAiView(
    content: TimelineItemAiContent,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Report the body as full-width so the bubble's ContentAvoidingLayout places the
            // timestamp / "edited" marker on its own row below, never overlapping the content.
            .onSizeChanged { size ->
                onContentLayoutChange(
                    ContentAvoidingLayoutData(
                        contentWidth = size.width,
                        contentHeight = size.height,
                        nonOverlappingContentWidth = size.width,
                        nonOverlappingContentHeight = size.height,
                    )
                )
            },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Mirror iOS BubbleMessageView: render ONLY the (hidden-filtered, ordered) stream parts.
        // There is no "thinking_process" concept in iOS — reasoning is a stream part. When parts
        // haven't loaded yet, fall back to real body text only. Matrix stream placeholders such as
        // "thinking", "loading", or the stream id itself render as a loading indicator instead.
        if (content.visibleParts.isNotEmpty()) {
            AiStreamPartsView(
                visibleParts = content.visibleParts,
                toolCallRoot = content.toolCallRoot,
                firstToolPartIndex = content.firstToolPartIndex,
                lastPartIsStreamingText = content.lastPartIsStreamingText,
                isStreaming = content.isStreaming,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
        } else if (content.shouldRenderBodyFallback()) {
            MarkdownBody(
                text = content.body,
                isStreaming = content.isStreaming,
                onLinkClick = onLinkClick,
            )
            if (content.isStreaming) {
                StreamingCursor()
            }
        } else {
            // Nothing renderable: keep a loading state while the stream is still in flight,
            // but once it reaches a terminal status with no content show a failure card
            // instead of an empty bubble.
            if (content.isTerminal) {
                AiUnavailableCard()
            } else {
                AiLoadingIndicator()
            }
        }
        if (content.sources.isNotEmpty()) {
            SourcesSection(content.sources)
        }
        if (content.quickActions.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                content.quickActions.forEach { action ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

internal fun TimelineItemAiContent.shouldRenderBodyFallback(): Boolean {
    if (body.isBlank()) return false
    val streamId = streamId ?: return true
    return !body.isStreamPlaceholderBody(streamId)
}

private fun String.isStreamPlaceholderBody(streamId: String): Boolean {
    val normalized = trim().lowercase()
        .trim('.', '…', '。', '!', '！')
    if (normalized == streamId.trim().lowercase()) return true
    return normalized in setOf(
        "thinking",
        "loading",
        "streaming",
        "pending",
        "running",
        "思考中",
        "处理中",
    )
}

@Composable
private fun AiStreamPartsView(
    visibleParts: List<AiStreamPart>,
    toolCallRoot: ToolCallRootRenderModel?,
    firstToolPartIndex: Int?,
    lastPartIsStreamingText: Boolean,
    isStreaming: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    // Mirror iOS BubbleMessageView: walk the ordered parts; insert ONE ToolCallRootCard at the
    // first tool part's position; render every other part inline in order; trailing streaming
    // cursor unless the last part is already a streaming text (which carries its own cursor).
    val toolCardInserted = toolCallRoot != null
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        visibleParts.forEachIndexed { index, part ->
            when (part) {
                is AiToolStreamPart -> {
                    if (index == firstToolPartIndex) {
                        val rootModel = toolCallRoot ?: return@forEachIndexed
                        ToolCallRootCard(
                            model = rootModel,
                            isStreaming = isStreaming,
                            onLinkClick = onLinkClick,
                            onLinkLongClick = onLinkLongClick,
                        )
                    }
                    // Other tool parts are represented by the single root card above.
                }
                is AiTextStreamPart -> TextPart(part, onLinkClick, onLinkLongClick)
                is AiReasoningStreamPart -> ReasoningPart(part)
                is AiSourceStreamPart -> SourcePart(part, onLinkClick, onLinkLongClick)
                is AiFileStreamPart -> FilePart(part, onLinkClick, onLinkLongClick)
                is AiErrorStreamPart -> ErrorPart(part)
                is AiDataStreamPart -> DataPart(part, onLinkClick, onLinkLongClick, toolCardInserted)
                is AiCustomStreamPart -> Unit
            }
        }
        if (isStreaming && !lastPartIsStreamingText) {
            StreamingCursor()
        }
    }
}

/** Trailing streaming indicator (iOS StreamingCursor). */
@Composable
private fun StreamingCursor() {
    val transition = rememberInfiniteTransition(label = "ai-streaming-cursor")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ai-streaming-cursor-alpha",
    )
    Box(
        modifier = Modifier
            .width(2.dp)
            .height(16.dp)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.onSurfaceVariant),
    )
}

/** Three pulsing dots shown while a stream is still in flight but has no renderable content yet. */
@Composable
private fun AiLoadingIndicator() {
    val transition = rememberInfiniteTransition(label = "ai-loading")
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 160),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "ai-dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/** Shown when a stream reaches a terminal status but produced no renderable content. */
@Composable
private fun AiUnavailableCard() {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "消息内容加载失败",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "该回复没有可显示的内容，请稍后重试。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TextPart(
    part: AiTextStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    if (part.text.isNotBlank()) {
        MarkdownBody(
            text = part.text,
            isStreaming = part.state == "streaming",
            onLinkClick = onLinkClick,
        )
    }
}

@Composable
private fun ReasoningPart(part: AiReasoningStreamPart) {
    if (part.text.isBlank()) return
    val isStreaming = part.state == "streaming"
    var expanded by remember(part.id) { mutableStateOf(isStreaming) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ShapedClickableSurface(
                onClick = { expanded = !expanded },
                enabled = !isStreaming,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth(),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = (if (expanded) "▾ " else "▸ ") + if (isStreaming) "思考中" else "思考",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                )
            }
            if (expanded) {
                Text(
                    text = part.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ToolCallRootCard(
    model: ToolCallRootRenderModel,
    isStreaming: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    val entries = model.entries
    if (entries.isEmpty()) return
    var selectedIndex by remember(model.id) { mutableStateOf(model.selectedIndex) }
    var expanded by remember(model.id) { mutableStateOf(model.expandedByDefault) }
    var userToggled by remember(entries.first().id) { mutableStateOf(false) }
    if (selectedIndex !in entries.indices) selectedIndex = model.selectedIndex.coerceIn(entries.indices)
    val selectedEntry = if (selectedIndex == model.selectedIndex) {
        model.selectedEntry ?: entries[selectedIndex]
    } else {
        entries[selectedIndex]
    }
    LaunchedEffect(model.selectedIndex, entries.size, model.allFinished) {
        if (!userToggled) {
            selectedIndex = model.selectedIndex
            if (!model.allFinished) {
                expanded = true
            }
        }
    }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 0f else -90f,
        animationSpec = tween(durationMillis = 300),
        label = "tool-card-chevron-rotation",
    )
    val cardShape = RoundedCornerShape(12.dp)
    val headerShape = if (expanded) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    } else {
        cardShape
    }
    val headerInteractionSource = remember { MutableInteractionSource() }

    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            ShapedClickableSurface(
                onClick = {
                    userToggled = true
                    expanded = !expanded
                },
                shape = headerShape,
                modifier = Modifier.fillMaxWidth(),
                interactionSource = headerInteractionSource,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ToolProgressIndicator(
                        total = entries.size,
                        doneCount = model.doneCount,
                        errorCount = model.errorCount,
                        isCalling = model.callingCount > 0,
                    )
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (model.doneCount > 0) {
                        CountPill(icon = Icons.Filled.Check, count = model.doneCount, color = Color(0xFF2E7D32))
                    }
                    if (model.errorCount > 0) {
                        CountPill(icon = Icons.Filled.PriorityHigh, count = model.errorCount, color = MaterialTheme.colorScheme.error)
                    }
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(chevronRotation),
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(durationMillis = 300)) + fadeIn(animationSpec = tween(durationMillis = 180)),
                exit = shrinkVertically(animationSpec = tween(durationMillis = 250)) + fadeOut(animationSpec = tween(durationMillis = 140)),
            ) {
                if (!model.isSingleTool) {
                    Column {
                        ToolSelectionTabs(
                            entries = entries,
                            selectedIndex = selectedIndex,
                            onSelected = {
                                userToggled = true
                                selectedIndex = it
                            },
                        )
                        ToolEntryContentViewport(
                            entry = selectedEntry,
                            isStreaming = isStreaming,
                            allFinished = model.allFinished,
                            onLinkClick = onLinkClick,
                            onLinkLongClick = onLinkLongClick,
                        )
                    }
                } else {
                    ToolEntryContentViewport(
                        entry = selectedEntry,
                        isStreaming = isStreaming,
                        allFinished = model.allFinished,
                        onLinkClick = onLinkClick,
                        onLinkLongClick = onLinkLongClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolSelectionTabs(
    entries: List<AiToolCardEntry>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entries.forEachIndexed { index, entry ->
            val isSelected = index == selectedIndex
            SelectedStatePill(
                selected = isSelected,
                onClick = { onSelected(index) },
                selectedColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                unselectedColor = Color.Transparent,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ToolStateDot(entry.state)
                    Text(
                        text = entry.name,
                        style = MaterialTheme.typography.labelMedium,
                        color = LocalContentColor.current,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolEntryContentViewport(
    entry: AiToolCardEntry,
    isStreaming: Boolean,
    allFinished: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    val scrollState = remember(entry.id) { androidx.compose.foundation.ScrollState(0) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ToolCallContentMaxHeight)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState),
        ) {
            ToolEntryContent(
                entry = entry,
                isStreaming = isStreaming,
                allFinished = allFinished,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ToolEntryContent(
    entry: AiToolCardEntry,
    isStreaming: Boolean,
    allFinished: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (entry.state) {
            "calling" -> ToolCallingEntryContent(entry, allFinished)
            "error" -> ToolErrorEntryContent(entry)
            else -> {
                val rendered = ToolEntryPayloadCard(
                    entry = entry,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
                if (!rendered) {
                    ToolEmptyState(isStreaming = isStreaming)
                }
            }
        }
    }
}

@Composable
private fun ToolCallingEntryContent(
    entry: AiToolCardEntry,
    allFinished: Boolean,
) {
    val rendered = ToolEntryPayloadCard(
        entry = entry,
        onLinkClick = {},
        onLinkLongClick = {},
    )
    if (!rendered) {
        ToolEmptyState(isStreaming = !allFinished)
    }
}

@Composable
private fun ToolErrorEntryContent(entry: AiToolCardEntry) {
    val props = remember(entry.props) { runCatching { JSONObject(entry.props) }.getOrNull() ?: JSONObject() }
    val message = props.optString("errorText")
        .takeIf { it.isNotBlank() && !it.looksLikeRawJsonError() }
        ?: "Failed to get results"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolEntryPayloadCard(
    entry: AiToolCardEntry,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
): Boolean {
    val props = remember(entry.props) { runCatching { JSONObject(entry.props) }.getOrNull() ?: JSONObject() }
    return ToolCardFinalProps(cardType = entry.cardType, data = props, onLinkClick = {})
}

@Composable
private fun ToolPartContent(
    part: AiToolStreamPart,
    isStreaming: Boolean,
    allFinished: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Mirror iOS: the tool name lives only in the header; the body shows the card content
    // (or a shimmer/state line while calling), never a repeated title.
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when {
            part.isCalling -> ToolCallingContent(part, allFinished, onLinkClick, onLinkLongClick)
            part.isError -> ToolErrorContent(part)
            else -> {
                val rendered = ToolPayloadCandidates(
                    part = part,
                    payloads = listOf(part.output, part.input, part.rawInput),
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
                if (!rendered) {
                    ToolEmptyState(isStreaming = !part.isDone)
                }
            }
        }
    }
}

@Composable
private fun ToolCallingContent(
    part: AiToolStreamPart,
    allFinished: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    if (part.input?.isNotBlank() == true) {
        val rendered = ToolPayloadCandidates(
            part = part,
            payloads = listOf(part.input, part.rawInput),
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
        )
        if (!rendered) {
            ToolEmptyState(isStreaming = !allFinished)
        }
    } else {
        ToolEmptyState(isStreaming = !allFinished)
    }
}

@Composable
private fun ToolEmptyState(isStreaming: Boolean) {
    if (isStreaming) {
        AiLoadingIndicator()
    } else {
        Text(
            text = "Completed",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolErrorContent(part: AiToolStreamPart) {
    Text(
        text = part.errorText?.takeIf { it.isNotBlank() } ?: "Tool call failed.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

private fun String.looksLikeRawJsonError(): Boolean {
    val normalized = trim()
    return normalized.startsWith("{") ||
        normalized.contains("ToolNotFoundError") ||
        normalized.contains("\"name\"") && normalized.contains("Error")
}

@Composable
private fun ToolPayloadCandidates(
    part: AiToolStreamPart,
    payloads: List<String?>,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
): Boolean {
    val distinctPayloads = payloads
        .mapNotNull { it?.takeIf { value -> value.isNotBlank() } }
        .distinct()
    for (payload in distinctPayloads) {
        if (ToolPayloadCard(part, payload = payload, onLinkClick, onLinkLongClick)) {
            return true
        }
    }
    return false
}

@Composable
private fun ToolPayloadCard(
    part: AiToolStreamPart,
    payload: String,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
): Boolean {
    // 1. Try the dispatched tool card (iOS ToolCallRootCardAdapter parity). If it renders, done.
    val cardType = remember(part.toolName) { resolveToolCardType(part.toolName) }
    val cardData = remember(payload) { payload.toCardDataJson() }
    if (cardType != null && cardData != null && ToolCard(cardType, cardData)) {
        return true
    }
    if (!part.allowsRawPayloadFallback()) {
        return false
    }
    // 2. Otherwise show only structured, user-readable items. Raw payloads remain available through
    // the stream snapshot/debug path, but the room timeline should not expose JSON to end users.
    val model = remember(part.toolName, payload) {
        payload.toToolCardModel(part.toolName)
    }
    if (model == null || model.items.isEmpty()) return false
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            model.items.take(MAX_RENDERED_ITEMS).forEach { item ->
                ToolCardItem(item, onLinkClick, onLinkLongClick)
            }
            if (model.moreCount > 0) {
                Text(
                    text = "+${model.moreCount} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    return true
}

@Composable
private fun ToolCardItem(
    item: ToolRenderableItem,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LinkifiedAiText(
            text = item.title,
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
        )
        item.subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.url?.takeIf { it.isNotBlank() }?.let {
            LinkifiedAiText(
                text = it,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
        }
    }
}

@Composable
private fun DataPart(
    part: AiDataStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    toolCardInserted: Boolean,
) {
    when (part.type) {
        "data-error" -> ErrorPart(AiErrorStreamPart(id = part.id, state = part.state, errorText = part.payload.errorTextFromJson() ?: "Stream error"))
        "data-error-card" -> ErrorCard(part.payload)
        "data-tool-call-suspended" -> SuspendedToolCard(part.payload)
        "data-ui-spec", "data-json-render", "data-spec" -> {
            if (!toolCardInserted) {
                JsonSpecRender(
                    payload = part.payload,
                    onLinkClick = onLinkClick,
                )
            }
        }
        else -> Unit
    }
}

@Composable
private fun ErrorCard(payload: String) {
    val json = payload.jsonObjectOrNull()
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = json?.optString("title")?.takeIf { it.isNotBlank() } ?: "Something went wrong",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                text = json?.optString("message")?.takeIf { it.isNotBlank() } ?: payload.errorTextFromJson().orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun SuspendedToolCard(payload: String) {
    val model = remember(payload) { payload.toSuspendedToolRenderModel() }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = if (model.kind == SuspendedToolKind.DeleteSchedule || model.kind == SuspendedToolKind.DeleteAgentVaultEntry) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            },
                            shape = RoundedCornerShape(9.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (model.kind == SuspendedToolKind.DeleteSchedule || model.kind == SuspendedToolKind.DeleteAgentVaultEntry) {
                            Icons.Filled.PriorityHigh
                        } else {
                            Icons.Filled.Check
                        },
                        contentDescription = null,
                        tint = if (model.kind == SuspendedToolKind.DeleteSchedule || model.kind == SuspendedToolKind.DeleteAgentVaultEntry) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(17.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = model.title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    model.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = "Suspended",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            model.reason?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            model.details.takeIf { it.isNotEmpty() }?.let { details ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    details.take(6).forEach { detail -> SuspendedToolDetailRow(detail) }
                }
            }
            model.choices.takeIf { it.isNotEmpty() }?.let { choices ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    choices.take(6).forEach { choice -> SuspendedToolChoiceRow(choice) }
                }
            }
            if (model.details.size > 6 || model.choices.size > 6) {
                Text(
                    text = "+${(model.details.size + model.choices.size) - 6} more",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuspendedToolDetailRow(detail: SuspendedToolDetail) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = detail.label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = detail.value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SuspendedToolChoiceRow(choice: SuspendedToolChoice) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = choice.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                choice.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolProgressIndicator(
    total: Int,
    doneCount: Int,
    errorCount: Int,
    isCalling: Boolean,
) {
    // Thin progress ring + check/error glyph, mirroring iOS ToolProgressRing (no big filled disc).
    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        if (isCalling) {
            CircularProgressIndicator(
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            val ringColor = if (errorCount > 0) MaterialTheme.colorScheme.error else Color(0xFF2E7D32)
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.5.dp, ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (errorCount > 0) Icons.Filled.PriorityHigh else Icons.Filled.Check,
                    contentDescription = null,
                    tint = ringColor,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun CountPill(icon: ImageVector, count: Int, color: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolStateDot(state: String) {
    Box(
        modifier = Modifier
            .padding(top = 5.dp)
            .size(8.dp)
            .background(color = toolStateColor(state), shape = CircleShape),
    )
}

@Composable
private fun SourcePart(
    part: AiSourceStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (part.sourceType == "document") "Document" else "Source",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = part.filename ?: part.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
            part.url?.takeIf { it.isNotBlank() }?.let {
                LinkifiedAiText(
                    text = it,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
            }
        }
    }
}

@Composable
private fun FilePart(
    part: AiFileStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = listOfNotNull(part.filename, part.mediaType).joinToString(" · ").ifBlank { "File · ${part.state}" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            part.url?.takeIf { it.isNotBlank() }?.let {
                LinkifiedAiText(
                    text = it,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
            }
        }
    }
}

@Composable
private fun ErrorPart(part: AiErrorStreamPart) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = part.errorText.ifBlank { "Stream error" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun toolStateColor(state: String): Color {
    return when (state) {
        "output-available", "approval-responded" -> Color(0xFF2E7D32)
        "output-error", "output-denied" -> MaterialTheme.colorScheme.error
        "input-available", "approval-requested" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
private fun LinkifiedAiText(
    text: String,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    val linkifiedText = remember(text) { LinkifyHelper.linkify(text) }
    CompositionLocalProvider(
        LocalContentColor provides ElementTheme.colors.textPrimary,
        LocalTextStyle provides ElementTheme.typography.fontBodyMdRegular,
    ) {
        EditorStyledText(
            text = linkifiedText,
            onLinkClickedListener = onLinkClick,
            onLinkLongClickedListener = onLinkLongClick,
            style = ElementRichTextEditorStyle.textStyle(),
            releaseOnDetach = false,
            modifier = modifier,
        )
    }
}

@Composable
private fun ThinkingSection(steps: List<AiThinkingStep>) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = (if (expanded) "▾ " else "▸ ") + "思考 (${steps.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
            )
            if (expanded) {
                steps.forEach { step ->
                    Column {
                        Text(text = step.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        if (step.description.isNotEmpty()) {
                            Text(
                                text = step.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolCallCard(toolCall: AiToolCall) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${toolCall.displayName} · ${toolCall.state}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            toolCall.output?.takeIf { it.isNotBlank() }?.let {
                if (!it.looksLikeRawJson()) {
                    Text(text = it, style = MaterialTheme.typography.bodySmall)
                }
            }
            toolCall.error?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun SourcesSection(sources: List<AiSource>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalDivider()
        Text(text = "Sources", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        sources.forEachIndexed { index, source ->
            Column {
                Text(text = "${index + 1}. ${source.title}", style = MaterialTheme.typography.bodyMedium)
                source.url?.let {
                    Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
