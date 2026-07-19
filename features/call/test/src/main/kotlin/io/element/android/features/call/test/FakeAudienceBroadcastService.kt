/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.test

import io.element.android.features.call.api.AudienceBroadcastDiscovery
import io.element.android.features.call.api.AudienceBroadcastService
import io.element.android.features.call.api.AudienceAccessMode
import io.element.android.features.call.api.AudienceHostControlState
import io.element.android.features.call.api.AudienceRuntimeStatus
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.tests.testutils.lambda.lambdaError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

class FakeAudienceBroadcastService(
    private val runtimeStatus: suspend (SessionId, String) -> Result<AudienceRuntimeStatus> = { _, _ -> lambdaError() },
    private val discovery: (SessionId, RoomId) -> Flow<AudienceBroadcastDiscovery?> = { _, _ -> flowOf(null) },
) : AudienceBroadcastService {
    private val hostControlState = MutableStateFlow(AudienceHostControlState())
    override suspend fun getRuntimeStatus(
        sessionId: SessionId,
        broadcastId: String,
    ): Result<AudienceRuntimeStatus> = runtimeStatus(sessionId, broadcastId)

    override suspend fun awaitRuntimeStatus(
        sessionId: SessionId,
        broadcastId: String,
    ): Result<AudienceRuntimeStatus> = runtimeStatus(sessionId, broadcastId)

    override fun observeRoomDiscovery(
        sessionId: SessionId,
        roomId: RoomId,
    ): Flow<AudienceBroadcastDiscovery?> = discovery(sessionId, roomId)

    override fun observeHostControl(sessionId: SessionId, roomId: RoomId): StateFlow<AudienceHostControlState> = hostControlState

    override suspend fun enableRelay(
        sessionId: SessionId,
        roomId: RoomId,
        accessMode: AudienceAccessMode,
    ): Result<AudienceHostControlState> = Result.success(hostControlState.value)

    override suspend fun disableRelay(sessionId: SessionId, roomId: RoomId): Result<AudienceHostControlState> =
        Result.success(hostControlState.value)

    override fun clearMeetingFence(sessionId: SessionId, roomId: RoomId, force: Boolean) = Unit
}
