/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.TimelineRoomInfo
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.components.event.TimelineItemEventContentView
import io.element.android.features.messages.impl.timeline.components.receipt.InlineReadReceiptView
import io.element.android.features.messages.impl.timeline.components.receipt.ReadReceiptViewState
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.TimelineItemThreadInfo
import io.element.android.features.messages.impl.timeline.model.TimelineContentPaddingPolicy
import io.element.android.features.messages.impl.timeline.model.TimelinePresentationModel
import io.element.android.features.messages.impl.timeline.model.TimelinePresentationReducer
import io.element.android.features.messages.impl.timeline.model.bubble.BubbleState
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionEvent
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionState
import io.element.android.features.messages.impl.timeline.protection.mustBeProtected
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.colors.AvatarColorsProvider
import io.element.android.libraries.designsystem.components.EqualWidthColumn
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.modifiers.niceClickable
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.preview.USER_NAME_ALICE
import io.element.android.libraries.designsystem.swipe.SwipeableActionsState
import io.element.android.libraries.designsystem.swipe.rememberSwipeableActionsState
import io.element.android.libraries.designsystem.text.toPx
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.core.toThreadId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.EmbeddedEventInfo
import io.element.android.libraries.matrix.api.timeline.item.ThreadSummary
import io.element.android.libraries.matrix.api.timeline.item.event.EventOrTransactionId
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.getAvatarUrl
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import io.element.android.libraries.matrix.api.timeline.item.event.getDisplayName
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetails
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToView
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToViewStyle
import io.element.android.libraries.matrix.ui.messages.reply.eventId
import io.element.android.libraries.matrix.ui.messages.sender.SenderName
import io.element.android.libraries.matrix.ui.messages.sender.SenderNameMode
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.utils.a11y.isTalkbackActive
import io.element.android.wysiwyg.link.Link
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private val TIMELINE_ROW_HORIZONTAL_PADDING = 16.dp

// Content axis for incoming messages: aligns message content under the sender label.
// Matches MessageSenderInformation layout: horizontal padding + avatar width + avatar→name gap.
private val TIMELINE_INCOMING_CONTENT_START = TIMELINE_ROW_HORIZONTAL_PADDING + AvatarSize.TimelineSender.dp + 10.dp

private data class TimelineRowMetrics(
    val contentStart: Dp,
    val contentEnd: Dp,
)

