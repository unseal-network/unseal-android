/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.UriHandler
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
import io.element.android.wysiwyg.link.Link

/**
 * Renders an AI message body as Markdown, mirroring the iOS base rendering logic
 * (`MarkdownRenderView` / `_MarkdownBody` in unseal-agent-ios). The component library differs
 * (mikepenz multiplatform-markdown-renderer here vs MarkdownView/cmark on iOS) but the rendering
 * logic is kept consistent: same heading hierarchy, link/inline-code/code-block treatment, and
 * — crucially — the same caching strategy.
 *
 * Caching (mirrors iOS `MarkdownViewCache`):
 *  - **Stable** text (not streaming): the parsed result is cached by full text in a process-level
 *    LRU and reused on every timeline-cell rebuild / scroll-back / room re-entry, so it is parsed
 *    exactly once.
 *  - **Streaming** text: parsed fresh on each update (the text mutates every chunk, so a parse can't
 *    be reused); the 500ms patch coalescing upstream bounds how often this happens.
 */
@Composable
internal fun MarkdownBody(
    text: String,
    isStreaming: Boolean,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (text.isBlank()) return

    val uriHandler = remember(onLinkClick) {
        object : UriHandler {
            override fun openUri(uri: String) = onLinkClick(Link(uri))
        }
    }
    val colors = aiMarkdownColors()
    val typography = aiMarkdownTypography()
    val components = remember {
        markdownComponents(
            codeBlock = { model ->
                MarkdownCodeBlock(model.content, model.node, model.typography.code) { code, language, _ ->
                    AiCodeBlock(code = code, language = language)
                }
            },
            codeFence = { model ->
                MarkdownCodeFence(model.content, model.node, model.typography.code) { code, language, _ ->
                    AiCodeBlock(code = code, language = language)
                }
            },
        )
    }

    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        val cached = if (isStreaming) null else MarkdownParseCache.get(text)
        if (cached != null) {
            // Stable + cache hit: render the already-parsed tree, zero re-parse.
            Markdown(
                state = cached,
                colors = colors,
                typography = typography,
                imageTransformer = Coil3ImageTransformerImpl,
                components = components,
                modifier = modifier,
            )
        } else {
            Markdown(
                content = text,
                colors = colors,
                typography = typography,
                imageTransformer = Coil3ImageTransformerImpl,
                components = components,
                modifier = modifier,
                success = { state, comps, mod ->
                    // Cache the parsed tree once the (stable) message has finished parsing.
                    SideEffect { if (!isStreaming) MarkdownParseCache.put(text, state) }
                    MarkdownSuccess(state = state, components = comps, modifier = mod)
                },
            )
        }
    }
}

/** Process-level LRU of parsed Markdown trees keyed by full source text. Mirrors iOS `MarkdownViewCache` (FIFO, 200). */
private object MarkdownParseCache {
    private const val LIMIT = 200
    private val lock = Any()
    private val store = object : LinkedHashMap<String, State.Success>(LIMIT, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, State.Success>): Boolean = size > LIMIT
    }

    fun get(text: String): State.Success? = synchronized(lock) { store[text] }

    fun put(text: String, state: State.Success) {
        synchronized(lock) { store[text] = state }
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
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }
    }
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
