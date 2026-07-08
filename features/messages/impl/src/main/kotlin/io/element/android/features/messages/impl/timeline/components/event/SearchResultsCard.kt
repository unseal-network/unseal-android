/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.element.android.wysiwyg.link.Link
import org.json.JSONObject

// ── Open-file mode ─────────────────────────────────────────────────────────────

/**
 * Controls how "Export to Doc" behaves.
 *
 * [PreviewDialog]  — Shows a full-screen preview sheet (mirrors iOS FullContentSheet).
 *                    Zero external dependencies; works without a miniapp launcher.
 * [WordMiniApp]    — Opens the Word miniapp (appId = 1 / docxEditorId) via
 *                    [DocumentViewerOverlay]. Requires a non-null [LocalDocumentLauncher].
 *                    Falls back to [PreviewDialog] when launcher is unavailable.
 */
enum class SearchResultsOpenMode { PreviewDialog, WordMiniApp }

// ── Color palette (mirrors web SearchResultsElement.tsx) ──────────────────────

private val SearchHeaderGradientLight = Brush.horizontalGradient(
    colors = listOf(Color(0xFFEFF6FF), Color(0xFFEEF2FF)),
)
private val SearchHeaderGradientDark = Brush.horizontalGradient(
    colors = listOf(Color(0xFF27272A), Color(0xFF18181B)),
)
private val SearchIconGreen      = Color(0xFF10B981)
private val SearchIconGreenDark  = Color(0xFF059669)
private val DomainChipBgLight    = Color(0xFFDBEAFE)
private val DomainChipTextLight  = Color(0xFF1D4ED8)
private val DomainChipBgDark     = Color(0x4D1E3A5F)
private val DomainChipTextDark   = Color(0xFF93C5FD)
private val SummaryAccent        = Color(0xFFF59E0B)
private val GapsAccent           = Color(0xFFF97316)
private val DomainTextLight      = Color(0xFF2563EB)
private val DomainTextDark       = Color(0xFF60A5FA)
private val DocButtonBgLight     = Color(0xFFEFF6FF)
private val DocButtonBgDark      = Color(0xFF1E293B)

// ── Data model ────────────────────────────────────────────────────────────────

data class SearchResult(
    val url: String,
    val title: String,
    val content: String,
    val publishedDate: String?,
    val domain: String,
)

internal data class SearchResultsData(
    val taskId: String,
    val topic: String,
    val keywordsUsed: List<String>,
    val totalSourcesFound: Int,
    val results: List<SearchResult>,
    val summary: String,
    val researchGaps: List<String>,
    val message: String,
) {
    companion object {
        fun fromJson(payload: String): SearchResultsData? = runCatching {
            val json = JSONObject(payload)
            val results = buildList {
                json.optJSONArray("search_results")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val r = arr.optJSONObject(i) ?: continue
                        val url = r.optString("url").takeIf { it.isNotBlank() } ?: continue
                        add(
                            SearchResult(
                                url = url,
                                title = r.optString("title").ifBlank { url },
                                content = r.optString("content"),
                                publishedDate = r.optString("published_date").takeIf { it.isNotBlank() },
                                domain = r.optString("domain").takeIf { it.isNotBlank() }
                                    ?: extractDomain(url),
                            )
                        )
                    }
                }
            }
            SearchResultsData(
                taskId = json.optString("task_id"),
                topic = json.optString("topic"),
                keywordsUsed = json.optJSONArray("keywords_used")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList(),
                totalSourcesFound = json.optInt("total_sources_found").takeIf { it > 0 } ?: results.size,
                results = results,
                summary = json.optString("summary"),
                researchGaps = json.optJSONArray("research_gaps")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
                } ?: emptyList(),
                message = json.optString("message"),
            )
        }.getOrNull()

        private fun extractDomain(url: String): String =
            url.removePrefix("https://").removePrefix("http://")
                .substringBefore("/").removePrefix("www.")
    }
}

// ── Markdown assembly for export ──────────────────────────────────────────────

internal fun SearchResultsData.assembleMarkdown(): String = buildString {
    if (topic.isNotBlank()) appendLine("# $topic\n")
    if (summary.isNotBlank()) {
        appendLine("## Key Insights\n")
        appendLine(summary)
        appendLine()
    }
    if (researchGaps.isNotEmpty()) {
        appendLine("## Research Gaps\n")
        researchGaps.forEach { appendLine("- $it") }
        appendLine()
    }
    if (results.isNotEmpty()) {
        appendLine("## Sources\n")
        appendLine("| Title | Domain | URL | Published |")
        appendLine("|-------|--------|-----|-----------|")
        results.forEach { r ->
            val title = r.title.replace("|", "\\|").replace("\n", " ")
            val domain = r.domain.replace("|", "\\|")
            val url = r.url.replace("|", "\\|")
            val date = (r.publishedDate ?: "").replace("|", "\\|")
            appendLine("| $title | $domain | $url | $date |")
        }
    }
}.trimEnd()

