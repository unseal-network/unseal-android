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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PriorityHigh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
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
import io.element.android.features.messages.impl.timeline.components.event.toolcards.LocalToolCardEmbeddedInRoot
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
import kotlinx.coroutines.delay
import org.json.JSONObject

private val ToolCallContentMaxHeight = 320.dp

private data class ToolRootUiState(
    val selectedIndex: Int,
    val expanded: Boolean,
    val userSelectedTab: Boolean,
    val userToggledExpanded: Boolean,
)

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
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    workflowProgress: kotlinx.collections.immutable.ImmutableMap<String, WorkflowTaskProgress> = kotlinx.collections.immutable.persistentMapOf(),
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit = {},
) {
    val toolRootUiStates = remember { mutableStateMapOf<String, ToolRootUiState>() }
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
                rootUiStates = toolRootUiStates,
                workflowProgress = workflowProgress,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
                onLongClick = onLongClick,
            )
        } else if (content.shouldRenderBodyFallback()) {
            MarkdownBody(
                text = content.body,
                renderMode = if (content.isStreaming) MarkdownRenderMode.Streaming else MarkdownRenderMode.Stable,
                onLinkClick = onLinkClick,
                onLongClick = onLongClick,
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

internal fun String.looksLikeDuplicateToolCardPayload(): Boolean {
    val trimmed = trim()
    if (trimmed.isBlank()) return false
    val jsonPayload = when {
        trimmed.startsWith("{") -> trimmed
        trimmed.startsWith("```") -> trimmed
            .lineSequence()
            .drop(1)
            .dropLastFence()
            .joinToString("\n")
            .trim()
            .takeIf { it.startsWith("{") }
        else -> null
    } ?: return false
    return jsonPayload.contains("\"_cardType\"") ||
        jsonPayload.contains("\"cards\"") ||
        jsonPayload.contains("\"component\"") ||
        jsonPayload.contains("\"hotels\"") ||
        jsonPayload.contains("\"flights\"") ||
        jsonPayload.contains("\"events\"") ||
        jsonPayload.contains("\"products\"") ||
        jsonPayload.contains("\"articles\"")
}

private fun Sequence<String>.dropLastFence(): Sequence<String> = sequence {
    for (line in this@dropLastFence) {
        if (line.trim() == "```") break
        yield(line)
    }
}

@Composable
private fun AiStreamPartsView(
    visibleParts: List<AiStreamPart>,
    toolCallRoot: ToolCallRootRenderModel?,
    firstToolPartIndex: Int?,
    lastPartIsStreamingText: Boolean,
    isStreaming: Boolean,
    rootUiStates: MutableMap<String, ToolRootUiState>,
    workflowProgress: kotlinx.collections.immutable.ImmutableMap<String, WorkflowTaskProgress> = kotlinx.collections.immutable.persistentMapOf(),
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
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
            key("${part.id}#$index") {
                when (part) {
                    is AiToolStreamPart -> {
                        val rootModel = toolCallRoot
                        if (index == firstToolPartIndex && rootModel != null) {
                            ToolCallRootCard(
                                model = rootModel,
                                isStreaming = isStreaming,
                                rootUiStates = rootUiStates,
                                onLinkClick = onLinkClick,
                                onLinkLongClick = onLinkLongClick,
                            )
                        }
                        // Other tool parts are represented by the single root card above.
                    }
                    is AiTextStreamPart -> {
                        if (!toolCardInserted || !part.text.looksLikeDuplicateToolCardPayload()) {
                            TextPart(part, onLinkClick, onLinkLongClick, onLongClick)
                        }
                    }
                    is AiReasoningStreamPart -> ReasoningPart(part)
                    is AiSourceStreamPart -> SourcePart(part, onLinkClick, onLinkLongClick)
                    is AiFileStreamPart -> FilePart(part, onLinkClick, onLinkLongClick)
                    is AiErrorStreamPart -> ErrorPart(part)
                    is AiDataStreamPart -> DataPart(part, onLinkClick, onLinkLongClick, toolCardInserted, workflowProgress)
                    is AiCustomStreamPart -> Unit
                }
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

/** Shown while an assistant stream exists but has no renderable text or named tool yet. */
@Composable
private fun AiLoadingIndicator() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 6.dp),
    ) {
        StreamingCursor()
        InlineLoadingDots()
    }
}

/** Shown when a stream reaches a terminal status but produced no renderable content. */
@Composable
private fun AiUnavailableCard() {
    val rootShape = RoundedCornerShape(12.dp)
    Surface(
        shape = rootShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f), rootShape),
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
    onLongClick: (() -> Unit)?,
) {
    if (part.text.isNotBlank()) {
        MarkdownBody(
            text = part.text,
            renderMode = if (part.state == "streaming") MarkdownRenderMode.Streaming else MarkdownRenderMode.Stable,
            onLinkClick = onLinkClick,
            onLongClick = onLongClick,
        )
    }
}

