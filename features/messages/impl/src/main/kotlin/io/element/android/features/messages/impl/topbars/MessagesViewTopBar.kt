/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.MessagesMenuActions
import io.element.android.features.messages.impl.SharedHistoryIcon
import io.element.android.features.messages.impl.roomdata.RoomMenuRenderModel
import io.element.android.features.messages.impl.roomdata.RoomScheduleMenuBadge
import io.element.android.features.messages.impl.roomdata.RoomTopbarAction
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.features.roomcall.api.aStandByCallState
import io.element.android.features.roomcall.api.anOngoingCallState
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.avatar.anAvatarData
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.preview.ROOM_NAME
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.encryption.identity.IdentityState
import io.element.android.libraries.matrix.ui.components.aMatrixUserList
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun MessagesViewTopBar(
    roomName: String?,
    roomAvatar: AvatarData,
    isTombstoned: Boolean,
    heroes: ImmutableList<AvatarData>,
    dmUserIdentityState: IdentityState?,
    sharedHistoryIcon: SharedHistoryIcon,
    onRoomDetailsClick: () -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    menuActions: @Composable RowScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            FloatingCircleButton(onClick = onBackClick) {
                Icon(
                    modifier = Modifier.size(24.dp),
                    imageVector = CompoundIcons.ArrowLeft(),
                    contentDescription = stringResource(CommonStrings.action_back),
                )
            }

            val roundedCornerShape = RoundedCornerShape(24.dp)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .shadow(10.dp, roundedCornerShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.07f))
                    .clip(roundedCornerShape)
                    .background(topBarBubbleBrush(isActive = false))
                    .topBarBubbleHighlight()
                    .border(1.dp, ElementTheme.colors.borderDisabled.copy(alpha = 0.24f), roundedCornerShape)
                    .clickable { onRoomDetailsClick() }
                    .semantics { heading() }
                    .padding(start = 12.dp, end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val titleModifier = Modifier.weight(1f, fill = false)
                RoomAvatarAndNameRow(
                    roomName = roomName,
                    roomAvatar = roomAvatar,
                    isTombstoned = isTombstoned,
                    heroes = heroes,
                    modifier = titleModifier
                )

                val iconModifier = Modifier.size(16.dp)

                when (dmUserIdentityState) {
                    IdentityState.Verified -> {
                        Icon(
                            modifier = iconModifier,
                            imageVector = CompoundIcons.Verified(),
                            tint = ElementTheme.colors.iconSuccessPrimary,
                            contentDescription = null,
                        )
                    }
                    IdentityState.VerificationViolation -> {
                        Icon(
                            modifier = iconModifier,
                            imageVector = CompoundIcons.ErrorSolid(),
                            tint = ElementTheme.colors.iconCriticalPrimary,
                            contentDescription = null,
                        )
                    }
                    else -> Unit
                }

                when (sharedHistoryIcon) {
                    SharedHistoryIcon.NONE -> Unit
                    SharedHistoryIcon.SHARED -> Icon(
                        modifier = iconModifier,
                        imageVector = CompoundIcons.History(),
                        tint = ElementTheme.colors.iconInfoPrimary,
                        contentDescription = stringResource(CommonStrings.common_shared_history),
                    )
                    SharedHistoryIcon.WORLD_READABLE -> Icon(
                        modifier = iconModifier,
                        imageVector = CompoundIcons.UserProfileSolid(),
                        tint = ElementTheme.colors.iconInfoPrimary,
                        contentDescription = stringResource(CommonStrings.common_world_readable_history),
                    )
                }
            }

            Row(
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.Top,
                content = menuActions,
            )
        }
    }
}

