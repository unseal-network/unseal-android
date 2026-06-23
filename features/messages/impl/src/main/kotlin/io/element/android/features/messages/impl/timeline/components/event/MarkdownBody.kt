/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.util.TypedValue
import android.widget.TextView
import android.text.Spannable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.MarkdownSuccess
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.State
import io.element.android.compound.theme.ElementTheme
import io.element.android.wysiwyg.link.Link
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor

/**
 * Renders an AI message body as Markdown, mirroring the iOS base rendering logic
 * (`MarkdownRenderView` / `_MarkdownBody` in unseal-agent-ios). The component library differs
 * (mikepenz multiplatform-markdown-renderer here vs MarkdownView/cmark on iOS) but the rendering
 * logic is kept consistent: same heading hierarchy, link/inline-code/code-block treatment, and
 * — crucially — the same caching strategy.
 *
 * Rendering modes are intentionally separate:
 *  - [MarkdownRenderMode.Stable] is for fixed timeline content. It performs a full render only
 *    when the text actually changes, so normal Compose recomposition does not reset the TextView.
 *  - [MarkdownRenderMode.Streaming] is for AI output in flight. It can update as chunks arrive,
 *    but still skips duplicate safe-text updates to avoid visible whole-block flashing.
 */
@Composable
internal fun MarkdownBody(
    text: String,
    renderMode: MarkdownRenderMode,
    onLinkClick: (Link) -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
) {
    if (text.isBlank()) return
    extractPrimaryJsonSpecFence(text)?.let { fence ->
        AiCodeOrJsonSpecBlock(
            code = fence.code,
            language = fence.language,
            onLinkClick = onLinkClick,
            modifier = modifier.then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier),
        )
        return
    }
    // Markwon renders GFM tables unreliably inside an AndroidView (rows collapse/overlap on a static
    // layout pass), so we split tables out and render them as a proper Compose grid (iOS parity),
    // keeping the surrounding prose on Markwon. Text-only content keeps the original single-view path.
    val safeText = when (renderMode) {
        MarkdownRenderMode.Stable -> text
        MarkdownRenderMode.Streaming -> text.streamingMarkdownSafeText()
    }
    val segments = remember(safeText) { splitMarkdownTableSegments(safeText) }
    val hasTable = segments.any { it is MarkdownSegment.Table }
    if (!hasTable) {
        MarkwonMarkdownBody(
            text = text,
            renderMode = renderMode,
            onLinkClick = onLinkClick,
            onLongClick = onLongClick,
            modifier = modifier,
            fillMaxWidth = fillMaxWidth,
        )
        return
    }
    Column(
        modifier = modifier.then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEach { segment ->
            when (segment) {
                is MarkdownSegment.Text -> MarkwonMarkdownBody(
                    text = segment.text,
                    // Segments are already streaming-trimmed above.
                    renderMode = MarkdownRenderMode.Stable,
                    onLinkClick = onLinkClick,
                    onLongClick = onLongClick,
                    fillMaxWidth = fillMaxWidth,
                )
                is MarkdownSegment.Table -> HtmlTableBody(
                    tables = listOf(segment.table),
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
    }
}

private sealed interface MarkdownSegment {
    data class Text(val text: String) : MarkdownSegment
    data class Table(val table: HtmlTable) : MarkdownSegment
}

/**
 * Splits markdown into prose segments and GFM table segments. Tables (a header row, a separator row,
 * then one or more data rows) become [HtmlTable]s rendered by [HtmlTableBody]; everything else stays
 * as text rendered by Markwon. Fenced code blocks are left untouched.
 */
private fun splitMarkdownTableSegments(text: String): List<MarkdownSegment> {
    val lines = text.lines()
    val segments = mutableListOf<MarkdownSegment>()
    val textBuffer = StringBuilder()
    var index = 0
    var inFence = false

    fun flushText() {
        val pending = textBuffer.toString().trim('\n')
        if (pending.isNotBlank()) segments += MarkdownSegment.Text(pending)
        textBuffer.setLength(0)
    }

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            textBuffer.append(line).append('\n')
            index += 1
            continue
        }
        if (!inFence && index + 1 < lines.size && isMarkdownTableSeparator(lines[index + 1])) {
            val headers = parseMarkdownTableRow(line)
            if (headers.size >= 2) {
                val dataRows = mutableListOf<List<String>>()
                var rowIndex = index + 2
                while (rowIndex < lines.size) {
                    val row = parseMarkdownTableRow(lines[rowIndex])
                    if (row.size < 2) break
                    dataRows += row
                    rowIndex += 1
                }
                if (dataRows.isNotEmpty()) {
                    flushText()
                    val rows = buildList {
                        add(HtmlTableRow(headers.map { HtmlTableCell(text = it, isHeader = true) }))
                        dataRows.forEach { row ->
                            add(HtmlTableRow(row.map { HtmlTableCell(text = it, isHeader = false) }))
                        }
                    }
                    segments += MarkdownSegment.Table(HtmlTable(rows = rows))
                    index = rowIndex
                    continue
                }
            }
        }
        textBuffer.append(line).append('\n')
        index += 1
    }
    flushText()
    return segments
}

