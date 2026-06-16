/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.config

import io.element.android.features.roomschedules.impl.model.isEnabled
import io.element.android.features.roomschedules.impl.model.stableId
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

enum class RoomSchedulesTab {
    Schedules,
    WorkingMemory,
}

data class RoomSchedulesState(
    val roomId: RoomId,
    val roomName: String,
    val currentUserId: String,
    val selectedTab: RoomSchedulesTab = RoomSchedulesTab.Schedules,
    val schedules: ImmutableList<ChatbotSchedule> = emptyList<ChatbotSchedule>().toImmutableList(),
    val isLoadingSchedules: Boolean = false,
    val showOnlyMine: Boolean = false,
    val scheduleError: String? = null,
    val workingMemory: String = "",
    val editingMemoryText: String = "",
    val isEditingMemory: Boolean = false,
    val isLoadingMemory: Boolean = false,
    val isSavingMemory: Boolean = false,
    val canEditMemory: Boolean = true,
    val memoryError: String? = null,
    val deleteConfirmationScheduleId: String? = null,
    val eventSink: (RoomSchedulesEvents) -> Unit,
) {
    val displayedSchedules: List<ChatbotSchedule> = schedules
        .filter { it.isEnabled() || it.creatorId == currentUserId }
        .filter { !showOnlyMine || it.creatorId == currentUserId }
        .sortedWith(
            compareByDescending<ChatbotSchedule> { it.isEnabled() }
                .thenBy { it.name.lowercase() }
                .thenBy { it.stableId() }
        )

    val scheduleItems: ImmutableList<ScheduleRenderModel> = schedules.toScheduleRenderModels(
        currentUserId = currentUserId,
        showOnlyMine = showOnlyMine,
    )

    val activeCount: Int = scheduleItems.count { it.isEnabled }
    val deleteConfirmationSchedule: ChatbotSchedule? = schedules.firstOrNull { it.stableId() == deleteConfirmationScheduleId }
    val deleteConfirmationScheduleItem: ScheduleRenderModel? = scheduleItems.firstOrNull { it.id == deleteConfirmationScheduleId }
}
