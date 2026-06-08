/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotGetRoomAgentsResponse
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RoomAgentResolverTest {
    @Test
    fun `roomAgentUserIds - returns mxids and ignores null mxids`() = runTest {
        val service = FakeChatbotApiService().apply {
            getRoomAgentsResult = {
                Result.success(
                    ChatbotGetRoomAgentsResponse(
                        agents = listOf(
                            ChatbotRoomAgent(agentId = "a1", mxid = "@agent:example.org"),
                            ChatbotRoomAgent(agentId = "a2", mxid = null),
                            ChatbotRoomAgent(agentId = "a3", mxid = "@jelf:example.org"),
                        )
                    )
                )
            }
        }
        val resolver = RoomAgentResolver(FakeMatrixClient(), FakeChatbotApiServiceFactory(service))

        assertThat(resolver.roomAgentUserIds(A_ROOM_ID, setOf(A_MEMBER_ID))).containsExactly(AGENT_ID, JELF_ID)
    }

    @Test
    fun `roomAgentUserIds - returns empty set on API failure`() = runTest {
        val service = FakeChatbotApiService().apply {
            getRoomAgentsResult = { Result.failure(IllegalStateException("No agents today")) }
        }
        val resolver = RoomAgentResolver(FakeMatrixClient(), FakeChatbotApiServiceFactory(service))

        assertThat(resolver.roomAgentUserIds(A_ROOM_ID, setOf(A_MEMBER_ID))).isEmpty()
    }

    @Test
    fun `roomAgentUserIds - caches same active member signature and refreshes when it changes`() = runTest {
        var callCount = 0
        val service = FakeChatbotApiService().apply {
            getRoomAgentsResult = {
                callCount += 1
                Result.success(ChatbotGetRoomAgentsResponse(agents = listOf(ChatbotRoomAgent(agentId = "a$callCount", mxid = "@agent$callCount:example.org"))))
            }
        }
        val resolver = RoomAgentResolver(FakeMatrixClient(), FakeChatbotApiServiceFactory(service))

        val first = resolver.roomAgentUserIds(A_ROOM_ID, setOf(A_MEMBER_ID))
        val second = resolver.roomAgentUserIds(A_ROOM_ID, setOf(A_MEMBER_ID))
        val refreshed = resolver.roomAgentUserIds(A_ROOM_ID, setOf(A_MEMBER_ID, ANOTHER_MEMBER_ID))

        assertThat(first).containsExactly(UserId("@agent1:example.org"))
        assertThat(second).containsExactly(UserId("@agent1:example.org"))
        assertThat(refreshed).containsExactly(UserId("@agent2:example.org"))
        assertThat(callCount).isEqualTo(2)
    }

    private companion object {
        val A_ROOM_ID = RoomId("!room:example.org")
        val A_MEMBER_ID = UserId("@member:example.org")
        val ANOTHER_MEMBER_ID = UserId("@another:example.org")
        val AGENT_ID = UserId("@agent:example.org")
        val JELF_ID = UserId("@jelf:example.org")
    }
}
