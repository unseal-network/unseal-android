/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.room

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aChatbotSchedule
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RoomScheduleBadgePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads active schedule count and has agent in room`() = runTest {
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                Result.success(
                    listOf(
                        aChatbotSchedule(scheduleId = "enabled").copy(status = "enabled"),
                        aChatbotSchedule(scheduleId = "disabled").copy(status = "disabled"),
                    )
                )
            }
            listAgentsResult = {
                Result.success(listOf(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com")))
            }
        }
        val presenter = createPresenter(service = service, room = roomWithMembers("@agent:example.com"))

        presenter.test {
            awaitItem().eventSink(RoomScheduleBadgeEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.isVisible }
            assertThat(loaded.activeScheduleCount).isEqualTo(1)
            assertThat(loaded.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - hides schedules when no agent is joined`() = runTest {
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = { Result.success(listOf(aChatbotSchedule(scheduleId = "enabled").copy(status = "enabled"))) }
            listAgentsResult = { Result.success(listOf(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com"))) }
        }
        val presenter = createPresenter(service = service, room = roomWithMembers("@someone:example.com"))

        presenter.test {
            awaitItem().eventSink(RoomScheduleBadgeEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading }
            assertThat(loaded.isVisible).isFalse()
            assertThat(loaded.activeScheduleCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - failure hides button and stores error`() = runTest {
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = { Result.failure(RuntimeException("network")) }
            listAgentsResult = { Result.success(listOf(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com"))) }
        }
        val presenter = createPresenter(service = service, room = roomWithMembers("@agent:example.com"))

        presenter.test {
            awaitItem().eventSink(RoomScheduleBadgeEvents.OnAppear)
            val loaded = awaitStateWhere { !it.isLoading && it.error?.contains("network") == true }
            assertThat(loaded.isVisible).isFalse()
            assertThat(loaded.activeScheduleCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - refresh reloads count`() = runTest {
        var second = false
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                if (second) {
                    Result.success(
                        listOf(
                            aChatbotSchedule(scheduleId = "one").copy(status = "enabled"),
                            aChatbotSchedule(scheduleId = "two").copy(status = "enabled"),
                        )
                    )
                } else {
                    second = true
                    Result.success(emptyList())
                }
            }
            listAgentsResult = { Result.success(listOf(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com"))) }
        }
        val presenter = createPresenter(service = service, room = roomWithMembers("@agent:example.com"))

        presenter.test {
            awaitItem().eventSink(RoomScheduleBadgeEvents.OnAppear)
            val empty = awaitStateWhere { !it.isLoading && it.isVisible && it.activeScheduleCount == 0 }
            empty.eventSink(RoomScheduleBadgeEvents.Refresh)
            awaitStateWhere { !it.isLoading && it.activeScheduleCount == 2 }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService,
        room: FakeJoinedRoom,
    ): RoomScheduleBadgePresenter {
        return RoomScheduleBadgePresenter(
            roomId = A_ROOM_ID,
            joinedRoom = room,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }

    private fun roomWithMembers(vararg userIds: String): FakeJoinedRoom {
        return FakeJoinedRoom().apply {
            givenRoomMembersState(RoomMembersState.Ready(userIds.map(::joinedMember).toImmutableList()))
        }
    }

    private fun joinedMember(userId: String) = RoomMember(
        userId = UserId(userId),
        displayName = null,
        avatarUrl = null,
        membership = RoomMembershipState.JOIN,
        isNameAmbiguous = false,
        powerLevel = 0,
        isIgnored = false,
        role = RoomMember.Role.User,
        membershipChangeReason = null,
        isServiceMember = false,
    )
}

private suspend fun TurbineTestContext<RoomScheduleBadgeState>.awaitStateWhere(
    predicate: (RoomScheduleBadgeState) -> Boolean,
): RoomScheduleBadgeState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}
