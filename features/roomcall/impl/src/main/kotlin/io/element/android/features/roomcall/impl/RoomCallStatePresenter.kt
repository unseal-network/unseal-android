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
        var previouslyHadRoomCall by remember(room.roomId) { mutableStateOf(roomInfo.hasRoomCall) }
        LaunchedEffect(roomInfo.hasRoomCall) {
            if (!roomInfo.hasRoomCall) {
                audienceBroadcastService.clearMeetingFence(
                    room.sessionId,
                    room.roomId,
                    force = previouslyHadRoomCall,
                )
            }
            previouslyHadRoomCall = roomInfo.hasRoomCall
        }
        val audienceDiscovery by produceState<AudienceBroadcastDiscovery?>(null, roomInfo.hasRoomCall, room.roomId) {
            if (!roomInfo.hasRoomCall) {
                value = null
            } else {
                audienceBroadcastService.observeRoomDiscovery(room.sessionId, room.roomId).collect { value = it }
            }
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
        val callState by remember {
            derivedStateOf {
                when {
                    isAvailable.not() -> RoomCallState.Unavailable
                    roomInfo.hasRoomCall -> RoomCallState.OnGoing(
                        canJoinCall = canJoinCall,
                        isUserInTheCall = isUserInTheCall,
                        isUserLocallyInTheCall = isUserLocallyInTheCall,
                        isAudioCall = roomInfo.activeCallIntentConsensus.isAudio(),
                        audienceBroadcastId = audienceDiscovery?.broadcastId,
                        audienceHostControl = audienceHostControl,
                    )
                    else -> RoomCallState.StandBy(
                        canStartCall = canJoinCall,
                        isDM = roomInfo.isDm,
                        audienceHostControl = audienceHostControl,
                    )
                }
            }
        }
        return callState
    }
}

fun CallIntentConsensus.isAudio(): Boolean {
    val intent = when (this) {
        is CallIntentConsensus.Full -> callIntent
        is CallIntentConsensus.Partial -> callIntent
        is CallIntentConsensus.None -> return false
    }
    return intent == CallIntent.AUDIO
}
