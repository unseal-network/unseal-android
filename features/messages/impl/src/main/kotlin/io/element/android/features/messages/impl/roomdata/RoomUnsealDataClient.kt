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
    suspend fun listRoomAgentSkills(roomId: RoomId, agentId: String, runtimeOwnerUserId: String?): Result<List<RoomAgentSkillDescriptor>>
    suspend fun listLegacyAgentSkills(agentLookupId: String): Result<List<RoomLegacyAgentSkillDescriptor>>
    suspend fun listWebhookTriggers(roomId: RoomId): Result<List<RoomWebhookTriggerDescriptor>>
    suspend fun getRoomWorkingMemory(roomId: RoomId): Result<String>
    suspend fun loadRoomData(roomId: RoomId): RoomUnsealDataSnapshot
}