@Composable
fun TimelineItemEventRow(
    event: TimelineItem.Event,
    timelineMode: Timeline.Mode,
    timelineRoomInfo: TimelineRoomInfo,
    timelineProtectionState: TimelineProtectionState,
    renderReadReceipts: Boolean,
    isLastOutgoingMessage: Boolean,
    displayThreadSummaries: Boolean,
    onEventClick: () -> Unit,
    onLongClick: () -> Unit,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onUserDataClick: (MatrixUser) -> Unit,
    inReplyToClick: (EventId) -> Unit,
    onReactionClick: (emoji: String, eventId: TimelineItem.Event) -> Unit,
    onReactionLongClick: (emoji: String, eventId: TimelineItem.Event) -> Unit,
    onMoreReactionsClick: (eventId: TimelineItem.Event) -> Unit,
    onReadReceiptClick: (event: TimelineItem.Event) -> Unit,
    onSwipeToReply: () -> Unit,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
    eventContentView: @Composable (Modifier) -> Unit = { contentModifier ->
        // Only pass down a custom clickable lambda if the content can be clicked separately
        val onContentClick = onEventClick.takeUnless { event.isWholeContentClickable }

        TimelineItemEventContentView(
            content = event.content,
            timelineRoomInfo = timelineRoomInfo,
            hideMediaContent = timelineProtectionState.hideMediaContent(event.eventId),
            onContentClick = onContentClick,
            onLongClick = onLongClick,
            onShowContentClick = { timelineProtectionState.eventSink(TimelineProtectionEvent.ShowContent(event.eventId)) },
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
            eventSink = eventSink,
            modifier = contentModifier,
        )
    },
) {
    val coroutineScope = rememberCoroutineScope()
    val interactionSource = remember { MutableInteractionSource() }

    val onContentClick = if (event.mustBeProtected()) {
        // In this case, let the content handle the click
        {}
    } else {
        onEventClick
    }

    fun onUserDataClick() {
        val sender = MatrixUser(
            userId = event.senderId,
            displayName = event.senderProfile.getDisplayName(),
            avatarUrl = event.senderProfile.getAvatarUrl(),
        )
        onUserDataClick(sender)
    }

    fun inReplyToClick() {
        val inReplyToEventId = event.inReplyTo?.eventId() ?: return
        inReplyToClick(inReplyToEventId)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (event.groupPosition.isNew()) {
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            Spacer(modifier = Modifier.height(2.dp))
        }

        val presentation = remember(
            event.content,
            event.groupPosition,
        ) {
            TimelinePresentationReducer.reduce(
                content = event.content,
                groupPosition = event.groupPosition,
            )
        }
        val canReply = timelineRoomInfo.userHasPermissionToSendMessage && event.canBeRepliedTo
        if (canReply) {
            val state: SwipeableActionsState = rememberSwipeableActionsState()
            val offset = state.offset.floatValue
            val swipeThresholdPx = 40.dp.toPx()
            val thresholdCrossed = abs(offset) > swipeThresholdPx
            SwipeSensitivity(3f) {
                Box(Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.matchParentSize()) {
                        ReplySwipeIndicator({ offset / 120 })
                    }
                    TimelineItemEventRowContent(
                        event = event,
                        timelineMode = timelineMode,
                        timelineProtectionState = timelineProtectionState,
                        timelineRoomInfo = timelineRoomInfo,
                        interactionSource = interactionSource,
                        onContentClick = onContentClick,
                        onLongClick = onLongClick,
                        inReplyToClick = ::inReplyToClick,
                        onUserDataClick = ::onUserDataClick,
                        onReactionClick = { emoji -> onReactionClick(emoji, event) },
                        onReactionLongClick = { emoji -> onReactionLongClick(emoji, event) },
                        onMoreReactionsClick = { onMoreReactionsClick(event) },
                        presentation = presentation,
                        renderReadReceipts = renderReadReceipts,
                        isLastOutgoingMessage = isLastOutgoingMessage,
                        displayThreadSummaries = displayThreadSummaries,
                        onReadReceiptsClick = { onReadReceiptClick(event) },
                        modifier = Modifier
                            .absoluteOffset { IntOffset(x = offset.roundToInt(), y = 0) }
                            .draggable(
                                orientation = Orientation.Horizontal,
                                enabled = !state.isResettingOnRelease,
                                onDragStopped = {
                                    coroutineScope.launch {
                                        if (thresholdCrossed) {
                                            onSwipeToReply()
                                        }
                                        state.resetOffset()
                                    }
                                },
                                state = state.draggableState,
                            ),
                        eventSink = eventSink,
                        eventContentView = eventContentView,
                    )
                }
            }
        } else {
            TimelineItemEventRowContent(
                event = event,
                timelineMode = timelineMode,
                timelineProtectionState = timelineProtectionState,
                timelineRoomInfo = timelineRoomInfo,
                interactionSource = interactionSource,
                onContentClick = onContentClick,
                onLongClick = onLongClick,
                inReplyToClick = ::inReplyToClick,
                onUserDataClick = ::onUserDataClick,
                onReactionClick = { emoji -> onReactionClick(emoji, event) },
                onReactionLongClick = { emoji -> onReactionLongClick(emoji, event) },
                onMoreReactionsClick = { onMoreReactionsClick(event) },
                presentation = presentation,
                renderReadReceipts = renderReadReceipts,
                isLastOutgoingMessage = isLastOutgoingMessage,
                displayThreadSummaries = displayThreadSummaries,
                onReadReceiptsClick = { onReadReceiptClick(event) },
                eventSink = eventSink,
                eventContentView = eventContentView,
            )
        }

    }
}

private fun Modifier.standaloneContentGestures(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = pointerInput(onClick, onLongClick) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        val touchSlop = viewConfiguration.touchSlop
        var movedPastSlop = false
        var childConsumedGesture = false
        val finishedBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                if (event.changes.any { it.isConsumed }) {
                    childConsumedGesture = true
                }
                if ((change.position - down.position).getDistance() > touchSlop) {
                    movedPastSlop = true
                }
                if (!change.pressed) {
                    if (!movedPastSlop && !childConsumedGesture) {
                        onClick()
                    }
                    return@withTimeoutOrNull true
                }
            }
        }

        if (finishedBeforeLongPress == null) {
            if (!movedPastSlop && !childConsumedGesture) {
                onLongClick()
            }
            waitForUpOrCancellation()
        }
    }
}

