/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.model

import io.element.android.libraries.matrix.api.room.RoomNotificationMode

data class RoomListItemActionsPresentation(
    val swipeActions: List<RoomListItemAction>,
    val contextMenuActions: List<RoomListItemAction>,
)

data class RoomListItemAction(
    val kind: RoomListItemActionKind,
    val enabled: Boolean = true,
)

enum class RoomListItemActionKind {
    MarkAsRead,
    MarkAsUnread,
    Pin,
    Unpin,
    Mute,
    Unmute,
    Favorite,
    Unfavorite,
    Archive,
    Unarchive,
    Settings,
    Report,
    Leave,
    ClearCache,
}

fun RoomListRoomSummary.toActionsPresentation(
    canReportRoom: Boolean,
    displayClearRoomCacheAction: Boolean,
): RoomListItemActionsPresentation {
    return roomListItemActionsPresentation(
        hasNewContent = hasNewContent,
        isPinned = isPinned,
        userDefinedNotificationMode = userDefinedNotificationMode,
        isFavorite = isFavorite,
        isArchived = isArchived,
        canReportRoom = canReportRoom,
        displayClearRoomCacheAction = displayClearRoomCacheAction,
    )
}

fun roomListItemActionsPresentation(
    hasNewContent: Boolean,
    isPinned: Boolean,
    userDefinedNotificationMode: RoomNotificationMode?,
    isFavorite: Boolean,
    isArchived: Boolean,
    canReportRoom: Boolean,
    displayClearRoomCacheAction: Boolean,
): RoomListItemActionsPresentation {
    val readAction = if (hasNewContent) RoomListItemActionKind.MarkAsRead else RoomListItemActionKind.MarkAsUnread
    val pinAction = if (isPinned) RoomListItemActionKind.Unpin else RoomListItemActionKind.Pin
    val muteAction = if (userDefinedNotificationMode == RoomNotificationMode.MUTE) RoomListItemActionKind.Unmute else RoomListItemActionKind.Mute
    val favoriteAction = if (isFavorite) RoomListItemActionKind.Unfavorite else RoomListItemActionKind.Favorite
    val archiveAction = if (isArchived) RoomListItemActionKind.Unarchive else RoomListItemActionKind.Archive

    return RoomListItemActionsPresentation(
        swipeActions = listOf(
            RoomListItemAction(pinAction, enabled = false),
            RoomListItemAction(readAction),
            RoomListItemAction(favoriteAction),
        ),
        contextMenuActions = buildList {
            add(RoomListItemAction(readAction))
            add(RoomListItemAction(pinAction, enabled = false))
            add(RoomListItemAction(muteAction))
            add(RoomListItemAction(favoriteAction))
            add(RoomListItemAction(archiveAction, enabled = false))
            add(RoomListItemAction(RoomListItemActionKind.Settings))
            if (canReportRoom) {
                add(RoomListItemAction(RoomListItemActionKind.Report))
            }
            add(RoomListItemAction(RoomListItemActionKind.Leave))
            if (displayClearRoomCacheAction) {
                add(RoomListItemAction(RoomListItemActionKind.ClearCache))
            }
        },
    )
}