internal enum class MarkdownRenderMode {
    Stable,
    Streaming,
}

@Composable
private fun MarkwonMarkdownBody(
    text: String,
    renderMode: MarkdownRenderMode,
    onLinkClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    fillMaxWidth: Boolean = true,
) {
    val context = LocalContext.current
    val currentOnLinkClick = rememberUpdatedState(onLinkClick)
    val textColor = ElementTheme.colors.textPrimary
    val linkColor = MaterialTheme.colorScheme.primary
    val textSizeSp = MaterialTheme.typography.bodyMedium.fontSize.value.takeIf { it > 0f } ?: 16f
    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link ->
                        currentOnLinkClick.value(Link(link))
                    }
                }
            })
            .build()
    }
    val renderedText = when (renderMode) {
        MarkdownRenderMode.Stable -> text
        MarkdownRenderMode.Streaming -> text.streamingMarkdownSafeText()
    }
    val textColorArgb = textColor.toArgb()
    val linkColorArgb = linkColor.toArgb()
    val codeBackgroundArgb = ElementTheme.colors.bgSubtleSecondary.toArgb()
    AndroidView(
        modifier = modifier
            .then(if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier)
            .collapseSemanticsForLongMarkdown(text),
        factory = { viewContext ->
            TextView(viewContext).apply {
                includeFontPadding = false
                setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
                setTextColor(textColorArgb)
                setLinkTextColor(linkColorArgb)
                setLineSpacing(0f, 1.08f)
                configureMarkdownTextViewSelection(this, onLongClick)
            }
        },
        update = { textView ->
            val previousState = textView.tag as? RenderedMarkdownState
            if (previousState?.textSizeSp != textSizeSp) {
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
            }
            if (previousState?.textColorArgb != textColorArgb) {
                textView.setTextColor(textColorArgb)
            }
            if (previousState?.linkColorArgb != linkColorArgb) {
                textView.setLinkTextColor(linkColorArgb)
            }
            if (
                previousState?.markdownText != renderedText ||
                previousState.renderMode != renderMode ||
                previousState.textColorArgb != textColorArgb ||
                previousState.linkColorArgb != linkColorArgb ||
                previousState.codeBackgroundArgb != codeBackgroundArgb
            ) {
                markwon.setMarkdown(textView, renderedText)
            }
            applyMarkdownThemeSpans(
                textView = textView,
                textColorArgb = textColorArgb,
                linkColorArgb = linkColorArgb,
                codeBackgroundArgb = codeBackgroundArgb,
            )
            textView.tag = RenderedMarkdownState(
                markdownText = renderedText,
                renderMode = renderMode,
                textColorArgb = textColorArgb,
                linkColorArgb = linkColorArgb,
                textSizeSp = textSizeSp,
                codeBackgroundArgb = codeBackgroundArgb,
            )
        },
    )
}

internal fun configureMarkdownTextViewSelection(textView: TextView, onLongClick: (() -> Unit)? = null) {
    textView.setTextIsSelectable(true)
    textView.setOnLongClickListener(
        onLongClick?.let {
            android.view.View.OnLongClickListener {
                onLongClick()
                false
            }
        }
    )
}

private data class RenderedMarkdownState(
    val markdownText: String,
    val renderMode: MarkdownRenderMode,
    val textColorArgb: Int,
    val linkColorArgb: Int,
    val textSizeSp: Float,
    val codeBackgroundArgb: Int,
)

