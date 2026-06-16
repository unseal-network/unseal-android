/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.model

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import org.junit.Test

class RoomListItemActionsPresentationTest {
    @Test
    fun `default room exposes iOS ordered swipe and context actions`() {
        val model = aRoomListRoomSummary().toActionsPresentation(
            canReportRoom = true,
            displayClearRoomCacheAction = false,
        )

        assertThat(model.swipeActions.map { it.kind }).containsExactly(
            RoomListItemActionKind.Pin,
            RoomListItemActionKind.MarkAsUnread,
            RoomListItemActionKind.Favorite,
        ).inOrder()
        assertThat(model.contextMenuActions.map { it.kind }).containsExactly(
            RoomListItemActionKind.MarkAsUnread,
            RoomListItemActionKind.Pin,
            RoomListItemActionKind.Mute,
            RoomListItemActionKind.Favorite,
            RoomListItemActionKind.Archive,
            RoomListItemActionKind.Settings,
            RoomListItemActionKind.Report,
            RoomListItemActionKind.Leave,
        ).inOrder()
        val enabledByKind = model.contextMenuActions.associate { it.kind to it.enabled }
        assertThat(enabledByKind).containsEntry(RoomListItemActionKind.Pin, false)
        assertThat(enabledByKind).containsEntry(RoomListItemActionKind.Mute, true)
        assertThat(enabledByKind).containsEntry(RoomListItemActionKind.Archive, false)
    }

    @Test
    fun `stateful room actions flip labels to match current state`() {
        val model = aRoomListRoomSummary(
            numberOfUnreadMessages = 2,
            notificationMode = RoomNotificationMode.MUTE,
            isFavorite = true,
            isPinned = true,
            isArchived = true,
        ).toActionsPresentation(
            canReportRoom = false,
            displayClearRoomCacheAction = true,
        )

        assertThat(model.swipeActions.map { it.kind }).containsExactly(
            RoomListItemActionKind.Unpin,
            RoomListItemActionKind.MarkAsRead,
            RoomListItemActionKind.Unfavorite,
        ).inOrder()
        assertThat(model.contextMenuActions.map { it.kind }).containsExactly(
            RoomListItemActionKind.MarkAsRead,
            RoomListItemActionKind.Unpin,
            RoomListItemActionKind.Unmute,
            RoomListItemActionKind.Unfavorite,
            RoomListItemActionKind.Unarchive,
            RoomListItemActionKind.Settings,
            RoomListItemActionKind.Leave,
            RoomListItemActionKind.ClearCache,
        ).inOrder()
        val enabledByKind = model.contextMenuActions.associate { it.kind to it.enabled }
        assertThat(enabledByKind).containsEntry(RoomListItemActionKind.Unpin, false)
        assertThat(enabledByKind).containsEntry(RoomListItemActionKind.Unmute, true)
        assertThat(enabledByKind).containsEntry(RoomListItemActionKind.Unarchive, false)
    }
}
