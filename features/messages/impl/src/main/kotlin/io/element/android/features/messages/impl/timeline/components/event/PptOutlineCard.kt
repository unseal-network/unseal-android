/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.R
import io.element.android.wysiwyg.link.Link
import org.json.JSONObject

/** Parsed fields from a `ppt_outline_v2` data part payload. */
internal data class PptOutlineData(
    val taskId: String,
    val presentationTitle: String,
    val totalSlides: Int,
    val slides: List<PptSlideData>,
    val message: String,
) {
    companion object {
        fun fromJson(payload: String): PptOutlineData? = runCatching {
            val json = JSONObject(payload)
            PptOutlineData(
                taskId = json.optString("task_id"),
                presentationTitle = json.optString("presentation_title"),
                totalSlides = json.optInt("total_slides"),
                message = json.optString("message"),
                slides = buildList {
                    val arr = json.optJSONArray("slides") ?: return@buildList
                    repeat(arr.length()) { i ->
                        val s = arr.optJSONObject(i) ?: return@repeat
                        add(
                            PptSlideData(
                                slideNumber = s.optInt("slide_number"),
                                title = s.optString("title"),
                                description = s.optString("description"),
                                contentType = s.optString("content_type"),
                                includeImage = s.optBoolean("include_image"),
                                keyPoints = buildList {
                                    val kp = s.optJSONArray("key_points") ?: return@buildList
                                    repeat(kp.length()) { j -> add(kp.optString(j)) }
                                },
                            )
                        )
                    }
                },
            )
        }.getOrNull()
    }
}

internal data class PptSlideData(
    val slideNumber: Int,
    val title: String,
    val description: String,
    val contentType: String,  // "cover" | "content" | "end"
    val includeImage: Boolean,
    val keyPoints: List<String>,
)

private fun slideTypeColor(contentType: String): Color = when (contentType) {
    "cover" -> Color(0xFF3B82F6)
    "end"   -> Color(0xFF10B981)
    else    -> Color(0xFF55B99F)
}

@Composable
private fun slideTypeLabel(contentType: String): String = when (contentType) {
    "cover" -> stringResource(R.string.screen_room_timeline_ppt_slide_type_cover)
    "end" -> stringResource(R.string.screen_room_timeline_ppt_slide_type_end)
    else -> stringResource(R.string.screen_room_timeline_ppt_slide_type_content)
}

/**
 * Card rendered for `content_type = "ppt_outline_v2"` data stream parts.
 *
 * Layout mirrors iOS `PptOutlineV2ElementView`:
 *  1. Markdown message above the card (from the `message` field, if present)
 *  2. Collapsible header: presentation title + "N/M slides" subtitle + chevron
 *  3. Scrollable list (max 400dp): document title card + per-slide `SlideOutlineCard`
 *  4. Action buttons: Edit (outline) + Continue (teal)
 */
@Composable
internal fun PptOutlineCard(
    data: PptOutlineData,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpanded by remember { mutableStateOf(true) }
    val chevronDegrees by animateFloatAsState(
        targetValue = if (isExpanded) 0f else -90f,
        animationSpec = tween(durationMillis = 250),
        label = "ppt-outline-chevron",
    )
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val headerBg = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF9FAFB)
    val borderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB)
    val tealColor = Color(0xFF55B99F)
    val defaultTitle = stringResource(R.string.screen_room_timeline_ppt_default_title)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (data.message.isNotBlank()) {
            MarkdownBody(
                text = data.message,
                renderMode = MarkdownRenderMode.Stable,
                onLinkClick = onLinkClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardBg,
            shadowElevation = if (isDark) 4.dp else 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(12.dp)),
        ) {
            Column {
                // Collapsible header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded }
                        .background(headerBg)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = data.presentationTitle.ifBlank { defaultTitle },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF111827),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${data.slides.size}/${data.totalSlides} slides",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280),
                        )
                    }
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
                    // Scrollable slide list (max 400dp, mirrors iOS maxHeight: 400)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        data.slides.forEach { slide ->
                            SlideOutlineCard(slide = slide, isDark = isDark)
                        }
                    }

                    HorizontalDivider(color = borderColor)

                    // Action buttons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, tealColor),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = tealColor),
                        ) {
                            Text(
                                text = stringResource(R.string.screen_room_timeline_ppt_edit),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = tealColor),
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.screen_room_timeline_ppt_continue),
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
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
                }
            }
        }
    }
}

@Composable
private fun SlideOutlineCard(slide: PptSlideData, isDark: Boolean) {
    val themeColor = slideTypeColor(slide.contentType)
    val typeLabel = slideTypeLabel(slide.contentType)
    val cardBg = if (isDark) Color(0xFF2C2C2E) else Color.White
    val borderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE2E8F0)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Numbered badge + type pill + image icon
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(themeColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = slide.slideNumber.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50.dp))
                        .background(themeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = themeColor,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (slide.includeImage) {
                    Icon(
                        imageVector = Icons.Outlined.Image,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF8E8E93) else Color(0xFF9CA3AF),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Slide title
            Text(
                text = slide.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1A1A1A),
            )

            // Description
            if (slide.description.isNotBlank()) {
                Text(
                    text = slide.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) Color(0xFF8E8E93) else Color(0xFF4A5568),
                )
            }

            // Key points with colored dot bullets
            if (slide.keyPoints.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    slide.keyPoints.forEach { point ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .size(5.dp)
                                    .background(themeColor, CircleShape),
                            )
                            Text(
                                text = point,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) Color(0xFF8E8E93) else Color(0xFF4A5568),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
