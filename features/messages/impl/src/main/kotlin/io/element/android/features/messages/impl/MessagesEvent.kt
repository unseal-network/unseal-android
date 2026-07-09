/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.roomdata.RoomDeviceAgent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.EventOrTransactionId
import io.element.android.libraries.matrix.api.user.MatrixUser

sealed interface MessagesEvent {
    data class HandleAction(val action: TimelineItemAction, val event: TimelineItem.Event) : MessagesEvent
    data class ToggleReaction(val emoji: String, val eventOrTransactionId: EventOrTransactionId) : MessagesEvent
    data class InviteDialogDismissed(val action: InviteDialogAction) : MessagesEvent
    data class OnUserClicked(val user: MatrixUser) : MessagesEvent
    data object StopLiveLocationShare : MessagesEvent
    data object ShowLiveLocationShare : MessagesEvent
    data object MarkAsFullyReadAndExit : MessagesEvent
    data class ToggleDeviceAgentChat(val deviceAgent: RoomDeviceAgent) : MessagesEvent
    data class OpenDeviceAgentTerminal(val deviceAgent: RoomDeviceAgent) : MessagesEvent
    data object OpenDeviceAgentTerminalSession : MessagesEvent
    data class UpdateDeviceAgentTerminalInput(val text: String) : MessagesEvent
    data object SendDeviceAgentTerminalInput : MessagesEvent
    data object CloseDeviceAgentTerminalSession : MessagesEvent
    data object DismissDeviceAgentTerminal : MessagesEvent
    data object DismissSelectableMessageText : MessagesEvent
}

enum class InviteDialogAction {
    Cancel,
    Invite,
}