internal fun applyMarkdownThemeSpans(
    textView: TextView,
    textColorArgb: Int,
    linkColorArgb: Int,
    codeBackgroundArgb: Int,
) {
    textView.setTextColor(textColorArgb)
    textView.setLinkTextColor(linkColorArgb)

    var needsTextViewUpdate = false
    val spannable = when (val text = textView.text) {
        is Spannable -> text
        is Spanned -> SpannableString(text).also {
            needsTextViewUpdate = true
        }
        else -> return
    }

    spannable.getSpans(0, spannable.length, ForegroundColorSpan::class.java).forEach { span ->
        spannable.removeSpan(span)
    }
    if (spannable.isNotEmpty()) {
        spannable.setSpan(
            ForegroundColorSpan(textColorArgb),
            0,
            spannable.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
        )
    }
    spannable.getSpans(0, spannable.length, URLSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        if (start >= 0 && end > start) {
            spannable.setSpan(
                ForegroundColorSpan(linkColorArgb),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }
    spannable.getSpans(0, spannable.length, UnderlineSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        if (start >= 0 && end > start && !spannable.hasUrlSpan(start, end)) {
            spannable.setSpan(
                ForegroundColorSpan(linkColorArgb),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
    }

    spannable.getSpans(0, spannable.length, BackgroundColorSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val flags = spannable.getSpanFlags(span)
        spannable.removeSpan(span)
        spannable.setSpan(BackgroundColorSpan(codeBackgroundArgb), start, end, flags)
    }

    spannable.getSpans(0, spannable.length, TypefaceSpan::class.java).forEach { span ->
        val family = span.family
        if (family == "monospace" || family == FontFamily.Monospace.toString()) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            if (start >= 0 && end > start && !spannable.hasBackgroundSpan(start, end)) {
                spannable.setSpan(BackgroundColorSpan(codeBackgroundArgb), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    if (needsTextViewUpdate) {
        textView.setText(spannable, TextView.BufferType.SPANNABLE)
    }
}

private fun Spannable.hasUrlSpan(start: Int, end: Int): Boolean =
    getSpans(start.coerceAtLeast(0), end.coerceAtLeast(start), URLSpan::class.java).isNotEmpty()

private fun Spannable.hasBackgroundSpan(start: Int, end: Int): Boolean =
    getSpans(start.coerceAtLeast(0), end.coerceAtLeast(start), BackgroundColorSpan::class.java).isNotEmpty()

private fun String.streamingMarkdownSafeText(): String {
    var safeEnd = length
    safeEnd = minOf(safeEnd, unclosedFenceStart())
    safeEnd = minOf(safeEnd, unclosedInlineMarkerStart("`"))
    safeEnd = minOf(safeEnd, unclosedInlineMarkerStart("**"))
    safeEnd = minOf(safeEnd, unclosedInlineMarkerStart("__"))
    safeEnd = minOf(safeEnd, unclosedLinkStart())
    return take(safeEnd.coerceIn(0, length))
}

internal fun String.streamingMarkdownSafeTextForTest(): String = streamingMarkdownSafeText()

internal fun String.mobileFriendlyMarkdownTablesForTest(): String = mobileFriendlyMarkdownTables()

private fun String.mobileFriendlyMarkdownTables(): String {
    val lines = lineSequence().toList()
    if (lines.none { it.contains('|') }) return this
    val out = mutableListOf<String>()
    var index = 0
    var inFence = false
    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
            out += line
            index += 1
            continue
        }
        if (!inFence && index + 1 < lines.size && isMarkdownTableSeparator(lines[index + 1])) {
            val headers = parseMarkdownTableRow(line)
            if (headers.size >= 2) {
                val rows = mutableListOf<List<String>>()
                var rowIndex = index + 2
                while (rowIndex < lines.size) {
                    val row = parseMarkdownTableRow(lines[rowIndex])
                    if (row.size < 2) break
                    rows += row
                    rowIndex += 1
                }
                if (rows.isNotEmpty()) {
                    if (out.lastOrNull()?.isNotBlank() == true) out += ""
                    rows.forEachIndexed { itemIndex, row ->
                        if (rows.size > 1) {
                            out += "**Item ${itemIndex + 1}**"
                        }
                        headers.zip(row).forEach { (header, value) ->
                            if (value.isNotBlank()) {
                                out += "- **${header.ifBlank { "Field" }}:** $value"
                            }
                        }
                        if (itemIndex < rows.lastIndex) out += ""
                    }
                    index = rowIndex
                    continue
                }
            }
        }
        out += line
        index += 1
    }
    return out.joinToString("\n")
}

private fun parseMarkdownTableRow(line: String): List<String> {
    val trimmed = line.trim()
    if (!trimmed.contains('|')) return emptyList()
    val body = trimmed.removePrefix("|").removeSuffix("|")
    return body.split('|').map { it.trim() }
}

private fun isMarkdownTableSeparator(line: String): Boolean {
    val cells = parseMarkdownTableRow(line)
    return cells.size >= 2 && cells.all { cell ->
        cell.matches(Regex(":?-{3,}:?"))
    }
}

private fun String.unclosedFenceStart(): Int {
    val matches = Regex("```").findAll(this).map { it.range.first }.toList()
    return if (matches.size % 2 == 1) matches.last() else length
}

private fun String.unclosedInlineMarkerStart(marker: String): Int {
    val matches = Regex(Regex.escape(marker)).findAll(this).map { it.range.first }.toList()
    return if (matches.size % 2 == 1) matches.last() else length
}

private fun String.unclosedLinkStart(): Int {
    val open = lastIndexOf('[')
    if (open < 0) return length
    val close = indexOf(']', startIndex = open + 1)
    if (close < 0) return open
    val parenOpen = indexOf('(', startIndex = close + 1)
    if (parenOpen < 0) return length
    val parenClose = indexOf(')', startIndex = parenOpen + 1)
    return if (parenClose < 0) open else length
}

private const val COLLAPSED_MARKDOWN_SEMANTICS_THRESHOLD = 500

private fun Modifier.collapseSemanticsForLongMarkdown(markdownText: String): Modifier {
    if (markdownText.length < COLLAPSED_MARKDOWN_SEMANTICS_THRESHOLD) return this
    return clearAndSetSemantics {
        text = AnnotatedString(markdownText)
    }
}

/** Process-level LRU of parsed Markdown trees keyed by full source text. Mirrors iOS `MarkdownViewCache` (FIFO, 200). */
private object MarkdownParseCache {
    private const val KEY_PREFIX = "gfm:v1:"
    private const val LIMIT = 200
    private val lock = Any()
    private val store = object : LinkedHashMap<String, State.Success>(LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, State.Success>): Boolean = size > LIMIT
    }

    fun get(text: String): State.Success? = synchronized(lock) { store[KEY_PREFIX + text] }

    fun put(text: String, state: State.Success) {
        synchronized(lock) { store[KEY_PREFIX + text] = state }
    }
}

@Composable
private fun aiMarkdownColors() = markdownColor(
    text = MaterialTheme.colorScheme.onSurface,
    codeBackground = MaterialTheme.colorScheme.surfaceVariant,
    inlineCodeBackground = MaterialTheme.colorScheme.secondaryContainer,
)

@Composable
private fun aiMarkdownTypography() = run {
    val primary = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val body = MaterialTheme.typography.bodyMedium
    markdownTypography(
        // Mirror iOS heading hierarchy (size + weight + primary/secondary colour).
        h1 = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold, color = primary),
        h2 = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = primary),
        h3 = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = primary),
        h4 = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = secondary),
        h5 = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = secondary),
        h6 = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, color = secondary),
        text = body,
        paragraph = body,
        ordered = body,
        bullet = body,
        list = body,
        code = body.copy(fontFamily = FontFamily.Monospace),
        textLink = TextLinkStyles(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            ),
        ),
    )
}

