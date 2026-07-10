/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.PinUnpinAction
import io.element.android.appconfig.MessageComposerConfig
import io.element.android.features.location.api.live.ActiveLiveLocationShareManager
import io.element.android.features.location.api.live.isCurrentlySharing
import io.element.android.features.messages.api.timeline.HtmlConverterProvider
import io.element.android.features.messages.impl.MessagesState.Threads
import io.element.android.features.messages.impl.actionlist.ActionListState
import io.element.android.features.messages.impl.actionlist.model.TimelineItemAction
import io.element.android.features.messages.impl.crypto.identity.IdentityChangeState
import io.element.android.features.messages.impl.link.LinkState
import io.element.android.features.messages.impl.messagecomposer.MessageComposerEvent
import io.element.android.features.messages.impl.messagecomposer.MessageComposerState
import io.element.android.features.messages.impl.pinned.banner.PinnedMessagesBannerState
import io.element.android.features.messages.impl.roomdata.AgentChatModeMemoryCache
import io.element.android.features.messages.impl.roomdata.RoomMenuReducer
import io.element.android.features.messages.impl.roomdata.RoomUnsealContext
import io.element.android.features.messages.impl.roomdata.RoomUnsealContextStore
import io.element.android.features.messages.impl.roomdata.RoomUnsealRefreshReason
import io.element.android.features.messages.impl.roomdata.roomUnsealMemberSignature
import io.element.android.features.messages.impl.terminal.DeviceAgentTerminalEvent
import io.element.android.features.messages.impl.terminal.DeviceAgentTerminalPanelState
import io.element.android.features.messages.impl.terminal.DeviceAgentTerminalReducer
import io.element.android.features.messages.impl.terminal.MatrixDeviceAgentTerminalTransport
import io.element.android.features.messages.impl.timeline.MarkAsFullyRead
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.TimelineState
import io.element.android.features.messages.impl.timeline.components.customreaction.CustomReactionState
import io.element.android.features.messages.impl.timeline.components.reactionsummary.ReactionSummaryState
import io.element.android.features.messages.impl.timeline.components.receipt.bottomsheet.ReadReceiptBottomSheetState
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemThreadInfo
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContentWithAttachment
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemPollContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionState
import io.element.android.features.messages.impl.voicemessages.composer.DefaultVoiceMessageComposerPresenter
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.features.roommembermoderation.api.RoomMemberModerationEvents
import io.element.android.features.roommembermoderation.api.RoomMemberModerationState
import io.element.android.features.roomschedules.api.room.RoomScheduleBadgeEvents
import io.element.android.features.roomschedules.api.room.RoomScheduleBadgeState
import io.element.android.libraries.androidutils.clipboard.ClipboardHelper
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.flatMap
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.di.annotations.SessionCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.toThreadId
import io.element.android.libraries.matrix.api.encryption.EncryptionService
import io.element.android.libraries.matrix.api.encryption.identity.IdentityState
import io.element.android.libraries.matrix.api.permalink.PermalinkParser
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.api.room.history.RoomHistoryVisibility
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsAsState
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.EventOrTransactionId
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DMessage
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DMsgType
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DTarget
import io.element.android.libraries.matrix.ui.messages.reply.map
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.room.getDirectRoomMember
import io.element.android.libraries.recentemojis.api.AddRecentEmoji
import io.element.android.libraries.textcomposer.model.MessageComposerMode
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.analytics.api.AnalyticsService
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@AssistedInject
class MessagesPresenter(
    @Assisted private val navigator: MessagesNavigator,
    private val room: JoinedRoom,
    @Assisted private val composerPresenter: Presenter<MessageComposerState>,
    voiceMessageComposerPresenterFactory: DefaultVoiceMessageComposerPresenter.Factory,
    @Assisted private val timelinePresenter: Presenter<TimelineState>,
    private val timelineProtectionPresenter: Presenter<TimelineProtectionState>,
    private val identityChangeStatePresenter: Presenter<IdentityChangeState>,
    private val linkPresenter: Presenter<LinkState>,
    @Assisted private val actionListPresenter: Presenter<ActionListState>,
    private val customReactionPresenter: Presenter<CustomReactionState>,
    private val reactionSummaryPresenter: Presenter<ReactionSummaryState>,
    private val readReceiptBottomSheetPresenter: Presenter<ReadReceiptBottomSheetState>,
    private val pinnedMessagesBannerPresenter: Presenter<PinnedMessagesBannerState>,
    private val roomCallStatePresenter: Presenter<RoomCallState>,
    private val roomMemberModerationPresenter: Presenter<RoomMemberModerationState>,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val dispatchers: CoroutineDispatchers,
    private val clipboardHelper: ClipboardHelper,
    private val htmlConverterProvider: HtmlConverterProvider,
    private val buildMeta: BuildMeta,
    @Assisted private val timelineController: TimelineController,
    private val permalinkParser: PermalinkParser,
    private val analyticsService: AnalyticsService,
    private val encryptionService: EncryptionService,
    private val featureFlagService: FeatureFlagService,
    private val addRecentEmoji: AddRecentEmoji,
    private val markAsFullyRead: MarkAsFullyRead,
    private val liveLocationShareManager: ActiveLiveLocationShareManager,
    private val roomUnsealContextStore: RoomUnsealContextStore,
    private val matrixClient: MatrixClient,
    @SessionCoroutineScope private val sessionCoroutineScope: CoroutineScope,
    @Assisted private val roomConfigChangeRequests: Flow<Unit>,
) : Presenter<MessagesState> {
    @AssistedFactory
    interface Factory {
        fun create(
            navigator: MessagesNavigator,
            composerPresenter: Presenter<MessageComposerState>,
            timelinePresenter: Presenter<TimelineState>,
            actionListPresenter: Presenter<ActionListState>,
            timelineController: TimelineController,
            roomConfigChangeRequests: Flow<Unit>,
        ): MessagesPresenter
    }

    private val voiceMessageComposerPresenter = voiceMessageComposerPresenterFactory.create(
        timelineMode = timelineController.mainTimelineMode()
    )
    private val markingAsReadAndExiting = AtomicBoolean(false)

    @Composable
    override fun present(): MessagesState {
        htmlConverterProvider.Update()

        val coroutineScope = rememberCoroutineScope()
        val roomInfo by room.roomInfoFlow.collectAsState()
        val localCoroutineScope = rememberCoroutineScope()
        val composerState = composerPresenter.present()
        val voiceMessageComposerState = voiceMessageComposerPresenter.present()
        val timelineState = timelinePresenter.present()
        val timelineProtectionState = timelineProtectionPresenter.present()
        val identityChangeState = identityChangeStatePresenter.present()
        val actionListState = actionListPresenter.present()
        val linkState = linkPresenter.present()
        val customReactionState = customReactionPresenter.present()
        val reactionSummaryState = reactionSummaryPresenter.present()
        val readReceiptBottomSheetState = readReceiptBottomSheetPresenter.present()
        val pinnedMessagesBannerState = pinnedMessagesBannerPresenter.present()
        val roomCallState = roomCallStatePresenter.present()
        val roomMemberModerationState = roomMemberModerationPresenter.present()
        val roomUnsealContextState by roomUnsealContextStore.context.collectAsState()
        var activeDeviceAgentBoundDeviceId by remember(room.roomId) {
            mutableStateOf(AgentChatModeMemoryCache.targetDeviceIdFor(room.roomId))
        }
        var deviceAgentTerminalPanel by remember(room.roomId) {
            mutableStateOf<DeviceAgentTerminalPanelState?>(null)
        }
        var deviceAgentTerminalTarget by remember(room.roomId) {
            mutableStateOf<UnsealD2DTarget?>(null)
        }
        val deviceAgentTerminalTransport = remember(matrixClient) {
            MatrixDeviceAgentTerminalTransport(matrixClient)
        }
        var selectableMessageText by remember(room.roomId) {
            mutableStateOf<String?>(null)
        }
        val membersState by room.membersStateFlow.collectAsState()
        val roomMemberSignature = remember(membersState) {
            membersState.roomUnsealMemberSignature()
        }
        val canOpenThreadList by featureFlagService.isFeatureEnabledFlow(FeatureFlags.RoomThreadList).collectAsState(initial = false)
        val threadsList by produceState(persistentListOf(), canOpenThreadList) {
            if (!canOpenThreadList) {
                value = persistentListOf()
                return@produceState
            }
            room.threadsListService.subscribeToItemUpdates()
                .onStart { room.threadsListService.paginate() }
                .collectLatest { value = it.toImmutableList() }
        }
        val isCurrentlySharingLiveLocationInRoom by remember { liveLocationShareManager.isCurrentlySharing(room.roomId) }.collectAsState()

        val userEventPermissions by room.permissionsAsState(UserEventPermissions.DEFAULT) { perms ->
            perms.userEventPermissions()
        }

        val roomAvatar by remember {
            derivedStateOf { roomInfo.avatarData() }
        }
        val heroes by remember {
            derivedStateOf { roomInfo.heroes().toImmutableList() }
        }

        var hasDismissedInviteDialog by rememberSaveable {
            mutableStateOf(false)
        }
        fun handleRoomScheduleBadgeEvent(event: RoomScheduleBadgeEvents) {
            when (event) {
                RoomScheduleBadgeEvents.OnAppear -> if (roomUnsealContextState.isUninitialized()) {
                    coroutineScope.launch { roomUnsealContextStore.refresh(RoomUnsealRefreshReason.Initial) }
                }
                RoomScheduleBadgeEvents.Refresh -> if (!roomUnsealContextState.isLoading()) {
                    coroutineScope.launch { roomUnsealContextStore.refresh(RoomUnsealRefreshReason.ScheduleChanged) }
                }
            }
        }

        val roomScheduleBadgeState = roomUnsealContextState.toRoomScheduleBadgeState(::handleRoomScheduleBadgeEvent)

        LaunchedEffect(Unit) {
            // Remove the unread flag on entering but don't send read receipts
            // as those will be handled by the timeline.
            withContext(dispatchers.io) {
                room.setUnreadFlag(isUnread = false)

                // If for some reason the encryption state is unknown, fetch it
                if (roomInfo.isEncrypted == null) {
                    room.getUpdatedIsEncrypted()
                }
            }
        }
        LaunchedEffect(room.roomId) {
            roomUnsealContextStore.refresh(RoomUnsealRefreshReason.Initial)
        }
        LaunchedEffect(room.roomId) {
            activeDeviceAgentBoundDeviceId?.let { targetDeviceId ->
                composerState.eventSink(MessageComposerEvent.SetAgentChatTargetDeviceId(targetDeviceId))
            }
        }
        LaunchedEffect(roomUnsealContextState, activeDeviceAgentBoundDeviceId) {
            val deviceAgent = roomUnsealContextState.dataOrNull()?.deviceAgentInRoom
            if (activeDeviceAgentBoundDeviceId != null && deviceAgent?.boundDeviceId != activeDeviceAgentBoundDeviceId) {
                activeDeviceAgentBoundDeviceId = null
                AgentChatModeMemoryCache.setTargetDeviceId(room.roomId, null)
                composerState.eventSink(MessageComposerEvent.SetAgentChatTargetDeviceId(null))
            }
            if (deviceAgentTerminalPanel != null && deviceAgent?.boundDeviceId != deviceAgentTerminalPanel?.deviceAgent?.boundDeviceId) {
                deviceAgentTerminalPanel = null
            }
        }
        LaunchedEffect(roomConfigChangeRequests) {
            roomConfigChangeRequests.collectLatest {
                roomUnsealContextStore.refresh(RoomUnsealRefreshReason.RoomConfigChanged)
            }
        }
        LaunchedEffect(roomMemberSignature) {
            if (roomMemberSignature != null && roomUnsealContextState.dataOrNull() != null && !roomUnsealContextState.isLoading()) {
                roomUnsealContextStore.refresh(RoomUnsealRefreshReason.MembersChanged)
            }
        }
        LifecycleResumeEffect(Unit) {
            if (roomUnsealContextState.dataOrNull() != null && !roomUnsealContextState.isLoading()) {
                coroutineScope.launch { roomUnsealContextStore.refresh(RoomUnsealRefreshReason.AppResumed) }
            }
            onPauseOrDispose {}
        }

        val inviteProgress = remember { mutableStateOf<AsyncData<Unit>>(AsyncData.Uninitialized) }
        var showReinvitePrompt by remember { mutableStateOf(false) }
        val composerHasFocus by remember { derivedStateOf { composerState.textEditorState.hasFocus() } }
        LaunchedEffect(hasDismissedInviteDialog, composerHasFocus, roomInfo) {
            withContext(dispatchers.io) {
                showReinvitePrompt = !hasDismissedInviteDialog && composerHasFocus && roomInfo.isDm && roomInfo.activeMembersCount == 1L
            }
        }

        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()

        var dmUserVerificationState by remember { mutableStateOf<IdentityState?>(null) }

        fun reduceDeviceAgentTerminal(event: DeviceAgentTerminalEvent) {
            deviceAgentTerminalPanel = deviceAgentTerminalPanel?.let { panel ->
                DeviceAgentTerminalReducer.reduce(panel, event)
            }
        }

        fun rememberDeviceAgentTerminalTarget(target: UnsealD2DTarget) {
            deviceAgentTerminalTarget = target
            reduceDeviceAgentTerminal(DeviceAgentTerminalEvent.TargetDetected(target))
        }

        val dmRoomMember by room.getDirectRoomMember(membersState)
        val roomMemberIdentityStateChanges = identityChangeState.roomMemberIdentityStateChanges

        // The top bar should show a "history" icon if:
        //   * The room is encrypted, and:
        //   * The room's history_visibility allows future users to see content.
        val topBarSharedHistoryIcon = roomInfo.sharedHistoryIcon()

        LifecycleResumeEffect(dmRoomMember, roomInfo.isEncrypted) {
            if (roomInfo.isEncrypted == true) {
                val dmRoomMemberId = dmRoomMember?.userId
                localCoroutineScope.launch {
                    dmRoomMemberId?.let { userId ->
                        dmUserVerificationState = roomMemberIdentityStateChanges.find { it.identityRoomMember.userId == userId }?.identityState
                            ?: encryptionService.getUserIdentity(userId).getOrNull()
                    }
                }
            }
            onPauseOrDispose {}
        }

        LaunchedEffect(matrixClient, room.roomId) {
            matrixClient.unsealD2DMessages.collect { message ->
                if (!matrixClient.isMe(message.sender)) {
                    return@collect
                }
                val target = message.terminalTarget()
                if (target != null && message.shouldRememberTerminalTarget()) {
                    rememberDeviceAgentTerminalTarget(target)
                }
                val terminalEvent = message.toDeviceAgentTerminalEvent() ?: return@collect
                if (target == null || deviceAgentTerminalPanel.doesNotAcceptTerminalEventFrom(target)) {
                    return@collect
                }
                reduceDeviceAgentTerminal(terminalEvent)
            }
        }

        fun handleEvent(event: MessagesEvent) {
            when (event) {
                is MessagesEvent.HandleAction -> {
                    localCoroutineScope.handleTimelineAction(
                        action = event.action,
                        targetEvent = event.event,
                        composerState = composerState,
                        enableTextFormatting = composerState.showTextFormatting,
                        timelineState = timelineState,
                        timelineProtectionState = timelineProtectionState,
                        onSelectText = { selectableMessageText = it },
                    )
                }
                is MessagesEvent.ToggleReaction -> {
                    localCoroutineScope.toggleReaction(event.emoji, event.eventOrTransactionId)
                }
                is MessagesEvent.InviteDialogDismissed -> {
                    hasDismissedInviteDialog = true

                    if (event.action == InviteDialogAction.Invite) {
                        localCoroutineScope.reinviteOtherUser(inviteProgress)
                    }
                }
                is MessagesEvent.OnUserClicked -> {
                    roomMemberModerationState.eventSink(RoomMemberModerationEvents.ShowActionsForUser(event.user))
                }
                MessagesEvent.StopLiveLocationShare -> {
                    localCoroutineScope.launch {
                        liveLocationShareManager.stopShare(room.roomId)
                            .onFailure {
                                Timber.e(it, "Failed to stop live location share for roomId=${room.roomId}")
                                snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_error))
                            }
                    }
                }
                MessagesEvent.ShowLiveLocationShare -> {
                    navigator.navigateToCurrentLiveLocation()
                }
                is MessagesEvent.ToggleDeviceAgentChat -> {
                    val nextTargetDeviceId = if (activeDeviceAgentBoundDeviceId == event.deviceAgent.boundDeviceId) {
                        null
                    } else {
                        event.deviceAgent.boundDeviceId
                    }
                    activeDeviceAgentBoundDeviceId = nextTargetDeviceId
                    AgentChatModeMemoryCache.setTargetDeviceId(room.roomId, nextTargetDeviceId)
                    composerState.eventSink(MessageComposerEvent.SetAgentChatTargetDeviceId(nextTargetDeviceId))
                }
                is MessagesEvent.OpenDeviceAgentTerminal -> {
                    Timber.i("Device agent terminal requested for boundDeviceId=${event.deviceAgent.boundDeviceId}")
                    val existingPanel = deviceAgentTerminalPanel
                    deviceAgentTerminalPanel = if (existingPanel?.deviceAgent?.boundDeviceId == event.deviceAgent.boundDeviceId) {
                        DeviceAgentTerminalReducer.reduce(existingPanel, DeviceAgentTerminalEvent.ExpandedChanged(true))
                    } else {
                        DeviceAgentTerminalPanelState.waiting(event.deviceAgent, deviceAgentTerminalTarget)
                    }
                }
                MessagesEvent.OpenDeviceAgentTerminalSession -> {
                    val panel = deviceAgentTerminalPanel ?: return
                    val target = panel.target ?: run {
                        reduceDeviceAgentTerminal(DeviceAgentTerminalEvent.Failed("No desktop target is available yet."))
                        return
                    }
                    val requestId = UUID.randomUUID().toString().lowercase()
                    reduceDeviceAgentTerminal(DeviceAgentTerminalEvent.OpenRequested(requestId))
                    localCoroutineScope.launch {
                        deviceAgentTerminalTransport.openTerminal(target, requestId = requestId, cols = 80, rows = 24)
                            .onFailure { error ->
                                reduceDeviceAgentTerminal(DeviceAgentTerminalEvent.Failed("Open failed: ${error.message ?: error}"))
                            }
                    }
                }
                is MessagesEvent.UpdateDeviceAgentTerminalInput -> {
                    reduceDeviceAgentTerminal(DeviceAgentTerminalEvent.InputChanged(event.text))
                }
                MessagesEvent.SendDeviceAgentTerminalInput -> {
                    val panel = deviceAgentTerminalPanel ?: return
                    val target = panel.target ?: return
                    val sessionId = panel.sessionId ?: return
                    val input = panel.inputText.takeIf { it.isNotBlank() } ?: return
                    localCoroutineScope.launch {
                        deviceAgentTerminalTransport.sendInput(target, sessionId, "$input\n")
                            .onSuccess {
                                reduceDeviceAgentTerminal(DeviceAgentTerminalEvent.InputSent)
                            }
                            .onFailure { error ->
                                reduceDeviceAgentTerminal(DeviceAgentTerminalEvent.Failed("Input failed: ${error.message ?: error}"))
                            }
                    }
                }
                MessagesEvent.CloseDeviceAgentTerminalSession -> {
                    val panel = deviceAgentTerminalPanel ?: return
                    val target = panel.target ?: return
                    val sessionId = panel.sessionId ?: return
                    localCoroutineScope.launch {
                        deviceAgentTerminalTransport.closeTerminal(target, sessionId)
                            .onFailure { error ->
                                reduceDeviceAgentTerminal(DeviceAgentTerminalEvent.Failed("Close failed: ${error.message ?: error}"))
                            }
                    }
                }
                MessagesEvent.DismissDeviceAgentTerminal -> {
                    reduceDeviceAgentTerminal(DeviceAgentTerminalEvent.ExpandedChanged(false))
                }
                MessagesEvent.DismissSelectableMessageText -> {
                    selectableMessageText = null
                }
                is MessagesEvent.MarkAsFullyReadAndExit -> if (!markingAsReadAndExiting.getAndSet(true)) {
                    coroutineScope.launch {
                        val latestEventId = room.liveTimeline.getLatestEventId().getOrElse {
                            Timber.w(it, "Failed to get latest event id to mark as fully read")
                            null
                        }
                        latestEventId?.let { eventId ->
                            sessionCoroutineScope.launch {
                                markAsFullyRead(room.roomId, eventId)
                            }
                        }
                        navigator.close()
                    }.invokeOnCompletion {
                        markingAsReadAndExiting.set(false)
                    }
                }
            }
        }

        val threads = Threads(
            hasThreads = canOpenThreadList && threadsList.isNotEmpty(),
            // TODO calculate this properly based on the thread list and the read state of each thread
            hasUnreadThreads = false,
        )

        return MessagesState(
            roomId = room.roomId,
            roomName = roomInfo.name,
            roomAvatar = roomAvatar,
            heroes = heroes,
            userEventPermissions = userEventPermissions,
            composerState = composerState,
            voiceMessageComposerState = voiceMessageComposerState,
            timelineState = timelineState,
            timelineProtectionState = timelineProtectionState,
            identityChangeState = identityChangeState,
            linkState = linkState,
            actionListState = actionListState,
            customReactionState = customReactionState,
            reactionSummaryState = reactionSummaryState,
            readReceiptBottomSheetState = readReceiptBottomSheetState,
            snackbarMessage = snackbarMessage,
            inviteProgress = inviteProgress.value,
            showReinvitePrompt = showReinvitePrompt,
            enableTextFormatting = MessageComposerConfig.ENABLE_RICH_TEXT_EDITING,
            roomCallState = roomCallState,
            roomScheduleBadgeState = roomScheduleBadgeState,
            roomUnsealContext = roomUnsealContextState,
            roomMenu = RoomMenuReducer.reduce(
                roomUnsealContext = roomUnsealContextState,
                hasThreads = threads.hasThreads,
                isThreadTimeline = timelineState.timelineMode is Timeline.Mode.Thread,
                canShareLocation = composerState.canShareLocation,
                enableTextFormatting = MessageComposerConfig.ENABLE_RICH_TEXT_EDITING,
                hasDirectAgentMember = roomInfo.isDm && dmRoomMember?.isAgentMember() == true,
                activeDeviceAgentBoundDeviceId = activeDeviceAgentBoundDeviceId,
            ),
            deviceAgentTerminalPanel = deviceAgentTerminalPanel,
            selectableMessageText = selectableMessageText,
            appName = buildMeta.applicationName,
            pinnedMessagesBannerState = pinnedMessagesBannerState,
            dmUserVerificationState = dmUserVerificationState,
            roomMemberModerationState = roomMemberModerationState,
            topBarSharedHistoryIcon = topBarSharedHistoryIcon,
            successorRoom = roomInfo.successorRoom,
            threads = threads,
            showLiveLocationShareBanner = isCurrentlySharingLiveLocationInRoom && timelineState.timelineMode !is Timeline.Mode.Thread,
            eventSink = ::handleEvent,
        )
    }

    private fun RoomInfo.sharedHistoryIcon(): SharedHistoryIcon {
        if (isEncrypted == true) {
            if (historyVisibility == RoomHistoryVisibility.Shared) {
                return SharedHistoryIcon.SHARED
            } else if (historyVisibility == RoomHistoryVisibility.WorldReadable) {
                return SharedHistoryIcon.WORLD_READABLE
            }
        }

        return SharedHistoryIcon.NONE
    }

    private fun RoomInfo.avatarData(): AvatarData {
        return AvatarData(
            id = id.value,
            name = name,
            url = avatarUrl,
            size = AvatarSize.TimelineRoom
        )
    }

    private fun RoomInfo.heroes(): List<AvatarData> {
        return heroes.map { user ->
            user.getAvatarData(size = AvatarSize.TimelineRoom)
        }
    }

    private fun CoroutineScope.handleTimelineAction(
        action: TimelineItemAction,
        targetEvent: TimelineItem.Event,
        composerState: MessageComposerState,
        timelineProtectionState: TimelineProtectionState,
        enableTextFormatting: Boolean,
        timelineState: TimelineState,
        onSelectText: (String) -> Unit,
    ) = launch {
        when (action) {
            TimelineItemAction.SelectText -> targetEvent.selectableText()?.let(onSelectText)
            TimelineItemAction.CopyText -> handleCopyContents(targetEvent)
            TimelineItemAction.CopyCaption -> handleCopyCaption(targetEvent)
            TimelineItemAction.CopyLink -> handleCopyLink(targetEvent)
            TimelineItemAction.Redact -> handleActionRedact(targetEvent)
            TimelineItemAction.Edit,
            TimelineItemAction.EditPoll -> handleActionEdit(targetEvent, composerState, enableTextFormatting)
            TimelineItemAction.AddCaption -> handleActionAddCaption(targetEvent, composerState)
            TimelineItemAction.EditCaption -> handleActionEditCaption(targetEvent, composerState)
            TimelineItemAction.RemoveCaption -> handleRemoveCaption(targetEvent)
            TimelineItemAction.Reply -> handleActionReply(targetEvent, composerState, timelineProtectionState)
            TimelineItemAction.ReplyInThread -> {
                val displayThreads = featureFlagService.isFeatureEnabled(FeatureFlags.Threads)
                if (displayThreads) {
                    // Get either the thread id this event is in, or the event id if it's not in a thread so we can start one
                    val threadId = when (targetEvent.threadInfo) {
                        is TimelineItemThreadInfo.ThreadResponse -> targetEvent.threadInfo.threadRootId
                        is TimelineItemThreadInfo.ThreadRoot, null -> targetEvent.eventId?.toThreadId()
                    } ?: return@launch
                    navigator.navigateToThread(threadId, null)
                } else {
                    handleActionReply(targetEvent, composerState, timelineProtectionState)
                }
            }
            TimelineItemAction.ViewSource -> handleShowDebugInfoAction(targetEvent)
            TimelineItemAction.Forward -> handleForwardAction(targetEvent)
            TimelineItemAction.ReportContent -> handleReportAction(targetEvent)
            TimelineItemAction.EndPoll -> handleEndPollAction(targetEvent, timelineState)
            TimelineItemAction.Pin -> handlePinAction(targetEvent)
            TimelineItemAction.Unpin -> handleUnpinAction(targetEvent)
            TimelineItemAction.ViewInTimeline -> Unit
        }
    }

    private suspend fun handleRemoveCaption(targetEvent: TimelineItem.Event) {
        timelineController.invokeOnCurrentTimeline {
            editCaption(
                eventOrTransactionId = targetEvent.eventOrTransactionId,
                caption = null,
                formattedCaption = null,
            )
        }
    }

    private suspend fun handlePinAction(targetEvent: TimelineItem.Event) {
        if (targetEvent.eventId == null) return
        analyticsService.capture(
            PinUnpinAction(
                from = PinUnpinAction.From.Timeline,
                kind = PinUnpinAction.Kind.Pin,
            )
        )
        timelineController.invokeOnCurrentTimeline {
            pinEvent(targetEvent.eventId)
                .onFailure {
                    Timber.e(it, "Failed to pin event ${targetEvent.eventId}")
                    snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_error))
                }
        }
    }

    private suspend fun handleUnpinAction(targetEvent: TimelineItem.Event) {
        if (targetEvent.eventId == null) return
        analyticsService.capture(
            PinUnpinAction(
                from = PinUnpinAction.From.Timeline,
                kind = PinUnpinAction.Kind.Unpin,
            )
        )
        timelineController.invokeOnCurrentTimeline {
            unpinEvent(targetEvent.eventId)
                .onFailure {
                    Timber.e(it, "Failed to unpin event ${targetEvent.eventId}")
                    snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_error))
                }
        }
    }

    private fun CoroutineScope.toggleReaction(
        emoji: String,
        eventOrTransactionId: EventOrTransactionId,
    ) = launch(dispatchers.io) {
        timelineController.invokeOnCurrentTimeline {
            toggleReaction(emoji, eventOrTransactionId)
                .flatMap { added -> if (added) addRecentEmoji(emoji) else Result.success(Unit) }
                .onFailure { Timber.e(it) }
        }
    }

    private fun CoroutineScope.reinviteOtherUser(inviteProgress: MutableState<AsyncData<Unit>>) = launch(dispatchers.io) {
        inviteProgress.value = AsyncData.Loading()
        runCatchingExceptions {
            val memberList = when (val memberState = room.membersStateFlow.value) {
                is RoomMembersState.Ready -> memberState.roomMembers
                is RoomMembersState.Error -> memberState.prevRoomMembers.orEmpty()
                else -> emptyList()
            }

            val member = memberList.first { it.userId != room.sessionId }
            room.inviteUserById(member.userId).onFailure { t ->
                Timber.e(t, "Failed to reinvite DM partner")
            }.getOrThrow()
        }.fold(
            onSuccess = {
                inviteProgress.value = AsyncData.Success(Unit)
            },
            onFailure = {
                inviteProgress.value = AsyncData.Failure(it)
            }
        )
    }

    private suspend fun handleActionRedact(event: TimelineItem.Event) {
        timelineController.invokeOnCurrentTimeline {
            redactEvent(eventOrTransactionId = event.eventOrTransactionId, reason = null)
                .onFailure { Timber.e(it) }
        }
    }

    private fun handleActionEdit(
        targetEvent: TimelineItem.Event,
        composerState: MessageComposerState,
        enableTextFormatting: Boolean,
    ) {
        when (targetEvent.content) {
            is TimelineItemPollContent -> {
                if (targetEvent.eventId == null) return
                navigator.navigateToEditPoll(targetEvent.eventId)
            }
            else -> {
                val composerMode = MessageComposerMode.Edit(
                    targetEvent.eventOrTransactionId,
                    (targetEvent.content as? TimelineItemTextBasedContent)?.let {
                        if (enableTextFormatting) {
                            it.htmlBody ?: it.body
                        } else {
                            it.body
                        }
                    }.orEmpty(),
                )
                composerState.eventSink(
                    MessageComposerEvent.SetMode(composerMode)
                )
            }
        }
    }

    private suspend fun handleActionAddCaption(
        targetEvent: TimelineItem.Event,
        composerState: MessageComposerState,
    ) {
        val composerMode = MessageComposerMode.EditCaption(
            eventOrTransactionId = targetEvent.eventOrTransactionId,
            content = "",
        )
        composerState.eventSink(
            MessageComposerEvent.SetMode(composerMode)
        )
    }

    private suspend fun handleActionEditCaption(
        targetEvent: TimelineItem.Event,
        composerState: MessageComposerState,
    ) {
        val composerMode = MessageComposerMode.EditCaption(
            eventOrTransactionId = targetEvent.eventOrTransactionId,
            content = (targetEvent.content as? TimelineItemEventContentWithAttachment)?.caption.orEmpty(),
        )
        composerState.eventSink(
            MessageComposerEvent.SetMode(composerMode)
        )
    }

    private suspend fun handleActionReply(
        targetEvent: TimelineItem.Event,
        composerState: MessageComposerState,
        timelineProtectionState: TimelineProtectionState,
    ) {
        if (targetEvent.eventId == null) return
        timelineController.invokeOnCurrentTimeline {
            val replyToDetails = loadReplyDetails(targetEvent.eventId).map(permalinkParser)
            val composerMode = MessageComposerMode.Reply(
                replyToDetails = replyToDetails,
                hideImage = timelineProtectionState.hideMediaContent(targetEvent.eventId, targetEvent.isMine),
            )
            composerState.eventSink(
                MessageComposerEvent.SetMode(composerMode)
            )
        }
    }

    private fun handleShowDebugInfoAction(event: TimelineItem.Event) {
        navigator.navigateToEventDebugInfo(event.eventId, event.debugInfo)
    }

    private fun handleForwardAction(event: TimelineItem.Event) {
        if (event.eventId == null) return
        navigator.forwardEvent(event.eventId)
    }

    private fun handleReportAction(event: TimelineItem.Event) {
        if (event.eventId == null) return
        navigator.navigateToReportMessage(event.eventId, event.senderId)
    }

    private fun handleEndPollAction(
        event: TimelineItem.Event,
        timelineState: TimelineState,
    ) {
        event.eventId?.let { timelineState.eventSink(TimelineEvent.EndPoll(it)) }
    }

    private suspend fun handleCopyLink(event: TimelineItem.Event) {
        event.eventId ?: return
        room.getPermalinkFor(event.eventId).fold(
            onSuccess = { permalink ->
                clipboardHelper.copyPlainText(permalink)
                snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_link_copied_to_clipboard))
            },
            onFailure = {
                Timber.e(it, "Failed to get permalink for event ${event.eventId}")
                snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_error))
            }
        )
    }

    private fun handleCopyContents(event: TimelineItem.Event) {
        val content = event.selectableText() ?: return
        clipboardHelper.copyPlainText(content)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            snackbarDispatcher.post(SnackbarMessage(R.string.screen_room_timeline_message_copied))
        }
    }

    private fun handleCopyCaption(event: TimelineItem.Event) {
        val content = (event.content as? TimelineItemEventContentWithAttachment)?.caption ?: return
        clipboardHelper.copyPlainText(content)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            snackbarDispatcher.post(SnackbarMessage(CommonStrings.common_copied_to_clipboard))
        }
    }
}

