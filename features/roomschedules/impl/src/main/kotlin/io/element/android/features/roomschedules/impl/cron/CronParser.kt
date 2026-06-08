/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.cron

enum class CronPickerMode {
    Workdays,
    EveryDay,
    EveryNHours,
    EveryHourAtMinute,
    Weekday,
}

data class CronPickerModel(
    val mode: CronPickerMode,
    val hour: Int,
    val minute: Int,
    val intervalHours: Int,
    val weekday: Int,
) {
    companion object {
        val Default = CronPickerModel(
            mode = CronPickerMode.EveryDay,
            hour = 9,
            minute = 0,
            intervalHours = 2,
            weekday = 2,
        )
    }
}

object CronParser {
    fun toReadable(expression: String): String {
        val fields = extractFields(expression)
        if (fields.size < 5) return expression

        val minuteField = fields[0]
        val hourField = fields[1]
        val dayOfWeekField = fields[4]
        if (hourField.startsWith("*/")) {
            val interval = hourField.drop(2)
            val minuteSuffix = if (minuteField == "0") {
                ""
            } else {
                " at :${minuteField.toIntOrNull().orZero().zeroPadded()}"
            }
            return "Every ${interval}h$minuteSuffix"
        }

        val hour = hourField.toIntOrNull().orZero()
        val minute = minuteField.toIntOrNull().orZero()
        val time = "${hour.zeroPadded()}:${minute.zeroPadded()}"
        if (dayOfWeekField == "*" || dayOfWeekField == "?") {
            return "Every day at $time"
        }

        val days = parseDayOfWeek(dayOfWeekField)
        return if (days.isEmpty()) {
            "Every day at $time"
        } else {
            "$days at $time"
        }
    }

    fun toCron(model: CronPickerModel): String {
        return when (model.mode) {
            CronPickerMode.Workdays -> "cron(${model.minute.zeroPadded()} ${model.hour.zeroPadded()} ? * 2-6 *)"
            CronPickerMode.EveryDay -> "cron(${model.minute.zeroPadded()} ${model.hour.zeroPadded()} ? * * *)"
            CronPickerMode.EveryNHours -> "cron(0 */${model.intervalHours.coerceAtLeast(1)} ? * * *)"
            CronPickerMode.EveryHourAtMinute -> "cron(${model.minute.zeroPadded()} */1 ? * * *)"
            CronPickerMode.Weekday -> "cron(${model.minute.zeroPadded()} ${model.hour.zeroPadded()} ? * ${model.weekday} *)"
        }
    }

    fun toPickerModel(expression: String): CronPickerModel {
        val fields = extractFields(expression)
        if (fields.size < 5) return CronPickerModel.Default

        val minuteField = fields[0]
        val hourField = fields[1]
        val dayOfWeekField = fields[4]
        val minute = minuteField.toIntOrNull().orZero()
        val hour = hourField.toIntOrNull() ?: 9

        if (hourField == "*/1" && minute != 0) {
            return CronPickerModel(CronPickerMode.EveryHourAtMinute, hour = hour, minute = minute, intervalHours = 1, weekday = 2)
        }
        if (hourField.startsWith("*/")) {
            val interval = hourField.drop(2).toIntOrNull() ?: 1
            return CronPickerModel(CronPickerMode.EveryNHours, hour = hour, minute = minute, intervalHours = interval, weekday = 2)
        }
        if (dayOfWeekField == "2-6") {
            return CronPickerModel(CronPickerMode.Workdays, hour = hour, minute = minute, intervalHours = 1, weekday = 2)
        }
        if (dayOfWeekField != "*" && dayOfWeekField != "?") {
            return CronPickerModel(CronPickerMode.Weekday, hour = hour, minute = minute, intervalHours = 1, weekday = dayOfWeekField.toIntOrNull() ?: 2)
        }
        return CronPickerModel(CronPickerMode.EveryDay, hour = hour, minute = minute, intervalHours = 1, weekday = 2)
    }

    private fun extractFields(expression: String): List<String> {
        var inner = expression.trim()
        if (inner.lowercase().startsWith("cron(") && inner.endsWith(")")) {
            inner = inner.drop(5).dropLast(1)
        }
        return inner.split(Regex("\\s+")).filter { it.isNotEmpty() }
    }

    private fun parseDayOfWeek(dayOfWeek: String): String {
        val names = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
        if ("-" in dayOfWeek) {
            val parts = dayOfWeek.split("-")
            val from = parts.getOrNull(0)?.toIntOrNull()
            val to = parts.getOrNull(1)?.toIntOrNull()
            if (from != null && to != null && from >= 1 && to <= 7 && from <= to) {
                if (from == 2 && to == 6) return "Mon-Fri"
                return (from..to).mapNotNull { names.getOrNull(it) }.joinToString(", ")
            }
        }
        if ("," in dayOfWeek) {
            return dayOfWeek.split(",")
                .mapNotNull { names.getOrNull(it.toIntOrNull() ?: -1) }
                .joinToString(", ")
        }
        return names.getOrNull(dayOfWeek.toIntOrNull() ?: -1).orEmpty()
    }
}

private fun Int?.orZero() = this ?: 0
private fun Int.zeroPadded(): String = toString().padStart(2, '0')
