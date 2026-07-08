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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.IconButton
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
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
import io.element.android.features.messages.impl.timeline.model.event.AiPptWorkflowStreamPart
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
private const val SLIDE_LIST_MAX_HEIGHT_DP = 480

// HTML slides are authored at 1280×720 CSS px (standard 16:9 presentation canvas).
private const val SLIDE_DESIGN_WIDTH_PX = 1280

// First N slides load immediately; the rest are staggered to avoid a renderer spike.
private const val EAGER_LOAD_SLIDES = 2
private const val SLIDE_STAGGER_MS = 250L

/**
 * Injects a <style> block that:
 *  1. Locks the document to the design canvas size (1280×720 CSS px).
 *  2. Applies a CSS scale transform so the canvas fills [cardWidthDp] CSS px exactly.
 *
 * Scale is computed on the Kotlin side from the card's measured width, so the WebView
 * never needs to measure itself (avoids window.innerWidth=0 in onPageFinished).
 */
/**
 * Ensures a viewport meta for [SLIDE_DESIGN_WIDTH_PX] is the first thing in <head>.
 * Combined with useWideViewPort=true + setInitialScale(percent), this tells the WebView:
 *   1. Layout at 1280px (slides are designed for this canvas).
 *   2. Zoom to the computed percent so the canvas fits the card.
 */
private fun injectViewportMeta(html: String): String {
    val meta = """<meta name="viewport" content="width=$SLIDE_DESIGN_WIDTH_PX">"""
    // Strip any existing viewport meta first to avoid conflicts.
    val stripped = html.replace(
        Regex("""<meta[^>]+name=["']viewport["'][^>]*/?>""", RegexOption.IGNORE_CASE), ""
    )
    return if (stripped.contains("<head>", ignoreCase = true)) {
        stripped.replaceFirst("<head>", "<head>$meta", ignoreCase = true)
    } else {
        "<head>$meta</head>$stripped"
    }
}

internal enum class PptFullscreenMode { CAROUSEL, MINIAPP }
internal val PPT_FULLSCREEN_MODE = PptFullscreenMode.MINIAPP

/** Allows [PptGenerationWorkflowCard] and [PptGeneratingCard] (deep in the tool card chain) to read workflow progress. */
internal val LocalWorkflowMessages = androidx.compose.runtime.compositionLocalOf<Map<String, WorkflowMessage>> { emptyMap() }
internal val LocalWorkflowSlides = androidx.compose.runtime.compositionLocalOf<Map<String, List<String>>> { emptyMap() }

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
    workflowMessages: Map<String, WorkflowMessage> = emptyMap(),
    workflowSlides: Map<String, List<String>> = emptyMap(),
    miniAppDocumentLauncher: MiniAppDocumentLauncher? = null,
) {
    val toolRootUiStates = remember { mutableStateMapOf<String, ToolRootUiState>() }
    Column(
        modifier = modifier.fillMaxWidth(),
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
                workflowMessages = workflowMessages,
                workflowSlides = workflowSlides,
                miniAppDocumentLauncher = miniAppDocumentLauncher,
                streamId = content.streamId,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
                onLongClick = onLongClick,
            )
        } else if (content.shouldRenderBodyFallback()) {
            val visibleBody = rememberStreamingRevealText(
                key = content.streamId ?: content.eventId ?: "body",
                targetText = content.body,
                isStreaming = content.isStreaming,
            )
            val isRevealingBody = visibleBody.length < content.body.length
            MarkdownBody(
                text = visibleBody,
                renderMode = if (content.isStreaming || isRevealingBody) MarkdownRenderMode.Streaming else MarkdownRenderMode.Stable,
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
    workflowMessages: Map<String, WorkflowMessage>,
    workflowSlides: Map<String, List<String>> = emptyMap(),
    miniAppDocumentLauncher: MiniAppDocumentLauncher? = null,
    streamId: String? = null,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
) {
    // Mirror iOS BubbleMessageView: walk the ordered parts; insert ONE ToolCallRootCard at the
    // first tool part's position; render every other part inline in order; trailing streaming
    // cursor unless the last part is already a streaming text (which carries its own cursor).
    val toolCardInserted = toolCallRoot != null
    androidx.compose.runtime.CompositionLocalProvider(
        LocalWorkflowMessages provides workflowMessages,
        LocalWorkflowSlides provides workflowSlides,
        LocalDocumentLauncher provides miniAppDocumentLauncher,
    ) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // First pass: render all non-PPT parts in stream order (text, tool cards, etc.).
        // AiPptWorkflowStreamPart is deferred to always appear below the text response,
        // matching the web layout where text is shown above the slides card.
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
                    is AiDataStreamPart -> DataPart(part, onLinkClick, onLinkLongClick, toolCardInserted, workflowMessages, streamId)
                    is AiPptWorkflowStreamPart -> Unit // rendered in second pass below
                    is AiCustomStreamPart -> Unit
                }
            }
        }
        // Second pass: PPT workflow cards always rendered after all text/tool content.
        visibleParts.forEachIndexed { index, part ->
            if (part is AiPptWorkflowStreamPart) {
                key("ppt-${part.id}#$index") {
                    PptActivityWorkflowCard(part, streamId)
                }
            }
        }
        if (isStreaming && !lastPartIsStreamingText) {
            StreamingCursor()
        }
    }
    } // end CompositionLocalProvider(LocalWorkflowMessages)
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
                    text = stringResource(R.string.screen_room_timeline_ai_unavailable_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.screen_room_timeline_ai_unavailable_description),
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
    val isStreaming = part.state == "streaming"
    if (part.text.isBlank()) {
        if (isStreaming) {
            StreamingCursor()
        }
        return
    }
    val visibleText = rememberStreamingRevealText(
        key = part.id,
        targetText = part.text,
        isStreaming = isStreaming,
    )
    val isRevealing = visibleText.length < part.text.length
    MarkdownBody(
        text = visibleText,
        renderMode = if (isStreaming || isRevealing) MarkdownRenderMode.Streaming else MarkdownRenderMode.Stable,
        onLinkClick = onLinkClick,
        onLongClick = onLongClick,
    )
    if (isStreaming) {
        StreamingCursor()
    }
}

