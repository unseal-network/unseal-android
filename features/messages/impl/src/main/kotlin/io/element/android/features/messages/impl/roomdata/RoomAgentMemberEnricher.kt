/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState

object RoomAgentMemberEnricher {
    fun enrich(members: List<RoomMember>, agents: List<RoomAgentDescriptor>): List<RoomMemberRender> {
        val agentsByUserId = agents.associateBy { it.userId }
        return members.map { member ->
            val agent = activeAgentFor(member, agentsByUserId)
            RoomMemberRender(
                member = member,
                userType = agent?.userType ?: agent?.let { "agent" },
                displayNameOverride = agent?.displayName,
                avatarUrlOverride = agent?.avatarUrl,
            )
        }
    }

    private fun activeAgentFor(member: RoomMember, agentsByUserId: Map<String, RoomAgentDescriptor>): RoomAgentDescriptor? {
        if (member.membership != RoomMembershipState.JOIN) return null
        val agent = agentsByUserId[member.userId.value] ?: return null
        if (!agent.isJoinedOrUnknownMembership()) return null
        return agent
    }
}