/**
 * Custom fenced/indented code block, mirroring iOS `CustomCodeBlockView`: a header row with a
 * language-coloured dot + uppercase language name and a copy button, then the horizontally
 * scrollable monospace code, wrapped in a rounded surface with a language-coloured accent.
 */
@Composable
private fun AiCodeOrJsonSpecBlock(
    code: String,
    language: String?,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (markdownCodeBlockRenderMode(language = language, code = code)) {
        MarkdownCodeBlockRenderMode.JsonSpec -> JsonSpecRender(payload = code, onLinkClick = onLinkClick, modifier = modifier.padding(vertical = 6.dp))
        MarkdownCodeBlockRenderMode.Code -> AiCodeBlock(code = code, language = language, modifier = modifier)
    }
}

internal enum class MarkdownCodeBlockRenderMode {
    Code,
    JsonSpec,
}

internal fun markdownCodeBlockRenderMode(language: String?, code: String): MarkdownCodeBlockRenderMode {
    val normalizedLanguage = language?.lowercase()
    return when (normalizedLanguage) {
        "spec" -> MarkdownCodeBlockRenderMode.JsonSpec
        "json", "jsonl" -> if (code.canRenderAsJsonSpec() || code.looksLikeReadableJsonSpecPayload()) {
            MarkdownCodeBlockRenderMode.JsonSpec
        } else {
            MarkdownCodeBlockRenderMode.Code
        }
        null, "" -> if (code.looksLikeReadableJsonSpecPayload()) {
            MarkdownCodeBlockRenderMode.JsonSpec
        } else {
            MarkdownCodeBlockRenderMode.Code
        }
        else -> MarkdownCodeBlockRenderMode.Code
    }
}