// ── Main composable ───────────────────────────────────────────────────────────

/**
 * Card for `content_type = "search_results"`.
 *
 * [openFileMode] controls the "Export to Doc" button behaviour:
 * - [SearchResultsOpenMode.PreviewDialog]  → full-screen content preview (default)
 * - [SearchResultsOpenMode.WordMiniApp]    → opens Word miniapp via DocumentViewerOverlay
 *
 * [streamId] is the AI stream ID of the parent message; passed as `stream_id` in the miniapp
 * options so docId can be stored and retrieved across sessions.
 */
@Composable
internal fun SearchResultsCard(
    data: SearchResultsData,
    workflowProgress: WorkflowMessage,
    onLinkClick: (Link) -> Unit,
    openFileMode: SearchResultsOpenMode = SearchResultsOpenMode.PreviewDialog,
    streamId: String? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    var isExpanded     by remember { mutableStateOf(true) }
    var showSummary    by remember { mutableStateOf(true) }
    var showGaps       by remember { mutableStateOf(true) }
    var showSources    by remember { mutableStateOf(true) }
    var selectedDomain by remember { mutableStateOf<String?>(null) }

    // Open-file overlay state
    var showPreviewDialog by remember { mutableStateOf(false) }
    var showWordOverlay   by remember { mutableStateOf(false) }

    val domains = remember(data.results) {
        data.results.map { it.domain }.distinct().sorted()
    }
    val filteredResults = remember(data.results, selectedDomain) {
        if (selectedDomain != null) data.results.filter { it.domain == selectedDomain }
        else data.results
    }

    // Launcher from CompositionLocal (set by TimelineItemAiView)
    val launcher = LocalDocumentLauncher.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        // ── Card ──────────────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = screenHeightDp * 0.9f)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    1.dp,
                    if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7),
                    RoundedCornerShape(12.dp),
                )
                .background(if (isDark) Color(0xFF18181B) else Color.White),
        ) {
            // ── Header ────────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) SearchHeaderGradientDark else SearchHeaderGradientLight)
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Emerald search icon
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) SearchIconGreenDark else SearchIconGreen),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    // Topic + stats
                    Column(modifier = Modifier.weight(1f)) {
                        if (data.topic.isNotBlank()) {
                            Text(
                                text = data.topic,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "${data.totalSourcesFound} sources from ${domains.size} domain${if (domains.size != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF52525B),
                        )
                    }

                    // Open Doc button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) DocButtonBgDark else DocButtonBgLight)
                            .border(
                                1.dp,
                                if (isDark) Color(0xFF334155) else Color(0xFFBFDBFE),
                                RoundedCornerShape(8.dp),
                            )
                            .clickable {
                                when {
                                    openFileMode == SearchResultsOpenMode.WordMiniApp && launcher != null ->
                                        showWordOverlay = true
                                    else -> showPreviewDialog = true
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DocIcon(
                                color = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "Open",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                            )
                        }
                    }

                    // Collapse button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { isExpanded = !isExpanded },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                // Domain filter chips
                AnimatedVisibility(
                    visible = isExpanded && domains.isNotEmpty(),
                    enter = expandVertically(),
                    exit = shrinkVertically(),
                ) {
                    DomainFilterRow(
                        domains = domains,
                        results = data.results,
                        selectedDomain = selectedDomain,
                        isDark = isDark,
                        onDomainClick = { domain ->
                            if (selectedDomain == domain) {
                                selectedDomain = null
                                showSummary = true
                                showGaps = true
                            } else {
                                selectedDomain = domain
                                showSummary = false
                                showGaps = false
                            }
                        },
                        onClearFilter = {
                            selectedDomain = null
                            showSummary = true
                            showGaps = true
                        },
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            // ── Body ──────────────────────────────────────────────────────────
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    // Key Insights
                    if (data.summary.isNotBlank()) {
                        SectionDivider(isDark)
                        CollapsibleSection(
                            title = "Key Insights",
                            icon = { BarChartIcon(color = SummaryAccent, modifier = Modifier.size(20.dp)) },
                            expanded = showSummary,
                            onToggle = { showSummary = !showSummary },
                            isDark = isDark,
                        ) {
                            MarkdownBody(
                                text = data.summary,
                                renderMode = MarkdownRenderMode.Stable,
                                onLinkClick = onLinkClick,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 28.dp)
                                    .padding(bottom = 16.dp),
                            )
                        }
                    }

                    // Research Gaps
                    if (data.researchGaps.isNotEmpty()) {
                        SectionDivider(isDark)
                        CollapsibleSection(
                            title = "Research Gaps",
                            icon = { BarChartIcon(color = GapsAccent, modifier = Modifier.size(20.dp)) },
                            expanded = showGaps,
                            onToggle = { showGaps = !showGaps },
                            isDark = isDark,
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(start = 28.dp)
                                    .padding(bottom = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                data.researchGaps.forEach { gap ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.Top,
                                    ) {
                                        Text(
                                            text = "•",
                                            color = GapsAccent,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.padding(top = 1.dp),
                                        )
                                        Text(
                                            text = gap,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isDark) Color(0xFFD4D4D8) else Color(0xFF3F3F46),
                                            modifier = Modifier.weight(1f),
                                            lineHeight = 22.sp,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Sources
                    SectionDivider(isDark)
                    CollapsibleSection(
                        title = if (selectedDomain != null) "Sources (${filteredResults.size})" else "Sources",
                        icon = null,
                        expanded = showSources,
                        onToggle = { showSources = !showSources },
                        isDark = isDark,
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            filteredResults.forEach { result ->
                                SourceResultItem(result = result, isDark = isDark)
                            }
                        }
                    }

                    // Workflow progress
                    if (workflowProgress !is WorkflowMessage.Empty) {
                        SectionDivider(isDark)
                        Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                            WorkflowProgressSection(
                                progress = workflowProgress,
                                accentColor = SearchIconGreen,
                            )
                        }
                    }
                }
            }
        }

        // ── Message BELOW the card (mirrors web placement) ────────────────────
        if (data.message.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            MarkdownBody(
                text = data.message,
                renderMode = MarkdownRenderMode.Stable,
                onLinkClick = onLinkClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    // ── Mode 1: Preview Dialog ────────────────────────────────────────────────
    if (showPreviewDialog) {
        SearchResultsPreviewDialog(
            data = data,
            onLinkClick = onLinkClick,
            onDismiss = { showPreviewDialog = false },
        )
    }

    // ── Mode 2: Word MiniApp ──────────────────────────────────────────────────
    if (showWordOverlay && launcher != null) {
        val options = remember(data, streamId) {
            val markdown = data.assembleMarkdown()
            val markdownBytes = markdown.toByteArray(Charsets.UTF_8)
            val fileBase64 = android.util.Base64.encodeToString(markdownBytes, android.util.Base64.NO_WRAP)
            val fileName = "${data.topic.ifBlank { "search_results" }}.docx"
            buildMap<String, Any> {
                put("create_type", "word")
                put("file_name", fileName)
                put("file_base64", fileBase64)
                put("file_size", markdownBytes.size.toLong())
                put("mime_type", "text/markdown")
                streamId?.takeIf { it.isNotBlank() }?.let { put("stream_id", it) }
            }
        }
        DocumentViewerOverlay(
            appId = MiniAppIds.DOCX,
            options = options,
            launcher = launcher,
            onDismiss = { showWordOverlay = false },
        )
    }
}

// ── Preview Dialog (Mode 1 — mirrors iOS FullContentSheet) ────────────────────

@Composable
private fun SearchResultsPreviewDialog(
    data: SearchResultsData,
    onLinkClick: (Link) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(if (isDark) Color(0xFF18181B) else Color.White),
        ) {
            // Dialog header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color(0xFF27272A) else Color(0xFFF9FAFB))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = data.topic.ifBlank { "Search Results" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onDismiss)
                        .background(if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
                    )
                }
            }

            HorizontalDivider(
                color = if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7),
                thickness = 1.dp,
            )

            // Scrollable content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Summary
                if (data.summary.isNotBlank()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BarChartIcon(color = SummaryAccent, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Key Insights",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
                            )
                        }
                        MarkdownBody(
                            text = data.summary,
                            renderMode = MarkdownRenderMode.Stable,
                            onLinkClick = onLinkClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                // Research Gaps
                if (data.researchGaps.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BarChartIcon(color = GapsAccent, modifier = Modifier.size(18.dp))
                            Text(
                                text = "Research Gaps",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            data.researchGaps.forEach { gap ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text("•", color = GapsAccent, fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.padding(top = 1.dp))
                                    Text(
                                        text = gap,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (isDark) Color(0xFFD4D4D8) else Color(0xFF3F3F46),
                                        modifier = Modifier.weight(1f),
                                        lineHeight = 22.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                // Sources
                if (data.results.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Sources (${data.results.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
                        )
                        data.results.forEach { result ->
                            SourceResultItem(result = result, isDark = isDark)
                        }
                    }
                }
            }
        }
    }
}

// ── Shared section composable ─────────────────────────────────────────────────

@Composable
private fun CollapsibleSection(
    title: String,
    icon: (@Composable () -> Unit)?,
    expanded: Boolean,
    onToggle: () -> Unit,
    isDark: Boolean,
    content: @Composable () -> Unit,
) {
    val chevron by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "section-chevron",
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (icon != null) icon()
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A),
                modifier = Modifier.size(16.dp).rotate(chevron),
            )
        }
        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            content()
        }
    }
}