@Composable
private fun ReasoningPart(part: AiReasoningStreamPart) {
    if (part.text.isBlank()) return
    val isStreaming = part.state == "streaming"
    var expanded by remember(part.id) { mutableStateOf(isStreaming) }
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.24f), shape),
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
    rootUiStates: MutableMap<String, ToolRootUiState>,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    val entries = model.entries
    if (entries.isEmpty()) return
    val rememberedState = rootUiStates[model.id]
    var selectedIndex by rememberSaveable(model.id) {
        mutableStateOf((rememberedState?.selectedIndex ?: model.selectedIndex).coerceIn(entries.indices))
    }
    var expanded by rememberSaveable(model.id) {
        mutableStateOf(rememberedState?.expanded ?: model.expandedByDefault)
    }
    var userSelectedTab by rememberSaveable(model.id) {
        mutableStateOf(rememberedState?.userSelectedTab ?: false)
    }
    var userToggledExpanded by rememberSaveable(model.id) {
        mutableStateOf(rememberedState?.userToggledExpanded ?: false)
    }
    fun persistRootState() {
        rootUiStates[model.id] = ToolRootUiState(
            selectedIndex = selectedIndex.coerceIn(entries.indices),
            expanded = expanded,
            userSelectedTab = userSelectedTab,
            userToggledExpanded = userToggledExpanded,
        )
    }
    val safeSelectedIndex = selectedIndex.coerceIn(entries.indices)
    val selectedEntry = if (safeSelectedIndex == model.selectedIndex) {
        model.selectedEntry ?: entries[safeSelectedIndex]
    } else {
        entries[safeSelectedIndex]
    }
    LaunchedEffect(model.id, model.selectedIndex, entries.size, model.allFinished, model.expandedByDefault) {
        if (!userSelectedTab) {
            selectedIndex = model.selectedIndex.coerceIn(entries.indices)
        } else if (selectedIndex !in entries.indices) {
            selectedIndex = entries.lastIndex
        }
        if (!userToggledExpanded) {
            expanded = model.expandedByDefault
        }
        persistRootState()
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

    val rootShape = RoundedCornerShape(20.dp)
    val isDarkTheme = isSystemInDarkTheme()
    val rootContainerColor = toolRootContainerColor()
    val rootBorderColor = toolRootBorderColor()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    val bringIntoViewTopPaddingPx = with(LocalDensity.current) { 220.dp.toPx() }
    LaunchedEffect(expanded) {
        if (expanded) {
            bringIntoViewRequester.bringIntoView()
            delay(320)
            bringIntoViewRequester.bringIntoView(
                Rect(
                    left = 0f,
                    top = -bringIntoViewTopPaddingPx,
                    right = rootSize.width.toFloat().coerceAtLeast(1f),
                    bottom = rootSize.height.toFloat().coerceAtLeast(1f),
                )
            )
        }
    }
    Surface(
        shape = rootShape,
        color = rootContainerColor,
        tonalElevation = if (isDarkTheme) 2.dp else 2.dp,
        shadowElevation = if (isDarkTheme) 14.dp else 10.dp,
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .onSizeChanged { rootSize = it }
            .zIndex(6f)
            .shadow(
                elevation = if (isDarkTheme) 14.dp else 10.dp,
                shape = rootShape,
                clip = false,
            )
            .border(
                width = 0.7.dp,
                color = rootBorderColor,
                shape = rootShape,
            ),
    ) {
        Column(
            modifier = Modifier.background(toolRootBodyBrush()),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(headerShape)
                    .clickable(
                        interactionSource = headerInteractionSource,
                        indication = ripple(),
                        onClick = {
                            userToggledExpanded = true
                            expanded = !expanded
                            persistRootState()
                        },
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (expanded) {
                                toolRootHeaderColor()
                            } else {
                                Color.Transparent
                            },
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
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
                            selectedIndex = safeSelectedIndex,
                            onSelected = {
                                userSelectedTab = true
                                selectedIndex = it.coerceIn(entries.indices)
                                persistRootState()
                            },
                        )
                            ToolEntryContentViewport(
                                entry = selectedEntry,
                                isStreaming = isStreaming,
                                allFinished = model.allFinished,
                                containerColor = rootContainerColor,
                                onLinkClick = onLinkClick,
                                onLinkLongClick = onLinkLongClick,
                            )
                    }
                } else {
                    ToolEntryContentViewport(
                        entry = selectedEntry,
                        isStreaming = isStreaming,
                        allFinished = model.allFinished,
                        containerColor = rootContainerColor,
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
    fun firstVisibleIndexFor(selection: Int): Int {
        return (selection - 1).coerceAtLeast(0).coerceIn(entries.indices)
    }

    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = firstVisibleIndexFor(selectedIndex)
    )
    LaunchedEffect(selectedIndex, entries.size) {
        val targetIndex = firstVisibleIndexFor(selectedIndex)
        if (listState.firstVisibleItemIndex != targetIndex) {
            listState.animateScrollToItem(targetIndex)
        }
    }
    val selectedPillColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.58f)
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(
                items = entries,
                key = { index, entry -> "${entry.id}#$index" },
            ) { index, entry ->
                val isSelected = index == selectedIndex
                SelectedStatePill(
                    selected = isSelected,
                    onClick = { onSelected(index) },
                    selectedColor = selectedPillColor,
                    unselectedColor = Color.Transparent,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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
}

@Composable
private fun toolRootContainerColor(): Color {
    return if (isSystemInDarkTheme()) {
        Color(0xFF111820)
    } else {
        Color.White
    }
}

@Composable
private fun toolRootHeaderColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    }
}

