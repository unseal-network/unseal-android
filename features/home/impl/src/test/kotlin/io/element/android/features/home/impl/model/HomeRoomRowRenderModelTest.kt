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

class HomeRoomRowRenderModelTest {
    @Test
    fun `current activity mode mirrors iOS badges and numeric unread policy`() {
        val model = createRoomListRoomSummary(
            numberOfUnreadMessages = 4,
            numberOfUnreadMentions = 1,
            numberOfUnreadNotifications = 2,
        ).toHomeRoomRowRenderModel(activityVisibility = HomeRoomActivityVisibility.Current)

        assertThat(model.type).isEqualTo(HomeRoomRowType.Room)
        assertThat(model.badges.showDot).isTrue()
        assertThat(model.badges.showMention).isTrue()
        assertThat(model.badges.showMute).isFalse()
        assertThat(model.showNumericUnreadBadge).isTrue()
        assertThat(model.unreadCount).isEqualTo(4)
        assertThat(model.isHighlighted).isTrue()
        assertThat(model.headerEmphasis).isEqualTo(HomeRoomTextEmphasis.Semibold)
        assertThat(model.previewEmphasis).isEqualTo(HomeRoomTextEmphasis.Regular)
    }

    @Test
    fun `muted rooms keep dot activity but hide mention and numeric badge`() {
        val model = createRoomListRoomSummary(
            numberOfUnreadMessages = 4,
            numberOfUnreadMentions = 1,
            numberOfUnreadNotifications = 2,
            userDefinedNotificationMode = RoomNotificationMode.MUTE,
        ).toHomeRoomRowRenderModel(activityVisibility = HomeRoomActivityVisibility.Current)

        assertThat(model.badges.showDot).isTrue()
        assertThat(model.badges.showMention).isFalse()
        assertThat(model.badges.showMute).isTrue()
        assertThat(model.showNumericUnreadBadge).isFalse()
        assertThat(model.isHighlighted).isFalse()
    }

    @Test
    fun `hidden activity mode only emphasizes notification level activity`() {
        val messageOnly = createRoomListRoomSummary(
            numberOfUnreadMessages = 3,
        ).toHomeRoomRowRenderModel(activityVisibility = HomeRoomActivityVisibility.Hide)

        val notification = createRoomListRoomSummary(
            numberOfUnreadMessages = 3,
            numberOfUnreadNotifications = 1,
        ).toHomeRoomRowRenderModel(activityVisibility = HomeRoomActivityVisibility.Hide)

        assertThat(messageOnly.badges.showDot).isFalse()
        assertThat(messageOnly.isHighlighted).isFalse()
        assertThat(messageOnly.previewEmphasis).isEqualTo(HomeRoomTextEmphasis.Regular)
        assertThat(notification.badges.showDot).isTrue()
        assertThat(notification.isHighlighted).isTrue()
        assertThat(notification.previewEmphasis).isEqualTo(HomeRoomTextEmphasis.Semibold)
    }

    @Test
    fun `unseen invite shows dot and highlight like iOS`() {
        val model = createRoomListRoomSummary(
            displayType = RoomSummaryDisplayType.INVITE,
        ).toHomeRoomRowRenderModel(isInviteSeen = false)

        assertThat(model.type).isEqualTo(HomeRoomRowType.Invite)
        assertThat(model.badges.showDot).isTrue()
        assertThat(model.isHighlighted).isTrue()
        assertThat(model.isInviteSeen).isFalse()
    }

    @Test
    fun `seen invite hides row dot while preserving invite row type`() {
        val model = createRoomListRoomSummary(
            displayType = RoomSummaryDisplayType.INVITE,
        ).toHomeRoomRowRenderModel(isInviteSeen = true)

        assertThat(model.type).isEqualTo(HomeRoomRowType.Invite)
        assertThat(model.badges.showDot).isFalse()
        assertThat(model.isHighlighted).isFalse()
        assertThat(model.isInviteSeen).isTrue()
    }

    @Test
    fun `selected row state is carried by render model for shell parity`() {
        val model = createRoomListRoomSummary()
            .toHomeRoomRowRenderModel(isSelected = true)

        assertThat(model.isSelected).isTrue()
    }

    @Test
    fun `preview state follows latest event and tombstone priority`() {
        val sending = aRoomListRoomSummary(
            latestEvent = LatestEvent.Sending("sending"),
        ).toHomeRoomRowRenderModel()
        val failed = aRoomListRoomSummary(
            latestEvent = LatestEvent.Error,
        ).toHomeRoomRowRenderModel()
        val tombstoned = aRoomListRoomSummary(
            latestEvent = LatestEvent.Error,
            isTombstoned = true,
        ).toHomeRoomRowRenderModel()

        assertThat(sending.previewState).isEqualTo(HomeRoomPreviewState.Sending)
        assertThat(sending.preview?.text).isEqualTo("sending")
        assertThat(failed.previewState).isEqualTo(HomeRoomPreviewState.Failed)
        assertThat(failed.preview).isNull()
        assertThat(tombstoned.previewState).isEqualTo(HomeRoomPreviewState.Tombstoned)
        assertThat(tombstoned.preview).isNull()
    }

    @Test
    fun `actions are embedded in the row model for UI consumption`() {
        val model = createRoomListRoomSummary(
            numberOfUnreadMessages = 1,
            isFavorite = true,
            isPinned = true,
        ).toHomeRoomRowRenderModel(
            canReportRoom = true,
            displayClearRoomCacheAction = true,
        )

        assertThat(model.actions.swipeActions.map { it.kind }).containsExactly(
            RoomListItemActionKind.Unpin,
            RoomListItemActionKind.MarkAsRead,
            RoomListItemActionKind.Unfavorite,
        ).inOrder()
        assertThat(model.actions.contextMenuActions.map { it.kind }).containsExactly(
            RoomListItemActionKind.MarkAsRead,
            RoomListItemActionKind.Unpin,
            RoomListItemActionKind.Mute,
            RoomListItemActionKind.Unfavorite,
            RoomListItemActionKind.Archive,
            RoomListItemActionKind.Settings,
            RoomListItemActionKind.Report,
            RoomListItemActionKind.Leave,
            RoomListItemActionKind.ClearCache,
        ).inOrder()
    }
}
