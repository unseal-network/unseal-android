/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.config

import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule

sealed interface RoomSchedulesEvents {
    data object OnAppear : RoomSchedulesEvents
    data object Refresh : RoomSchedulesEvents
    data class SelectTab(val tab: RoomSchedulesTab) : RoomSchedulesEvents
    data class ShowOnlyMineChanged(val showOnlyMine: Boolean) : RoomSchedulesEvents
    data object CreateSchedule : RoomSchedulesEvents
    data class EditSchedule(val schedule: ChatbotSchedule) : RoomSchedulesEvents
    data class ToggleSchedule(val schedule: ChatbotSchedule) : RoomSchedulesEvents
    data class RequestDeleteSchedule(val schedule: ChatbotSchedule) : RoomSchedulesEvents
    data object ConfirmDeleteSchedule : RoomSchedulesEvents
    data object DismissDeleteConfirmation : RoomSchedulesEvents
    data object RefreshMemory : RoomSchedulesEvents
    data object StartEditingMemory : RoomSchedulesEvents
    data class EditingMemoryChanged(val text: String) : RoomSchedulesEvents
    data object CancelEditingMemory : RoomSchedulesEvents
    data object SaveMemory : RoomSchedulesEvents
    data object ClearError : RoomSchedulesEvents
    data object Dismiss : RoomSchedulesEvents
}
