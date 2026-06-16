/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.roomdata.FakeRoomUnsealDataClient
import io.element.android.features.messages.impl.roomdata.RoomAgentDescriptor
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomAgentResolverTest {
    @Test
    fun `roomAgentUserIds - returns mxids and ignores null mxids`() = runTest {
        val dataClient = FakeRoomUnsealDataClient().apply {
            getRoomAgentsResult = {
                Result.success(
                    listOf(
                        roomAgent("@agent:example.org"),
                        roomAgent("a2"),
                        roomAgent("@jelf:example.org"),
                    )
                )
            }
        }
        val resolver = RoomAgentResolver(dataClient)

        assertThat(resolver.roomAgentUserIds(A_ROOM_ID, setOf(AGENT_ID, JELF_ID, A_MEMBER_ID))).containsExactly(AGENT_ID, JELF_ID)
    }

    @Test
    fun `roomAgentUserIds - only returns active joined room agents`() = runTest {
        val inactiveAgentId = UserId("@inactive-agent:example.org")
        val leftAgentId = UserId("@left-agent:example.org")
        val dataClient = FakeRoomUnsealDataClient().apply {
            getRoomAgentsResult = {
                Result.success(
                    listOf(
                        roomAgent(AGENT_ID.value, membership = " JOIN "),
                        roomAgent(inactiveAgentId.value),
                        roomAgent(leftAgentId.value, membership = "leave"),
                    )
                )
            }
        }
        val resolver = RoomAgentResolver(dataClient)

        assertThat(resolver.roomAgentUserIds(A_ROOM_ID, setOf(AGENT_ID, leftAgentId))).containsExactly(AGENT_ID)
    }

    @Test
    fun `roomAgentUserIds - returns empty set on API failure`() = runTest {
        val dataClient = FakeRoomUnsealDataClient().apply {
            getRoomAgentsResult = { Result.failure(IllegalStateException("No agents today")) }
        }
        val resolver = RoomAgentResolver(dataClient)

        assertThat(resolver.roomAgentUserIds(A_ROOM_ID, setOf(A_MEMBER_ID))).isEmpty()
    }

    @Test
    fun `roomAgentUserIds - caches same active member signature and refreshes when it changes`() = runTest {
        var callCount = 0
        val dataClient = FakeRoomUnsealDataClient().apply {
            getRoomAgentsResult = {
                callCount += 1
                Result.success(listOf(roomAgent("@agent$callCount:example.org")))
            }
        }
        val resolver = RoomAgentResolver(dataClient)

        val firstAgentId = UserId("@agent1:example.org")
        val secondAgentId = UserId("@agent2:example.org")
        val first = resolver.roomAgentUserIds(A_ROOM_ID, setOf(firstAgentId))
        val second = resolver.roomAgentUserIds(A_ROOM_ID, setOf(firstAgentId))
        val refreshed = resolver.roomAgentUserIds(A_ROOM_ID, setOf(secondAgentId))

        assertThat(first).containsExactly(firstAgentId)
        assertThat(second).containsExactly(firstAgentId)
        assertThat(refreshed).containsExactly(secondAgentId)
        assertThat(callCount).isEqualTo(2)
    }

    private fun roomAgent(
        userId: String,
        membership: String? = "join",
    ) = RoomAgentDescriptor(
        userId = userId,
        displayName = null,
        avatarUrl = null,
        userType = "agent",
        membership = membership,
    )

    private companion object {
        val A_ROOM_ID = RoomId("!room:example.org")
        val A_MEMBER_ID = UserId("@member:example.org")
        val ANOTHER_MEMBER_ID = UserId("@another:example.org")
        val AGENT_ID = UserId("@agent:example.org")
        val JELF_ID = UserId("@jelf:example.org")
    }
}
