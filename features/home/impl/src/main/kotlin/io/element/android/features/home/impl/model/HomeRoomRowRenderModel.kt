/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import io.element.android.libraries.matrix.api.room.RoomNotificationMode

@Immutable
data class HomeRoomRowRenderModel(
    val id: String,
    val type: HomeRoomRowType,
    val displayName: String?,
    val timestamp: String?,
    val preview: AnnotatedString?,
    val previewState: HomeRoomPreviewState,
    val badges: HomeRoomRowBadges,
    val unreadCount: Long,
    val isHighlighted: Boolean,
    val isFavorite: Boolean,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isSelected: Boolean,
    val isInviteSeen: Boolean,
    val showNumericUnreadBadge: Boolean,
    val headerEmphasis: HomeRoomTextEmphasis,
    val previewEmphasis: HomeRoomTextEmphasis,
    val actions: RoomListItemActionsPresentation,
)

enum class HomeRoomRowType {
    Placeholder,
    Room,
    Invite,
    Knocked,
}

@Immutable
data class HomeRoomRowBadges(
    val showDot: Boolean,
    val showMention: Boolean,
    val showMute: Boolean,
    val showCall: Boolean,
)

enum class HomeRoomPreviewState {
    Normal,
    Sending,
    Failed,
    Tombstoned,
}

enum class HomeRoomActivityVisibility {
    Current,
    Show,
    Hide,
}

enum class HomeRoomTextEmphasis {
    Regular,
    Semibold,
}

fun RoomListRoomSummary.toHomeRoomRowRenderModel(
    isSelected: Boolean = false,
    isInviteSeen: Boolean = true,
    activityVisibility: HomeRoomActivityVisibility = HomeRoomActivityVisibility.Current,
    canReportRoom: Boolean = false,
    displayClearRoomCacheAction: Boolean = false,
): HomeRoomRowRenderModel {
    val isMuted = userDefinedNotificationMode == RoomNotificationMode.MUTE
    val isUnseenInvite = displayType == RoomSummaryDisplayType.INVITE && !isInviteSeen
    val showDot = when (activityVisibility) {
        HomeRoomActivityVisibility.Current -> hasNewContent || isUnseenInvite
        HomeRoomActivityVisibility.Hide,
        HomeRoomActivityVisibility.Show -> (!isMuted && (numberOfUnreadNotifications > 0 || numberOfUnreadMentions > 0)) ||
            isMarkedUnread ||
            isUnseenInvite
    }
    val highlighted = isMarkedUnread || (!isMuted && (numberOfUnreadNotifications > 0 || numberOfUnreadMentions > 0)) || isUnseenInvite
    val hasUnreadMessages = numberOfUnreadMessages > 0
    val rowType = when (displayType) {
        RoomSummaryDisplayType.PLACEHOLDER -> HomeRoomRowType.Placeholder
        RoomSummaryDisplayType.ROOM -> HomeRoomRowType.Room
        RoomSummaryDisplayType.INVITE -> HomeRoomRowType.Invite
        RoomSummaryDisplayType.KNOCKED -> HomeRoomRowType.Knocked
    }
    val previewState = when {
        isTombstoned -> HomeRoomPreviewState.Tombstoned
        latestEvent is LatestEvent.Error -> HomeRoomPreviewState.Failed
        latestEvent is LatestEvent.Sending -> HomeRoomPreviewState.Sending
        else -> HomeRoomPreviewState.Normal
    }
    val preview = when (previewState) {
        HomeRoomPreviewState.Failed,
        HomeRoomPreviewState.Tombstoned -> null
        HomeRoomPreviewState.Normal,
        HomeRoomPreviewState.Sending -> latestEvent.content()?.let { it as? AnnotatedString ?: AnnotatedString(it.toString()) }
    }

    return HomeRoomRowRenderModel(
        id = id,
        type = rowType,
        displayName = name,
        timestamp = timestamp.takeUnless { latestEvent is LatestEvent.None },
        preview = preview,
        previewState = previewState,
        badges = HomeRoomRowBadges(
            showDot = showDot,
            showMention = numberOfUnreadMentions > 0 && !isMuted,
            showMute = isMuted,
            showCall = hasRoomCall,
        ),
        unreadCount = numberOfUnreadMessages,
        isHighlighted = highlighted,
        isFavorite = isFavorite,
        isPinned = isPinned,
        isArchived = isArchived,
        isSelected = isSelected,
        isInviteSeen = isInviteSeen,
        showNumericUnreadBadge = activityVisibility == HomeRoomActivityVisibility.Current && numberOfUnreadMessages > 0 && !isMuted,
        headerEmphasis = when (activityVisibility) {
            HomeRoomActivityVisibility.Current -> HomeRoomTextEmphasis.Semibold
            HomeRoomActivityVisibility.Show -> if (hasUnreadMessages) HomeRoomTextEmphasis.Semibold else HomeRoomTextEmphasis.Regular
            HomeRoomActivityVisibility.Hide -> if (highlighted) HomeRoomTextEmphasis.Semibold else HomeRoomTextEmphasis.Regular
        },
        previewEmphasis = when (activityVisibility) {
            HomeRoomActivityVisibility.Current -> HomeRoomTextEmphasis.Regular
            HomeRoomActivityVisibility.Show -> if (hasUnreadMessages) HomeRoomTextEmphasis.Semibold else HomeRoomTextEmphasis.Regular
            HomeRoomActivityVisibility.Hide -> if (highlighted) HomeRoomTextEmphasis.Semibold else HomeRoomTextEmphasis.Regular
        },
        actions = toActionsPresentation(
            canReportRoom = canReportRoom,
            displayClearRoomCacheAction = displayClearRoomCacheAction,
        ),
    )
}
