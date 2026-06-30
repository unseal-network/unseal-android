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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.wysiwyg.link.Link
import org.json.JSONObject

/** Parsed fields from a `ppt_planning` data part payload. */
internal data class PptPlanningData(
    val topic: String,
    val numberOfSlides: Int,
    val tone: String,
    val colorPalette: String,
    val language: String,
    val message: String,
    val requirements: List<String>,
    val taskId: String,
) {
    companion object {
        fun fromJson(payload: String): PptPlanningData? = runCatching {
            val json = JSONObject(payload)
            PptPlanningData(
                topic = json.optString("topic"),
                numberOfSlides = json.optInt("number_of_slides"),
                tone = json.optString("tone"),
                colorPalette = json.optString("color_palette"),
                language = json.optString("language"),
                message = json.optString("message"),
                requirements = buildList {
                    val arr = json.optJSONArray("requirements") ?: return@buildList
                    repeat(arr.length()) { add(arr.optString(it)) }
                },
                taskId = json.optString("task_id"),
            )
        }.getOrNull()
    }
}

// ── Color palette data (mirrors iOS PptPlanningModels.swift) ──────────────────

private data class PptColorPalette(
    val name: String,
    val displayName: String,
    val colors: List<Color>,
)

private val PPT_PALETTES = listOf(
    PptColorPalette("blue_professional", "Blue Professional",
        listOf(Color(0xFF2E5BBA), Color(0xFF4A90D9), Color(0xFF7BB3F0), Color(0xFFF8F9FA), Color(0xFF1A1A1A))),
    PptColorPalette("corporate_grey", "Corporate Grey",
        listOf(Color(0xFF5A6C7D), Color(0xFF8B9DC3), Color(0xFFDFE3E8), Color(0xFFF5F7FA), Color(0xFF2C3E50))),
    PptColorPalette("elegant_navy", "Elegant Navy",
        listOf(Color(0xFF1B365D), Color(0xFF2E5984), Color(0xFF4A7C9C), Color(0xFFE8EDF2), Color(0xFF0F1419))),
    PptColorPalette("warm_orange", "Warm Orange",
        listOf(Color(0xFFE67E22), Color(0xFFF39C12), Color(0xFFF8C471), Color(0xFFFEF9E7), Color(0xFF2C1810))),
    PptColorPalette("cool_green", "Cool Green",
        listOf(Color(0xFF27AE60), Color(0xFF52C082), Color(0xFF7FB069), Color(0xFFE8F6F3), Color(0xFF1B4332))),
    PptColorPalette("vibrant_multi", "Vibrant Multi",
        listOf(Color(0xFFE74C3C), Color(0xFF9B59B6), Color(0xFF3498DB), Color(0xFFF1C40F), Color(0xFF2C3E50))),
    PptColorPalette("minimal_monochrome", "Minimal Monochrome",
        listOf(Color(0xFF2C3E50), Color(0xFF34495E), Color(0xFF95A5A6), Color(0xFFECF0F1), Color(0xFF000000))),
    PptColorPalette("modern_purple", "Modern Purple",
        listOf(Color(0xFF6C5CE7), Color(0xFFA29BFE), Color(0xFFDDD6FE), Color(0xFFF8F7FF), Color(0xFF2D3436))),
)

private fun paletteByName(name: String): PptColorPalette =
    PPT_PALETTES.firstOrNull { it.name.equals(name, ignoreCase = true) }
        ?: PPT_PALETTES.firstOrNull { name.replace(" ", "_").contains(it.name, ignoreCase = true) }
        ?: PptColorPalette(name, name.replace('_', ' ').replaceFirstChar { it.uppercaseChar() }, emptyList())

// ── Main composable ───────────────────────────────────────────────────────────

/**
 * Card rendered for `content_type = "ppt_planning"` data stream parts.
 *
 * Layout matches iOS `PptPlanningContentView`:
 *  1. Markdown message (above the collapsible card, from the `message` field)
 *  2. Collapsible "Present Requirements Gathered" section with grid of parameter cards
 *  3. WorkflowProgressSection for live WebSocket progress
 */
