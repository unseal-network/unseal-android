/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ChartGreen = Color(0xFF2E7D32)
private val ChartOrange = Color(0xFFEF6C00)

/** Converts a `usageMicros` string to a USD double. */
internal fun microsToUsd(micros: String): Double = (micros.toLongOrNull() ?: 0L) / 1_000_000.0

/** 14-day mini area+line sparkline shown in the balance card (mirrors iOS `sparklineView`). */
@Composable
internal fun BalanceSparkline(
    values: List<Double>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    Canvas(modifier = modifier) {
        val maxValue = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
        val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
        fun pointY(v: Double) = size.height - (v / maxValue * size.height).toFloat()
        val linePath = Path()
        val areaPath = Path()
        values.forEachIndexed { index, v ->
            val x = index * stepX
            val y = pointY(v)
            if (index == 0) {
                linePath.moveTo(x, y)
                areaPath.moveTo(x, size.height)
                areaPath.lineTo(x, y)
            } else {
                linePath.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
        }
        areaPath.lineTo((values.size - 1) * stepX, size.height)
        areaPath.close()
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(listOf(ChartOrange.copy(alpha = 0.3f), ChartOrange.copy(alpha = 0.05f))),
        )
        drawPath(path = linePath, color = ChartOrange, style = Stroke(width = 3f))
    }
}

/** Daily-usage vertical bar chart with x-axis date labels (mirrors iOS `dailyUsageChart`). */
@Composable
internal fun DailyUsageBarChart(
    buckets: List<Pair<Int, Double>>,
    sevenDayRange: Boolean,
    modifier: Modifier = Modifier,
) {
    val maxValue = (buckets.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(0.0001)
    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            if (buckets.isEmpty()) return@Canvas
            val slot = size.width / buckets.size
            val barWidth = (slot * 0.6f).coerceAtMost(20f)
            buckets.forEachIndexed { index, (_, value) ->
                val barHeight = (value / maxValue * size.height).toFloat()
                val left = index * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    color = ChartGreen,
                    topLeft = Offset(left, size.height - barHeight),
                    size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                )
            }
        }
        // X-axis labels at a stride so they don't overlap.
        val stride = if (sevenDayRange) 1 else 5
        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            buckets.forEachIndexed { index, (epoch, _) ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (index % stride == 0) {
                        Text(
                            text = formatBucketLabel(epoch, sevenDayRange),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/** Small token bar chart shown above the usage ranking (mirrors iOS `miniChart`). */
@Composable
internal fun TokenMiniBarChart(
    values: List<Double>,
    modifier: Modifier = Modifier,
) {
    if (values.isEmpty()) return
    Canvas(modifier = modifier) {
        val maxValue = (values.maxOrNull() ?: 0.0).coerceAtLeast(0.0001)
        val slot = size.width / values.size
        val barWidth = (slot * 0.6f).coerceAtMost(12f)
        values.forEachIndexed { index, v ->
            val barHeight = (v / maxValue * size.height).toFloat()
            val left = index * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = ChartGreen.copy(alpha = 0.7f),
                topLeft = Offset(left, size.height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            )
        }
    }
}

/** iOS-style capsule pill picker used for the small range/period/tab selectors. */
@Composable
internal fun <T> CreditPillPicker(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    // Compact pills for the right-side range/period selectors (mirrors iOS bodyXS sizing); keeps the
    // usage-card header on one line so short labels don't wrap to two lines.
    compact: Boolean = false,
) {
    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                onClick = { onSelect(value) },
                modifier = Modifier
                    .clip(CircleShape),
                shape = CircleShape,
                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = if (compact) 10.dp else 12.dp, vertical = if (compact) 5.dp else 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

private fun formatBucketLabel(epochSeconds: Int, sevenDayRange: Boolean): String {
    val date = Instant.ofEpochSecond(epochSeconds.toLong()).atZone(ZoneId.systemDefault()).toLocalDate()
    val pattern = if (sevenDayRange) "d" else "M/d"
    return date.format(DateTimeFormatter.ofPattern(pattern, Locale.getDefault()))
}

/** Formats an ISO-8601 ledger timestamp as "MMM d, HH:mm" (mirrors iOS `formattedDate`). */
internal fun formatLedgerTimestamp(iso: String): String {
    return runCatching {
        val instant = Instant.parse(iso)
        instant.atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.getDefault()))
    }.getOrDefault(iso)
}
