/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.model.HomeRoomActivityVisibility
import io.element.android.features.home.impl.model.HomeRoomPreviewState
import io.element.android.features.home.impl.model.HomeRoomRowRenderModel
import io.element.android.features.home.impl.model.HomeRoomTextEmphasis
import io.element.android.features.home.impl.model.RoomListItemAction
import io.element.android.features.home.impl.model.RoomListItemActionKind
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomListRoomSummaryProvider
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.model.toHomeRoomRowRenderModel
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.libraries.core.extensions.toSafeLength
import io.element.android.libraries.designsystem.atomic.atoms.UnreadIndicatorAtom
import io.element.android.libraries.designsystem.atomic.molecules.InviteButtonsRowMolecule
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.roomListRoomMessage
import io.element.android.libraries.designsystem.theme.roomListRoomMessageDate
import io.element.android.libraries.designsystem.theme.roomListRoomName
import io.element.android.libraries.designsystem.theme.unreadIndicator
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.ui.components.InviteSenderView
import io.element.android.libraries.matrix.ui.model.InviteSender
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.roundToInt

internal val minHeight = 84.dp

@Composable
internal fun RoomSummaryRow(
    room: RoomListRoomSummary,
    hideInviteAvatars: Boolean,
    isInviteSeen: Boolean,
    onClick: (RoomListRoomSummary) -> Unit,
    modifier: Modifier = Modifier,
    showUnreadCount: Boolean = false,
    isSelected: Boolean = false,
    activityVisibility: HomeRoomActivityVisibility = HomeRoomActivityVisibility.Current,
    swipeActionsEnabled: Boolean = false,
    openedSwipeRoomId: String? = null,
    onOpenSwipeRoom: (String?) -> Unit = {},
    eventSink: (RoomListEvent) -> Unit,
) {
    val renderModel = remember(room, isSelected, isInviteSeen, activityVisibility) {
        room.toHomeRoomRowRenderModel(
            isSelected = isSelected,
            isInviteSeen = isInviteSeen,
            activityVisibility = activityVisibility,
        )
    }
    Box(modifier = modifier) {
        when (room.displayType) {
            RoomSummaryDisplayType.PLACEHOLDER -> {
                RoomSummaryPlaceholderRow()
            }
            RoomSummaryDisplayType.INVITE -> {
                RoomSummaryScaffoldRow(
                    room = room,
                    hideAvatarImage = hideInviteAvatars,
                    onClick = onClick,
                    onLongClick = {
                        Timber.d("Long click on invite room")
                    },
                    renderModel = renderModel,
                ) {
                    InviteNameAndIndicatorRow(renderModel = renderModel)
                    InviteSubtitle(isDm = room.isDm, inviteSender = room.inviteSender)
                    if (!room.isDm && room.inviteSender != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        InviteSenderView(
                            modifier = Modifier.fillMaxWidth(),
                            inviteSender = room.inviteSender,
                            hideAvatarImage = hideInviteAvatars
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    InviteButtonsRowMolecule(
                        onAcceptClick = {
                            eventSink(RoomListEvent.AcceptInvite(room))
                        },
                        onDeclineClick = {
                            eventSink(RoomListEvent.ShowDeclineInviteMenu(room))
                        }
                    )
                }
            }
            RoomSummaryDisplayType.ROOM -> {
                val rowContent: @Composable BoxScope.() -> Unit = {
                    RoomSummaryScaffoldRow(
                        room = room,
                        onClick = onClick,
                        onLongClick = {
                            eventSink(RoomListEvent.ShowContextMenu(room))
                        },
                        renderModel = renderModel,
                    ) {
                        NameAndTimestampRow(
                            name = renderModel.displayName,
                            timestamp = renderModel.timestamp,
                            isHighlighted = renderModel.isHighlighted,
                            textEmphasis = renderModel.headerEmphasis,
                        )
                        MessagePreviewAndIndicatorRow(room = room, renderModel = renderModel, showUnreadCount = showUnreadCount)
                    }
                }
                if (swipeActionsEnabled) {
                    SwipeableRoomActions(
                        room = room,
                        renderModel = renderModel,
                        openedSwipeRoomId = openedSwipeRoomId,
                        onOpenSwipeRoom = onOpenSwipeRoom,
                        eventSink = eventSink,
                        content = rowContent,
                    )
                } else {
                    rowContent()
                }
            }
            RoomSummaryDisplayType.KNOCKED -> {
                RoomSummaryScaffoldRow(
                    room = room,
                    onClick = onClick,
                    onLongClick = {
                        Timber.d("Long click on knocked room")
                    },
                    renderModel = renderModel,
                ) {
                        NameAndTimestampRow(
                            name = renderModel.displayName,
                            timestamp = null,
                            isHighlighted = renderModel.isHighlighted,
                            textEmphasis = renderModel.headerEmphasis,
                        )
                    if (room.canonicalAlias != null) {
                        Text(
                            text = room.canonicalAlias.value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = stringResource(id = R.string.screen_roomlist_knock_event_sent_description),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SwipeableRoomActions(
    room: RoomListRoomSummary,
    renderModel: HomeRoomRowRenderModel,
    openedSwipeRoomId: String?,
    onOpenSwipeRoom: (String?) -> Unit,
    eventSink: (RoomListEvent) -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val actionWidth = 64.dp
    val swipeActions = renderModel.actions.swipeActions
    if (swipeActions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
        return
    }
    val totalRevealPx = with(LocalDensity.current) { (actionWidth * swipeActions.size).toPx() }
    var dragOffsetPx by remember(room.id) { mutableFloatStateOf(0f) }
    var isDragging by remember(room.id) { androidx.compose.runtime.mutableStateOf(false) }
    val targetOffsetPx = if (openedSwipeRoomId == room.id) -totalRevealPx else 0f
    val isSwipeActive = isDragging || openedSwipeRoomId == room.id || dragOffsetPx != 0f
    val animatedOffsetPx by animateFloatAsState(
        targetValue = if (isDragging) dragOffsetPx else targetOffsetPx,
        label = "room-list-swipe-offset",
    )
    val draggableState = rememberDraggableState { delta ->
        if (!isDragging) {
            isDragging = true
            dragOffsetPx = animatedOffsetPx
        }
        dragOffsetPx = (dragOffsetPx + delta).coerceIn(-totalRevealPx - 16f, 10f)
    }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(openedSwipeRoomId) {
        if (openedSwipeRoomId != room.id && !isDragging) {
            dragOffsetPx = 0f
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        if (isSwipeActive) {
            Row(
                modifier = Modifier
                    .matchParentSize()
                    .padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                swipeActions.forEach { action ->
                    SwipeActionButton(
                        action = action,
                        modifier = Modifier
                            .width(actionWidth)
                            .fillMaxHeight()
                            .heightIn(min = minHeight),
                        onClick = {
                            onOpenSwipeRoom(null)
                            coroutineScope.launch {
                                delay(300)
                                when (action.kind) {
                                    RoomListItemActionKind.MarkAsRead -> eventSink(RoomListEvent.MarkAsRead(room.roomId))
                                    RoomListItemActionKind.MarkAsUnread -> eventSink(RoomListEvent.MarkAsUnread(room.roomId))
                                    RoomListItemActionKind.Favorite -> eventSink(RoomListEvent.SetRoomIsFavorite(room.roomId, true))
                                    RoomListItemActionKind.Unfavorite -> eventSink(RoomListEvent.SetRoomIsFavorite(room.roomId, false))
                                    RoomListItemActionKind.Pin,
                                    RoomListItemActionKind.Unpin,
                                    RoomListItemActionKind.Mute,
                                    RoomListItemActionKind.Unmute,
                                    RoomListItemActionKind.Archive,
                                    RoomListItemActionKind.Unarchive,
                                    RoomListItemActionKind.Settings,
                                    RoomListItemActionKind.Report,
                                    RoomListItemActionKind.Leave,
                                    RoomListItemActionKind.ClearCache -> Unit
                                }
                            }
                        },
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .absoluteOffset { IntOffset(x = animatedOffsetPx.roundToInt(), y = 0) }
                .background(ElementTheme.colors.bgCanvasDefault)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    startDragImmediately = openedSwipeRoomId == room.id,
                    onDragStopped = {
                        val threshold = totalRevealPx * 0.35f
                        val shouldStayOpen = if (openedSwipeRoomId == room.id) {
                            dragOffsetPx < -totalRevealPx + threshold
                        } else {
                            -dragOffsetPx > threshold
                        }
                        isDragging = false
                        onOpenSwipeRoom(if (shouldStayOpen) room.id else null)
                    },
                ),
            content = content,
        )
    }
}

@Composable
private fun SwipeActionButton(
    action: RoomListItemAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = when (action.kind) {
        RoomListItemActionKind.Pin,
        RoomListItemActionKind.Unpin -> ElementTheme.colors.iconAccentTertiary
        RoomListItemActionKind.MarkAsRead,
        RoomListItemActionKind.MarkAsUnread -> ElementTheme.colors.borderFocused
        RoomListItemActionKind.Favorite,
        RoomListItemActionKind.Unfavorite -> Color(0xFFFF9F0A)
        else -> ElementTheme.colors.bgSubtleSecondary
    }
    val contentAlpha = if (action.enabled) 1f else 0.42f
    Column(
        modifier = modifier
            .background(containerColor)
            .clickable(enabled = action.enabled, onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = when (action.kind) {
                RoomListItemActionKind.Pin,
                RoomListItemActionKind.Unpin -> CompoundIcons.Pin()
                RoomListItemActionKind.MarkAsRead -> CompoundIcons.MarkAsRead()
                RoomListItemActionKind.MarkAsUnread -> CompoundIcons.MarkAsUnread()
                RoomListItemActionKind.Favorite -> CompoundIcons.Favourite()
                RoomListItemActionKind.Unfavorite -> CompoundIcons.FavouriteSolid()
                else -> CompoundIcons.Settings()
            },
            contentDescription = null,
            tint = Color.White.copy(alpha = contentAlpha),
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = swipeActionTitle(action.kind),
            color = Color.White.copy(alpha = contentAlpha),
            style = ElementTheme.typography.fontBodyXsMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun swipeActionTitle(kind: RoomListItemActionKind): String {
    return when (kind) {
        RoomListItemActionKind.Pin -> stringResource(id = CommonStrings.action_pin)
        RoomListItemActionKind.Unpin -> stringResource(id = CommonStrings.action_unpin)
        RoomListItemActionKind.MarkAsRead -> stringResource(id = R.string.screen_roomlist_mark_as_read)
        RoomListItemActionKind.MarkAsUnread -> stringResource(id = R.string.screen_roomlist_mark_as_unread)
        RoomListItemActionKind.Favorite -> stringResource(id = CommonStrings.common_favourite)
        RoomListItemActionKind.Unfavorite -> stringResource(id = CommonStrings.common_favourited)
        else -> ""
    }
}

@Composable
private fun RoomSummaryScaffoldRow(
    room: RoomListRoomSummary,
    onClick: (RoomListRoomSummary) -> Unit,
    onLongClick: (RoomListRoomSummary) -> Unit,
    renderModel: HomeRoomRowRenderModel,
    modifier: Modifier = Modifier,
    hideAvatarImage: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = Modifier
        .combinedClickable(
            onClick = { onClick(room) },
            onLongClick = { onLongClick(room) },
            onLongClickLabel = stringResource(CommonStrings.action_open_context_menu),
            indication = ripple(),
            interactionSource = remember { MutableInteractionSource() }
        )
        .onKeyboardContextMenuAction { onLongClick(room) }
    val avatarData = if (room.isDm && room.avatarData.url == null && room.heroes.isEmpty()) {
        room.avatarData.copy(id = room.name ?: room.id, name = room.name)
    } else {
        room.avatarData
    }
    val avatarType = when {
        room.isSpace -> AvatarType.Space(isTombstoned = room.isTombstoned)
        room.isDm && room.avatarData.url == null && room.heroes.isEmpty() -> AvatarType.User
        else -> AvatarType.Room(
            heroes = room.heroes,
            isTombstoned = room.isTombstoned,
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(
                if (renderModel.isSelected) {
                    ElementTheme.colors.bgSubtleSecondary
                } else {
                    ElementTheme.colors.bgCanvasDefault
                }
            )
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 11.dp),
    ) {
        Avatar(
            avatarData = avatarData,
            avatarType = avatarType,
            hideImage = hideAvatarImage,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun NameAndTimestampRow(
    name: String?,
    timestamp: String?,
    isHighlighted: Boolean,
    textEmphasis: HomeRoomTextEmphasis,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(16.dp)
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .clipToBounds(),
            style = when (textEmphasis) {
                HomeRoomTextEmphasis.Semibold -> ElementTheme.typography.fontBodyLgMedium
                HomeRoomTextEmphasis.Regular -> ElementTheme.typography.fontBodyLgRegular
            },
            text = name?.toSafeLength(ellipsize = true) ?: stringResource(id = CommonStrings.common_no_room_name),
            fontStyle = FontStyle.Italic.takeIf { name == null },
            color = ElementTheme.colors.roomListRoomName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // Timestamp
        Text(
            text = timestamp ?: "",
            style = ElementTheme.typography.fontBodySmMedium,
            color = if (isHighlighted) {
                ElementTheme.colors.unreadIndicator
            } else {
                ElementTheme.colors.roomListRoomMessageDate
            },
        )
    }
}

@Composable
private fun InviteSubtitle(
    isDm: Boolean,
    inviteSender: InviteSender?,
    modifier: Modifier = Modifier
) {
    val subtitle = if (isDm) {
        inviteSender?.userId?.value
    } else {
        null
    }
    if (subtitle != null) {
        Text(
            modifier = modifier.clipToBounds(),
            text = subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.roomListRoomMessage,
        )
    }
}

@Composable
private fun MessagePreviewAndIndicatorRow(
    room: RoomListRoomSummary,
    renderModel: HomeRoomRowRenderModel,
    showUnreadCount: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (renderModel.previewState == HomeRoomPreviewState.Tombstoned) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.screen_roomlist_tombstoned_room_description),
                color = ElementTheme.colors.roomListRoomMessage,
                style = renderModel.previewEmphasis.toPreviewTextStyle(),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            if (renderModel.previewState == HomeRoomPreviewState.Failed) {
                Icon(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                    imageVector = CompoundIcons.ErrorSolid(),
                    // The last message contains the error.
                    contentDescription = null,
                    tint = ElementTheme.colors.iconCriticalPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(CommonStrings.common_message_failed_to_send),
                    color = ElementTheme.colors.textCriticalPrimary,
                    style = renderModel.previewEmphasis.toPreviewTextStyle(),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                if (renderModel.previewState == HomeRoomPreviewState.Sending) {
                    Icon(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp),
                        imageVector = CompoundIcons.Time(),
                        contentDescription = stringResource(CommonStrings.common_sending),
                        tint = ElementTheme.colors.iconTertiary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds(),
                    text = renderModel.preview ?: AnnotatedString(text = ""),
                    color = ElementTheme.colors.roomListRoomMessage,
                    style = renderModel.previewEmphasis.toPreviewTextStyle(),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        // Call and unread
        Row(
            modifier = Modifier
                .height(16.dp)
                // Used to force this line to be read aloud earlier than the latest event when using Talkback
                .zIndex(-1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = if (renderModel.isHighlighted) ElementTheme.colors.unreadIndicator else ElementTheme.colors.iconQuaternary
            if (renderModel.badges.showCall) {
                OnGoingCallIcon(
                    color = tint,
                    isAudio = room.activeCallIntent == CallIntent.AUDIO
                )
            }
            if (renderModel.badges.showMute) {
                NotificationOffIndicatorAtom()
            } else if (renderModel.badges.showMention) {
                MentionIndicatorAtom()
            }
            if (renderModel.badges.showDot) {
                val contentDescription = stringResource(CommonStrings.a11y_notifications_new_messages)
                val count = if (showUnreadCount) {
                    if (renderModel.showNumericUnreadBadge) {
                        renderModel.unreadCount
                    } else {
                        null
                    }
                } else {
                    null
                }
                UnreadIndicatorAtom(
                    color = tint,
                    count = count,
                    contentDescription = contentDescription,
                )
            }
        }
    }
}

@Composable
private fun InviteNameAndIndicatorRow(
    renderModel: HomeRoomRowRenderModel,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .clipToBounds(),
            style = when (renderModel.headerEmphasis) {
                HomeRoomTextEmphasis.Semibold -> ElementTheme.typography.fontBodyLgMedium
                HomeRoomTextEmphasis.Regular -> ElementTheme.typography.fontBodyLgRegular
            },
            text = renderModel.displayName?.toSafeLength(ellipsize = true) ?: stringResource(id = CommonStrings.common_no_room_name),
            fontStyle = FontStyle.Italic.takeIf { renderModel.displayName == null },
            color = ElementTheme.colors.roomListRoomName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (renderModel.badges.showDot) {
            UnreadIndicatorAtom(
                color = ElementTheme.colors.unreadIndicator
            )
        }
    }
}

@Composable
private fun HomeRoomTextEmphasis.toPreviewTextStyle() = when (this) {
    HomeRoomTextEmphasis.Semibold -> ElementTheme.typography.fontBodyMdMedium
    HomeRoomTextEmphasis.Regular -> ElementTheme.typography.fontBodyMdRegular
}

@Composable
private fun OnGoingCallIcon(
    color: Color,
    isAudio: Boolean
) {
    Icon(
        modifier = Modifier.size(16.dp),
        imageVector = if (isAudio) CompoundIcons.VoiceCallSolid() else CompoundIcons.VideoCallSolid(),
        contentDescription = stringResource(CommonStrings.a11y_notifications_ongoing_call),
        tint = color,
    )
}

@Composable
private fun NotificationOffIndicatorAtom() {
    Icon(
        modifier = Modifier.size(16.dp),
        contentDescription = stringResource(CommonStrings.a11y_notifications_muted),
        imageVector = CompoundIcons.NotificationsOffSolid(),
        tint = ElementTheme.colors.iconQuaternary,
    )
}

@Composable
private fun MentionIndicatorAtom() {
    Icon(
        modifier = Modifier.size(16.dp),
        contentDescription = stringResource(CommonStrings.a11y_notifications_new_mentions),
        imageVector = CompoundIcons.Mention(),
        tint = ElementTheme.colors.unreadIndicator,
    )
}

@PreviewsDayNight
@Composable
internal fun RoomSummaryRowPreview(@PreviewParameter(RoomListRoomSummaryProvider::class) data: RoomListRoomSummary) = ElementPreview {
    RoomSummaryRow(
        room = data,
        hideInviteAvatars = false,
        // Set isInviteSeen to true for the preview when the room has name "Bob"
        isInviteSeen = data.name == "Bob",
        onClick = {},
        eventSink = {},
    )
}