@Composable
internal fun PptPlanningCard(
    data: PptPlanningData,
    workflowProgress: WorkflowMessage,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    var isExpanded by remember { mutableStateOf(true) }
    val chevronDegrees by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 200),
        label = "ppt-chevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = screenHeightDp * 0.85f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Markdown message (above the collapsible card) ─────────────────────
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
                        text = "Present Requirements Gathered",
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
                        // Requirements showcase (teal gradient)
                        RequirementsShowcaseCard(requirements = data.requirements)

                        // Slides + Tone
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SlidesDisplayCard(slides = data.numberOfSlides, modifier = Modifier.weight(1f))
                            ToneDisplayCard(tone = data.tone, modifier = Modifier.weight(1f))
                        }

                        // Palette + Language
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PaletteDisplayCard(paletteName = data.colorPalette, modifier = Modifier.weight(1f))
                            LanguageDisplayCard(language = data.language, modifier = Modifier.weight(1f))
                        }

                        // Mindmap entry card
                        MindmapEntryCard()

                        // Continue button
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Button(
                                onClick = {},
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BBA7)),
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

                        // WebSocket progress (when active)
                        WorkflowProgressSection(
                            progress = workflowProgress,
                            accentColor = Color(0xFF00BBA7),
                        )
                    }
                }
            }
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun RequirementsShowcaseCard(requirements: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF00BBA7), Color(0xFF007D5C)),
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
private fun SlidesDisplayCard(slides: Int, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    ParameterCardSurface(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Slides",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF374151),
                )
                Text(
                    text = "$slides",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF111827),
                )
            }
            // Read-only slider
            androidx.compose.material3.Slider(
                value = slides.toFloat(),
                onValueChange = {},
                valueRange = 3f..30f,
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.SliderDefaults.colors(
                    disabledThumbColor = Color(0xFF00BBA7),
                    disabledActiveTrackColor = Color(0xFF00BBA7),
                    disabledInactiveTrackColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFD1D5DB),
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "3", style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF9CA3AF))
                Text(text = "30", style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF9CA3AF))
            }
        }
    }
}

@Composable
private fun ToneDisplayCard(tone: String, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    ParameterCardSurface(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Select presentation tone",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF374151),
            )
            DropdownDisplayBox(
                value = tone.replaceFirstChar { it.uppercaseChar() },
                isDark = isDark,
            )
        }
    }
}

@Composable
private fun PaletteDisplayCard(paletteName: String, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val palette = remember(paletteName) { paletteByName(paletteName) }
    ParameterCardSurface(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Color Palette",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF374151),
            )
            DropdownDisplayBox(value = palette.displayName, isDark = isDark)
            if (palette.colors.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    palette.colors.forEach { color ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(color),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageDisplayCard(language: String, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val displayName = remember(language) {
        when (language.lowercase()) {
            "en", "en-gb", "en-us", "english" -> "English"
            "zh-cn", "zh", "chinese" -> "Chinese"
            "zh-tw" -> "Chinese (TW)"
            "ja", "ja-jp", "japanese" -> "Japanese"
            "ko", "ko-kr", "korean" -> "Korean"
            "es", "es-es", "spanish" -> "Spanish"
            "fr", "fr-fr", "french" -> "French"
            "de", "de-de", "german" -> "German"
            "ar", "ar-sa", "arabic" -> "Arabic"
            else -> language.replaceFirstChar { it.uppercaseChar() }
        }
    }
    ParameterCardSurface(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Select presentation language",
                style = MaterialTheme.typography.labelMedium,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF374151),
            )
            DropdownDisplayBox(value = displayName, isDark = isDark)
        }
    }
}

@Composable
private fun MindmapEntryCard() {
    val isDark = isSystemInDarkTheme()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF8FAFC))
            .border(1.dp, if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Psychology,
            contentDescription = null,
            tint = Color(0xFF00BBA7),
            modifier = Modifier.size(32.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Create Mindmap",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF111827),
            )
            Text(
                text = "Generate visual mind maps from your content",
                style = MaterialTheme.typography.bodySmall,
                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280),
            )
        }
    }
}

// ── Shared helpers ────────────────────────────────────────────────────────────

@Composable
private fun ParameterCardSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFF8FAFC))
            .border(1.dp, if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
            .padding(16.dp),
    ) {
        content()
    }
}

@Composable
private fun DropdownDisplayBox(value: String, isDark: Boolean) {
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

// ── Workflow progress section ─────────────────────────────────────────────────

@Composable
internal fun WorkflowProgressSection(progress: WorkflowMessage, accentColor: Color) {
    when (progress) {
        is WorkflowMessage.Empty -> Unit

        is WorkflowMessage.Progress -> {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (progress.isDone) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color(0xFF2FDB72),
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    WorkflowSpinner(color = accentColor)
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (progress.stage.isNotBlank()) {
                        Text(
                            text = progress.stage,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (progress.message.isNotBlank()) {
                        Text(
                            text = progress.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    progress.totalSlides?.let { total ->
                        Text(
                            text = stringResource(R.string.screen_room_timeline_ppt_total_pages, total),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        is WorkflowMessage.Completed -> {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF2FDB72),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.screen_room_timeline_ppt_completed),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2FDB72),
                )
            }
        }

        is WorkflowMessage.Error -> {
            val fallbackReason = stringResource(R.string.screen_room_timeline_ppt_failed)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = progress.reason.ifBlank { fallbackReason },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
internal fun WorkflowSpinner(color: Color) {
    val transition = rememberInfiniteTransition(label = "workflow-spinner")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "workflow-spinner-alpha",
    )
    Box(
        modifier = Modifier
            .size(14.dp)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}
