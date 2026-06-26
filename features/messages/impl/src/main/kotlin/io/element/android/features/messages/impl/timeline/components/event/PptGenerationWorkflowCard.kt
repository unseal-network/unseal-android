/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject

internal data class PptGenerationWorkflowData(
    val taskId: String,
    val totalSlides: Int,
    val websocketUrl: String,
    val status: String,
) {
    companion object {
        fun fromJson(data: JSONObject): PptGenerationWorkflowData? = runCatching {
            val taskId = data.optString("task_id").takeIf { it.isNotBlank() } ?: return@runCatching null
            PptGenerationWorkflowData(
                taskId = taskId,
                totalSlides = data.optInt("total_slides").takeIf { it > 0 } ?: 1,
                websocketUrl = data.optString("websocket_url"),
                status = data.optString("status"),
            )
        }.getOrNull()
    }
}

@Composable
internal fun PptGenerationWorkflowCard(
    data: PptGenerationWorkflowData,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1C1C1E) else Color.White
    val borderColor = if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB)
    val textPrimary = if (isDark) Color(0xFFF2F2F7) else Color(0xFF111827)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF6B7280)
    val tealColor = Color(0xFF55B99F)

    val isGenerating = data.status != "completed" && data.status != "complete"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header row: icon + title + bouncing dots
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PptPresentationIcon(tint = Color(0xFF60A5FA))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (isGenerating) "正在生成演示文稿" else "演示文稿已生成",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )
                    if (isGenerating) {
                        BouncingDots(color = textSecondary)
                    }
                }
                Text(
                    text = "0/${data.totalSlides} slides ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = textSecondary,
                )
            }
        }

        // Progress bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (isDark) Color(0xFF3A3A3C) else Color(0xFFE5E7EB)),
        ) {
            if (isGenerating) {
                IndeterminateShimmerBar(tealColor)
            }
        }

        // Slide cards row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(data.totalSlides, key = { it }) { index ->
                ShimmerSlideCard(index = index, isDark = isDark)
            }
        }
    }
}

@Composable
private fun PptPresentationIcon(tint: Color) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        // Simple grid icon matching the web SVG: rect with inner lines
        Column(
            modifier = Modifier.size(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(tint),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(tint.copy(alpha = 0.4f)),
                )
            }
        }
    }
}

@Composable
private fun BouncingDots(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing-dots")
    val offsets = listOf(0, 200, 400).map { delay ->
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600, delayMillis = delay, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dot-bounce-$delay",
        )
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        offsets.forEach { offset ->
            val y by offset
            Box(
                modifier = Modifier
                    .offset(y = y.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun IndeterminateShimmerBar(color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "progress-shimmer")
    val progress by infiniteTransition.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress-pos",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth(0.35f.coerceAtLeast(progress.coerceIn(0f, 1f)))
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(color),
    )
}

@Composable
private fun ShimmerSlideCard(index: Int, isDark: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "slide-shimmer-$index")
    val shimmerX by infiniteTransition.animateFloat(
        initialValue = -600f,
        targetValue = 600f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1800,
                delayMillis = index * 80,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer-x-$index",
    )

    val shimmerBase = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF0F4F8)
    val shimmerHighlight = Color(0xFF60A5FA).copy(alpha = if (isDark) 0.18f else 0.22f)

    val shimmerBrush = Brush.linearGradient(
        colors = listOf(shimmerBase, shimmerHighlight, shimmerBase),
        start = Offset(shimmerX, 0f),
        end = Offset(shimmerX + 300f, 144f),
    )

    val slideNumberBadgeColor = Color(0xFF60A5FA).copy(alpha = 0.85f)

    Box(
        modifier = Modifier
            .width(192.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(10.dp))
            .background(shimmerBrush),
    ) {
        // Slide number badge
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(slideNumberBadgeColor)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                text = (index + 1).toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        // Skeleton content lines
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) { lineIndex ->
                val widthFraction = when (lineIndex) {
                    0 -> 0.65f
                    1 -> 0.8f
                    else -> 0.5f
                }
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth(widthFraction)
                        .height(5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.45f)),
                )
            }
        }

        // Bottom bouncing dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            listOf(0, 200, 400).forEachIndexed { dotIndex, delay ->
                val dotInfinite = rememberInfiniteTransition(label = "dot-$index-$dotIndex")
                val dotY by dotInfinite.animateFloat(
                    initialValue = 0f,
                    targetValue = -4f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(500, delayMillis = delay, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "dot-y-$index-$dotIndex",
                )
                Box(
                    modifier = Modifier
                        .offset(y = dotY.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF60A5FA)),
                )
            }
        }
    }
}
