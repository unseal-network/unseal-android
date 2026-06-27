/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

class CreditFormattersTest {
    @Test
    fun `prefixedDollar preserves existing dollars and adds missing prefix`() {
        assertThat("0.00".prefixedDollar()).isEqualTo("$0.00")
        assertThat("$4.50".prefixedDollar()).isEqualTo("$4.50")
        assertThat("".prefixedDollar()).isEqualTo("$0.00")
    }

    @Test
    fun `formatMicrosUsd converts micros to dollars`() {
        assertThat(formatMicrosUsd("0")).isEqualTo("$0.00")
        assertThat(formatMicrosUsd("1500000")).isEqualTo("$1.50")
        assertThat(formatMicrosUsd("123456789")).isEqualTo("$123.45")
        assertThat(formatMicrosUsd("invalid")).isEqualTo("$0.00")
    }

    @Test
    fun `formatMicrosDelta shows explicit signs`() {
        assertThat(formatMicrosDelta("-250000")).isEqualTo("-$0.25")
        assertThat(formatMicrosDelta("250000")).isEqualTo("+$0.25")
        assertThat(formatMicrosDelta("0")).isEqualTo("+$0.00")
    }

    @Test
    fun `isLowBalance matches iOS one dollar threshold`() {
        assertThat(isLowBalance("999999")).isTrue()
        assertThat(isLowBalance("1000000")).isFalse()
        assertThat(isLowBalance("invalid")).isTrue()
    }

    @Test
    fun `credits periods use analytics api values`() {
        assertThat(CreditsPeriod.SevenDays.apiValue).isEqualTo("7d")
        assertThat(CreditsPeriod.ThirtyDays.apiValue).isEqualTo("30d")
        assertThat(CreditsPeriod.All.apiValue).isEqualTo("all")
    }

    @Test
    fun `daily usage range uses local start of day through tomorrow`() {
        val clock = Clock.fixed(Instant.parse("2026-06-09T12:34:56Z"), ZoneId.of("UTC"))

        assertThat(localDayRangeEpochSeconds(DailyUsageRange.SevenDays, clock, ZoneId.of("UTC")))
            .isEqualTo(DayRangeEpochSeconds(start = 1_780_444_800, end = 1_781_049_600))
        assertThat(localDayRangeEpochSeconds(DailyUsageRange.ThirtyDays, clock, ZoneId.of("UTC")))
            .isEqualTo(DayRangeEpochSeconds(start = 1_778_457_600, end = 1_781_049_600))
    }
}
