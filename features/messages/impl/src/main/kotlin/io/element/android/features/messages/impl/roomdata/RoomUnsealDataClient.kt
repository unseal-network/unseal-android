/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import io.element.android.libraries.matrix.api.core.RoomId

interface RoomUnsealDataClient {
    suspend fun getRoomAgents(roomId: RoomId): Result<List<RoomAgentDescriptor>>
    suspend fun listAgents(): Result<List<AgentAccountDescriptor>>
    suspend fun listSchedules(roomId: RoomId): Result<List<RoomScheduleDescriptor>>
    suspend fun listRoomAgentSkills(roomId: RoomId, agentId: String, runtimeOwnerUserId: String?): Result<RoomAgentSkillCatalogDescriptor>
    suspend fun refreshRoomAgentSkills(roomId: RoomId, agentId: String?, cacheKey: String, runtimeOwnerUserId: String?): Result<RoomAgentSkillCatalogDescriptor>
    suspend fun listWebhookTriggers(roomId: RoomId): Result<List<RoomWebhookTriggerDescriptor>>
    suspend fun getRoomWorkingMemory(roomId: RoomId): Result<String>
    suspend fun loadRoomIdentityData(roomId: RoomId): RoomUnsealDataSnapshot {
        return RoomUnsealDataSnapshot(
            roomAgents = getRoomAgents(roomId).toRoomUnsealResource(emptyList()),
            allAgents = listAgents().toRoomUnsealResource(emptyList()),
        )
    }
    suspend fun loadRoomData(roomId: RoomId): RoomUnsealDataSnapshot
    suspend fun loadRoomData(
        roomId: RoomId,
        onIdentitySnapshot: (RoomUnsealDataSnapshot) -> Unit,
    ): RoomUnsealDataSnapshot {
        val snapshot = loadRoomData(roomId)
        onIdentitySnapshot(
            RoomUnsealDataSnapshot(
                roomAgents = snapshot.roomAgents,
                allAgents = snapshot.allAgents,
            )
        )
        return snapshot
    }
}

private fun <T> Result<T>.toRoomUnsealResource(defaultValue: T): RoomUnsealResource<T> {
    return fold(
        onSuccess = { RoomUnsealResource.success(it) },
        onFailure = { RoomUnsealResource.failure(defaultValue, it) },
    )
}
