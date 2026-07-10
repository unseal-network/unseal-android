/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@ContributesBinding(RoomScope::class)
@Inject
class DefaultRoomUnsealDataClient(
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : RoomUnsealDataClient {
    override suspend fun getRoomAgents(roomId: RoomId): Result<List<RoomAgentDescriptor>> {
        return service().getRoomAgents(roomId.value)
            .map { response -> response.agents.map { it.toRoomAgentDescriptor() } }
    }

    override suspend fun listAgents(): Result<List<AgentAccountDescriptor>> {
        return service().listAgents()
            .map { agents -> agents.map { it.toAgentAccountDescriptor() } }
    }

    override suspend fun listSchedules(roomId: RoomId): Result<List<RoomScheduleDescriptor>> {
        return service().listSchedules(roomId.value)
            .map { schedules -> schedules.map { it.toRoomScheduleDescriptor() } }
    }

    override suspend fun listRoomAgentSkills(roomId: RoomId, agentId: String, runtimeOwnerUserId: String?): Result<RoomAgentSkillCatalogDescriptor> {
        return service().listRoomAgentSkills(roomId.value, agentId, runtimeOwnerUserId)
            .map { response -> response.toRoomAgentSkillCatalogDescriptor() }
    }

    override suspend fun refreshRoomAgentSkills(roomId: RoomId, agentId: String?, cacheKey: String, runtimeOwnerUserId: String?): Result<RoomAgentSkillCatalogDescriptor> {
        return service().refreshRoomAgentSkills(roomId.value, agentId, cacheKey, runtimeOwnerUserId)
            .map { response -> response.toRoomAgentSkillCatalogDescriptor() }
    }

    override suspend fun listWebhookTriggers(roomId: RoomId): Result<List<RoomWebhookTriggerDescriptor>> {
        return service().listWebhookTriggers(agentId = null, source = null, roomId = roomId.value, status = null)
            .map { triggers -> triggers.map { it.toRoomWebhookTriggerDescriptor() } }
    }

    override suspend fun getRoomWorkingMemory(roomId: RoomId): Result<String> {
        return service().getRoomWorkingMemory(roomId.value)
    }

    override suspend fun loadRoomIdentityData(roomId: RoomId): RoomUnsealDataSnapshot = coroutineScope {
        val roomAgents = async { getRoomAgents(roomId).toResource(emptyList()) }
        val allAgents = async { listAgents().toResource(emptyList()) }
        RoomUnsealDataSnapshot(
            roomAgents = roomAgents.await(),
            allAgents = allAgents.await(),
        )
    }

    override suspend fun loadRoomData(roomId: RoomId): RoomUnsealDataSnapshot {
        return loadRoomData(roomId, onIdentitySnapshot = {})
    }

    override suspend fun loadRoomData(
        roomId: RoomId,
        onIdentitySnapshot: (RoomUnsealDataSnapshot) -> Unit,
    ): RoomUnsealDataSnapshot = coroutineScope {
        val roomAgents = async { getRoomAgents(roomId).toResource(emptyList()) }
        val allAgents = async { listAgents().toResource(emptyList()) }
        val schedules = async { listSchedules(roomId).toResource(emptyList()) }
        val webhookTriggers = async { listWebhookTriggers(roomId).toResource(emptyList()) }
        val workingMemory = async { getRoomWorkingMemory(roomId).toResource("") }
        val identitySnapshot = RoomUnsealDataSnapshot(
            roomAgents = roomAgents.await(),
            allAgents = allAgents.await(),
        )
        onIdentitySnapshot(identitySnapshot)
        RoomUnsealDataSnapshot(
            roomAgents = identitySnapshot.roomAgents,
            allAgents = identitySnapshot.allAgents,
            schedules = schedules.await(),
            webhookTriggers = webhookTriggers.await(),
            workingMemory = workingMemory.await(),
        )
    }

    private suspend fun service(): ChatbotApiService {
        return chatbotApiServiceFactory.createForHomeserver(matrixClient)
    }
}

private fun <T> Result<T>.toResource(defaultValue: T): RoomUnsealResource<T> {
    return fold(
        onSuccess = { RoomUnsealResource.success(it) },
        onFailure = { RoomUnsealResource.failure(defaultValue, it) },
    )
}
