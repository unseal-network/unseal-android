/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.cron

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CronParserTest {
    @Test
    fun `toCron - writes all iOS supported modes`() {
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.Workdays, hour = 9, minute = 5, intervalHours = 2, weekday = 2)))
            .isEqualTo("cron(05 09 ? * 2-6 *)")
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.EveryDay, hour = 9, minute = 0, intervalHours = 2, weekday = 2)))
            .isEqualTo("cron(00 09 ? * * *)")
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.EveryNHours, hour = 9, minute = 5, intervalHours = 6, weekday = 2)))
            .isEqualTo("cron(0 */6 ? * * *)")
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.EveryHourAtMinute, hour = 9, minute = 15, intervalHours = 1, weekday = 2)))
            .isEqualTo("cron(15 */1 ? * * *)")
        assertThat(CronParser.toCron(CronPickerModel(mode = CronPickerMode.Weekday, hour = 18, minute = 30, intervalHours = 1, weekday = 1)))
            .isEqualTo("cron(30 18 ? * 1 *)")
    }

    @Test
    fun `toReadable - formats iOS supported expressions`() {
        assertThat(CronParser.toReadable("cron(0 9 ? * 2-6 *)")).isEqualTo("Mon-Fri at 09:00")
        assertThat(CronParser.toReadable("cron(15 */6 ? * * *)")).isEqualTo("Every 6h at :15")
        assertThat(CronParser.toReadable("cron(0 */2 ? * * *)")).isEqualTo("Every 2h")
        assertThat(CronParser.toReadable("cron(30 18 ? * * *)")).isEqualTo("Every day at 18:30")
        assertThat(CronParser.toReadable("cron(30 18 ? * 1,3,5 *)")).isEqualTo("Mon, Wed, Fri at 18:30")
        assertThat(CronParser.toReadable("not-cron")).isEqualTo("not-cron")
    }

    @Test
    fun `toPickerModel - parses iOS supported expressions and falls back to default`() {
        assertThat(CronParser.toPickerModel("cron(0 9 ? * 2-6 *)")).isEqualTo(
            CronPickerModel(mode = CronPickerMode.Workdays, hour = 9, minute = 0, intervalHours = 1, weekday = 2)
        )
        assertThat(CronParser.toPickerModel("cron(0 */6 ? * * *)")).isEqualTo(
            CronPickerModel(mode = CronPickerMode.EveryNHours, hour = 9, minute = 0, intervalHours = 6, weekday = 2)
        )
        assertThat(CronParser.toPickerModel("cron(15 */1 ? * * *)")).isEqualTo(
            CronPickerModel(mode = CronPickerMode.EveryHourAtMinute, hour = 9, minute = 15, intervalHours = 1, weekday = 2)
        )
        assertThat(CronParser.toPickerModel("cron(30 18 ? * 1 *)")).isEqualTo(
            CronPickerModel(mode = CronPickerMode.Weekday, hour = 18, minute = 30, intervalHours = 1, weekday = 1)
        )
        assertThat(CronParser.toPickerModel("cron(30 18 ? * * *)")).isEqualTo(
            CronPickerModel(mode = CronPickerMode.EveryDay, hour = 18, minute = 30, intervalHours = 1, weekday = 2)
        )
        assertThat(CronParser.toPickerModel("bad")).isEqualTo(CronPickerModel.Default)
    }
}
