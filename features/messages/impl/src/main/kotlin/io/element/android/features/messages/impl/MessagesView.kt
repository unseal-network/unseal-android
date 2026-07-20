/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.api.AudienceAccessMode
import io.element.android.features.location.api.LiveLocationSharingBanner
import io.element.android.features.messages.api.timeline.voicemessages.composer.VoiceMessageComposerEvent
import io.element.android.features.messages.impl.actionlist.ActionListEvent
import io.element.android.features.messages.impl.actionlist.ActionListView
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.crypto.identity.IdentityChangeStateView
import io.element.android.features.messages.impl.link.LinkEvent
import io.element.android.features.messages.impl.link.LinkView
import io.element.android.features.messages.impl.messagecomposer.AttachmentsBottomSheet
import io.element.android.features.messages.impl.messagecomposer.DisabledComposerView
import io.element.android.features.messages.impl.messagecomposer.MessageComposerEvent
import io.element.android.features.messages.impl.messagecomposer.MessageComposerView
import io.element.android.features.messages.impl.messagecomposer.skills.ComposerAgentSkillPickerView
import io.element.android.features.messages.impl.messagecomposer.suggestions.SuggestionsPickerView
import io.element.android.features.messages.impl.pinned.banner.PinnedMessagesBannerState
import io.element.android.features.messages.impl.pinned.banner.PinnedMessagesBannerView
import io.element.android.features.messages.impl.pinned.banner.PinnedMessagesBannerViewDefaults
import io.element.android.features.messages.impl.roomdata.RoomDeviceAgent
import io.element.android.features.messages.impl.roomdata.RoomMenuRenderModel
import io.element.android.features.messages.impl.roomdata.RoomTopbarAction
import io.element.android.features.messages.impl.roomdata.RoomTopbarToolRenderModel
import io.element.android.features.messages.impl.terminal.DeviceAgentTerminalPanel
import io.element.android.features.messages.impl.timeline.FOCUS_ON_PINNED_EVENT_DEBOUNCE_DURATION_IN_MILLIS
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.TimelineView
import io.element.android.features.messages.impl.timeline.aGroupedEvents
import io.element.android.features.messages.impl.timeline.aTimelineItemDaySeparator
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineState
import io.element.android.features.messages.impl.timeline.components.customreaction.CustomReactionBottomSheet
import io.element.android.features.messages.impl.timeline.components.customreaction.CustomReactionEvent
import io.element.android.features.messages.impl.timeline.components.reactionsummary.ReactionSummaryEvent
import io.element.android.features.messages.impl.timeline.components.reactionsummary.ReactionSummaryView
import io.element.android.features.messages.impl.timeline.components.receipt.bottomsheet.ReadReceiptBottomSheet
import io.element.android.features.messages.impl.timeline.components.receipt.bottomsheet.ReadReceiptBottomSheetEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemStateEventContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.topbars.MessagesViewTopBar
import io.element.android.features.messages.impl.topbars.ThreadTopBar
import io.element.android.features.messages.impl.voicemessages.composer.VoiceMessagePermissionRationaleDialog
import io.element.android.features.messages.impl.voicemessages.composer.VoiceMessageSendingFailedDialog
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.libraries.androidutils.ui.hideKeyboard
import io.element.android.libraries.designsystem.atomic.molecules.ComposerAlertMolecule
import io.element.android.libraries.designsystem.components.ExpandableBottomSheetLayout
import io.element.android.libraries.designsystem.components.ExpandableBottomSheetLayoutState
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.components.rememberExpandableBottomSheetLayoutState
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.text.toAnnotatedString
import io.element.android.libraries.designsystem.text.toDp
import io.element.android.libraries.designsystem.theme.components.BottomSheetDragHandle
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.utils.HideKeyboardWhenDisposed
import io.element.android.libraries.designsystem.utils.KeepScreenOn
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.identity.IdentityState
import io.element.android.libraries.matrix.api.room.tombstone.SuccessorRoom
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.textcomposer.model.TextEditorState
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.wysiwyg.link.Link
import kotlinx.collections.immutable.persistentListOf
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

private val DefaultTimelineTopChromeInset = 132.dp