@Composable
private fun rememberStreamingRevealText(
    key: String,
    targetText: String,
    isStreaming: Boolean,
): String {
    var hasAnimatedThisSession by remember(key) { mutableStateOf(isStreaming) }
    var visibleText by remember(key) {
        mutableStateOf(if (isStreaming) "" else targetText)
    }

    LaunchedEffect(key, targetText, isStreaming) {
        if (targetText.isEmpty()) {
            visibleText = ""
            return@LaunchedEffect
        }

        val shouldAnimate = isStreaming || hasAnimatedThisSession
        if (!shouldAnimate) {
            visibleText = targetText
            return@LaunchedEffect
        }
        hasAnimatedThisSession = true

        if (!targetText.startsWith(visibleText)) {
            visibleText = targetText
            return@LaunchedEffect
        }

        val frameDelayMs = streamingRevealFrameDelayMs(targetText.length)
        while (visibleText.length < targetText.length) {
            val nextIndex = nextStreamingRevealEndIndex(
                currentEndIndex = visibleText.length,
                text = targetText,
            )
            if (nextIndex <= visibleText.length) {
                visibleText = targetText
                return@LaunchedEffect
            }
            if (nextIndex >= targetText.length) {
                visibleText = targetText
                return@LaunchedEffect
            } else {
                visibleText = targetText.substring(0, nextIndex)
            }
            delay(frameDelayMs)
        }
    }

    return visibleText
}

internal fun nextStreamingRevealEndIndex(
    currentEndIndex: Int,
    text: String,
): Int {
    if (currentEndIndex >= text.length) return text.length
    val safeCurrentEndIndex = currentEndIndex.coerceIn(0, text.length)
    val step = streamingRevealCodePointStep(
        remainingTextUnits = text.length - safeCurrentEndIndex,
        totalTextUnits = text.length,
    )
    var nextIndex = safeCurrentEndIndex
    repeat(step) {
        if (nextIndex >= text.length) return text.length
        nextIndex += Character.charCount(text.codePointAt(nextIndex))
    }
    return nextIndex.coerceAtMost(text.length)
}

