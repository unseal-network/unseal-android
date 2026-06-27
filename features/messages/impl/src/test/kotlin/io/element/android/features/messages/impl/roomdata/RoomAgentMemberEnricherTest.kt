/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.room.aRoomMember
import org.junit.Test

class RoomAgentMemberEnricherTest {
    @Test
    fun `enrich marks joined room agent with default user type`() {
        val members = listOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN))
        val agents = listOf(RoomAgentDescriptor(userId = AGENT_ID.value, displayName = "Agent", avatarUrl = null, userType = null, membership = "join"))

        val enriched = RoomAgentMemberEnricher.enrich(members, agents)

        assertThat(enriched.single().userType).isEqualTo("agent")
        assertThat(enriched.single().isAgent).isTrue()
    }

    @Test
    fun `enrich accepts room agent membership case insensitively`() {
        val members = listOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN))
        val agents = listOf(RoomAgentDescriptor(userId = AGENT_ID.value, displayName = "Agent", avatarUrl = null, userType = null, membership = " JOIN "))

        val enriched = RoomAgentMemberEnricher.enrich(members, agents)

        assertThat(enriched.single().userType).isEqualTo("agent")
        assertThat(enriched.single().isAgent).isTrue()
    }

    @Test
    fun `enrich preserves external bot as agent type`() {
        val members = listOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN))
        val agents = listOf(RoomAgentDescriptor(userId = AGENT_ID.value, displayName = "Agent", avatarUrl = null, userType = "trusted_external_bot", membership = "join"))

        val enriched = RoomAgentMemberEnricher.enrich(members, agents)

        assertThat(enriched.single().userType).isEqualTo("trusted_external_bot")
        assertThat(enriched.single().isAgent).isTrue()
    }

    @Test
    fun `enrich prefers room agent display data over matrix member fallback`() {
        val members = listOf(
            aRoomMember(
                userId = AGENT_ID,
                displayName = "Matrix fallback",
                avatarUrl = "mxc://matrix/fallback",
                membership = RoomMembershipState.JOIN,
            )
        )
        val agents = listOf(
            RoomAgentDescriptor(
                userId = AGENT_ID.value,
                displayName = "Rayson",
                avatarUrl = "mxc://agent/avatar",
                userType = "agent",
                membership = "join",
            )
        )

        val enriched = RoomAgentMemberEnricher.enrich(members, agents)

        assertThat(enriched.single().displayName).isEqualTo("Rayson")
        assertThat(enriched.single().avatarUrl).isEqualTo("mxc://agent/avatar")
    }

    @Test
    fun `enrich ignores non joined matrix member`() {
        val members = listOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.INVITE))
        val agents = listOf(RoomAgentDescriptor(userId = AGENT_ID.value, displayName = "Agent", avatarUrl = null, userType = "agent", membership = "join"))

        val enriched = RoomAgentMemberEnricher.enrich(members, agents)

        assertThat(enriched.single().userType).isNull()
        assertThat(enriched.single().isAgent).isFalse()
    }

    @Test
    fun `enrich ignores room agent whose membership is not join`() {
        val members = listOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN))
        val agents = listOf(RoomAgentDescriptor(userId = AGENT_ID.value, displayName = "Agent", avatarUrl = null, userType = "agent", membership = "leave"))

        val enriched = RoomAgentMemberEnricher.enrich(members, agents)

        assertThat(enriched.single().userType).isNull()
        assertThat(enriched.single().isAgent).isFalse()
    }

    @Test
    fun `enrich preserves matrix enriched agent type when room context endpoint is empty`() {
        val members = listOf(aRoomMember(userId = AGENT_ID, userType = "agent", membership = RoomMembershipState.JOIN))

        val enriched = RoomAgentMemberEnricher.enrich(members, emptyList())

        assertThat(enriched.single().userType).isEqualTo("agent")
        assertThat(enriched.single().isAgent).isTrue()
    }

    private companion object {
        val AGENT_ID = UserId("@agent:example.org")
    }
}
