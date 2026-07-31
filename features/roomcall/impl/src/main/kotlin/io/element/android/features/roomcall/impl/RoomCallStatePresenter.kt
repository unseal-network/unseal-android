/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomcall.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.call.api.AudienceBroadcastDiscovery
import io.element.android.features.call.api.AudienceBroadcastService
import io.element.android.features.call.api.CurrentCall
import io.element.android.features.call.api.CurrentCallService
import io.element.android.features.enterprise.api.SessionEnterpriseService
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.api.room.CallIntentConsensus
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.powerlevels.canCall
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsAsState
import kotlinx.coroutines.flow.catch

@Inject
class RoomCallStatePresenter(
    private val room: JoinedRoom,
    private val currentCallService: CurrentCallService,
    private val sessionEnterpriseService: SessionEnterpriseService,
    private val audienceBroadcastService: AudienceBroadcastService,
) : Presenter<RoomCallState> {
    @Composable
    override fun present(): RoomCallState {
        val isAvailable by produceState(false) {
            value = sessionEnterpriseService.isElementCallAvailable()
        }
        val roomInfo by room.roomInfoFlow.collectAsState()
        val audienceHostControl by audienceBroadcastService
            .observeHostControl(room.sessionId, room.roomId)
            .collectAsState()
        val audienceDiscoveryState by produceState(
            initialValue = AudienceDiscoveryState(
                discovery = null,
            ),
            // Relay discovery is an agent-owned Matrix state event. It must not
            // be cancelled and restarted when the separate MatrixRTC meeting
            // state changes: doing so discards an in-flight custom-state read
            // exactly while a meeting is starting, delaying the listener entry.
            key1 = room.sessionId,
            key2 = room.roomId,
        ) {
            audienceBroadcastService
                .observeRoomDiscovery(room.sessionId, room.roomId)
                .catch { emit(null) }
                .collect { discovery ->
                    value = AudienceDiscoveryState(discovery = discovery)
                }
        }
        val hasJoinableMeeting = roomInfo.hasRoomCall || audienceDiscoveryState.discovery != null
        var previouslyHadJoinableMeeting by remember(room.roomId) { mutableStateOf(hasJoinableMeeting) }
        LaunchedEffect(hasJoinableMeeting) {
            if (!hasJoinableMeeting) {
                audienceBroadcastService.clearMeetingFence(
                    room.sessionId,
                    room.roomId,
                    force = previouslyHadJoinableMeeting,
                )
            }
            previouslyHadJoinableMeeting = hasJoinableMeeting
        }
        val canJoinCall by room.permissionsAsState(false) { perms -> perms.canCall() }
        val isUserInTheCall by remember {
            derivedStateOf {
                room.sessionId in roomInfo.activeRoomCallParticipants
            }
        }
        val currentCall by currentCallService.currentCall.collectAsState()
        val isUserLocallyInTheCall by remember {
            derivedStateOf {
                (currentCall as? CurrentCall.RoomCall)?.roomId == room.roomId
            }
        }
        // `hasJoinableMeeting` is a value derived during this composition. Do
        // not capture its first value in a remembered derivedStateOf: doing so
        // leaves a room in StandBy forever when the relay discovery arrives.
        val callState = when {
            isAvailable.not() -> RoomCallState.Unavailable
            hasJoinableMeeting -> RoomCallState.OnGoing(
                canJoinCall = canJoinCall,
                isUserInTheCall = isUserInTheCall,
                isUserLocallyInTheCall = isUserLocallyInTheCall,
                isAudioCall = roomInfo.activeCallIntentConsensus.isAudio(),
                audienceBroadcastId = audienceDiscoveryState.discovery?.broadcastId,
                audienceListenerCount = audienceDiscoveryState.discovery?.listenerCount ?: 0,
                // A normal meeting is always joinable. Listener discovery is
                // additive and may arrive later; it must never gate the normal
                // Element Call entrance.
                isAudienceDiscoveryPending = false,
                audienceHostControl = audienceHostControl,
            )
            else -> RoomCallState.StandBy(
                canStartCall = canJoinCall,
                isDM = roomInfo.isDm,
                audienceHostControl = audienceHostControl,
            )
        }
        return callState
    }
}

private data class AudienceDiscoveryState(
    val discovery: AudienceBroadcastDiscovery?,
)

fun CallIntentConsensus.isAudio(): Boolean {
    val intent = when (this) {
        is CallIntentConsensus.Full -> callIntent
        is CallIntentConsensus.Partial -> callIntent
        is CallIntentConsensus.None -> return false
    }
    return intent == CallIntent.AUDIO
}