internal fun streamingRevealCodePointStep(
    remainingTextUnits: Int,
    totalTextUnits: Int,
): Int {
    val normalizedRemaining = remainingTextUnits.coerceAtLeast(0)
    val catchUpStep = when {
        normalizedRemaining <= 24 -> 1
        normalizedRemaining <= 96 -> 2
        normalizedRemaining <= 240 -> 4
        normalizedRemaining <= 600 -> 8
        normalizedRemaining <= 1_200 -> 16
        else -> 32
    }
    val maxStep = when {
        totalTextUnits <= 280 -> 8
        totalTextUnits <= 800 -> 16
        totalTextUnits <= 1_600 -> 24
        else -> 32
    }
    return catchUpStep.coerceAtMost(maxStep).coerceAtLeast(1)
}

internal fun streamingRevealFrameDelayMs(totalTextUnits: Int): Long {
    return when {
        totalTextUnits <= 280 -> 28L
        totalTextUnits <= 1_200 -> 40L
        else -> 56L
    }
}

@Composable
private fun ReasoningPart(part: AiReasoningStreamPart) {
    if (part.text.isBlank()) return
    val isStreaming = part.state == "streaming"
    val visibleText = rememberStreamingRevealText(
        key = part.id,
        targetText = part.text,
        isStreaming = isStreaming,
    )
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
                    text = (if (expanded) "▾ " else "▸ ") + if (isStreaming) {
                        stringResource(R.string.screen_room_timeline_ai_reasoning_in_progress)
                    } else {
                        stringResource(R.string.screen_room_timeline_ai_reasoning)
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                )
            }
            if (expanded) {
                Text(
                    text = visibleText,
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
    val rootTitle = model.title
        .takeUnless { it.equals("Tool Calls", ignoreCase = true) }
        ?.localizedToolDisplayName()
        ?: stringResource(R.string.screen_room_timeline_tool_card_tool_calls)
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
                        text = rootTitle,
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
                            text = entry.name.localizedToolDisplayName(),
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
private fun String.localizedToolDisplayName(): String = when (this) {
    "Flights" -> stringResource(R.string.screen_room_timeline_tool_card_flights)
    "Hotels" -> stringResource(R.string.screen_room_timeline_tool_card_hotels)
    "News" -> stringResource(R.string.screen_room_timeline_tool_card_news)
    "Web Search" -> stringResource(R.string.screen_room_timeline_tool_card_web_search)
    "Search" -> stringResource(R.string.screen_room_timeline_tool_card_search)
    "Scholar" -> stringResource(R.string.screen_room_timeline_tool_card_scholar)
    "Images" -> stringResource(R.string.screen_room_timeline_tool_card_images)
    "Shopping" -> stringResource(R.string.screen_room_timeline_tool_card_shopping)
    "Finance" -> stringResource(R.string.screen_room_timeline_tool_card_finance)
    "Weather" -> stringResource(R.string.screen_room_timeline_tool_card_weather)
    "Events" -> stringResource(R.string.screen_room_timeline_tool_card_events)
    "Places" -> stringResource(R.string.screen_room_timeline_tool_card_places)
    "Web Content" -> stringResource(R.string.screen_room_timeline_tool_card_web_content)
    "Issues" -> stringResource(R.string.screen_room_timeline_tool_card_issues)
    "Issues & PRs" -> stringResource(R.string.screen_room_timeline_tool_card_issues_and_prs)
    "Pull Requests" -> stringResource(R.string.screen_room_timeline_tool_card_pull_requests)
    "Issue" -> stringResource(R.string.screen_room_timeline_tool_card_issue)
    "Check Runs" -> stringResource(R.string.screen_room_timeline_tool_card_check_runs)
    "Commits" -> stringResource(R.string.screen_room_timeline_tool_card_commits)
    "Contributors" -> stringResource(R.string.screen_room_timeline_tool_card_contributors)
    "Deployments" -> stringResource(R.string.screen_room_timeline_tool_card_deployments)
    "Notifications" -> stringResource(R.string.screen_room_timeline_tool_card_notifications)
    "Organizations" -> stringResource(R.string.screen_room_timeline_tool_card_organizations)
    "Release" -> stringResource(R.string.screen_room_timeline_tool_card_release)
    "Repositories" -> stringResource(R.string.screen_room_timeline_tool_card_repositories)
    "Starred" -> stringResource(R.string.screen_room_timeline_tool_card_starred)
    "Secret Alerts" -> stringResource(R.string.screen_room_timeline_tool_card_secret_alerts)
    "Workflows" -> stringResource(R.string.screen_room_timeline_tool_card_workflows)
    "Comments" -> stringResource(R.string.screen_room_timeline_tool_card_comments)
    "PR Comments" -> stringResource(R.string.screen_room_timeline_tool_card_pr_comments)
    "Email" -> stringResource(R.string.screen_room_timeline_tool_card_email)
    "Emails" -> stringResource(R.string.screen_room_timeline_tool_card_emails)
    "Draft" -> stringResource(R.string.screen_room_timeline_tool_card_draft)
    "Files" -> stringResource(R.string.screen_room_timeline_tool_card_files)
    "File" -> stringResource(R.string.screen_room_timeline_tool_card_file)
    "Timeline" -> stringResource(R.string.screen_room_timeline_tool_card_timeline)
    "Posts" -> stringResource(R.string.screen_room_timeline_tool_card_posts)
    "Post" -> stringResource(R.string.screen_room_timeline_tool_card_post)
    "Create Schedule" -> stringResource(R.string.screen_room_timeline_tool_card_create_schedule)
    "Update Schedule" -> stringResource(R.string.screen_room_timeline_tool_card_update_schedule)
    "Schedule Status" -> stringResource(R.string.screen_room_timeline_tool_card_schedule_status)
    "Generate Presentation" -> stringResource(R.string.screen_room_timeline_tool_card_generate_presentation)
    else -> this
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
            text = if (hasRenderedContent) {
                stringResource(R.string.screen_room_timeline_ai_updating_results)
            } else {
                stringResource(R.string.screen_room_timeline_ai_waiting_for_results)
            },
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
    val failedResults = stringResource(R.string.screen_room_timeline_ai_failed_to_get_results)
    val statusFailed = stringResource(R.string.screen_room_timeline_ai_tool_error_status_failed)
    val toolLabel = stringResource(R.string.screen_room_timeline_ai_tool_error_tool)
    val reasonLabel = stringResource(R.string.screen_room_timeline_ai_tool_error_reason)
    val nestedLabel = stringResource(R.string.screen_room_timeline_ai_tool_error_nested)
    val rawLabel = stringResource(R.string.screen_room_timeline_ai_tool_error_raw)
    val detail = remember(entry.id, propsHash, failedResults, statusFailed, toolLabel, reasonLabel, nestedLabel, rawLabel) {
        props.toolErrorDetail(
            rawProps = entry.props,
            failedResults = failedResults,
            statusFailed = statusFailed,
            toolLabel = toolLabel,
            reasonLabel = reasonLabel,
            nestedLabel = nestedLabel,
            rawLabel = rawLabel,
        )
    }
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
                        text = entry.name.ifBlank { stringResource(R.string.screen_room_timeline_ai_tool_call) },
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

@Composable
private fun String?.emptyToolResultLabel(): String {
    return when (this) {
        "socialPostFeed" -> stringResource(R.string.screen_room_timeline_tool_card_no_posts_returned)
        "productList" -> stringResource(R.string.screen_room_timeline_tool_card_no_products_returned)
        "eventList" -> stringResource(R.string.screen_room_timeline_tool_card_no_events_returned)
        "imageGrid" -> stringResource(R.string.screen_room_timeline_tool_card_no_images_returned)
        "hotelBooking" -> stringResource(R.string.screen_room_timeline_tool_card_no_hotels_returned)
        "flightAlert" -> stringResource(R.string.screen_room_timeline_tool_card_no_flights_returned)
        "headlineList", "urlContent" -> stringResource(R.string.screen_room_timeline_tool_card_no_results_returned)
        "fileAttachment" -> stringResource(R.string.screen_room_timeline_tool_card_no_files_returned)
        "githubIssuesList", "linearIssuesList" -> stringResource(R.string.screen_room_timeline_tool_card_no_issues_returned)
        "repoList" -> stringResource(R.string.screen_room_timeline_tool_card_no_repositories_returned)
        "notifications" -> stringResource(R.string.screen_room_timeline_tool_card_no_notifications_returned)
        else -> stringResource(R.string.screen_room_timeline_tool_card_no_visual_result_returned)
    }
}

@Composable
private fun ToolErrorContent(part: AiToolStreamPart) {
    Text(
        text = part.errorText
            ?.takeIf { it.isNotBlank() && !it.equals("Tool call failed.", ignoreCase = true) && !it.equals("Tool call failed", ignoreCase = true) }
            ?: stringResource(R.string.screen_room_timeline_tool_card_tool_call_failed),
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

private fun JSONObject.toolErrorDetail(
    rawProps: String,
    failedResults: String,
    statusFailed: String,
    toolLabel: String,
    reasonLabel: String,
    nestedLabel: String,
    rawLabel: String,
): ToolErrorDetail {
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
        ?: failedResults
    val body = buildList {
        add(statusFailed)
        firstErrorString("toolName", "name")?.let { add("$toolLabel: $it") }
        firstErrorString("errorText", "message", "error", "reason", "detail", "details", "cause", "description")
            ?.takeIf { it.isNotBlank() }
            ?.let { add("$reasonLabel: ${it.compactToolErrorText()}") }
        nestedErrorString()?.takeIf { it != summary }?.let { add("$nestedLabel: ${it.compactToolErrorText()}") }
        rawProps.takeIf { it.isNotBlank() }?.let { add("$rawLabel: ${it.compactToolErrorText()}") }
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
                    text = stringResource(R.string.screen_room_timeline_tool_card_more_count, model.moreCount),
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

/** 已收到 slide HTML 时的横向滑动查看器（WebView 渲染每张幻灯片）。 */
@Composable
private fun PptSlidesView(slides: List<String>, totalSlides: Int, isGenerating: Boolean = false, streamId: String? = null, taskId: String? = null) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280)

    var currentIndex by rememberSaveable { mutableStateOf(0) }
    val safeIndex = if (slides.isEmpty()) 0 else currentIndex.coerceIn(0, slides.size - 1)

    var showFullscreen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = if (isGenerating) {
                        stringResource(R.string.screen_room_timeline_ppt_generating)
                    } else {
                        stringResource(R.string.screen_room_timeline_ppt_generated)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (isGenerating) BouncingDots(color = textSecondary)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${slides.size}/$totalSlides",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary,
                )
                if (slides.isNotEmpty() && !isGenerating) {
                    IconButton(
                        onClick = { showFullscreen = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "全屏查看",
                            tint = textSecondary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        if (slides.isNotEmpty()) {
            SlideHtmlCard(index = safeIndex, html = slides[safeIndex], forceLoad = true)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { currentIndex = safeIndex - 1 },
                    enabled = safeIndex > 0,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "上一页",
                        tint = if (safeIndex > 0) MaterialTheme.colorScheme.primary else textSecondary,
                    )
                }
                Text(
                    text = "${safeIndex + 1} / ${slides.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary,
                )
                IconButton(
                    onClick = { currentIndex = safeIndex + 1 },
                    enabled = safeIndex < slides.size - 1,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "下一页",
                        tint = if (safeIndex < slides.size - 1) MaterialTheme.colorScheme.primary else textSecondary,
                    )
                }
            }
        }
    }

    // Fullscreen overlay — triggered by the expand button above.
    if (showFullscreen && slides.isNotEmpty()) {
        when (PPT_FULLSCREEN_MODE) {
            PptFullscreenMode.CAROUSEL -> PptCarouselFullscreen(
                slides = slides,
                initialIndex = safeIndex,
                onDismiss = { showFullscreen = false },
            )
            PptFullscreenMode.MINIAPP -> {
                val launcher = LocalDocumentLauncher.current
                if (launcher != null) {
                    DocumentViewerOverlay(
                        appId = MiniAppIds.PPT,
                        options = buildMap {
                            put("htmls", slides)
                            put("initialIndex", safeIndex)
                            // Prefer the AI stream ID; fall back to the PPT task ID so
                            // createSuccess can always persist doc_id with a stable key.
                            val sid = streamId?.takeIf { it.isNotBlank() }
                                ?: taskId?.takeIf { it.isNotBlank() }
                            sid?.let { put("stream_id", it) }
                        },
                        launcher = launcher,
                        onDismiss = { showFullscreen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun PptCarouselFullscreen(
    slides: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .systemBarsPadding(),
        ) {
            BackHandler(onBack = onDismiss)

            val pagerState = rememberPagerState(initialPage = initialIndex) { slides.size }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                pageSpacing = 24.dp,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) { page ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    SlideHtmlCard(
                        index = page,
                        html = slides[page],
                        forceLoad = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                    )
                }
            }

            // Top bar: close + page counter
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "${pagerState.currentPage + 1} / ${slides.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                // Spacer to balance the close button on the left
                Box(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun SlideHtmlCard(index: Int, html: String, forceLoad: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
    var shouldLoad by remember(index) { mutableStateOf(forceLoad || index < EAGER_LOAD_SLIDES) }
    LaunchedEffect(index, forceLoad) {
        if (!shouldLoad) {
            kotlinx.coroutines.delay(index * SLIDE_STAGGER_MS)
            shouldLoad = true
        }
    }

    // BoxWithConstraints gives us the real card width in dp == CSS px at default density.
    // setInitialScale(percent) tells WebView to zoom the already-correct 1280px layout
    // down to the card width — native zoom, no CSS transform on html element.
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White),
    ) {
        val cardWidthDp = maxWidth.value
        // scale percent: e.g. card=300dp → 300/1280*100 ≈ 23
        val scalePercent = remember(cardWidthDp) {
            if (cardWidthDp > 0) ((cardWidthDp / SLIDE_DESIGN_WIDTH_PX) * 100).toInt().coerceAtLeast(1) else 0
        }
        // Inject viewport meta once per html string (stable across recompositions).
        val htmlWithViewport = remember(html) { injectViewportMeta(html) }

        if (shouldLoad && scalePercent > 0) {
            // key(scalePercent) forces WebView recreation if card width changes.
            androidx.compose.runtime.key(scalePercent) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        android.webkit.WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            // useWideViewPort makes WebView use the injected viewport
                            // width (1280px) as the layout viewport — elements with
                            // absolute positioning are placed correctly at full resolution.
                            settings.useWideViewPort = true
                            settings.loadWithOverviewMode = false
                            isVerticalScrollBarEnabled = false
                            isHorizontalScrollBarEnabled = false
                            // setInitialScale zooms the correctly-laid-out 1280px canvas
                            // down to the card width — native browser zoom, preserves layout.
                            setInitialScale(scalePercent)
                        }
                    },
                    update = { webView ->
                        webView.loadDataWithBaseURL(null, htmlWithViewport, "text/html", "UTF-8", null)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color(0xFFF0F4F8)),
            )
        }
        // Slide number badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF60A5FA).copy(alpha = 0.85f))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PptActivityWorkflowCard(part: AiPptWorkflowStreamPart, streamId: String? = null) {
    val slides = LocalWorkflowSlides.current[part.taskId] ?: emptyList()
    val workflowMsg = LocalWorkflowMessages.current[part.taskId]
    // isCompleted: WebSocket Completed message OR no active Progress AND slides look finished.
    // Historical loads (no WS messages, slides already stored) are treated as completed.
    val isCompleted = when (workflowMsg) {
        is WorkflowMessage.Completed -> true
        is WorkflowMessage.Progress -> false
        else -> slides.size >= part.totalSlides && slides.isNotEmpty()
    }
    if (slides.isEmpty()) {
        // No slides yet — show a lightweight loading card (no shimmer list, no rapid text).
        PptGeneratingCard(totalSlides = part.totalSlides)
    } else {
        // Show slides as they arrive; title and dots reflect in-progress vs. done.
        PptSlidesView(slides = slides, totalSlides = part.totalSlides, isGenerating = !isCompleted, streamId = streamId, taskId = part.taskId)
    }
}

@Composable
private fun DataPart(
    part: AiDataStreamPart,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    toolCardInserted: Boolean,
    workflowMessages: Map<String, WorkflowMessage> = emptyMap(),
    streamId: String? = null,
) {
    when (part.type) {
        "data-error" -> ErrorPart(AiErrorStreamPart(id = part.id, state = part.state, errorText = part.payload.errorTextFromJson().orEmpty()))
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
        "data" -> {
            val contentType = remember(part.id, part.payload) {
                runCatching { JSONObject(part.payload).optString("content_type") }.getOrNull().orEmpty()
            }
            when (contentType) {
                "ppt_planning" -> {
                    val pptData = remember(part.id, part.payload) { PptPlanningData.fromJson(part.payload) }
                    if (pptData != null) {
                        val progress = workflowMessages[pptData.taskId] ?: WorkflowMessage.Empty
                        PptPlanningCard(
                            data = pptData,
                            workflowProgress = progress,
                            onLinkClick = onLinkClick,
                        )
                    }
                }
                "writing_planning" -> {
                    val writingData = remember(part.id, part.payload) { WritingPlanningData.fromJson(part.payload) }
                    if (writingData != null) {
                        val progress = workflowMessages[writingData.taskId] ?: WorkflowMessage.Empty
                        WritingPlanningCard(
                            data = writingData,
                            workflowProgress = progress,
                            onLinkClick = onLinkClick,
                        )
                    }
                }
                "search_results" -> {
                    val searchData = remember(part.id, part.payload) { SearchResultsData.fromJson(part.payload) }
                    if (searchData != null) {
                        val progress = workflowMessages[searchData.taskId] ?: WorkflowMessage.Empty
                        SearchResultsCard(
                            data = searchData,
                            workflowProgress = progress,
                            onLinkClick = onLinkClick,
                            openFileMode = SearchResultsOpenMode.WordMiniApp,
                            streamId = streamId,
                        )
                    }
                }
                "ppt_outline_v2" -> {
                    val outlineData = remember(part.id, part.payload) { PptOutlineData.fromJson(part.payload) }
                    if (outlineData != null) {
                        PptOutlineCard(
                            data = outlineData,
                            onLinkClick = onLinkClick,
                        )
                    }
                }
                else -> Unit
            }
        }
        else -> Unit
    }
}

@Composable
private fun ErrorCard(payload: String) {
    val json = payload.jsonObjectOrNull()
    val title = json?.optString("title")?.takeIf { it.isNotBlank() } ?: stringResource(R.string.screen_room_timeline_ai_generic_error_title)
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
                    text = message.ifBlank { stringResource(R.string.screen_room_timeline_tool_card_stream_error) },
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
                    text = stringResource(R.string.screen_room_timeline_ai_suspended),
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
                    submitLabel = model.submitLabel ?: stringResource(R.string.screen_room_timeline_ai_continue),
                )
            }
            if (model.details.size > 6 || model.choices.size > 6) {
                Text(
                    text = stringResource(R.string.screen_room_timeline_tool_card_more_count, (model.details.size + model.choices.size) - 6),
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
        label = if (part.sourceType == "document") {
            stringResource(R.string.screen_room_timeline_ai_document)
        } else {
            stringResource(R.string.screen_room_timeline_ai_source)
        },
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
    val fileLabel = stringResource(R.string.screen_room_timeline_tool_card_file)
    val fallbackTitle = "$fileLabel · ${localizedAiState(part.state)}"
    InlineInfoRow(
        label = fileLabel,
        title = listOfNotNull(part.filename, part.mediaType).joinToString(" · ").ifBlank { fallbackTitle },
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
    ErrorBanner(message = part.errorText.ifBlank { stringResource(R.string.screen_room_timeline_tool_card_stream_error) })
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
                text = (if (expanded) "▾ " else "▸ ") + stringResource(R.string.screen_room_timeline_ai_reasoning) + " (${steps.size})",
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
                text = "${toolCall.displayName.localizedToolDisplayName()} · ${localizedAiState(toolCall.state)}",
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
private fun localizedAiState(state: String): String = when (state.lowercase()) {
    "pending" -> stringResource(R.string.screen_room_timeline_ai_state_pending)
    "running" -> stringResource(R.string.screen_room_timeline_ai_state_running)
    "streaming" -> stringResource(R.string.screen_room_timeline_ai_state_streaming)
    "completed", "complete", "done", "success" -> stringResource(R.string.screen_room_timeline_ai_state_completed)
    "failed", "error" -> stringResource(R.string.screen_room_timeline_ai_state_failed)
    "cancelled", "canceled" -> stringResource(R.string.screen_room_timeline_ai_state_cancelled)
    else -> state
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
                    text = stringResource(R.string.screen_room_timeline_ai_sources, sources.size),
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
