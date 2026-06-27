/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl.model

import java.time.Clock
import java.time.ZoneId
import kotlin.math.abs

private const val MICROS_PER_DOLLAR = 1_000_000L
private const val MICROS_PER_CENT = 10_000L

enum class DailyUsageRange(val days: Long) {
    SevenDays(7),
    ThirtyDays(30),
}

enum class CreditsPeriod(val apiValue: String) {
    SevenDays("7d"),
    ThirtyDays("30d"),
    All("all"),
}

enum class UsageRankingTab {
    Agent,
    Model,
}

data class DayRangeEpochSeconds(
    val start: Int,
    val end: Int,
)

fun String.prefixedDollar(): String {
    return when {
        isBlank() -> "$0.00"
        startsWith("$") -> this
        else -> "$$this"
    }
}

fun formatMicrosUsd(micros: String): String {
    val value = micros.toLongOrNull() ?: 0L
    val cents = abs(value) / MICROS_PER_CENT
    val formatted = "$${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    return if (value < 0) "-$formatted" else formatted
}

fun formatMicrosDelta(micros: String): String {
    val value = micros.toLongOrNull() ?: 0L
    val sign = if (value >= 0) "+" else "-"
    return sign + formatMicrosUsd(abs(value).toString())
}

fun isLowBalance(balanceMicros: String): Boolean {
    return (balanceMicros.toLongOrNull() ?: 0L) < MICROS_PER_DOLLAR
}

fun localDayRangeEpochSeconds(
    range: DailyUsageRange,
    clock: Clock = Clock.systemDefaultZone(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): DayRangeEpochSeconds {
    val todayStart = clock.instant().atZone(zoneId).toLocalDate().atStartOfDay(zoneId)
    val start = todayStart.minusDays(range.days - 1)
    val end = todayStart.plusDays(1)
    return DayRangeEpochSeconds(
        start = start.toEpochSecond().toInt(),
        end = end.toEpochSecond().toInt(),
    )
}
