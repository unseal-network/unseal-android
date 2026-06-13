/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import io.element.android.libraries.architecture.AsyncData

data class RoomMenuRenderModel(
    val topbarActions: List<RoomTopbarAction>,
    val scheduleBadge: RoomScheduleMenuBadge?,
    val deviceAgent: RoomDeviceAgent?,
) {
    fun hasTopbarAction(action: RoomTopbarAction): Boolean = topbarActions.contains(action)

    companion object {
        val Empty = RoomMenuRenderModel(
            topbarActions = emptyList(),
            scheduleBadge = null,
            deviceAgent = null,
        )
    }
}

data class RoomScheduleMenuBadge(
    val activeScheduleCount: Int,
    val isLoading: Boolean,
    val error: String?,
)

enum class RoomTopbarAction {
    Threads,
    Schedules,
    DeviceAgentChat,
    DeviceAgentTerminal,
}

object RoomMenuReducer {
    fun reduce(
        roomUnsealContext: AsyncData<RoomUnsealContext>,
        hasThreads: Boolean,
        isThreadTimeline: Boolean,
    ): RoomMenuRenderModel {
        val context = roomUnsealContext.dataOrNull()
        val actions = buildList {
            if (!isThreadTimeline && hasThreads) {
                add(RoomTopbarAction.Threads)
            }
            if (context?.hasAgentInRoom == true) {
                add(RoomTopbarAction.Schedules)
            }
            if (context?.deviceAgentInRoom != null) {
                add(RoomTopbarAction.DeviceAgentChat)
                add(RoomTopbarAction.DeviceAgentTerminal)
            }
        }
        return RoomMenuRenderModel(
            topbarActions = actions,
            scheduleBadge = context?.takeIf { it.hasAgentInRoom }?.let {
                RoomScheduleMenuBadge(
                    activeScheduleCount = it.activeScheduleCount,
                    isLoading = roomUnsealContext.isLoading(),
                    error = roomUnsealContext.errorOrNull()?.message,
                )
            },
            deviceAgent = context?.deviceAgentInRoom,
        )
    }
}
