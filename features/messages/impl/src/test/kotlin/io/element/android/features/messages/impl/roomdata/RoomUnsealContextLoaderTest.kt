/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomMember
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomUnsealContextLoaderTest {
    @Test
    fun `load refreshes room members when cache is empty`() = runTest {
        var updateMembersCalled = 0
        val room = FakeJoinedRoom().apply {
            baseRoom.givenUpdateMembersResult {
                updateMembersCalled += 1
                givenRoomMembersState(RoomMembersState.Ready(persistentListOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN))))
            }
        }
        val loader = RoomUnsealContextLoader(
            room = room,
            roomUnsealDataClient = FakeRoomUnsealDataClient(
                snapshot = RoomUnsealDataSnapshot(
                    roomAgents = RoomUnsealResource.success(listOf(RoomAgentDescriptor(AGENT_ID.value, "Agent", null, null, "join")))
                )
            ),
        )

        val context = loader.load()

        assertThat(updateMembersCalled).isEqualTo(1)
        assertThat(context.members.single().isAgent).isTrue()
    }

    @Test
    fun `load does not refresh room members when cache is ready`() = runTest {
        var updateMembersCalled = 0
        val room = FakeJoinedRoom().apply {
            givenRoomMembersState(RoomMembersState.Ready(persistentListOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN))))
            baseRoom.givenUpdateMembersResult { updateMembersCalled += 1 }
        }
        val loader = RoomUnsealContextLoader(
            room = room,
            roomUnsealDataClient = FakeRoomUnsealDataClient(
                snapshot = RoomUnsealDataSnapshot(
                    roomAgents = RoomUnsealResource.success(listOf(RoomAgentDescriptor(AGENT_ID.value, "Agent", null, null, "join")))
                )
            ),
        )

        val context = loader.load()

        assertThat(updateMembersCalled).isEqualTo(0)
        assertThat(context.hasAgentInRoom).isTrue()
    }

    private class FakeRoomUnsealDataClient(
        private val snapshot: RoomUnsealDataSnapshot = RoomUnsealDataSnapshot(),
    ) : RoomUnsealDataClient {
        override suspend fun getRoomAgents(roomId: RoomId): Result<List<RoomAgentDescriptor>> = Result.success(snapshot.roomAgents.value)
        override suspend fun listAgents(): Result<List<AgentAccountDescriptor>> = Result.success(snapshot.allAgents.value)
        override suspend fun listSchedules(roomId: RoomId): Result<List<RoomScheduleDescriptor>> = Result.success(snapshot.schedules.value)
        override suspend fun listRoomAgentSkills(roomId: RoomId, agentId: String, runtimeOwnerUserId: String?): Result<RoomAgentSkillCatalogDescriptor> =
            Result.success(RoomAgentSkillCatalogDescriptor())
        override suspend fun refreshRoomAgentSkills(roomId: RoomId, agentId: String?, cacheKey: String, runtimeOwnerUserId: String?): Result<RoomAgentSkillCatalogDescriptor> =
            Result.success(RoomAgentSkillCatalogDescriptor(status = "complete"))
        override suspend fun listWebhookTriggers(roomId: RoomId): Result<List<RoomWebhookTriggerDescriptor>> = Result.success(snapshot.webhookTriggers.value)
        override suspend fun getRoomWorkingMemory(roomId: RoomId): Result<String> = Result.success(snapshot.workingMemory.value)
        override suspend fun loadRoomData(roomId: RoomId): RoomUnsealDataSnapshot = snapshot
    }

    private companion object {
        val AGENT_ID = UserId("@agent:example.org")
    }
}
