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
    val topbarTools: List<RoomTopbarToolRenderModel>,
    val attachmentActions: List<RoomAttachmentAction>,
    val scheduleBadge: RoomScheduleMenuBadge?,
    val deviceAgent: RoomDeviceAgent?,
    val isDeviceAgentChatActive: Boolean = false,
    val webhookSummary: RoomWebhookMenuSummary? = null,
    val workingMemory: RoomWorkingMemoryMenuState? = null,
) {
    fun hasTopbarAction(action: RoomTopbarAction): Boolean = topbarActions.contains(action)
    fun hasAttachmentAction(action: RoomAttachmentAction): Boolean = attachmentActions.contains(action)

    companion object {
        val Empty = RoomMenuRenderModel(
            topbarActions = emptyList(),
            topbarTools = emptyList(),
            attachmentActions = emptyList(),
            scheduleBadge = null,
            deviceAgent = null,
            isDeviceAgentChatActive = false,
            webhookSummary = null,
            workingMemory = null,
        )
    }
}

data class RoomTopbarToolRenderModel(
    val action: RoomTopbarAction,
    val isActive: Boolean = false,
    val badgeCount: Int? = null,
    val isEnabled: Boolean = true,
)

data class RoomScheduleMenuBadge(
    val activeScheduleCount: Int,
    val isLoading: Boolean,
    val error: String?,
)

data class RoomWebhookMenuSummary(
    val totalCount: Int,
    val activeCount: Int,
    val isLoading: Boolean,
    val error: String?,
)

data class RoomWorkingMemoryMenuState(
    val hasContent: Boolean,
    val preview: String,
    val isLoading: Boolean,
    val error: String?,
)

enum class RoomTopbarAction {
    Threads,
    Schedules,
    Webhooks,
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
        activeDeviceAgentBoundDeviceId: String? = null,
    ): RoomMenuRenderModel {
        val context = roomUnsealContext.dataOrNull()
        val deviceAgent = context?.deviceAgentInRoom
        val actions = buildList {
            if (context?.hasAgentInRoom == true) {
                add(RoomTopbarAction.Schedules)
                add(RoomTopbarAction.Webhooks)
            }
            if (deviceAgent != null) {
                add(RoomTopbarAction.DeviceAgentChat)
                add(RoomTopbarAction.DeviceAgentTerminal)
            }
        }
        val attachmentActions = buildList {
            add(RoomAttachmentAction.Game)
            if (enableTextFormatting) {
                add(RoomAttachmentAction.TextFormatting)
            }
            add(RoomAttachmentAction.Poll)
            if (canShareLocation) {
                add(RoomAttachmentAction.Location)
            }
            add(RoomAttachmentAction.Files)
            add(RoomAttachmentAction.Gallery)
            add(RoomAttachmentAction.PhotoFromCamera)
            add(RoomAttachmentAction.VideoFromCamera)
        }
        val scheduleBadge = context?.takeIf { it.hasAgentInRoom }?.let {
            RoomScheduleMenuBadge(
                activeScheduleCount = it.activeScheduleCount,
                isLoading = roomUnsealContext.isLoading(),
                error = roomUnsealContext.errorOrNull()?.message,
            )
        }
        val webhookSummary = context?.let {
            RoomWebhookMenuSummary(
                totalCount = it.webhookSummary.totalCount,
                activeCount = it.webhookSummary.activeCount,
                isLoading = roomUnsealContext.isLoading(),
                error = roomUnsealContext.errorOrNull()?.message,
            )
        }
        return RoomMenuRenderModel(
            topbarActions = actions,
            topbarTools = actions.toTopbarTools(
                scheduleBadge = scheduleBadge,
                webhookSummary = webhookSummary,
                isDeviceAgentChatActive = deviceAgent?.boundDeviceId == activeDeviceAgentBoundDeviceId,
            ),
            attachmentActions = attachmentActions,
            scheduleBadge = scheduleBadge,
            deviceAgent = deviceAgent,
            isDeviceAgentChatActive = deviceAgent?.boundDeviceId == activeDeviceAgentBoundDeviceId,
            webhookSummary = webhookSummary,
            workingMemory = context?.let {
                RoomWorkingMemoryMenuState(
                    hasContent = it.workingMemory.isNotBlank(),
                    preview = it.workingMemory.toWorkingMemoryPreview(),
                    isLoading = roomUnsealContext.isLoading(),
                    error = roomUnsealContext.errorOrNull()?.message,
                )
            },
        )
    }
}

private fun List<RoomTopbarAction>.toTopbarTools(
    scheduleBadge: RoomScheduleMenuBadge?,
    webhookSummary: RoomWebhookMenuSummary?,
    isDeviceAgentChatActive: Boolean,
): List<RoomTopbarToolRenderModel> {
    val actionSet = toSet()
    return buildList {
        if (RoomTopbarAction.DeviceAgentTerminal in actionSet) {
            add(RoomTopbarToolRenderModel(action = RoomTopbarAction.DeviceAgentTerminal))
        }
        if (RoomTopbarAction.DeviceAgentChat in actionSet) {
            add(RoomTopbarToolRenderModel(action = RoomTopbarAction.DeviceAgentChat, isActive = isDeviceAgentChatActive))
        }
        if (RoomTopbarAction.Webhooks in actionSet) {
            add(RoomTopbarToolRenderModel(action = RoomTopbarAction.Webhooks, badgeCount = webhookSummary?.activeCount?.takeIf { it > 0 }))
        }
        if (RoomTopbarAction.Schedules in actionSet) {
            add(RoomTopbarToolRenderModel(action = RoomTopbarAction.Schedules, badgeCount = scheduleBadge?.activeScheduleCount?.takeIf { it > 0 }))
        }
    }
}

private fun String.toWorkingMemoryPreview(maxLength: Int = 120): String {
    val collapsed = trim().replace(WhitespaceRegex, " ")
    return if (collapsed.length <= maxLength) collapsed else collapsed.take(maxLength).trimEnd() + "..."
}

private val WhitespaceRegex = Regex("\\s+")
