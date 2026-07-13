/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import io.element.android.libraries.matrix.api.core.RoomId

class FakeRoomUnsealDataClient(
    var snapshot: RoomUnsealDataSnapshot = RoomUnsealDataSnapshot(),
) : RoomUnsealDataClient {
    var getRoomAgentsResult: (RoomId) -> Result<List<RoomAgentDescriptor>> = { Result.success(snapshot.roomAgents.value) }
    var roomAgentSkillsResult: (RoomId, String, String?) -> Result<RoomAgentSkillCatalogDescriptor> = { _, _, _ -> Result.success(RoomAgentSkillCatalogDescriptor()) }
    var refreshRoomAgentSkillsResult: (RoomId, String?, String, String?) -> Result<RoomAgentSkillCatalogDescriptor> = { _, _, _, _ ->
        Result.success(RoomAgentSkillCatalogDescriptor(status = "complete"))
    }

    val roomAgentRequests = mutableListOf<RoomId>()
    val roomAgentSkillRequests = mutableListOf<RoomAgentSkillRequest>()
    val roomAgentSkillRefreshRequests = mutableListOf<RoomAgentSkillRefreshRequest>()

    override suspend fun getRoomAgents(roomId: RoomId): Result<List<RoomAgentDescriptor>> {
        roomAgentRequests += roomId
        return getRoomAgentsResult(roomId)
    }

    override suspend fun listAgents(): Result<List<AgentAccountDescriptor>> = Result.success(snapshot.allAgents.value)

    override suspend fun listSchedules(roomId: RoomId): Result<List<RoomScheduleDescriptor>> = Result.success(snapshot.schedules.value)

    override suspend fun listRoomAgentSkills(roomId: RoomId, agentId: String, runtimeOwnerUserId: String?): Result<RoomAgentSkillCatalogDescriptor> {
        roomAgentSkillRequests += RoomAgentSkillRequest(roomId = roomId, agentId = agentId, runtimeOwnerUserId = runtimeOwnerUserId)
        return roomAgentSkillsResult(roomId, agentId, runtimeOwnerUserId)
    }

    override suspend fun refreshRoomAgentSkills(roomId: RoomId, agentId: String?, cacheKey: String, runtimeOwnerUserId: String?): Result<RoomAgentSkillCatalogDescriptor> {
        roomAgentSkillRefreshRequests += RoomAgentSkillRefreshRequest(roomId = roomId, agentId = agentId, cacheKey = cacheKey, runtimeOwnerUserId = runtimeOwnerUserId)
        return refreshRoomAgentSkillsResult(roomId, agentId, cacheKey, runtimeOwnerUserId)
    }

    override suspend fun listWebhookTriggers(roomId: RoomId): Result<List<RoomWebhookTriggerDescriptor>> = Result.success(snapshot.webhookTriggers.value)

    override suspend fun getRoomWorkingMemory(roomId: RoomId): Result<String> = Result.success(snapshot.workingMemory.value)

    override suspend fun loadRoomData(roomId: RoomId): RoomUnsealDataSnapshot = snapshot
}

data class RoomAgentSkillRequest(
    val roomId: RoomId,
    val agentId: String,
    val runtimeOwnerUserId: String?,
)

data class RoomAgentSkillRefreshRequest(
    val roomId: RoomId,
    val agentId: String?,
    val cacheKey: String,
    val runtimeOwnerUserId: String?,
)