@Composable
fun MessagesView(
    state: MessagesState,
    onBackClick: () -> Unit,
    onRoomDetailsClick: () -> Unit,
    onEventContentClick: (isLive: Boolean, event: TimelineItem.Event) -> Boolean,
    onUserDataClick: (UserId) -> Unit,
    onLinkClick: (String, Boolean) -> Unit,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onJoinAudienceClick: (broadcastId: String) -> Unit,
    onStartCallWithListeners: (AudienceAccessMode) -> Unit = {},
    onSetAudienceRelay: (AudienceAccessMode?) -> Unit = {},
    onRoomSchedulesClick: () -> Unit,
    onRoomWebhooksClick: () -> Unit = {},
    onViewAllPinnedMessagesClick: () -> Unit,
    onThreadsListClick: () -> Unit,
    onDeviceAgentChatClick: (RoomDeviceAgent) -> Unit = {},
    onDeviceAgentTerminalClick: (RoomDeviceAgent) -> Unit = {},
    modifier: Modifier = Modifier,
    forceJumpToBottomVisibility: Boolean = false,
    knockRequestsBannerView: @Composable () -> Unit,
) {
    OnLifecycleEvent { _, event ->
        state.voiceMessageComposerState.eventSink(VoiceMessageComposerEvent.LifecycleEvent(event))
    }

    KeepScreenOn(state.voiceMessageComposerState.keepScreenOn)

    HideKeyboardWhenDisposed()

    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)

    var maxComposerHeightPx by remember { mutableIntStateOf(120) }

    val density = LocalDensity.current
    var composerHeightDp by remember { mutableStateOf(80.dp) }
    var topBarHeightDp by remember { mutableStateOf(DefaultTimelineTopChromeInset) }

    // This is needed because the composer is inside an AndroidView that can't be affected by the FocusManager in Compose
    val localView = LocalView.current

    fun hidingKeyboard(block: () -> Unit) {
        localView.hideKeyboard()
        block()
    }

    fun onContentClick(event: TimelineItem.Event) {
        Timber.v("onMessageClick= ${event.id}")
        val hideKeyboard = onEventContentClick(state.timelineState.isLive, event)
        if (hideKeyboard) {
            localView.hideKeyboard()
        }
    }

    fun onMessageLongClick(event: TimelineItem.Event) {
        Timber.v("OnMessageLongClicked= ${event.id}")
        hidingKeyboard {
            state.actionListState.eventSink(
                ActionListEvent.ComputeForMessage(
                    event = event,
                    userEventPermissions = state.userEventPermissions,
                )
            )
        }
    }

    fun onActionSelected(action: TimelineItemAction, event: TimelineItem.Event) {
        state.eventSink(MessagesEvent.HandleAction(action, event))
    }

    fun onEmojiReactionClick(emoji: String, event: TimelineItem.Event) {
        state.eventSink(MessagesEvent.ToggleReaction(emoji, event.eventOrTransactionId))
    }

    fun onEmojiReactionLongClick(emoji: String, event: TimelineItem.Event) {
        if (event.eventId == null) return
        state.reactionSummaryState.eventSink(ReactionSummaryEvent.ShowReactionSummary(event.eventId, event.reactionsState.reactions, emoji))
    }

    fun onMoreReactionsClick(event: TimelineItem.Event) {
        state.customReactionState.eventSink(CustomReactionEvent.ShowCustomReactionSheet(event))
    }

    val expandableState = rememberExpandableBottomSheetLayoutState()
    ExpandableBottomSheetLayout(
        modifier = modifier
                .fillMaxSize()
                .imePadding()
                .systemBarsPadding()
                .onSizeChanged { size ->
                    // Let the composer takes at max half of the available height.
                    // The value will be different if the soft keyboard is displayed
                    // or not.
                    maxComposerHeightPx = (size.height * 0.5f).toInt()
                },
        content = {
            Scaffold(
                contentWindowInsets = WindowInsets(0.dp),
                topBar = {
                    if (state.timelineState.timelineMode is Timeline.Mode.Thread) {
                        ThreadTopBar(
                            roomName = state.roomName,
                            roomAvatarData = state.roomAvatar,
                            heroes = state.heroes,
                            isTombstoned = state.isTombstoned,
                            onBackClick = onBackClick,
                        )
                    }
                },
                content = { padding ->
                    val composerBottomInset = maxOf(0.dp, composerHeightDp - ComposerFadeZone)
                    Box(
                        modifier = Modifier
                                .padding(padding)
                                .consumeWindowInsets(padding)
                    ) {
                        MessagesViewContent(
                            state = state,
                            onContentClick = ::onContentClick,
                            onMessageLongClick = ::onMessageLongClick,
                            onUserDataClick = {
                                hidingKeyboard {
                                    state.eventSink(MessagesEvent.OnUserClicked(it))
                                }
                            },
                            onLinkClick = { link, customTab ->
                                if (customTab) {
                                    onLinkClick(link.url, true)
                                    // Do not check those links, they are internal link only
                                } else {
                                    state.linkState.eventSink(LinkEvent.OnLinkClick(link))
                                }
                            },
                            onReactionClick = ::onEmojiReactionClick,
                            onReactionLongClick = ::onEmojiReactionLongClick,
                            onMoreReactionsClick = ::onMoreReactionsClick,
                            onReadReceiptClick = { event ->
                                state.readReceiptBottomSheetState.eventSink(ReadReceiptBottomSheetEvent.EventSelected(event))
                            },
                            onSendLocationClick = onSendLocationClick,
                            onCreatePollClick = onCreatePollClick,
                            onSwipeToReply = { targetEvent ->
                                state.eventSink(MessagesEvent.HandleAction(TimelineItemAction.Reply, targetEvent))
                            },
                            forceJumpToBottomVisibility = forceJumpToBottomVisibility,
                            onViewAllPinnedMessagesClick = onViewAllPinnedMessagesClick,
                            knockRequestsBannerView = knockRequestsBannerView,
                            // Reserve the composer height minus the fade zone, so the newest message
                            // extends into the composer's transparent top zone (never behind the opaque
                            // input pill) where the gradient below fades it out.
                            composerBottomInset = composerBottomInset,
                            bottomContentPadding = (composerHeightDp - composerBottomInset).coerceAtLeast(0.dp),
                            topChromeInset = topBarHeightDp,
                        )

                        // Gradient-transparent backdrop: the last message fades from fully visible to the
                        // solid canvas colour within the composer's top fade zone. Pure Compose gradient
                        // over the timeline — no AndroidView overlap, no scroll cost.
                        ComposerChromeBackdrop(
                            composerHeight = composerHeightDp,
                            useSolidBackground = state.composerState.showTextFormatting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(composerHeightDp)
                                .align(Alignment.BottomCenter)
                        )

                        if (state.timelineState.timelineMode !is Timeline.Mode.Thread) {
                            MessagesViewTopBar(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .onSizeChanged { size ->
                                        topBarHeightDp = with(density) { size.height.toDp() }
                                    },
                                roomName = state.roomName,
                                roomAvatar = state.roomAvatar,
                                isTombstoned = state.isTombstoned,
                                heroes = state.heroes,
                                dmUserIdentityState = state.dmUserVerificationState,
                                sharedHistoryIcon = state.topBarSharedHistoryIcon,
                                onBackClick = { hidingKeyboard { onBackClick() } },
                                onRoomDetailsClick = { hidingKeyboard { onRoomDetailsClick() } },
                                menuActions = {
                                    MessagesMenuActions(
                                        roomMenu = state.roomMenu,
                                        roomCallState = state.roomCallState,
                                        onJoinCallClick = onJoinCallClick,
                                        onJoinAudienceClick = onJoinAudienceClick,
                                        onStartCallWithListeners = onStartCallWithListeners,
                                        onSetAudienceRelay = onSetAudienceRelay,
                                        onRoomSchedulesClick = onRoomSchedulesClick,
                                        onRoomWebhooksClick = onRoomWebhooksClick,
                                        onThreadsListClick = onThreadsListClick,
                                        onDeviceAgentChatClick = {
                                            state.eventSink(MessagesEvent.ToggleDeviceAgentChat(it))
                                            onDeviceAgentChatClick(it)
                                        },
                                        onDeviceAgentTerminalClick = {
                                            state.eventSink(MessagesEvent.OpenDeviceAgentTerminal(it))
                                            onDeviceAgentTerminalClick(it)
                                        },
                                    )
                                }
                            )
                        }

                        SuggestionsPickerView(
                            modifier = Modifier
                                    .shadow(10.dp)
                                    .background(ElementTheme.colors.bgCanvasDefault)
                                    .align(Alignment.BottomStart)
                                    .heightIn(max = 230.dp),
                            roomId = state.roomId,
                            roomName = state.roomName,
                            roomAvatarData = state.roomAvatar,
                            suggestions = state.composerState.suggestions,
                            suggestionRenderModels = state.composerState.suggestionRenderModels,
                            onSelectSuggestion = {
                                state.composerState.eventSink(MessageComposerEvent.InsertSuggestion(it))
                            }
                        )

                        DeviceAgentTerminalPanel(
                            panel = state.deviceAgentTerminalPanel,
                            onExpand = {
                                state.deviceAgentTerminalPanel?.deviceAgent?.let {
                                    state.eventSink(MessagesEvent.OpenDeviceAgentTerminal(it))
                                }
                            },
                            onDismiss = { state.eventSink(MessagesEvent.DismissDeviceAgentTerminal) },
                            onOpen = { state.eventSink(MessagesEvent.OpenDeviceAgentTerminalSession) },
                            onInputChange = { state.eventSink(MessagesEvent.UpdateDeviceAgentTerminalInput(it)) },
                            onSendInput = { state.eventSink(MessagesEvent.SendDeviceAgentTerminalInput) },
                            onCloseSession = { state.eventSink(MessagesEvent.CloseDeviceAgentTerminalSession) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 20.dp, vertical = 96.dp),
                        )
                    }
                },
                snackbarHost = {
                    SnackbarHost(
                        snackbarHostState,
                        modifier = Modifier.navigationBarsPadding()
                    )
                },
            )
        },
        bottomSheetContent = {
            MessagesViewComposerBottomSheetContents(
                state = state,
                onLinkClick = { url, customTab -> onLinkClick(url, customTab) },
                onRoomSuccessorClick = { roomId ->
                    state.timelineState.eventSink(TimelineEvent.NavigateToPredecessorOrSuccessorRoom(roomId = roomId))
                },
                onHeightChanged = { composerHeightDp = it },
            )
        },
        sheetDragHandle = @Composable { toggleAction ->
            if (state.composerState.showTextFormatting) {
                val expandA11yLabel = stringResource(CommonStrings.a11y_expand_message_text_field)
                val collapseA11yLabel = stringResource(CommonStrings.a11y_collapse_message_text_field)
                BottomSheetDragHandle(
                    modifier = Modifier.semantics {
                        role = Role.Button
                        // Accessibility action to toggle the bottom sheet state
                        val label = when (expandableState.position) {
                            ExpandableBottomSheetLayoutState.Position.COLLAPSED, ExpandableBottomSheetLayoutState.Position.DRAGGING -> expandA11yLabel
                            ExpandableBottomSheetLayoutState.Position.EXPANDED -> collapseA11yLabel
                        }
                        onClick(label) {
                            toggleAction()
                            true
                        }
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    // Ensure that the bottom sheet is collapsed
                    if (expandableState.position == ExpandableBottomSheetLayoutState.Position.EXPANDED) {
                        toggleAction()
                    }
                }
            }
        },
        isSwipeGestureEnabled = state.composerState.showTextFormatting,
        state = expandableState,
        sheetShape = if (state.composerState.showTextFormatting || state.composerState.suggestions.isNotEmpty()) {
            MaterialTheme.shapes.large
        } else {
            RectangleShape
        },
        overlayBottomSheet = state.timelineState.timelineMode !is Timeline.Mode.Thread,
        maxBottomSheetContentHeight = maxComposerHeightPx.toDp(),
    )

    var endPollConfirmingEvent: TimelineItem.Event? by remember { mutableStateOf(null) }

    if (endPollConfirmingEvent != null) {
        ConfirmationDialog(
            content = stringResource(id = CommonStrings.common_poll_end_confirmation),
            onSubmitClick = {
                endPollConfirmingEvent?.let { event ->
                    onActionSelected(TimelineItemAction.EndPoll, event)
                }
                endPollConfirmingEvent = null
            },
            onDismiss = { endPollConfirmingEvent = null },
        )
    }

    ActionListView(
        state = state.actionListState,
        onSelectAction = { action: TimelineItemAction, event: TimelineItem.Event ->
            if (action == TimelineItemAction.EndPoll) {
                endPollConfirmingEvent = event
            } else {
                onActionSelected(action, event)
            }
        },
        onCustomReactionClick = { event ->
            state.customReactionState.eventSink(CustomReactionEvent.ShowCustomReactionSheet(event))
        },
        onEmojiReactionClick = ::onEmojiReactionClick,
        onVerifiedUserSendFailureClick = { event ->
            state.timelineState.eventSink(TimelineEvent.ComputeVerifiedUserSendFailure(event))
        },
    )

    CustomReactionBottomSheet(
        state = state.customReactionState,
        onSelectEmoji = { uniqueId, emoji ->
            state.eventSink(MessagesEvent.ToggleReaction(emoji.unicode, uniqueId))
        }
    )

    ReactionSummaryView(state = state.reactionSummaryState)
    ReadReceiptBottomSheet(
        state = state.readReceiptBottomSheetState,
        onUserDataClick = onUserDataClick,
    )
    ReinviteDialog(state = state)
    LinkView(
        onLinkValid = { link ->
            onLinkClick(link.url, false)
        },
        state = state.linkState,
    )
    SelectableMessageTextDialog(
        text = state.selectableMessageText,
        onDismiss = { state.eventSink(MessagesEvent.DismissSelectableMessageText) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectableMessageTextDialog(
    text: String?,
    onDismiss: () -> Unit,
) {
    if (text == null) return
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = ElementTheme.colors.bgCanvasDefault,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 280.dp, max = 560.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(CommonStrings.action_select_text),
                    style = ElementTheme.typography.fontHeadingMdBold,
                    color = ElementTheme.colors.textPrimary,
                )
                SelectionContainer {
                    Text(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        text = text,
                        style = ElementTheme.typography.fontBodyLgRegular,
                        color = ElementTheme.colors.textPrimary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = stringResource(CommonStrings.action_ok))
                    }
                }
            }
        }
    }
}

