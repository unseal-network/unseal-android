/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.model.RoomListItemAction
import io.element.android.features.home.impl.model.RoomListItemActionKind
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ListItemStyle
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomListContextMenu(
    contextMenu: RoomListState.ContextMenu.Shown,
    canReportRoom: Boolean,
    eventSink: (RoomListEvent.ContextMenuEvent) -> Unit,
    onRoomSettingsClick: (roomId: RoomId) -> Unit,
    onReportRoomClick: (roomId: RoomId) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { eventSink(RoomListEvent.HideContextMenu) },
        scrollable = false,
    ) {
        RoomListModalBottomSheetContent(
            contextMenu = contextMenu,
            canReportRoom = canReportRoom,
            onRoomMarkReadClick = {
                eventSink(RoomListEvent.HideContextMenu)
                eventSink(RoomListEvent.MarkAsRead(contextMenu.roomId))
            },
            onRoomMarkUnreadClick = {
                eventSink(RoomListEvent.HideContextMenu)
                eventSink(RoomListEvent.MarkAsUnread(contextMenu.roomId))
            },
            onRoomMuteClick = {
                eventSink(RoomListEvent.HideContextMenu)
                eventSink(RoomListEvent.SetRoomMuted(contextMenu.roomId, true))
            },
            onRoomUnmuteClick = {
                eventSink(RoomListEvent.HideContextMenu)
                eventSink(RoomListEvent.SetRoomMuted(contextMenu.roomId, false))
            },
            onRoomSettingsClick = {
                eventSink(RoomListEvent.HideContextMenu)
                onRoomSettingsClick(contextMenu.roomId)
            },
            onLeaveRoomClick = {
                eventSink(RoomListEvent.HideContextMenu)
                eventSink(RoomListEvent.LeaveRoom(contextMenu.roomId, needsConfirmation = true))
            },
            onFavoriteChange = { isFavorite ->
                eventSink(RoomListEvent.SetRoomIsFavorite(contextMenu.roomId, isFavorite))
            },
            onClearCacheRoomClick = {
                eventSink(RoomListEvent.HideContextMenu)
                eventSink(RoomListEvent.ClearCacheOfRoom(contextMenu.roomId))
            },
            onReportRoomClick = {
                eventSink(RoomListEvent.HideContextMenu)
                onReportRoomClick(contextMenu.roomId)
            },
        )
    }
}

@Composable
private fun RoomListModalBottomSheetContent(
    contextMenu: RoomListState.ContextMenu.Shown,
    canReportRoom: Boolean,
    onRoomSettingsClick: () -> Unit,
    onLeaveRoomClick: () -> Unit,
    onFavoriteChange: (isFavorite: Boolean) -> Unit,
    onRoomMarkReadClick: () -> Unit,
    onRoomMarkUnreadClick: () -> Unit,
    onRoomMuteClick: () -> Unit,
    onRoomUnmuteClick: () -> Unit,
    onClearCacheRoomClick: () -> Unit,
    onReportRoomClick: () -> Unit,
) {
    val renderModel = contextMenu.toHomeRoomRowRenderModel(canReportRoom)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = contextMenu.roomName ?: stringResource(id = CommonStrings.common_no_room_name),
                    style = ElementTheme.typography.fontBodyLgMedium,
                    fontStyle = FontStyle.Italic.takeIf { contextMenu.roomName == null }
                )
            }
        )
        renderModel.actions.contextMenuActions.forEach { action ->
            RoomListActionItem(
                action = action,
                onRoomMarkReadClick = onRoomMarkReadClick,
                onRoomMarkUnreadClick = onRoomMarkUnreadClick,
                onRoomMuteClick = onRoomMuteClick,
                onRoomUnmuteClick = onRoomUnmuteClick,
                onRoomSettingsClick = onRoomSettingsClick,
                onLeaveRoomClick = onLeaveRoomClick,
                onFavoriteChange = { onFavoriteChange(!contextMenu.isFavorite) },
                onClearCacheRoomClick = onClearCacheRoomClick,
                onReportRoomClick = onReportRoomClick,
            )
        }
    }
}

