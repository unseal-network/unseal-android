/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

/**
 * Card rendered for `content_type = "ppt_planning"` data stream parts.
 *
 * Shows the PPT planning specification (topic, meta chips, message, requirements) and a
 * live progress section driven by [workflowProgress] from [WorkflowProgressManager].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PptPlanningCard(
    data: PptPlanningData,
    workflowProgress: WorkflowMessage,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val cardShape = RoundedCornerShape(16.dp)
    val accentColor = if (isDark) Color(0xFF4A90D9) else Color(0xFF1A5FAD)
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    Surface(
        shape = cardShape,
        color = if (isDark) Color(0xFF111820) else Color.White,
        tonalElevation = if (isDark) 2.dp else 1.dp,
        shadowElevation = if (isDark) 10.dp else 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = screenHeightDp * 0.65f)
            .border(
                width = 0.7.dp,
                color = accentColor.copy(alpha = if (isDark) 0.22f else 0.18f),
                shape = cardShape,
            ),
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Topic ──────────────────────────────────────────────────────────────
            if (data.topic.isNotBlank()) {
                Text(
                    text = data.topic,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // ── Meta chips (wrap automatically) ───────────────────────────────────
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (data.numberOfSlides > 0) MetaChip("${data.numberOfSlides}页", accentColor)
                if (data.tone.isNotBlank()) MetaChip(data.tone, accentColor)
                if (data.colorPalette.isNotBlank()) MetaChip(data.colorPalette, accentColor)
                if (data.language.isNotBlank()) MetaChip(data.language, accentColor)
            }

            // ── Message (markdown) ────────────────────────────────────────────────
            if (data.message.isNotBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = data.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ── Requirements list ─────────────────────────────────────────────────
            if (data.requirements.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Text(
                    text = "核心要求",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    data.requirements.forEachIndexed { index, req ->
                        RequirementRow(index = index + 1, text = req, accentColor = accentColor)
                    }
                }
            }

            // ── WebSocket progress ────────────────────────────────────────────────
            WorkflowProgressSection(progress = workflowProgress, accentColor = accentColor)
        }
    }
}

@Composable
private fun MetaChip(label: String, accentColor: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(accentColor.copy(alpha = 0.10f))
            .border(0.5.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
        )
    }
}

@Composable
private fun RequirementRow(index: Int, text: String, accentColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(accentColor.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accentColor,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun WorkflowProgressSection(progress: WorkflowMessage, accentColor: Color) {
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
                            text = "共 $total 页",
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
                    text = "生成完成",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2FDB72),
                )
            }
        }

        is WorkflowMessage.Error -> {
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
                    text = progress.reason.ifBlank { "生成失败" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun WorkflowSpinner(color: Color) {
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