@Composable
private fun SectionDivider(isDark: Boolean) {
    HorizontalDivider(
        color = if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7),
        thickness = 1.dp,
    )
}

// ── Domain filter row ─────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DomainFilterRow(
    domains: List<String>,
    results: List<SearchResult>,
    selectedDomain: String?,
    isDark: Boolean,
    onDomainClick: (String) -> Unit,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        domains.forEach { domain ->
            val count = results.count { it.domain == domain }
            val isSelected = selectedDomain == domain
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            isSelected && isDark -> Color(0xFF3F3F46)
                            isSelected -> Color(0xFFE4E4E7)
                            isDark -> DomainChipBgDark
                            else -> DomainChipBgLight
                        }
                    )
                    .clickable { onDomainClick(domain) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = domain,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = when {
                            isSelected && isDark -> Color(0xFFF4F4F5)
                            isSelected -> Color(0xFF18181B)
                            isDark -> DomainChipTextDark
                            else -> DomainChipTextLight
                        },
                    )
                    Text(
                        text = "($count)",
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isSelected && isDark -> Color(0xFFA1A1AA)
                            isSelected -> Color(0xFF71717A)
                            else -> if (isDark) DomainChipTextDark.copy(alpha = 0.7f) else DomainChipTextLight.copy(alpha = 0.7f)
                        },
                    )
                }
            }
        }
        if (selectedDomain != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7))
                    .clickable(onClick = onClearFilter)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "Clear filter",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF71717A),
                )
            }
        }
    }
}