@Composable
private fun toolRootBorderColor(): Color {
    return if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    }
}

@Composable
private fun toolRootBodyBrush(): Brush {
    return if (isSystemInDarkTheme()) {
        Brush.verticalGradient(
            colors = listOf(
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        Color.Transparent,
        Color.Black.copy(alpha = 0.06f),
            ),
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
                Color.White,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f),
            ),
        )
    }
}

@Composable
private fun ToolEntryContentViewport(
    entry: AiToolCardEntry,
    isStreaming: Boolean,
    allFinished: Boolean,
    containerColor: Color,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    val scrollState = remember(entry.id) { androidx.compose.foundation.ScrollState(0) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ToolCallContentMaxHeight)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
    ) {
        CompositionLocalProvider(LocalToolCardEmbeddedInRoot provides true) {
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
            "calling" -> ToolCallingEntryContent(
                entry = entry,
                allFinished = allFinished,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
            "error" -> ToolErrorEntryContent(entry)
            else -> {
                val rendered = ToolEntryPayloadCard(
                    entry = entry,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                )
                if (!rendered) {
                    ToolEmptyState(isStreaming = isStreaming, cardType = entry.cardType)
                }
            }
        }
    }
}

@Composable
private fun ToolCallingEntryContent(
    entry: AiToolCardEntry,
    allFinished: Boolean,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    val rendered = ToolEntryPayloadCard(
        entry = entry,
        onLinkClick = onLinkClick,
        onLinkLongClick = onLinkLongClick,
    )
    if (!allFinished) {
        ToolCallingProgressRow(hasRenderedContent = rendered)
    } else if (!rendered) {
        ToolEmptyState(isStreaming = false, cardType = entry.cardType)
    }
}

