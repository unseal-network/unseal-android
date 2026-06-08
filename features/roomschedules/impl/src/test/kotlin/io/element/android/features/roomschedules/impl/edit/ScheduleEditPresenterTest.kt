/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.roomschedules.impl.cron.CronPickerMode
import io.element.android.features.roomschedules.impl.cron.CronPickerModel
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.FakeChatbotApiServiceFactory
import io.element.android.libraries.chatbot.test.aChatbotSchedule
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ScheduleEditPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - create mode loads agents and selects first agent`() = runTest {
        val service = FakeChatbotApiService().apply {
            listAgentsResult = { Result.success(listOf(agent("alpha"), agent("beta"))) }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(ScheduleEditEvents.OnAppear)
            val loaded = awaitStateWhere { it.agents.size == 2 && it.selectedAgentBotName == "alpha" }
            assertThat(loaded.title).isEqualTo("New Schedule")
            assertThat(loaded.isCreate).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - edit mode seeds immutable name and agent plus editable action and cron`() = runTest {
        val schedule = aChatbotSchedule(scheduleId = "edit").copy(name = "Daily", agentId = "agent", action = "Do work", cron = "cron(15 10 ? * * *)")
        val presenter = createPresenter(mode = ScheduleEditMode.Edit(schedule))

        presenter.test {
            val state = awaitItem()
            assertThat(state.name).isEqualTo("Daily")
            assertThat(state.selectedAgentBotName).isEqualTo("agent")
            assertThat(state.action).isEqualTo("Do work")
            assertThat(state.cronModel).isEqualTo(CronPickerModel(CronPickerMode.EveryDay, hour = 10, minute = 15, intervalHours = 1, weekday = 2))
            assertThat(state.isCreate).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - submit validates create fields`() = runTest {
        val presenter = createPresenter()
        presenter.test {
            awaitItem().eventSink(ScheduleEditEvents.Submit)
            val missingName = awaitStateWhere { it.error == "Schedule name cannot be empty" }
            missingName.eventSink(ScheduleEditEvents.NameChanged("Daily"))
            val named = awaitStateWhere { it.name == "Daily" }
            named.eventSink(ScheduleEditEvents.Submit)
            val missingAction = awaitStateWhere { it.error == "Action cannot be empty" }
            assertThat(missingAction.error).isEqualTo("Action cannot be empty")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - create builds full agent matrix id and saves`() = runTest {
        val requests = mutableListOf<String>()
        val navigator = FakeScheduleEditNavigator()
        val service = FakeChatbotApiService().apply {
            listAgentsResult = { Result.success(listOf(agent("bot", localpart = "agent", serverName = "example.com"))) }
            createScheduleResult = {
                requests += "${it.agentId}|${it.name}|${it.action}|${it.roomId}"
                Result.success(io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleResponse(success = true, ebScheduleId = "ok"))
            }
        }
        val presenter = createPresenter(service = service, navigator = navigator)

        presenter.test {
            awaitItem().eventSink(ScheduleEditEvents.OnAppear)
            val loaded = awaitStateWhere { it.selectedAgentBotName == "bot" }
            loaded.eventSink(ScheduleEditEvents.NameChanged(" Daily "))
            val named = awaitStateWhere { it.name == " Daily " }
            named.eventSink(ScheduleEditEvents.ActionChanged(" Work "))
            val action = awaitStateWhere { it.action == " Work " }
            action.eventSink(ScheduleEditEvents.Submit)
            awaitStateWhere { navigator.savedCalls == 1 && !it.isSubmitting }
            assertThat(requests.single()).isEqualTo("@agent:example.com|Daily|Work|${A_ROOM_ID.value}")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - create blocks known agent that is not in room`() = runTest {
        val service = FakeChatbotApiService().apply {
            listAgentsResult = { Result.success(listOf(agent("bot", localpart = "agent", serverName = "example.com"))) }
        }
        val room = FakeJoinedRoom(
            baseRoom = FakeBaseRoom(
                getMembersResult = { Result.success(listOf(joinedMember("@someone:example.com"))) },
            )
        )
        val presenter = createPresenter(service = service, room = room)

        presenter.test {
            awaitItem().eventSink(ScheduleEditEvents.OnAppear)
            val loaded = awaitStateWhere { it.selectedAgentBotName == "bot" && it.joinedMemberIds.isNotEmpty() }
            loaded.eventSink(ScheduleEditEvents.NameChanged("Daily"))
            awaitStateWhere { it.name == "Daily" }.eventSink(ScheduleEditEvents.ActionChanged("Work"))
            awaitStateWhere { it.action == "Work" }.eventSink(ScheduleEditEvents.Submit)
            assertThat(awaitStateWhere { it.error == "Agent not in room" }.selectedAgentIsInRoom).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - edit sends cron action and timezone only`() = runTest {
        val updates = mutableListOf<String>()
        val navigator = FakeScheduleEditNavigator()
        val service = FakeChatbotApiService().apply {
            updateScheduleResult = { id, request ->
                updates += "$id|${request.action}|${request.cron}|${request.timezone.isNotBlank()}"
                Result.success(io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleResponse(success = true, ebScheduleId = "ok"))
            }
        }
        val schedule = aChatbotSchedule(scheduleId = "edit").copy(action = "Old", cron = "cron(00 09 ? * * *)")
        val presenter = createPresenter(mode = ScheduleEditMode.Edit(schedule), service = service, navigator = navigator)

        presenter.test {
            val state = awaitItem()
            state.eventSink(ScheduleEditEvents.ActionChanged("New"))
            val changed = awaitStateWhere { it.action == "New" }
            changed.eventSink(ScheduleEditEvents.Submit)
            awaitStateWhere { navigator.savedCalls == 1 }
            assertThat(updates.single()).startsWith("edit|New|cron(")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        mode: ScheduleEditMode = ScheduleEditMode.Create,
        service: FakeChatbotApiService = FakeChatbotApiService(),
        room: FakeJoinedRoom = FakeJoinedRoom(),
        navigator: FakeScheduleEditNavigator = FakeScheduleEditNavigator(),
    ): ScheduleEditPresenter {
        return ScheduleEditPresenter(
            mode = mode,
            roomId = A_ROOM_ID,
            joinedRoom = room,
            navigator = navigator,
            matrixClient = FakeMatrixClient(),
            chatbotApiServiceFactory = FakeChatbotApiServiceFactory(service),
        )
    }

    private fun agent(botName: String, localpart: String? = botName, serverName: String? = "example.com") =
        ChatbotAgent(botName = botName, localpart = localpart, serverName = serverName)

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

private class FakeScheduleEditNavigator : ScheduleEditNavigator {
    var savedCalls = 0
    var cancelledCalls = 0

    override fun onSaved() {
        savedCalls++
    }

    override fun onCancelled() {
        cancelledCalls++
    }
}

private suspend fun TurbineTestContext<ScheduleEditState>.awaitStateWhere(
    predicate: (ScheduleEditState) -> Boolean,
): ScheduleEditState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}
