/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.RoomNotificationMode

open class RoomListStateContextMenuShownProvider : PreviewParameterProvider<RoomListState.ContextMenu.Shown> {
    override val values: Sequence<RoomListState.ContextMenu.Shown>
        get() = sequenceOf(
            aContextMenuShown(hasNewContent = true),
            aContextMenuShown(isDm = true, isFavorite = true),
            aContextMenuShown(roomName = null)
        )
}

internal fun aContextMenuShown(
    roomName: String? = "aRoom",
    isDm: Boolean = false,
    hasNewContent: Boolean = false,
    isFavorite: Boolean = false,
    userDefinedNotificationMode: RoomNotificationMode? = null,
    isPinned: Boolean = false,
    isArchived: Boolean = false,
) = RoomListState.ContextMenu.Shown(
    roomSummary = aRoomListRoomSummary(
        id = "!aRoom:aDomain",
        name = roomName,
        isDm = isDm,
        isFavorite = isFavorite,
        notificationMode = userDefinedNotificationMode,
        numberOfUnreadMessages = if (hasNewContent) 1 else 0,
        isPinned = isPinned,
        isArchived = isArchived,
    ),
    roomId = RoomId("!aRoom:aDomain"),
    roomName = roomName,
    isDm = isDm,
    hasNewContent = hasNewContent,
    isFavorite = isFavorite,
    userDefinedNotificationMode = userDefinedNotificationMode,
    isPinned = isPinned,
    isArchived = isArchived,
    displayClearRoomCacheAction = false,
)