private val AGENT_MEMBER_USER_TYPES = setOf("agent", "bot", "external_bot", "trusted_external_bot")

private fun RoomMember.isAgentMember(): Boolean = userType in AGENT_MEMBER_USER_TYPES

private fun UnsealD2DMessage.terminalTarget(): UnsealD2DTarget? {
    val deviceId = senderDeviceId?.takeIf { it.isNotBlank() } ?: return null
    return UnsealD2DTarget(sender, deviceId)
}

private fun UnsealD2DMessage.shouldRememberTerminalTarget(): Boolean {
    return msgType == UnsealD2DMsgType.Ping ||
        msgType == UnsealD2DMsgType.Pong ||
        isTerminalMessage
}

private fun UnsealD2DMessage.toDeviceAgentTerminalEvent(): DeviceAgentTerminalEvent? {
    return when (msgType) {
        UnsealD2DMsgType.TerminalReady -> DeviceAgentTerminalEvent.Ready(
            requestId = stringContent("request_id", "requestId"),
            sessionId = stringContent("session_id", "sessionId"),
            shell = stringContent("shell"),
        )
        UnsealD2DMsgType.TerminalOutput -> DeviceAgentTerminalEvent.Output(
            sessionId = stringContent("session_id", "sessionId"),
            data = stringContent("data").orEmpty(),
        )
        UnsealD2DMsgType.TerminalClosed -> DeviceAgentTerminalEvent.Closed(
            sessionId = stringContent("session_id", "sessionId"),
        )
        else -> null
    }
}

private fun DeviceAgentTerminalPanelState?.doesNotAcceptTerminalEventFrom(target: UnsealD2DTarget): Boolean {
    return this?.target?.let { currentTarget -> currentTarget != target } ?: false
}

private fun UnsealD2DMessage.stringContent(vararg keys: String): String? {
    return keys.firstNotNullOfOrNull { key -> stringContent(key) }
}

private fun TimelineItem.Event.selectableText(): String? {
    return when (val content = content) {
        is TimelineItemTextBasedContent -> content.plainText.ifBlank { content.body }
        is TimelineItemAiContent -> content.body.ifBlank { null }
        is TimelineItemStateContent -> content.body
        else -> null
    }
}

private fun AsyncData<RoomUnsealContext>.toRoomScheduleBadgeState(
    eventSink: (RoomScheduleBadgeEvents) -> Unit,
): RoomScheduleBadgeState {
    val context = dataOrNull()
    return RoomScheduleBadgeState(
        isLoading = isLoading(),
        isVisible = context?.hasAgentInRoom == true,
        activeScheduleCount = context?.activeScheduleCount ?: 0,
        error = errorOrNull()?.message,
        eventSink = eventSink,
    )
}