@Composable
private fun ToolCallingProgressRow(hasRenderedContent: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (hasRenderedContent) 2.dp else 6.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreamingCursor()
        InlineLoadingDots()
        Text(
            text = if (hasRenderedContent) "Updating results" else "Waiting for results",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolErrorEntryContent(entry: AiToolCardEntry) {
    val propsHash = entry.props.hashCode()
    val props = remember(entry.id, propsHash) {
        runCatching { JSONObject(entry.props) }.getOrNull() ?: JSONObject()
    }
    val detail = remember(entry.id, propsHash) { props.toolErrorDetail(entry.props) }
    val message = detail.summary
    var expanded by rememberSaveable(entry.id, "tool-error-expanded") { mutableStateOf(true) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = if (expanded) 0.18f else 0.11f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.34f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = entry.name.ifBlank { "Tool call" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) 4 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (expanded) 180f else 0f),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Text(
                    text = detail.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolEntryPayloadCard(
    entry: AiToolCardEntry,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
): Boolean {
    val propsHash = entry.props.hashCode()
    val props = remember(entry.id, propsHash) {
        runCatching { JSONObject(entry.props) }.getOrNull() ?: JSONObject()
    }
    val toolCardUriHandler = remember(onLinkClick) {
        object : UriHandler {
            override fun openUri(uri: String) {
                onLinkClick(Link(uri))
            }
        }
    }
    var rendered = false
    CompositionLocalProvider(LocalUriHandler provides toolCardUriHandler) {
        rendered = ToolCardFinalProps(cardType = entry.cardType, data = props, onLinkClick = {})
    }
    return rendered
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
                    ToolEmptyState(isStreaming = !part.isDone, cardType = part.cardTypeForEmptyState())
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
            ToolEmptyState(isStreaming = !allFinished, cardType = part.cardTypeForEmptyState())
        }
    } else {
        ToolEmptyState(isStreaming = !allFinished, cardType = part.cardTypeForEmptyState())
    }
}

@Composable
private fun ToolEmptyState(isStreaming: Boolean, cardType: String? = null) {
    if (isStreaming) {
        AiLoadingIndicator()
    } else {
        Text(
            text = cardType.emptyToolResultLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun AiToolStreamPart.cardTypeForEmptyState(): String? {
    return runCatching {
        val props = output?.takeIf { it.isNotBlank() } ?: input?.takeIf { it.isNotBlank() } ?: rawInput
        props?.let { JSONObject(it).optString("_cardType").takeIf(String::isNotBlank) }
    }.getOrNull()
}

private fun String?.emptyToolResultLabel(): String {
    return when (this) {
        "socialPostFeed" -> "No posts returned"
        "productList" -> "No products returned"
        "eventList" -> "No events returned"
        "imageGrid" -> "No images returned"
        "hotelBooking" -> "No hotels returned"
        "flightAlert" -> "No flights returned"
        "headlineList", "urlContent" -> "No results returned"
        "fileAttachment" -> "No files returned"
        "githubIssuesList", "linearIssuesList" -> "No issues returned"
        "repoList" -> "No repositories returned"
        "notifications" -> "No notifications returned"
        else -> "No visual result returned"
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

private data class ToolErrorDetail(
    val summary: String,
    val body: String,
)

private fun JSONObject.toolErrorDetail(rawProps: String): ToolErrorDetail {
    val summary = firstErrorString(
        "errorText",
        "message",
        "error",
        "reason",
        "detail",
        "details",
        "cause",
        "description",
    )?.takeIf { !it.looksLikeRawJsonError() }
        ?: nestedErrorString()
        ?: "Failed to get results"
    val body = buildList {
        add("Status: failed")
        firstErrorString("toolName", "name")?.let { add("Tool: $it") }
        firstErrorString("errorText", "message", "error", "reason", "detail", "details", "cause", "description")
            ?.takeIf { it.isNotBlank() }
            ?.let { add("Reason: ${it.compactToolErrorText()}") }
        nestedErrorString()?.takeIf { it != summary }?.let { add("Nested: ${it.compactToolErrorText()}") }
        rawProps.takeIf { it.isNotBlank() }?.let { add("Raw: ${it.compactToolErrorText()}") }
    }.joinToString("\n")
    return ToolErrorDetail(summary = summary.compactToolErrorText(), body = body)
}

private fun JSONObject.firstErrorString(vararg keys: String): String? {
    for (key in keys) {
        val value = opt(key)
        when (value) {
            is String -> value.takeIf { it.isNotBlank() }?.let { return it }
            is JSONObject -> value.nestedErrorString()?.let { return it }
        }
    }
    return null
}

private fun JSONObject.nestedErrorString(): String? {
    firstErrorString("message", "error", "reason", "detail", "details", "cause", "description")?.let { return it }
    val keys = keys()
    while (keys.hasNext()) {
        val value = opt(keys.next())
        if (value is JSONObject) {
            value.nestedErrorString()?.let { return it }
        }
    }
    return null
}

private fun String.compactToolErrorText(): String {
    val normalized = trim().replace(Regex("\\s+"), " ")
    return if (normalized.length > 700) normalized.take(700) + "..." else normalized
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
    workflowProgress: kotlinx.collections.immutable.ImmutableMap<String, WorkflowTaskProgress> = kotlinx.collections.immutable.persistentMapOf(),
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
        "data-json-block" -> {
            val json = part.payload.jsonObjectOrNull()
            val taskId = json?.optString("task_id")?.takeIf { it.isNotBlank() }
            val progress = taskId?.let { workflowProgress[it] }
            when (json?.optString("content_type")) {
                "ppt_planning" -> PptPlanningCard(part.payload, progress)
                "writing_generation_workflow_activity",
                "ppt_generation_workflow_activity" -> WorkflowActivityCard(part.payload, progress)
                "writing_planning" -> WritingPlanningCard(part.payload)
                "ppt_outline_v2", "ppt_outline" -> OutlineCard(label = "PPT Outline", payload = part.payload)
                "writing_outline" -> OutlineCard(label = "Writing Outline", payload = part.payload)
                "deep_research", "research" -> ResearchCard(part.payload)
                "professor_review" -> ReviewCard(part.payload)
                else -> {
                    val msg = json?.optString("message")?.takeIf { it.isNotBlank() }
                    if (msg != null) {
                        MarkdownBody(
                            text = msg,
                            renderMode = MarkdownRenderMode.Stable,
                            onLinkClick = {},
                        )
                    }
                }
            }
        }
        else -> Unit
    }
}

@Composable
private fun PptPlanningCard(payload: String, progress: WorkflowTaskProgress? = null) {
    val json = payload.jsonObjectOrNull()
    val topic = json?.optString("topic")?.takeIf { it.isNotBlank() }
    val slides = json?.optInt("number_of_slides", 0)?.takeIf { it > 0 }
    val tone = json?.optString("tone")?.takeIf { it.isNotBlank() }
    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "PPT Planning",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            topic?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            if (slides != null || tone != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    slides?.let {
                        Text(
                            text = "$it slides",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    tone?.let {
                        Text(
                            text = it.replaceFirstChar { c -> c.uppercaseChar() },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WorkflowProgressOverlay(progress)
        }
    }
}

@Composable
private fun WorkflowActivityCard(payload: String, progress: WorkflowTaskProgress? = null) {
    val json = payload.jsonObjectOrNull()
    val contentType = json?.optString("content_type")?.takeIf { it.isNotBlank() }
    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
    val title = when (contentType) {
        "ppt_generation_workflow_activity" -> "Creating presentation"
        else -> "Writing in progress"
    }
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WorkflowProgressOverlay(progress)
        }
    }
}

/**
 * Renders live WebSocket workflow progress below the card's static content.
 * Shown only when [progress] is non-null and has something to display.
 */
@Composable
private fun WorkflowProgressOverlay(progress: WorkflowTaskProgress?) {
    if (progress == null) return
    val progressText = progress.progressText ?: return
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        if (progress.isTracking) {
            androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = progressText,
            style = MaterialTheme.typography.labelSmall,
            color = if (progress.isComplete) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    progress.activities.lastOrNull()?.let { latest ->
        Text(
            text = latest,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WritingPlanningCard(payload: String) {
    val json = payload.jsonObjectOrNull()
    val topic = json?.optString("topic")?.takeIf { it.isNotBlank() }
    val sections = json?.optInt("total_sections", 0)?.takeIf { it > 0 }
    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
    JsonBlockCard(label = "Writing Plan") {
        topic?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        sections?.let {
            Text(
                text = "$it sections",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OutlineCard(label: String, payload: String) {
    val json = payload.jsonObjectOrNull()
    val topic = json?.optString("topic")?.takeIf { it.isNotBlank() }
    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
    val slidesOrSections = (json?.optInt("number_of_slides", 0)
        ?: json?.optInt("total_sections", 0))?.takeIf { it > 0 }
    JsonBlockCard(label = label) {
        topic?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        slidesOrSections?.let {
            Text(
                text = "$it items",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResearchCard(payload: String) {
    val json = payload.jsonObjectOrNull()
    val topic = json?.optString("topic")?.takeIf { it.isNotBlank() }
        ?: json?.optString("query")?.takeIf { it.isNotBlank() }
    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
    JsonBlockCard(label = "Research") {
        topic?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReviewCard(payload: String) {
    val json = payload.jsonObjectOrNull()
    val message = json?.optString("message")?.takeIf { it.isNotBlank() }
    val status = json?.optString("status")?.takeIf { it.isNotBlank() }
    JsonBlockCard(label = "Review") {
        status?.let {
            Text(
                text = it.replaceFirstChar { c -> c.uppercaseChar() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        message?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun JsonBlockCard(
    label: String,
    content: @Composable () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun ErrorCard(payload: String) {
    val json = payload.jsonObjectOrNull()
    val title = json?.optString("title")?.takeIf { it.isNotBlank() } ?: "Something went wrong"
    val message = json?.optString("message")?.takeIf { it.isNotBlank() } ?: payload.errorTextFromJson().orEmpty()
    ErrorBanner(title = title, message = message)
}

@Composable
private fun ErrorBanner(title: String? = null, message: String) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.24f), shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
                title?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Text(
                    text = message.ifBlank { "Stream error" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SuspendedToolCard(payload: String) {
    val model = remember(payload) { payload.toSuspendedToolRenderModel() }
    val shape = RoundedCornerShape(16.dp)
    val accentColor = if (model.kind == SuspendedToolKind.DeleteSchedule || model.kind == SuspendedToolKind.DeleteAgentVaultEntry) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val cardColor = if (isSystemInDarkTheme()) {
        Color(0xFF15191F)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Surface(
        shape = shape,
        color = cardColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = if (isSystemInDarkTheme()) 2.dp else 1.dp,
        shadowElevation = if (isSystemInDarkTheme()) 8.dp else 6.dp,
        border = BorderStroke(0.6.dp, accentColor.copy(alpha = if (isSystemInDarkTheme()) 0.22f else 0.18f)),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accentColor.copy(alpha = if (isSystemInDarkTheme()) 0.12f else 0.06f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = accentColor.copy(alpha = 0.12f),
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
            model.fields.takeIf { it.isNotEmpty() }?.let { fields ->
                SuspendedToolFieldEditor(
                    fields = fields,
                    submitLabel = model.submitLabel ?: "Continue",
                )
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
private fun SuspendedToolFieldEditor(
    fields: List<SuspendedToolField>,
    submitLabel: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        fields.take(4).forEach { field ->
            SuspendedToolDisplayField(label = field.label, value = field.value)
        }
        Button(
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(submitLabel)
        }
    }
}

@Composable
private fun SuspendedToolDisplayField(
    label: String,
    value: String,
) {
    val shape = RoundedCornerShape(10.dp)
    val fieldBorderColor = if (isSystemInDarkTheme()) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    }
    val fieldContainerColor = if (isSystemInDarkTheme()) {
        Color.White.copy(alpha = 0.035f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(fieldContainerColor, shape)
                .border(0.6.dp, fieldBorderColor, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                text = value.ifBlank { "-" },
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    val doneFraction = if (total > 0) doneCount.toFloat() / total.toFloat() else 0f
    val errorFraction = if (total > 0) errorCount.toFloat() / total.toFloat() else 0f
    val allFinished = total > 0 && doneCount + errorCount >= total && !isCalling
    val hasPartialError = doneCount > 0 && errorCount > 0
    val hasOnlyErrors = doneCount == 0 && errorCount > 0
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.16f)
    val doneColor = Color(0xFF2FDB72)
    val errorColor = Color(0xFFFF4D4F)
    val partialColor = Color(0xFFFFC247)
    Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = trackColor, style = stroke)
            if (doneFraction > 0f) {
                drawArc(
                    color = doneColor,
                    startAngle = -90f,
                    sweepAngle = doneFraction * 360f,
                    useCenter = false,
                    style = stroke,
                )
            }
            if (errorFraction > 0f) {
                drawArc(
                    color = errorColor,
                    startAngle = -90f + doneFraction * 360f,
                    sweepAngle = errorFraction * 360f,
                    useCenter = false,
                    style = stroke,
                )
            }
        }
        if (allFinished) {
            Icon(
                imageVector = if (hasOnlyErrors || hasPartialError) Icons.Filled.PriorityHigh else Icons.Filled.Check,
                contentDescription = null,
                tint = when {
                    hasOnlyErrors -> errorColor
                    hasPartialError -> partialColor
                    else -> doneColor
                },
                modifier = Modifier.size(10.dp),
            )
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
    val doneColor = Color(0xFF2FDB72)
    val errorColor = Color(0xFFFF4D4F)
    when (state) {
        "done", "output-available", "approval-responded" -> {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(doneColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
        "error", "output-error", "output-denied" -> {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(errorColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
        "calling", "input-available", "approval-requested" -> {
            CircularProgressIndicator(
                modifier = Modifier.size(13.dp),
                strokeWidth = 1.5.dp,
                color = doneColor,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f),
            )
        }
        else -> {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = toolStateColor(state), shape = CircleShape),
            )
        }
    }
}

@Composable
private fun SourcePart(
    part: AiSourceStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    InlineInfoRow(
        label = if (part.sourceType == "document") "Document" else "Source",
        title = part.filename ?: part.title,
        accent = MaterialTheme.colorScheme.primary,
    ) {
        part.url?.takeIf { it.isNotBlank() }?.let {
            LinkifiedAiText(
                text = it,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
        }
    }
}

@Composable
private fun FilePart(
    part: AiFileStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
) {
    InlineInfoRow(
        label = "File",
        title = listOfNotNull(part.filename, part.mediaType).joinToString(" · ").ifBlank { "File · ${part.state}" },
        accent = MaterialTheme.colorScheme.secondary,
    ) {
        part.url?.takeIf { it.isNotBlank() }?.let {
            LinkifiedAiText(
                text = it,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
        }
    }
}

@Composable
private fun ErrorPart(part: AiErrorStreamPart) {
    ErrorBanner(message = part.errorText.ifBlank { "Stream error" })
}

@Composable
private fun InlineInfoRow(
    label: String,
    title: String,
    accent: Color,
    supportingContent: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f), shape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.take(1),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                supportingContent()
            }
        }
    }
}

@Composable
private fun InlineLoadingDots() {
    val transition = rememberInfiniteTransition(label = "inline-loading")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = index * 120),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "inline-loading-dot-$index",
            )
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape),
            )
        }
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
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.58f),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.52f), shape),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Sources (${sources.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (expanded) 0f else -90f),
                )
            }
            if (expanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sources.forEachIndexed { index, source ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = "${index + 1}. ${source.title}", style = MaterialTheme.typography.bodyMedium)
                            source.url?.let {
                                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
