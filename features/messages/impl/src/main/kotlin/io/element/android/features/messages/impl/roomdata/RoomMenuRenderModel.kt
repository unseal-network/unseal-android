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
    val attachmentActions: List<RoomAttachmentAction>,
    val scheduleBadge: RoomScheduleMenuBadge?,
    val deviceAgent: RoomDeviceAgent?,
) {
    fun hasTopbarAction(action: RoomTopbarAction): Boolean = topbarActions.contains(action)
    fun hasAttachmentAction(action: RoomAttachmentAction): Boolean = attachmentActions.contains(action)

    companion object {
        val Empty = RoomMenuRenderModel(
            topbarActions = emptyList(),
            attachmentActions = emptyList(),
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

enum class RoomAttachmentAction {
    PhotoFromCamera,
    VideoFromCamera,
    Gallery,
    Files,
    Location,
    Poll,
    Game,
    TextFormatting,
}

object RoomMenuReducer {
    fun reduce(
        roomUnsealContext: AsyncData<RoomUnsealContext>,
        hasThreads: Boolean,
        isThreadTimeline: Boolean,
        canShareLocation: Boolean = false,
        enableTextFormatting: Boolean = false,
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
        val attachmentActions = buildList {
            add(RoomAttachmentAction.PhotoFromCamera)
            add(RoomAttachmentAction.VideoFromCamera)
            add(RoomAttachmentAction.Gallery)
            add(RoomAttachmentAction.Files)
            if (canShareLocation) {
                add(RoomAttachmentAction.Location)
            }
            add(RoomAttachmentAction.Poll)
            add(RoomAttachmentAction.Game)
            if (enableTextFormatting) {
                add(RoomAttachmentAction.TextFormatting)
            }
        }
        return RoomMenuRenderModel(
            topbarActions = actions,
            attachmentActions = attachmentActions,
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