@Composable
internal fun RowScope.MessagesMenuActions(
    roomMenu: RoomMenuRenderModel,
    roomCallState: RoomCallState,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onJoinAudienceClick: (broadcastId: String) -> Unit,
    onStartCallWithListeners: (AudienceAccessMode) -> Unit = {},
    onSetAudienceRelay: (AudienceAccessMode?) -> Unit = {},
    onRoomSchedulesClick: () -> Unit,
    onRoomWebhooksClick: () -> Unit = {},
    onThreadsListClick: () -> Unit,
    onDeviceAgentChatClick: (RoomDeviceAgent) -> Unit = {},
    onDeviceAgentTerminalClick: (RoomDeviceAgent) -> Unit = {},
) {
    RoomCallButton(
        roomCallState = roomCallState,
        onJoinCallClick = onJoinCallClick,
        onJoinAudienceClick = onJoinAudienceClick,
        onStartCallWithListeners = onStartCallWithListeners,
        onSetAudienceRelay = onSetAudienceRelay,
    )
    RoomToolMenu(
        roomMenu = roomMenu,
        onRoomSchedulesClick = onRoomSchedulesClick,
        onRoomWebhooksClick = onRoomWebhooksClick,
        onDeviceAgentChatClick = onDeviceAgentChatClick,
        onDeviceAgentTerminalClick = onDeviceAgentTerminalClick,
    )
}