@Composable
private fun FloatingCircleButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(48.dp)
            .shadow(10.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.07f))
            .clip(CircleShape)
            .background(topBarBubbleBrush(isActive = false))
            .topBarBubbleHighlight()
            .border(1.dp, ElementTheme.colors.borderDisabled.copy(alpha = 0.24f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun topBarBubbleBrush(isActive: Boolean): Brush {
    val topAlpha = if (isActive) 0.92f else 0.78f
    val bottomAlpha = if (isActive) 0.76f else 0.58f
    return Brush.verticalGradient(
        colors = listOf(
            ElementTheme.colors.bgCanvasDefault.copy(alpha = topAlpha),
            ElementTheme.colors.bgCanvasDefault.copy(alpha = bottomAlpha),
        )
    )
}

private fun Modifier.topBarBubbleHighlight(): Modifier = drawBehind {
    drawLine(
        color = Color.White.copy(alpha = 0.42f),
        start = Offset(0f, 0.7f),
        end = Offset(size.width, 0.7f),
        strokeWidth = 1.2f,
    )
    drawLine(
        color = Color.Black.copy(alpha = 0.04f),
        start = Offset(0f, size.height - 0.7f),
        end = Offset(size.width, size.height - 0.7f),
        strokeWidth = 1f,
    )
}
@Composable
private fun RoomAvatarAndNameRow(
    roomName: String?,
    roomAvatar: AvatarData,
    heroes: ImmutableList<AvatarData>,
    isTombstoned: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(
            avatarData = roomAvatar,
            avatarType = AvatarType.Room(
                heroes = heroes,
                isTombstoned = isTombstoned,
            ),
        )
        Text(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f, fill = false),
            text = roomName ?: stringResource(CommonStrings.common_no_room_name),
            style = ElementTheme.typography.fontBodyLgMedium,
            fontStyle = FontStyle.Italic.takeIf { roomName == null },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@PreviewsDayNight
@Composable
internal fun MessagesViewTopBarPreview() = ElementPreview {
    @Composable
    fun AMessagesViewTopBar(
        roomName: String? = ROOM_NAME,
        roomAvatar: AvatarData = anAvatarData(
            name = ROOM_NAME,
            size = AvatarSize.TimelineRoom,
        ),
        isTombstoned: Boolean = false,
        heroes: ImmutableList<AvatarData> = persistentListOf(),
        roomCallState: RoomCallState = RoomCallState.Unavailable,
        dmUserIdentityState: IdentityState? = null,
        sharedHistoryIcon: SharedHistoryIcon = SharedHistoryIcon.NONE,
        displayThreads: Boolean = false,
        displaySchedules: Boolean = false,
    ) = MessagesViewTopBar(
        roomName = roomName,
        roomAvatar = roomAvatar,
        isTombstoned = isTombstoned,
        heroes = heroes,
        dmUserIdentityState = dmUserIdentityState,
        sharedHistoryIcon = sharedHistoryIcon,
        onRoomDetailsClick = {},
        onBackClick = {},
        menuActions = {
            val roomMenu = RoomMenuRenderModel(
                topbarActions = buildList {
                    if (displayThreads) add(RoomTopbarAction.Threads)
                    if (displaySchedules) add(RoomTopbarAction.Schedules)
                },
                topbarTools = emptyList(),
                attachmentActions = emptyList(),
                scheduleBadge = RoomScheduleMenuBadge(
                    activeScheduleCount = 2,
                    isLoading = false,
                    error = null,
                ).takeIf { displaySchedules },
                deviceAgent = null,
            )
            MessagesMenuActions(
                roomCallState = roomCallState,
                roomMenu = roomMenu,
                onJoinCallClick = {},
                onJoinAudienceClick = {},
                onRoomSchedulesClick = {},
                onThreadsListClick = {},
            )
        }
    )
    Column {
        AMessagesViewTopBar()
        HorizontalDivider()
        AMessagesViewTopBar(
            heroes = aMatrixUserList().map { it.getAvatarData(AvatarSize.TimelineRoom) }.toImmutableList(),
            roomCallState = anOngoingCallState(),
        )
        HorizontalDivider()
        AMessagesViewTopBar(
            roomName = null,
            roomCallState = anOngoingCallState(canJoinCall = false),
        )
        HorizontalDivider()
        AMessagesViewTopBar(
            roomName = "A DM with a very very very long name",
            roomAvatar = anAvatarData(
                size = AvatarSize.TimelineRoom,
                url = "https://some-avatar.jpg"
            ),
            roomCallState = aStandByCallState(canStartCall = false),
            dmUserIdentityState = IdentityState.Verified
        )
        HorizontalDivider()
        AMessagesViewTopBar(
            roomName = "A DM with a very very very long name",
            isTombstoned = true,
            dmUserIdentityState = IdentityState.VerificationViolation
        )
        HorizontalDivider()
        AMessagesViewTopBar(
            roomName = "A DM with shared history",
            dmUserIdentityState = IdentityState.Verified,
            sharedHistoryIcon = SharedHistoryIcon.SHARED,
        )
        HorizontalDivider()
        AMessagesViewTopBar(
            roomName = "A room with world_readable history",
            sharedHistoryIcon = SharedHistoryIcon.WORLD_READABLE,
        )
        HorizontalDivider()
        AMessagesViewTopBar(
            displayThreads = true,
            displaySchedules = true,
        )
    }
}