@Composable
private fun RoomListActionItem(
    action: RoomListItemAction,
    onRoomSettingsClick: () -> Unit,
    onLeaveRoomClick: () -> Unit,
    onFavoriteChange: () -> Unit,
    onRoomMarkReadClick: () -> Unit,
    onRoomMarkUnreadClick: () -> Unit,
    onRoomMuteClick: () -> Unit,
    onRoomUnmuteClick: () -> Unit,
    onClearCacheRoomClick: () -> Unit,
    onReportRoomClick: () -> Unit,
) {
    val onClick = when (action.kind) {
        RoomListItemActionKind.MarkAsRead -> onRoomMarkReadClick
        RoomListItemActionKind.MarkAsUnread -> onRoomMarkUnreadClick
        RoomListItemActionKind.Favorite,
        RoomListItemActionKind.Unfavorite -> onFavoriteChange
        RoomListItemActionKind.Mute -> onRoomMuteClick
        RoomListItemActionKind.Unmute -> onRoomUnmuteClick
        RoomListItemActionKind.Settings -> onRoomSettingsClick
        RoomListItemActionKind.Report -> onReportRoomClick
        RoomListItemActionKind.Leave -> onLeaveRoomClick
        RoomListItemActionKind.ClearCache -> onClearCacheRoomClick
        RoomListItemActionKind.Pin,
        RoomListItemActionKind.Unpin,
        RoomListItemActionKind.Archive,
        RoomListItemActionKind.Unarchive -> null
    }
    ListItem(
        headlineContent = {
            Text(
                text = roomListActionText(action.kind),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        enabled = action.enabled,
        onClick = onClick.takeIf { action.enabled },
        leadingContent = ListItemContent.Icon(
            iconSource = IconSource.Vector(roomListActionIcon(action.kind))
        ),
        trailingContent = when (action.kind) {
            RoomListItemActionKind.Favorite -> ListItemContent.Switch(checked = false)
            RoomListItemActionKind.Unfavorite -> ListItemContent.Switch(checked = true)
            else -> null
        },
        style = when (action.kind) {
            RoomListItemActionKind.Report,
            RoomListItemActionKind.Leave -> ListItemStyle.Destructive
            else -> ListItemStyle.Default
        },
    )
}

@Composable
private fun roomListActionText(kind: RoomListItemActionKind): String {
    return when (kind) {
        RoomListItemActionKind.MarkAsRead -> stringResource(id = R.string.screen_roomlist_mark_as_read)
        RoomListItemActionKind.MarkAsUnread -> stringResource(id = R.string.screen_roomlist_mark_as_unread)
        RoomListItemActionKind.Pin -> stringResource(id = CommonStrings.action_pin)
        RoomListItemActionKind.Unpin -> stringResource(id = CommonStrings.action_unpin)
        RoomListItemActionKind.Mute -> stringResource(id = CommonStrings.common_mute)
        RoomListItemActionKind.Unmute -> stringResource(id = CommonStrings.common_unmute)
        RoomListItemActionKind.Favorite -> stringResource(id = CommonStrings.common_favourite)
        RoomListItemActionKind.Unfavorite -> stringResource(id = CommonStrings.common_favourited)
        RoomListItemActionKind.Archive -> stringResource(id = R.string.screen_roomlist_archive)
        RoomListItemActionKind.Unarchive -> stringResource(id = R.string.screen_roomlist_unarchive)
        RoomListItemActionKind.Settings -> stringResource(id = CommonStrings.common_settings)
        RoomListItemActionKind.Report -> stringResource(id = CommonStrings.action_report_room)
        RoomListItemActionKind.Leave -> stringResource(id = CommonStrings.action_leave_room)
        RoomListItemActionKind.ClearCache -> "Clear cache for this room"
    }
}

@Composable
private fun roomListActionIcon(kind: RoomListItemActionKind) = when (kind) {
    RoomListItemActionKind.MarkAsRead -> CompoundIcons.MarkAsRead()
    RoomListItemActionKind.MarkAsUnread -> CompoundIcons.MarkAsUnread()
    RoomListItemActionKind.Pin,
    RoomListItemActionKind.Unpin -> CompoundIcons.Pin()
    RoomListItemActionKind.Mute,
    RoomListItemActionKind.Unmute -> CompoundIcons.NotificationsOffSolid()
    RoomListItemActionKind.Favorite -> CompoundIcons.Favourite()
    RoomListItemActionKind.Unfavorite -> CompoundIcons.FavouriteSolid()
    RoomListItemActionKind.Archive,
    RoomListItemActionKind.Unarchive -> CompoundIcons.ExportArchive()
    RoomListItemActionKind.Settings -> CompoundIcons.Settings()
    RoomListItemActionKind.Report -> CompoundIcons.ChatProblem()
    RoomListItemActionKind.Leave -> CompoundIcons.Leave()
    RoomListItemActionKind.ClearCache -> CompoundIcons.Delete()
}

@PreviewsDayNight
@Composable
internal fun RoomListContextMenuPreview(
    @PreviewParameter(RoomListStateContextMenuShownProvider::class) contextMenu: RoomListState.ContextMenu.Shown
) = ElementPreview {
    RoomListContextMenu(
        contextMenu = contextMenu,
        canReportRoom = true,
        onRoomSettingsClick = {},
        onReportRoomClick = {},
        eventSink = {},
    )
}
