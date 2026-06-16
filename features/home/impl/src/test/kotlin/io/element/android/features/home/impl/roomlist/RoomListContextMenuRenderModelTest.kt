/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.roomlist

import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.model.RoomListItemActionKind
import io.element.android.libraries.matrix.api.room.RoomNotificationMode
import org.junit.Test

class RoomListContextMenuRenderModelTest {
    @Test
    fun `context menu render model exposes iOS ordered actions`() {
        val renderModel = aContextMenuShown(
            hasNewContent = true,
            isFavorite = true,
            userDefinedNotificationMode = RoomNotificationMode.MUTE,
            isPinned = true,
            isArchived = true,
        ).toHomeRoomRowRenderModel(canReportRoom = true)

        assertThat(renderModel.actions.contextMenuActions.map { it.kind }).containsExactly(
            RoomListItemActionKind.MarkAsRead,
            RoomListItemActionKind.Unpin,
            RoomListItemActionKind.Unmute,
            RoomListItemActionKind.Unfavorite,
            RoomListItemActionKind.Unarchive,
            RoomListItemActionKind.Settings,
            RoomListItemActionKind.Report,
            RoomListItemActionKind.Leave,
        ).inOrder()
    }

    @Test
    fun `context menu render model preserves developer clear cache extension`() {
        val renderModel = aContextMenuShown().copy(
            displayClearRoomCacheAction = true,
        ).toHomeRoomRowRenderModel(canReportRoom = false)

        assertThat(renderModel.actions.contextMenuActions.map { it.kind }).containsExactly(
            RoomListItemActionKind.MarkAsUnread,
            RoomListItemActionKind.Pin,
            RoomListItemActionKind.Mute,
            RoomListItemActionKind.Favorite,
            RoomListItemActionKind.Archive,
            RoomListItemActionKind.Settings,
            RoomListItemActionKind.Leave,
            RoomListItemActionKind.ClearCache,
        ).inOrder()
    }
}