private data class MarkdownCodeFence(val language: String?, val code: String)

private fun extractPrimaryJsonSpecFence(markdown: String): MarkdownCodeFence? {
    val lines = markdown.trim().lines()
    if (lines.size < 3) return null
    val first = lines.first().trim()
    val last = lines.last().trim()
    if (!first.startsWith("```") || last != "```") return null
    val language = first.removePrefix("```").trim().takeIf { it.isNotBlank() }
    val code = lines.drop(1).dropLast(1).joinToString("\n").trim()
    if (code.isBlank()) return null
    return if (markdownCodeBlockRenderMode(language = language, code = code) == MarkdownCodeBlockRenderMode.JsonSpec) {
        MarkdownCodeFence(language = language, code = code)
    } else {
        null
    }
}

private fun String.looksLikeReadableJsonSpecPayload(): Boolean {
    val trimmed = trim()
    if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) return false
    val json = runCatching { org.json.JSONTokener(trimmed).nextValue() }.getOrNull()
    return when (json) {
        is org.json.JSONObject -> json.has("component") ||
            json.has("_cardType") ||
            json.has("cards") ||
            json.has("sections") ||
            json.has("props")
        is org.json.JSONArray -> json.length() > 0 && (0 until json.length()).any { index ->
            val item = json.optJSONObject(index)
            item != null && (item.has("component") || item.has("_cardType") || item.has("op") || item.has("path"))
        }
        else -> false
    }
}

internal fun extractPrimaryJsonSpecFenceForTest(markdown: String): String? = extractPrimaryJsonSpecFence(markdown)?.code

@Composable
private fun AiCodeBlock(
    code: String,
    language: String?,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember(code) { mutableStateOf(false) }
    val langColor = languageColor(language)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.padding(vertical = 2.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!language.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(langColor),
                    )
                    Text(
                        text = language.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        fontWeight = FontWeight.SemiBold,
                        color = langColor,
                    )
                }
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (copied) Color(0x1F2E7D32) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    modifier = Modifier.clickable {
                        clipboard.setText(AnnotatedString(code))
                        copied = true
                    },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (copied) Icons.Filled.Check else Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = if (copied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = if (copied) "Copied" else "Copy",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (copied) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            // Pretty-print JSON so minified payloads read cleanly; other code is shown as-is.
            val display = remember(code, language) { prettyPrintedCode(code, language) }
            Text(
                text = display,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
}

/** Pretty-print JSON code (2-space indent) for readability; leave other languages untouched. */
private fun prettyPrintedCode(code: String, language: String?): String {
    val trimmed = code.trim()
    val looksJson = language?.lowercase() in setOf("json", "jsonl") ||
        trimmed.startsWith("{") || trimmed.startsWith("[")
    if (!looksJson) return code
    return runCatching {
        when {
            trimmed.startsWith("{") -> org.json.JSONObject(trimmed).toString(2)
            trimmed.startsWith("[") -> org.json.JSONArray(trimmed).toString(2)
            else -> code
        }
    }.getOrDefault(code)
}

/** Language → accent colour, mirroring iOS `CustomCodeBlockView.langColor`. */
private fun languageColor(language: String?): Color = when (language?.lowercase()) {
    "swift" -> Color(0xFFFF8C00)
    "python" -> Color(0xFF3878B8)
    "javascript", "js" -> Color(0xFFF5C724)
    "typescript", "ts" -> Color(0xFF2B70C2)
    "kotlin" -> Color(0xFF734FEB)
    "rust" -> Color(0xFFD65E2E)
    "go" -> Color(0xFF00AED6)
    "css" -> Color(0xFFFF6EC7)
    "html", "xml" -> Color(0xFFE56133)
    "bash", "sh", "shell", "zsh" -> Color(0xFF2E9E44)
    "json", "jsonl", "yaml", "toml" -> Color(0xFF2EC9B0)
    "sql" -> Color(0xFF5EADED)
    "c", "cpp", "c++" -> Color(0xFF5791CC)
    "java" -> Color(0xFFCC4D40)
    "ruby", "rb" -> Color(0xFFE53935)
    "php" -> Color(0xFF7866A6)
    else -> Color(0xFF8E8E93)
}
