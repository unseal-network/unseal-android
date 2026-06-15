/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.shared

import io.element.android.libraries.chatbot.api.ChatbotApiService
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.joinedRoomMembers

/**
 * Mirrors iOS `RoomAgentMemberEnricher`: room agents come from the homeserver
 * chatbot API, but the visible picker should only expose agents that are active
 * Matrix members of the room.
 */
internal suspend fun loadWebhookRoomAgents(
    matrixClient: MatrixClient,
    homeserverApi: ChatbotApiService,
    roomId: String,
): Result<List<ChatbotRoomAgent>> {
    val agents = homeserverApi.getRoomAgents(roomId).getOrElse {
        return Result.failure(it)
    }.agents

    val joinedAgents = agents.filter { it.isJoinedAgent() }
    val room = matrixClient.getJoinedRoom(RoomId(roomId)) ?: return Result.success(joinedAgents)

    runCatching { room.updateMembers() }

    val joinedMembers = room.membersStateFlow.value.joinedRoomMembers()
    if (joinedMembers.isEmpty()) {
        return Result.success(joinedAgents)
    }

    val agentsByUserId = joinedAgents.associateBy { it.matrixUserId }
    return Result.success(
        joinedMembers
            .mapNotNull { member ->
                val agent = agentsByUserId[member.userId.value] ?: return@mapNotNull null
                agent.enrichedBy(member)
            }
    )
}

private val ChatbotRoomAgent.matrixUserId: String
    get() = mxid?.takeIf { it.isNotBlank() } ?: agentId

private fun ChatbotRoomAgent.isJoinedAgent(): Boolean {
    return membership
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
        ?.equals("join", ignoreCase = true) ?: true
}

private fun ChatbotRoomAgent.enrichedBy(member: RoomMember): ChatbotRoomAgent {
    return copy(
        agentId = member.userId.value,
        displayName = member.displayName ?: displayName,
        avatarUrl = member.avatarUrl ?: avatarUrl,
        userType = userType?.takeIf { it.isNotBlank() } ?: "agent",
        membership = "join",
        mxid = mxid ?: member.userId.value,
    )
}
