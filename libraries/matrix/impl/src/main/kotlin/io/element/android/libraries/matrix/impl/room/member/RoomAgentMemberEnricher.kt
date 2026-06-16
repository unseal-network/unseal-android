/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room.member

import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState

internal data class RoomAgentMember(
    val userId: String,
    val membership: String?,
    val userType: String?,
)

internal interface RoomAgentMemberProvider {
    suspend fun getRoomAgents(roomId: String): List<RoomAgentMember>
}

internal fun interface SessionCredentialsProvider {
    suspend fun getSessionCredentials(): SessionCredentials?
}

internal data class SessionCredentials(
    val homeserverUrl: String,
    val accessToken: String,
)

internal object NoopRoomAgentMemberProvider : RoomAgentMemberProvider {
    override suspend fun getRoomAgents(roomId: String): List<RoomAgentMember> = emptyList()
}

internal class RoomAgentMemberEnricher {
    fun enrich(members: List<RoomMember>, agents: List<RoomAgentMember>): List<RoomMember> {
        val agentsByUserId = agents.associateBy { it.userId }
        return members.map { member ->
            val agent = agentsByUserId[member.userId.value]
            val agentMembership = agent?.membership?.trim()
            if (member.membership == RoomMembershipState.JOIN && agent != null && (agentMembership.isNullOrEmpty() || agentMembership == "join")) {
                member.copy(userType = agent.userType ?: "agent")
            } else {
                member.copy(userType = null)
            }
        }
    }
}
