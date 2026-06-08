/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

import io.element.android.features.roomschedules.impl.cron.CronPickerModel

sealed interface ScheduleEditEvents {
    data object OnAppear : ScheduleEditEvents
    data class NameChanged(val name: String) : ScheduleEditEvents
    data class AgentChanged(val botName: String) : ScheduleEditEvents
    data class ActionChanged(val action: String) : ScheduleEditEvents
    data class CronModelChanged(val model: CronPickerModel) : ScheduleEditEvents
    data object Submit : ScheduleEditEvents
    data object Cancel : ScheduleEditEvents
    data object ClearError : ScheduleEditEvents
}
