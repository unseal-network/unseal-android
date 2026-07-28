/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.api

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AudienceBroadcastService {
    suspend fun getRuntimeStatus(
        sessionId: SessionId,
        broadcastId: String,
    ): Result<AudienceRuntimeStatus>

    /** Waits through transient audience API outages until a runtime can be resolved or a terminal request error occurs. */
    suspend fun awaitRuntimeStatus(
        sessionId: SessionId,
        broadcastId: String,
    ): Result<AudienceRuntimeStatus>

    fun observeRoomDiscovery(
        sessionId: SessionId,
        roomId: RoomId,
    ): Flow<AudienceBroadcastDiscovery?>

    fun observeHostControl(sessionId: SessionId, roomId: RoomId): StateFlow<AudienceHostControlState>

    suspend fun enableRelay(
        sessionId: SessionId,
        roomId: RoomId,
        accessMode: AudienceAccessMode,
    ): Result<AudienceHostControlState>

    suspend fun disableRelay(sessionId: SessionId, roomId: RoomId): Result<AudienceHostControlState>

    fun clearMeetingFence(sessionId: SessionId, roomId: RoomId, force: Boolean = false)
}

data class AudienceBroadcastDiscovery(
    val broadcastId: String,
    val meetingInstanceId: String,
    val accessMode: AudienceAccessMode,
    val phase: AudienceRuntimePhase,
    /** Refreshed from the runtime status endpoint after Matrix discovery succeeds. */
    val listenerCount: Int = 0,
)

data class AudienceRuntimeStatus(
    val broadcastId: String,
    val roomId: RoomId,
    val meetingInstanceId: String,
    val phase: AudienceRuntimePhase,
    val desired: AudienceRelayDesired,
    val agentInMeeting: Boolean,
    val broadcastArmed: Boolean,
    val playable: Boolean,
    val pollAfterMs: Long,
    val generation: Int = 0,
    val manifestRevision: Int = 0,
    val participantCount: Int = 0,
    val listenerCount: Int = 0,
)

data class AudienceHostControlState(
    val meetingInstanceId: String? = null,
    val broadcastId: String? = null,
    val accessMode: AudienceAccessMode? = null,
    val phase: AudienceRuntimePhase = AudienceRuntimePhase.Idle,
    val desired: AudienceRelayDesired = AudienceRelayDesired.Left,
    val broadcastArmed: Boolean = false,
    val isUpdating: Boolean = false,
    val errorMessage: String? = null,
) {
    val isEnabled: Boolean
        get() = desired == AudienceRelayDesired.Joined && phase != AudienceRuntimePhase.Ended
}

enum class AudienceRelayDesired {
    Joined,
    Left,
}

enum class AudienceAccessMode {
    Authenticated,
    RoomMembers,
}

enum class AudienceRuntimePhase {
    Idle,
    Joining,
    Publishing,
    Live,
    Recovering,
    Leaving,
    Ended,
}

private val BROADCAST_ID_PATTERN = Regex("^bcast_[A-Za-z0-9_-]+$")

fun String.isValidAudienceBroadcastId(): Boolean = BROADCAST_ID_PATTERN.matches(this)