@Composable
private fun ThreadSummaryView(
    threadSummary: ThreadSummary,
    latestEventText: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    shape = RoundedCornerShape(8.dp)
                    clip = true
                }
                .background(ElementTheme.colors.bgSubtleSecondary)
                .niceClickable(onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .widthIn(max = maxWidth * MessageEventBubbleDefaults.BUBBLE_WIDTH_RATIO),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.size(20.dp),
                imageVector = CompoundIcons.ThreadsSolid(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = pluralStringResource(CommonPlurals.common_replies, threadSummary.numberOfReplies.toInt(), threadSummary.numberOfReplies),
                style = ElementTheme.typography.fontBodySmMedium,
                color = ElementTheme.colors.textSecondary,
            )

            Spacer(modifier = Modifier.width(8.dp))

            threadSummary.latestEvent.dataOrNull()?.let { latestEvent ->
                val avatarData = AvatarData(
                    id = latestEvent.senderId.value,
                    name = latestEvent.senderProfile.getDisplayName(),
                    url = latestEvent.senderProfile.getAvatarUrl(),
                    size = AvatarSize.TimelineThreadLatestEventSender,
                )
                Avatar(
                    avatarData = avatarData,
                    avatarType = AvatarType.User,
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = latestEvent.senderProfile.getDisambiguatedDisplayName(latestEvent.senderId),
                    style = ElementTheme.typography.fontBodySmMedium,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.width(4.dp))

                latestEventText?.let {
                    Text(
                        text = it,
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Impact ViewConfiguration.touchSlop by [sensitivityFactor].
 * Inspired from https://issuetracker.google.com/u/1/issues/269627294.
 * @param sensitivityFactor the factor to multiply the touchSlop by. The highest value, the more the user will
 * have to drag to start the drag.
 * @param content the content to display.
 */
@Composable
private fun SwipeSensitivity(
    sensitivityFactor: Float,
    content: @Composable () -> Unit,
) {
    val current = LocalViewConfiguration.current
    CompositionLocalProvider(
        LocalViewConfiguration provides object : ViewConfiguration by current {
            override val touchSlop: Float
                get() = current.touchSlop * sensitivityFactor
        }
    ) {
        content()
    }
}

@Composable
private fun TimelineItemEventRowContent(
    event: TimelineItem.Event,
    timelineMode: Timeline.Mode,
    timelineProtectionState: TimelineProtectionState,
    timelineRoomInfo: TimelineRoomInfo,
    interactionSource: MutableInteractionSource,
    onContentClick: () -> Unit,
    onLongClick: () -> Unit,
    inReplyToClick: () -> Unit,
    onUserDataClick: () -> Unit,
    onReactionClick: (emoji: String) -> Unit,
    onReactionLongClick: (emoji: String) -> Unit,
    onMoreReactionsClick: (event: TimelineItem.Event) -> Unit,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    presentation: TimelinePresentationModel,
    renderReadReceipts: Boolean,
    isLastOutgoingMessage: Boolean,
    displayThreadSummaries: Boolean,
    onReadReceiptsClick: () -> Unit,
    modifier: Modifier = Modifier,
    eventContentView: @Composable (Modifier) -> Unit,
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    Column(
        modifier = modifier
            .wrapContentHeight()
            .fillMaxWidth(),
    ) {
        val isEventPinned = timelineRoomInfo.pinnedEventIds.contains(event.eventId)
        val rowMetrics = TimelineRowMetrics(
            contentStart = if (screenWidth < 360.dp) TIMELINE_INCOMING_CONTENT_START - 8.dp else TIMELINE_INCOMING_CONTENT_START,
            contentEnd = TIMELINE_ROW_HORIZONTAL_PADDING,
        )
        val hasThreadSummary = displayThreadSummaries && timelineMode !is Timeline.Mode.Thread && event.threadInfo is TimelineItemThreadInfo.ThreadRoot

        if (presentation.showSenderInformation) {
            MessageSenderInformation(
                event.senderId,
                event.senderProfile,
                event.senderAvatar,
                onUserDataClick,
                Modifier
                    .padding(horizontal = TIMELINE_ROW_HORIZONTAL_PADDING)
                    .zIndex(1f),
            )
        }

        Column(
            modifier = Modifier
                .wrapContentHeight()
                .fillMaxWidth()
                .padding(start = rowMetrics.contentStart, end = rowMetrics.contentEnd),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val bubbleState = BubbleState(
                groupPosition = event.groupPosition,
                isMine = event.isMine,
                timelineRoomInfo = timelineRoomInfo,
            )
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (presentation.isStandalone) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(TestTags.timelineItemEventContent)
                            .standaloneContentGestures(
                                onClick = onContentClick,
                                onLongClick = onLongClick,
                            )
                            .onKeyboardContextMenuAction { onLongClick() }
                            .semantics {
                                onClick {
                                    onContentClick()
                                    true
                                }
                            },
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        MessageEventBubbleContent(
                            event = event,
                            timelineMode = timelineMode,
                            timelineProtectionState = timelineProtectionState,
                            onMessageLongClick = onLongClick,
                            inReplyToClick = inReplyToClick,
                            eventSink = eventSink,
                            alignContentToStart = true,
                            contentPaddingPolicy = presentation.contentPaddingPolicy,
                            eventContentView = eventContentView,
                        )
                    }
                } else {
                    MessageEventBubble(
                        modifier = Modifier.fillMaxWidth(),
                        state = bubbleState,
                        interactionSource = interactionSource,
                        onClick = onContentClick,
                        onLongClick = onLongClick,
                    ) {
                        MessageEventBubbleContent(
                            event = event,
                            timelineMode = timelineMode,
                            timelineProtectionState = timelineProtectionState,
                            onMessageLongClick = onLongClick,
                            inReplyToClick = inReplyToClick,
                            eventSink = eventSink,
                            contentPaddingPolicy = presentation.contentPaddingPolicy,
                            eventContentView = eventContentView,
                        )
                    }
                }
            }

            TimelineEventMetadataRow(
                event = event,
                renderReadReceipts = renderReadReceipts,
                isLastOutgoingMessage = isLastOutgoingMessage,
                onReadReceiptsClick = onReadReceiptsClick,
                eventSink = eventSink,
                modifier = Modifier.fillMaxWidth(),
            )

            if (isEventPinned || event.reactionsState.reactions.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (event.reactionsState.reactions.isNotEmpty()) {
                        TimelineItemReactionsView(
                            reactionsState = event.reactionsState,
                            userCanSendReaction = timelineRoomInfo.userHasPermissionToSendReaction,
                            isOutgoing = event.isMine,
                            onReactionClick = onReactionClick,
                            onReactionLongClick = onReactionLongClick,
                            onMoreReactionsClick = { onMoreReactionsClick(event) },
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .zIndex(1f)
                        )
                    }
                    if (isEventPinned) {
                        Icon(
                            imageVector = CompoundIcons.PinSolid(),
                            contentDescription = stringResource(CommonStrings.common_pinned),
                            tint = ElementTheme.colors.iconTertiary,
                            modifier = Modifier
                                .padding(1.dp)
                                .size(16.dp)
                                .align(Alignment.CenterEnd)
                        )
                    }
                }
            }

            if (hasThreadSummary) {
                ThreadSummaryView(
                    modifier = Modifier.fillMaxWidth(),
                    threadSummary = event.threadInfo.summary,
                    latestEventText = event.threadInfo.latestEventText,
                    onClick = {
                        event.eventId?.let {
                            eventSink(TimelineEvent.OpenThread(it.toThreadId(), null))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun TimelineEventMetadataRow(
    event: TimelineItem.Event,
    renderReadReceipts: Boolean,
    isLastOutgoingMessage: Boolean,
    onReadReceiptsClick: () -> Unit,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 1.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InlineReadReceiptView(
            state = ReadReceiptViewState(
                sendState = if (event.isMine) event.localSendState else null,
                isLastOutgoingMessage = event.isMine && isLastOutgoingMessage,
                receipts = event.readReceiptState.receipts,
            ),
            renderReadReceipts = renderReadReceipts,
            onReadReceiptsClick = onReadReceiptsClick,
            modifier = Modifier.padding(end = 4.dp),
        )
        TimelineEventTimestampView(
            event = event,
            eventSink = eventSink,
        )
    }
}

@Composable
private fun MessageSenderInformation(
    senderId: UserId,
    senderProfile: ProfileDetails,
    senderAvatar: AvatarData,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val avatarColors = AvatarColorsProvider.provide(senderAvatar.id)
    Row(
        modifier = modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            modifier = Modifier
                .testTag(TestTags.timelineItemSenderAvatar)
                .clip(CircleShape)
                .clickable(onClick = onClick),
            avatarData = senderAvatar,
            avatarType = AvatarType.User,
        )
        Spacer(modifier = Modifier.width(10.dp))
        SenderName(
            modifier = Modifier
                .weight(1f, fill = false)
                .testTag(TestTags.timelineItemSenderName)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp),
            senderId = senderId,
            senderProfile = senderProfile,
            senderNameMode = SenderNameMode.Timeline(avatarColors.foreground),
        )
    }
}

@Suppress("MultipleEmitters") // False positive
@Composable
private fun MessageEventBubbleContent(
    event: TimelineItem.Event,
    timelineMode: Timeline.Mode,
    timelineProtectionState: TimelineProtectionState,
    onMessageLongClick: () -> Unit,
    inReplyToClick: () -> Unit,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    contentPaddingPolicy: TimelineContentPaddingPolicy,
    // When true the content has no surrounding bubble, so the bubble's internal left padding is
    // dropped from the reply preview and the textual body. This lines their left edge up with the
    // shared timeline content column.
    alignContentToStart: Boolean = false,
    @SuppressLint("ModifierParameter")
    // need to rename this modifier to prevent linter false positives
    @Suppress("ModifierNaming")
    bubbleModifier: Modifier = Modifier,
    eventContentView: @Composable (Modifier) -> Unit,
) {
    @Composable
    fun ThreadDecoration(
        modifier: Modifier = Modifier
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.Start),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                modifier = Modifier.height(14.dp),
                imageVector = CompoundIcons.Threads(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
            )
            Text(
                text = stringResource(CommonStrings.common_thread),
                style = ElementTheme.typography.fontBodyXsRegular,
                color = ElementTheme.colors.textPrimary,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }

    @Composable
    fun CommonLayout(
        showThreadDecoration: Boolean,
        paddingBehaviour: ContentPadding,
        inReplyToDetails: InReplyToDetails?,
        modifier: Modifier = Modifier,
    ) {
        val topPadding = if (inReplyToDetails != null) 0.dp else 8.dp
        val textualStartPadding = if (alignContentToStart) 0.dp else 12.dp
        val contentModifier = when (paddingBehaviour) {
            ContentPadding.Textual ->
                Modifier.padding(start = textualStartPadding, end = 12.dp, top = topPadding, bottom = 8.dp)
            ContentPadding.Media -> {
                if (inReplyToDetails == null) {
                    Modifier
                } else {
                    Modifier.clip(RoundedCornerShape(10.dp))
                }
            }
            ContentPadding.CaptionedMedia ->
                Modifier.padding(start = 8.dp, end = 8.dp, top = topPadding, bottom = 8.dp)
        }

        val threadDecoration = @Composable {
            if (showThreadDecoration) {
                ThreadDecoration(modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp))
            }
        }
        val eventContent = @Composable {
            Box(
                modifier = Modifier.semantics(mergeDescendants = false) {
                    isTraversalGroup = true
                    traversalIndex = -1f
                }
            ) {
                eventContentView(contentModifier)
            }
        }

        val inReplyTo = @Composable { inReplyTo: InReplyToDetails ->
            val topPadding = if (showThreadDecoration) 0.dp else 8.dp
            // Align the reply preview's left edge with the message content column when standalone.
            val replyStartPadding = if (alignContentToStart) 0.dp else 8.dp
            val inReplyToModifier = Modifier
                .padding(top = topPadding, start = replyStartPadding, end = 8.dp)

            val talkbackCompatModifier = if (isTalkbackActive()) {
                // Use z-index to make the replied to text being read after the message
                // Usually, you'd use traversalIndex for that, but it's not working for some reason
                inReplyToModifier.zIndex(1f)
            } else {
                inReplyToModifier.clickable(onClick = inReplyToClick)
            }
            // iOS-parity timeline style: accent bar + sender pill + preview, no bordered card.
            Box(modifier = talkbackCompatModifier) {
                InReplyToView(
                    inReplyTo = inReplyTo,
                    hideImage = timelineProtectionState.hideMediaContent(inReplyTo.eventId()),
                    style = InReplyToViewStyle.Timeline,
                )
            }
        }
        if (inReplyToDetails != null) {
            EqualWidthColumn(spacing = 8.dp) {
                threadDecoration()
                inReplyTo(inReplyToDetails)
                eventContent()
            }
        } else {
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                threadDecoration()
                eventContent()
            }
        }
    }

    val paddingBehaviour = when (contentPaddingPolicy) {
        TimelineContentPaddingPolicy.Textual -> ContentPadding.Textual
        TimelineContentPaddingPolicy.Media -> ContentPadding.Media
        TimelineContentPaddingPolicy.CaptionedMedia -> ContentPadding.CaptionedMedia
    }
    CommonLayout(
        showThreadDecoration = timelineMode !is Timeline.Mode.Thread && event.threadInfo is TimelineItemThreadInfo.ThreadResponse,
        paddingBehaviour = paddingBehaviour,
        inReplyToDetails = event.inReplyTo,
        modifier = bubbleModifier,
    )
}

@PreviewsDayNight
@Composable
internal fun TimelineItemEventRowPreview() = ElementPreview {
    Column {
        sequenceOf(false, true).forEach { isMine ->
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    senderDisplayName = "Sender with a super long name that should ellipsize",
                    isMine = isMine,
                    content = aTimelineItemTextContent(
                        body = "A long text which will be displayed on several lines and" +
                            " hopefully can be manually adjusted to test different behaviors."
                    ),
                    groupPosition = TimelineItemGroupPosition.First,
                ),
            )
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    isMine = isMine,
                    content = aTimelineItemImageContent(
                        aspectRatio = 2.5f
                    ),
                    groupPosition = TimelineItemGroupPosition.Last,
                ),
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemEventRowWithThreadSummaryPreview() = ElementPreview {
    Column {
        sequenceOf(false, true).forEach { isMine ->
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    senderDisplayName = "Sender with a super long name that should ellipsize",
                    isMine = isMine,
                    content = aTimelineItemTextContent(
                        body = "A long text which will be displayed on several lines and" +
                            " hopefully can be manually adjusted to test different behaviors."
                    ),
                    groupPosition = TimelineItemGroupPosition.First,
                    threadInfo = TimelineItemThreadInfo.ThreadRoot(
                        latestEventText = "This is the latest message in the thread",
                        summary = ThreadSummary(
                            latestEvent = AsyncData.Success(
                                EmbeddedEventInfo(
                                    eventOrTransactionId = EventOrTransactionId.Event(EventId("\$event-id")),
                                    content = MessageContent(
                                        body = "This is the latest message in the thread",
                                        inReplyTo = null,
                                        isEdited = false,
                                        threadInfo = null,
                                        type = TextMessageType("This is the latest message in the thread", null)
                                    ),
                                    senderId = UserId("@user:id"),
                                    senderProfile = ProfileDetails.Ready(
                                        displayName = USER_NAME_ALICE,
                                        avatarUrl = null,
                                        displayNameAmbiguous = false,
                                    ),
                                    timestamp = 0L,
                                )
                            ),
                            numberOfReplies = 20L,
                        )
                    )
                ),
                displayThreadSummaries = true,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ThreadSummaryViewPreview() {
    ElementPreview {
        val body = "This is the latest message in the thread"
        val threadSummary = ThreadSummary(
            AsyncData.Success(
                EmbeddedEventInfo(
                    eventOrTransactionId = EventOrTransactionId.Event(EventId("\$event-id")),
                    content = MessageContent(
                        body = body,
                        inReplyTo = null,
                        isEdited = false,
                        threadInfo = null,
                        type = TextMessageType(body, null)
                    ),
                    senderId = UserId("@user:id"),
                    senderProfile = ProfileDetails.Ready(
                        displayName = USER_NAME_ALICE,
                        avatarUrl = null,
                        displayNameAmbiguous = true,
                    ),
                    timestamp = 0L,
                )
            ),
            numberOfReplies = 12,
        )

        ThreadSummaryView(
            threadSummary = threadSummary,
            latestEventText = "Some event with a very long text that should get clipped",
            onClick = {},
        )
    }
}
