/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import io.element.android.wysiwyg.link.Link
import org.json.JSONObject

private val WritingAccent = Color(0xFF6366F1)  // indigo — distinct from PPT teal

/** Parsed fields from a `writing_planning` data part payload. */
internal data class WritingPlanningData(
    val taskId: String,
    val topic: String,
    val documentType: String,
    val academicLevel: String,
    val wordCountRange: String,
    val citationFormat: String,
    val language: String,
    val requirements: List<String>,
    val primaryKeywords: List<String>,
    val secondaryKeywords: List<String>,
    val searchPhrases: List<String>,
    val relatedTerms: List<String>,
    val message: String,
) {
    companion object {
        fun fromJson(payload: String): WritingPlanningData? = runCatching {
            val json = JSONObject(payload)
            WritingPlanningData(
                taskId = json.optString("task_id"),
                topic = json.optString("topic"),
                documentType = json.optString("document_type"),
                academicLevel = json.optString("academic_level"),
                wordCountRange = json.optString("word_count_range"),
                citationFormat = json.optString("citation_format"),
                language = json.optString("language"),
                requirements = json.optJSONArray("requirements")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
                primaryKeywords = json.optJSONArray("primary_keywords")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
                secondaryKeywords = json.optJSONArray("secondary_keywords")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
                searchPhrases = json.optJSONArray("search_phrases")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
                relatedTerms = json.optJSONArray("related_terms")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }
                } ?: emptyList(),
                message = json.optString("message"),
            )
        }.getOrNull()
    }
}

// ── Main composable ───────────────────────────────────────────────────────────

/**
 * Card rendered for `content_type = "writing_planning"` data stream parts.
 *
 * Layout mirrors iOS `WritingPlanningContentView`:
 *  1. Markdown message (above the collapsible card, from the `message` field)
 *  2. Collapsible "Writing Requirements Gathered" section with parameter cards
 *  3. WorkflowProgressSection for live WebSocket progress
 */
@Composable
internal fun WritingPlanningCard(
    data: WritingPlanningData,
    workflowProgress: WorkflowMessage,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    var isExpanded by remember { mutableStateOf(true) }
    val chevronDegrees by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 200),
        label = "writing-chevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = screenHeightDp * 0.85f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Markdown message ──────────────────────────────────────────────────
        if (data.message.isNotBlank()) {
            MarkdownBody(
                text = data.message,
                renderMode = MarkdownRenderMode.Stable,
                onLinkClick = onLinkClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // ── Collapsible card ──────────────────────────────────────────────────
        val isDark = isSystemInDarkTheme()
        val cardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
        val headerBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF9FAFB)
        val borderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB)

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardBg,
            shadowElevation = if (isDark) 4.dp else 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        ) {
            Column {
                // Header row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .background(headerBg)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Writing Requirements Gathered",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF111827),
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280),
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(chevronDegrees),
                    )
                }

                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        // Requirements showcase (indigo gradient)
                        WritingRequirementsShowcase(requirements = data.requirements)

                        // Document type + Academic level
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WritingDropdownCard(
                                label = "Document Type",
                                value = data.documentType.replaceFirstChar { it.uppercaseChar() },
                                modifier = Modifier.weight(1f),
                            )
                            WritingDropdownCard(
                                label = "Academic Level",
                                value = data.academicLevel.replaceFirstChar { it.uppercaseChar() },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Word count + Citation format
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            WritingDropdownCard(
                                label = "Word Count Range",
                                value = data.wordCountRange.ifBlank { "—" },
                                modifier = Modifier.weight(1f),
                            )
                            WritingDropdownCard(
                                label = "Citation Format",
                                value = data.citationFormat.uppercase().ifBlank { "—" },
                                modifier = Modifier.weight(1f),
                            )
                        }

                        // Language
                        WritingDropdownCard(
                            label = "Language",
                            value = languageDisplayName(data.language),
                        )

                        // Primary keywords
                        if (data.primaryKeywords.isNotEmpty()) {
                            KeywordsCard(
                                title = "Primary Keywords",
                                keywords = data.primaryKeywords,
                                chipColor = WritingAccent,
                            )
                        }

                        // Secondary keywords
                        if (data.secondaryKeywords.isNotEmpty()) {
                            KeywordsCard(
                                title = "Secondary Keywords",
                                keywords = data.secondaryKeywords,
                                chipColor = WritingAccent.copy(alpha = 0.75f),
                            )
                        }

                        // Search phrases
                        if (data.searchPhrases.isNotEmpty()) {
                            KeywordsCard(
                                title = "Search Phrases",
                                keywords = data.searchPhrases,
                                chipColor = Color(0xFF8B5CF6),
                            )
                        }

                        // Continue button
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = WritingAccent),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = "Continue",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        // WebSocket progress
                        WorkflowProgressSection(
                            progress = workflowProgress,
                            accentColor = WritingAccent,
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun WritingRequirementsShowcase(requirements: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF6366F1), Color(0xFF4338CA)),
                )
            )
            .padding(16.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Requirements Gathered",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (requirements.isEmpty()) {
                    Text(
                        text = "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                requirements.forEach { req ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f),
                        )
                        Text(
                            text = req,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WritingDropdownCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF8FAFC))
            .border(1.dp, if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF374151),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF1C1C1E) else Color.White)
                    .border(1.dp, if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF111827),
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KeywordsCard(
    title: String,
    keywords: List<String>,
    chipColor: Color,
) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF8FAFC))
            .border(1.dp, if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF374151),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                keywords.forEach { keyword ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(chipColor.copy(alpha = 0.15f))
                            .border(1.dp, chipColor.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = keyword,
                            style = MaterialTheme.typography.labelSmall,
                            color = chipColor,
                        )
                    }
                }
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun languageDisplayName(language: String): String = when (language.lowercase()) {
    "en", "en-gb", "en-us", "english" -> "English"
    "zh-cn", "zh", "chinese" -> "Chinese"
    "zh-tw" -> "Chinese (TW)"
    "ja", "ja-jp", "japanese" -> "Japanese"
    "ko", "ko-kr", "korean" -> "Korean"
    "es", "es-es", "spanish" -> "Spanish"
    "fr", "fr-fr", "french" -> "French"
    "de", "de-de", "german" -> "German"
    "ar", "ar-sa", "arabic" -> "Arabic"
    else -> language.replaceFirstChar { it.uppercaseChar() }.ifBlank { "—" }
}