@Composable
private fun RoomCallButton(
    roomCallState: RoomCallState,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onJoinAudienceClick: (broadcastId: String) -> Unit,
    onStartCallWithListeners: (AudienceAccessMode) -> Unit,
    onSetAudienceRelay: (AudienceAccessMode?) -> Unit,
) {
    var showMeetingEntry by remember { mutableStateOf(false) }
    var showMeetingJoin by remember { mutableStateOf(false) }
    var showListenerSettings by remember { mutableStateOf(false) }
    LaunchedEffect(roomCallState) {
        when (roomCallState) {
            RoomCallState.Unavailable -> {
                showMeetingEntry = false
                showMeetingJoin = false
                showListenerSettings = false
            }
            is RoomCallState.StandBy -> {
                showMeetingJoin = false
                showListenerSettings = false
            }
            is RoomCallState.OnGoing -> {
                showMeetingEntry = false
                if (roomCallState.audienceBroadcastId == null) showMeetingJoin = false
            }
        }
    }
    when (roomCallState) {
        RoomCallState.Unavailable -> Unit
        is RoomCallState.StandBy -> {
            if (roomCallState.isDM) {
                ToolbarCircleButton(
                    onClick = { onJoinCallClick(true) },
                    enabled = roomCallState.canStartCall,
                ) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        imageVector = CompoundIcons.VoiceCallSolid(),
                        contentDescription = stringResource(CommonStrings.a11y_start_voice_call),
                    )
                }
            }
            ToolbarCircleButton(
                onClick = { showMeetingEntry = true },
                enabled = roomCallState.canStartCall,
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    imageVector = CompoundIcons.VideoCallSolid(),
                    contentDescription = stringResource(CommonStrings.a11y_start_call),
                )
            }
            ListenerToolbarButton(
                onClick = { showMeetingEntry = true },
                enabled = roomCallState.canStartCall,
                contentDescription = stringResource(R.string.a11y_start_meeting_with_listeners),
            )
        }
        is RoomCallState.OnGoing -> {
            val shouldManageListeners = roomCallState.audienceHostControl.isEnabled ||
                roomCallState.canJoinCall &&
                (roomCallState.isUserLocallyInTheCall || roomCallState.audienceBroadcastId == null)
            if (shouldManageListeners) {
                ListenerToolbarButton(
                    onClick = { showListenerSettings = true },
                    enabled = !roomCallState.audienceHostControl.isUpdating,
                    isActive = roomCallState.audienceHostControl.isEnabled,
                    contentDescription = stringResource(R.string.a11y_manage_meeting_listeners),
                )
            } else if (!roomCallState.isUserLocallyInTheCall) {
                roomCallState.audienceBroadcastId?.let { broadcastId ->
                    ListenerToolbarButton(
                        onClick = { onJoinAudienceClick(broadcastId) },
                        enabled = !roomCallState.isAudienceDiscoveryPending,
                        contentDescription = stringResource(R.string.a11y_listen_to_meeting),
                    )
                }
            }
            if (!roomCallState.isUserLocallyInTheCall) {
                ToolbarCircleButton(
                    onClick = {
                        if (roomCallState.audienceBroadcastId == null) {
                            onJoinCallClick(roomCallState.isAudioCall)
                        } else {
                            showMeetingJoin = true
                        }
                    },
                    enabled = !roomCallState.isAudienceDiscoveryPending &&
                        (roomCallState.canJoinCall || roomCallState.audienceBroadcastId != null),
                ) {
                    Icon(
                        modifier = Modifier.size(22.dp),
                        imageVector = if (roomCallState.isAudioCall) {
                            CompoundIcons.VoiceCallSolid()
                        } else {
                            CompoundIcons.VideoCallSolid()
                        },
                        contentDescription = stringResource(CommonStrings.action_join),
                    )
                }
            }
        }
    }
    if (showMeetingJoin && roomCallState is RoomCallState.OnGoing) {
        roomCallState.audienceBroadcastId?.let { broadcastId ->
            MeetingJoinDialog(
                canJoinMeeting = roomCallState.canJoinCall,
                onJoinMeeting = {
                    showMeetingJoin = false
                    onJoinCallClick(roomCallState.isAudioCall)
                },
                onListenOnly = {
                    showMeetingJoin = false
                    onJoinAudienceClick(broadcastId)
                },
                onDismiss = { showMeetingJoin = false },
            )
        }
    }
    if (showMeetingEntry && roomCallState is RoomCallState.StandBy) {
        ListenerMeetingDialog(
            title = stringResource(R.string.listener_meeting_start_title),
            isUpdating = roomCallState.audienceHostControl.isUpdating,
            errorMessage = roomCallState.audienceHostControl.errorMessage,
            showDisable = false,
            onStandard = {
                showMeetingEntry = false
                onJoinCallClick(false)
            },
            onEnable = { mode -> onStartCallWithListeners(mode) },
            onDisable = {},
            onDismiss = { if (!roomCallState.audienceHostControl.isUpdating) showMeetingEntry = false },
        )
    }
    if (showListenerSettings && roomCallState is RoomCallState.OnGoing) {
        ListenerMeetingDialog(
            title = stringResource(R.string.listener_meeting_settings_title),
            isUpdating = roomCallState.audienceHostControl.isUpdating,
            errorMessage = roomCallState.audienceHostControl.errorMessage,
            showDisable = roomCallState.audienceHostControl.isEnabled,
            onStandard = null,
            onEnable = onSetAudienceRelay,
            onDisable = { onSetAudienceRelay(null) },
            onDismiss = { if (!roomCallState.audienceHostControl.isUpdating) showListenerSettings = false },
        )
    }
}