// ── Source result item ────────────────────────────────────────────────────────

@Composable
private fun SourceResultItem(result: SearchResult, isDark: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isDark) Color(0xFF27272A) else Color.White)
            .border(
                1.dp,
                if (isDark) Color(0xFF3F3F46) else Color(0xFFE4E4E7),
                RoundedCornerShape(10.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = result.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 22.sp,
        )
        if (result.content.isNotBlank()) {
            Text(
                text = result.content,
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Color(0xFFA1A1AA) else Color(0xFF52525B),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = result.domain,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) DomainTextDark else DomainTextLight,
            )
            result.publishedDate?.let { date ->
                Text("·", style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color(0xFF71717A) else Color(0xFFA1A1AA))
                Text(date, style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color(0xFF71717A) else Color(0xFFA1A1AA))
            }
        }
    }
}

// ── Canvas icons ──────────────────────────────────────────────────────────────

@Composable
private fun BarChartIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width; val h = size.height; val r = w * 2 / 24f
        // Tall right bar
        drawRoundRect(color = color,
            topLeft = androidx.compose.ui.geometry.Offset(w * 16 / 24f, h * 3 / 24f),
            size = androidx.compose.ui.geometry.Size(w * 5 / 24f, h * 18 / 24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        // Mid bar
        drawRoundRect(color = color,
            topLeft = androidx.compose.ui.geometry.Offset(w * 9.5f / 24f, h * 9 / 24f),
            size = androidx.compose.ui.geometry.Size(w * 5 / 24f, h * 12 / 24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        // Short left bar
        drawRoundRect(color = color,
            topLeft = androidx.compose.ui.geometry.Offset(w * 3 / 24f, h * 16 / 24f),
            size = androidx.compose.ui.geometry.Size(w * 5 / 24f, h * 5 / 24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
    }
}

/** Word document icon (simplified "W" outline). */
@Composable
private fun DocIcon(color: Color, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.1f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round)
        // Page outline
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.25f, 0f)
            lineTo(w * 0.65f, 0f)
            lineTo(w, h * 0.38f)
            lineTo(w, h)
            lineTo(w * 0.25f, h)
            close()
        }
        drawPath(path, color = color, style = stroke)
        // Fold corner
        val fold = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.65f, 0f)
            lineTo(w * 0.65f, h * 0.38f)
            lineTo(w, h * 0.38f)
        }
        drawPath(fold, color = color, style = stroke)
        // Lines (text)
        val lx1 = w * 0.38f; val lx2 = w * 0.82f; val lineStroke = w * 0.08f
        val lPaint = androidx.compose.ui.graphics.drawscope.Stroke(width = lineStroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(color.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(lx1, h * 0.55f),
            androidx.compose.ui.geometry.Offset(lx2, h * 0.55f), lineStroke)
        drawLine(color.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(lx1, h * 0.70f),
            androidx.compose.ui.geometry.Offset(lx2, h * 0.70f), lineStroke)
        drawLine(color.copy(alpha = 0.6f), androidx.compose.ui.geometry.Offset(lx1, h * 0.85f),
            androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.85f), lineStroke)
    }
}