@Composable
private fun ListenerToolbarButton(
    onClick: () -> Unit,
    enabled: Boolean,
    contentDescription: String,
    isActive: Boolean = false,
) {
    ToolbarCircleButton(
        onClick = onClick,
        enabled = enabled,
        isActive = isActive,
    ) {
        Icon(
            modifier = Modifier.size(22.dp),
            imageVector = CompoundIcons.HeadphonesSolid(),
            contentDescription = contentDescription,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun ListenerToolbarButtonPreview() = ElementPreview {
    ListenerToolbarButton(
        onClick = {},
        enabled = true,
        contentDescription = stringResource(R.string.a11y_manage_meeting_listeners),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeetingJoinDialog(
    canJoinMeeting: Boolean,
    onJoinMeeting: () -> Unit,
    onListenOnly: () -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier.padding(24.dp).widthIn(min = 280.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.listener_meeting_join_title),
                    style = ElementTheme.typography.fontHeadingMdBold,
                )
                Text(
                    text = stringResource(R.string.listener_meeting_join_description),
                    color = ElementTheme.colors.textSecondary,
                )
                TextButton(onClick = onJoinMeeting, enabled = canJoinMeeting) {
                    Text(stringResource(R.string.listener_meeting_join_participant))
                }
                TextButton(onClick = onListenOnly) {
                    Text(stringResource(R.string.listener_meeting_join_audience))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(CommonStrings.action_cancel))
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
private fun MeetingJoinDialogPreview() = ElementPreview {
    MeetingJoinDialog(
        canJoinMeeting = true,
        onJoinMeeting = {},
        onListenOnly = {},
        onDismiss = {},
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListenerMeetingDialog(
    title: String,
    isUpdating: Boolean,
    errorMessage: String?,
    showDisable: Boolean,
    onStandard: (() -> Unit)?,
    onEnable: (AudienceAccessMode) -> Unit,
    onDisable: () -> Unit,
    onDismiss: () -> Unit,
) {
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge) {
            Column(
                modifier = Modifier.padding(24.dp).widthIn(min = 280.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = title, style = ElementTheme.typography.fontHeadingMdBold)
                Text(
                    text = stringResource(R.string.listener_meeting_access_description),
                    color = ElementTheme.colors.textSecondary,
                )
                errorMessage?.let {
                    Text(text = it, color = ElementTheme.colors.textCriticalPrimary)
                }
                onStandard?.let { action ->
                    TextButton(onClick = action, enabled = !isUpdating) {
                        Text(stringResource(R.string.listener_meeting_standard))
                    }
                }
                TextButton(onClick = { onEnable(AudienceAccessMode.Authenticated) }, enabled = !isUpdating) {
                    Text(stringResource(R.string.listener_meeting_authenticated))
                }
                TextButton(onClick = { onEnable(AudienceAccessMode.RoomMembers) }, enabled = !isUpdating) {
                    Text(stringResource(R.string.listener_meeting_room_members))
                }
                if (showDisable) {
                    TextButton(onClick = onDisable, enabled = !isUpdating) {
                        Text(stringResource(R.string.listener_meeting_disable))
                    }
                }
                if (isUpdating) {
                    Text(
                        text = stringResource(R.string.listener_meeting_arming),
                        color = ElementTheme.colors.textSecondary,
                    )
                } else {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(CommonStrings.action_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomToolMenu(
    roomMenu: RoomMenuRenderModel,
    onRoomSchedulesClick: () -> Unit,
    onRoomWebhooksClick: () -> Unit,
    onDeviceAgentChatClick: (RoomDeviceAgent) -> Unit,
    onDeviceAgentTerminalClick: (RoomDeviceAgent) -> Unit,
) {
    val tools = roomMenu.topbarTools
    if (tools.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    val deviceAgent = roomMenu.deviceAgent
    val topbarAgentChatActive = tools.any { it.action == RoomTopbarAction.DeviceAgentChat && it.isActive }
    val moreRotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = spring(dampingRatio = 0.85f),
        label = "room-tool-menu-rotation",
    )

    Box(contentAlignment = Alignment.TopEnd) {
        ToolbarCircleButton(
            onClick = { expanded = !expanded },
            isActive = topbarAgentChatActive,
            badgeContent = {
                if (topbarAgentChatActive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(ElementTheme.colors.iconSuccessPrimary)
                    )
                }
            },
        ) {
            Icon(
                modifier = Modifier
                    .size(22.dp)
                    .rotate(moreRotation),
                imageVector = CompoundIcons.OverflowHorizontal(),
                contentDescription = stringResource(R.string.screen_room_topbar_tools),
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 248.dp, max = 320.dp),
        ) {
            tools.forEach { tool ->
                RoomTopbarToolMenuItem(
                    tool = tool,
                    deviceAgent = deviceAgent,
                    onClick = {
                        expanded = false
                        when (tool.action) {
                            RoomTopbarAction.DeviceAgentTerminal -> deviceAgent?.let(onDeviceAgentTerminalClick)
                            RoomTopbarAction.DeviceAgentChat -> deviceAgent?.let(onDeviceAgentChatClick)
                            RoomTopbarAction.Webhooks -> onRoomWebhooksClick()
                            RoomTopbarAction.Schedules -> onRoomSchedulesClick()
                            RoomTopbarAction.Threads -> Unit
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RoomTopbarToolMenuItem(
    tool: RoomTopbarToolRenderModel,
    deviceAgent: RoomDeviceAgent?,
    onClick: () -> Unit,
) {
    val enabled = tool.isEnabled && when (tool.action) {
        RoomTopbarAction.DeviceAgentTerminal,
        RoomTopbarAction.DeviceAgentChat -> deviceAgent != null
        RoomTopbarAction.Schedules,
        RoomTopbarAction.Webhooks -> true
        RoomTopbarAction.Threads -> false
    }
    val label = when (tool.action) {
        RoomTopbarAction.DeviceAgentTerminal -> stringResource(R.string.screen_room_topbar_remote_terminal)
        RoomTopbarAction.DeviceAgentChat -> stringResource(R.string.screen_room_topbar_device_agent_chat)
        RoomTopbarAction.Webhooks -> stringResource(R.string.screen_room_topbar_webhooks)
        RoomTopbarAction.Schedules -> stringResource(R.string.screen_room_topbar_ai_config)
        RoomTopbarAction.Threads -> stringResource(CommonStrings.common_threads)
    }
    val deviceStatus = when (tool.action) {
        RoomTopbarAction.DeviceAgentTerminal,
        RoomTopbarAction.DeviceAgentChat -> deviceAgent?.displayName?.takeIf { it.isNotBlank() }
            ?: deviceAgent?.boundDeviceId
            ?: stringResource(R.string.screen_room_topbar_device_agent_waiting)
        RoomTopbarAction.Schedules,
        RoomTopbarAction.Webhooks,
        RoomTopbarAction.Threads -> null
    }
    DropdownMenuItem(
        onClick = onClick,
        enabled = enabled,
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = if (enabled) ElementTheme.colors.textPrimary else ElementTheme.colors.textDisabled,
                    maxLines = 1,
                )
                deviceStatus?.let {
                    Text(
                        text = it,
                        style = ElementTheme.typography.fontBodyXsRegular,
                        color = if (enabled) ElementTheme.colors.textSecondary else ElementTheme.colors.textDisabled,
                        maxLines = 1,
                    )
                }
            }
        },
        leadingIcon = {
            BadgedBox(
                badge = {
                    tool.badgeCount?.let { count ->
                        Badge(
                            containerColor = when (tool.action) {
                                RoomTopbarAction.Schedules -> Color(0xFF8B5CF6)
                                else -> ElementTheme.colors.iconAccentPrimary
                            },
                            contentColor = Color.White,
                        ) {
                            Text(count.toString())
                        }
                    }
                }
            ) {
                Icon(
                    modifier = Modifier.size(22.dp),
                    tint = when {
                        !enabled -> ElementTheme.colors.iconDisabled
                        tool.isActive -> ElementTheme.colors.iconSuccessPrimary
                        else -> ElementTheme.colors.iconPrimary
                    },
                    imageVector = when (tool.action) {
                        RoomTopbarAction.DeviceAgentTerminal -> CompoundIcons.Code()
                        RoomTopbarAction.DeviceAgentChat -> CompoundIcons.Computer()
                        RoomTopbarAction.Webhooks -> CompoundIcons.Link()
                        RoomTopbarAction.Schedules -> CompoundIcons.Time()
                        RoomTopbarAction.Threads -> CompoundIcons.Threads()
                    },
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun ToolbarCircleButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    isActive: Boolean = false,
    badgeContent: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    BadgedBox(
        badge = badgeContent,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(10.dp, CircleShape, clip = false, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.07f))
                .clip(CircleShape)
                .background(toolbarBubbleBrush(isActive = isActive))
                .toolbarBubbleHighlight()
                .border(1.dp, ElementTheme.colors.borderDisabled.copy(alpha = 0.24f), CircleShape)
                .clickable(
                    enabled = enabled,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true),
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
private fun toolbarBubbleBrush(isActive: Boolean): Brush {
    val topAlpha = if (isActive) 0.92f else 0.78f
    val bottomAlpha = if (isActive) 0.76f else 0.58f
    return Brush.verticalGradient(
        colors = listOf(
            ElementTheme.colors.bgCanvasDefault.copy(alpha = topAlpha),
            ElementTheme.colors.bgCanvasDefault.copy(alpha = bottomAlpha),
        )
    )
}

private fun Modifier.toolbarBubbleHighlight(): Modifier = drawBehind {
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
private fun ReinviteDialog(state: MessagesState) {
    if (state.showReinvitePrompt) {
        ConfirmationDialog(
            title = stringResource(id = R.string.screen_room_invite_again_alert_title),
            content = stringResource(id = R.string.screen_room_invite_again_alert_message),
            cancelText = stringResource(id = CommonStrings.action_cancel),
            submitText = stringResource(id = CommonStrings.action_invite),
            onSubmitClick = { state.eventSink(MessagesEvent.InviteDialogDismissed(InviteDialogAction.Invite)) },
            onDismiss = { state.eventSink(MessagesEvent.InviteDialogDismissed(InviteDialogAction.Cancel)) }
        )
    }
}

@Composable
private fun MessagesViewContent(
    state: MessagesState,
    onContentClick: (TimelineItem.Event) -> Unit,
    onUserDataClick: (MatrixUser) -> Unit,
    onLinkClick: (Link, Boolean) -> Unit,
    onReactionClick: (key: String, TimelineItem.Event) -> Unit,
    onReactionLongClick: (key: String, TimelineItem.Event) -> Unit,
    onMoreReactionsClick: (TimelineItem.Event) -> Unit,
    onReadReceiptClick: (TimelineItem.Event) -> Unit,
    onMessageLongClick: (TimelineItem.Event) -> Unit,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    onViewAllPinnedMessagesClick: () -> Unit,
    forceJumpToBottomVisibility: Boolean,
    onSwipeToReply: (TimelineItem.Event) -> Unit,
    composerBottomInset: Dp = 88.dp,
    bottomContentPadding: Dp = 24.dp,
    topChromeInset: Dp = 132.dp,
    modifier: Modifier = Modifier,
    knockRequestsBannerView: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .imePadding(),
    ) {
        AttachmentsBottomSheet(
            state = state.composerState,
            attachmentActions = state.roomMenu.attachmentActionEntries,
            onSendLocationClick = onSendLocationClick,
            onCreatePollClick = onCreatePollClick,
        )

        if (state.voiceMessageComposerState.showPermissionRationaleDialog) {
            VoiceMessagePermissionRationaleDialog(
                onContinue = {
                    state.voiceMessageComposerState.eventSink(VoiceMessageComposerEvent.AcceptPermissionRationale)
                },
                onDismiss = {
                    state.voiceMessageComposerState.eventSink(VoiceMessageComposerEvent.DismissPermissionsRationale)
                },
                appName = state.appName
            )
        }
        if (state.voiceMessageComposerState.showSendFailureDialog) {
            VoiceMessageSendingFailedDialog(
                onDismiss = { state.voiceMessageComposerState.eventSink(VoiceMessageComposerEvent.DismissSendFailureDialog) },
            )
        }

        Box {
            val scrollBehavior = PinnedMessagesBannerViewDefaults.rememberScrollBehavior(
                pinnedMessagesCount = (state.pinnedMessagesBannerState as? PinnedMessagesBannerState.Visible)?.pinnedMessagesCount() ?: 0,
            )
            val density = LocalDensity.current
            var pinnedBannerHeightDp by remember { mutableStateOf(0.dp) }

            TimelineView(
                state = state.timelineState,
                timelineProtectionState = state.timelineProtectionState,
                onUserDataClick = onUserDataClick,
                onLinkClick = { link -> onLinkClick(link, false) },
                onContentClick = onContentClick,
                onMessageLongClick = onMessageLongClick,
                onSwipeToReply = onSwipeToReply,
                onReactionClick = onReactionClick,
                onReactionLongClick = onReactionLongClick,
                onMoreReactionsClick = onMoreReactionsClick,
                onReadReceiptClick = onReadReceiptClick,
                forceJumpToBottomVisibility = forceJumpToBottomVisibility,
                nestedScrollConnection = scrollBehavior.nestedScrollConnection,
                floatingDateTopOffset = pinnedBannerHeightDp,
                composerBottomInset = composerBottomInset,
                bottomContentPadding = bottomContentPadding,
                topChromeInset = topChromeInset,
            )

            if (state.timelineState.timelineMode !is Timeline.Mode.Thread) {
                Column {
                    AnimatedVisibility(
                        visible = state.pinnedMessagesBannerState is PinnedMessagesBannerState.Visible && scrollBehavior.isVisible,
                        modifier = Modifier.onSizeChanged { pinnedBannerHeightDp = with(density) { it.height.toDp() } },
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        fun focusOnPinnedEvent(eventId: EventId) {
                            state.timelineState.eventSink(
                                TimelineEvent.FocusOnEvent(eventId = eventId, debounce = FOCUS_ON_PINNED_EVENT_DEBOUNCE_DURATION_IN_MILLIS.milliseconds)
                            )
                        }
                        PinnedMessagesBannerView(
                            state = state.pinnedMessagesBannerState,
                            onClick = ::focusOnPinnedEvent,
                            onViewAllClick = onViewAllPinnedMessagesClick,
                        )
                    }
                    if (state.showLiveLocationShareBanner) {
                        LiveLocationSharingBanner(
                            onClick = { state.eventSink(MessagesEvent.ShowLiveLocationShare) },
                            onStopClick = { state.eventSink(MessagesEvent.StopLiveLocationShare) }
                        )
                    }
                }
            }

            knockRequestsBannerView()
        }
    }
}

@Composable
private fun MessagesViewComposerBottomSheetContents(
    state: MessagesState,
    onRoomSuccessorClick: (RoomId) -> Unit,
    onLinkClick: (String, Boolean) -> Unit,
    onHeightChanged: (Dp) -> Unit,
) {
    RoomComposerChrome(onHeightChanged = onHeightChanged) {
        when {
            state.successorRoom != null -> {
                SuccessorRoomBanner(roomSuccessor = state.successorRoom, onRoomSuccessorClick = onRoomSuccessorClick)
            }
            state.userEventPermissions.canSendMessage -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Do not show the identity change if user is composing a Rich message or is seeing suggestion(s).
                    if (state.composerState.suggestions.isEmpty() &&
                        state.composerState.textEditorState is TextEditorState.Markdown) {
                        IdentityChangeStateView(
                            state = state.identityChangeState,
                            onLinkClick = onLinkClick,
                        )
                    }
                    val verificationViolation = state.identityChangeState.roomMemberIdentityStateChanges.firstOrNull {
                        it.identityState == IdentityState.VerificationViolation
                    }
                    if (verificationViolation != null) {
                        DisabledComposerView(modifier = Modifier.fillMaxWidth())
                    } else {
                        ComposerAgentSkillPickerView(
                            state = state.composerState.agentSkillState,
                            onTogglePicker = {
                                state.composerState.eventSink(MessageComposerEvent.ToggleAgentSkillPicker)
                            },
                            onReloadPicker = {
                                state.composerState.eventSink(MessageComposerEvent.ReloadAgentSkillPicker)
                            },
                            onSelectTarget = {
                                state.composerState.eventSink(MessageComposerEvent.SelectAgentSkillTarget(it))
                            },
                            onSelectSkill = {
                                state.composerState.eventSink(MessageComposerEvent.SelectAgentSkill(it))
                            },
                            onRemoveSkill = {
                                state.composerState.eventSink(MessageComposerEvent.RemoveSelectedAgentSkill(it))
                            },
                        )
                        MessageComposerView(
                            state = state.composerState,
                            voiceMessageState = state.voiceMessageComposerState,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            else -> {
                CantSendMessageBanner()
            }
        }
    }
}

@Composable
private fun RoomComposerChrome(
    onHeightChanged: (Dp) -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val density = LocalDensity.current
    val bgColor = ElementTheme.colors.bgCanvasDefault
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                onHeightChanged(with(density) { size.height.toDp() })
            }
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
    ) {
        // Transparent top zone the timeline overlaps into; the gradient fades the last message out
        // here, above the opaque input pill, so it reads as a soft fade edge without the pill
        // ever covering content.
        Spacer(Modifier.height(ComposerFadeZone))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor),
            content = content,
        )
    }
}

private val ComposerFadeZone = 36.dp

// Vertical gradient that fades timeline content from fully visible (top) to the solid canvas colour
// (bottom) as it reaches the composer, giving a soft fade transition instead of a hard edge.
// Pure Compose gradient — drawn over the timeline, never causing AndroidView-over-AndroidView cost.
@Composable
private fun ComposerChromeBackdrop(
    composerHeight: Dp,
    useSolidBackground: Boolean,
    modifier: Modifier = Modifier,
) {
    val bgColor = ElementTheme.colors.bgCanvasDefault
    if (useSolidBackground) {
        Box(modifier = modifier.background(bgColor))
        return
    }
    // The gradient backdrop is composerHeight tall; the timeline overlaps only the top ComposerFadeZone
    // of it. Place the "fully opaque" stop at exactly that fraction so the fade finishes right where the
    // input pill begins — regardless of composer height (single line, multi-line, reply preview, etc.).
    val fadeFraction = if (composerHeight > 0.dp) {
        (ComposerFadeZone / composerHeight).coerceIn(0.05f, 0.95f)
    } else {
        0.3f
    }
    val brush = remember(bgColor, fadeFraction) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0.0f to bgColor.copy(alpha = 0f),
                fadeFraction * 0.6f to bgColor.copy(alpha = 0.8f),
                fadeFraction to bgColor,
                1.0f to bgColor,
            )
        )
    }
    Box(modifier = modifier.background(brush))
}

@Composable
private fun CantSendMessageBanner() {
    Row(
        modifier = Modifier
                .fillMaxWidth()
                .background(ElementTheme.colors.bgSubtleSecondary)
                .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(id = R.string.screen_room_timeline_no_permission_to_post),
            color = ElementTheme.colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic,
        )
    }
}

@Composable
private fun SuccessorRoomBanner(
    roomSuccessor: SuccessorRoom,
    onRoomSuccessorClick: (RoomId) -> Unit,
    modifier: Modifier = Modifier,
) {
    ComposerAlertMolecule(
        avatar = null,
        content = stringResource(R.string.screen_room_timeline_tombstoned_room_message).toAnnotatedString(),
        onSubmitClick = { onRoomSuccessorClick(roomSuccessor.roomId) },
        modifier = modifier,
        submitText = stringResource(R.string.screen_room_timeline_tombstoned_room_action)
    )
}

@PreviewsDayNight
@Composable
internal fun MessagesViewPreview(@PreviewParameter(MessagesStateProvider::class) state: MessagesState) = ElementPreview {
    MessagesView(
        state = state,
        onBackClick = {},
        onRoomDetailsClick = {},
        onEventContentClick = { _, _ -> false },
        onUserDataClick = {},
        onLinkClick = { _, _ -> },
        onSendLocationClick = {},
        onCreatePollClick = {},
        onJoinCallClick = {},
        onJoinAudienceClick = {},
        onRoomSchedulesClick = {},
        onViewAllPinnedMessagesClick = { },
        forceJumpToBottomVisibility = true,
        knockRequestsBannerView = {},
        onThreadsListClick = {},
    )
}

@Preview
@Composable
internal fun MessagesViewA11yPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        body = "A message content"
    )
    MessagesView(
        state = aMessagesState(
            roomName = "A DM with a very looong name",
            dmUserVerificationState = IdentityState.VerificationViolation,
            timelineState = aTimelineState(
                timelineItems = persistentListOf(
                    // 1 items with isMine = false
                    aTimelineItemEvent(
                        isMine = false,
                        content = content,
                        groupPosition = TimelineItemGroupPosition.None,
                        sendState = LocalEventSendState.Failed.Unknown("Message failed to send"),
                    ),
                    // A state event on top of it
                    aTimelineItemEvent(
                        isMine = false,
                        content = aTimelineItemStateEventContent(),
                        groupPosition = TimelineItemGroupPosition.None
                    ),
                    // 1 item with isMine = true
                    aTimelineItemEvent(
                        isMine = true,
                        content = content,
                        groupPosition = TimelineItemGroupPosition.None
                    ),
                    // A grouped event on top of it
                    aGroupedEvents(),
                    // A day separator
                    aTimelineItemDaySeparator(),
                ),
                // Render a focused event for an event with sender information displayed
                focusedEventIndex = 2,
            )
        ),
        onBackClick = {},
        onRoomDetailsClick = {},
        onEventContentClick = { _, _ -> false },
        onUserDataClick = {},
        onLinkClick = { _, _ -> },
        onSendLocationClick = {},
        onCreatePollClick = {},
        onJoinCallClick = {},
        onJoinAudienceClick = {},
        onRoomSchedulesClick = {},
        onViewAllPinnedMessagesClick = {},
        onThreadsListClick = {},
        forceJumpToBottomVisibility = true,
        knockRequestsBannerView = {},
    )
}
