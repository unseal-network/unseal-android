/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.config

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.roomschedules.impl.model.isEnabled
import io.element.android.features.roomschedules.impl.model.stableId
import io.element.android.libraries.chatbot.test.FakeChatbotApiService
import io.element.android.libraries.chatbot.test.aChatbotSchedule
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class RoomSchedulesPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads schedules and working memory once`() = runTest {
        var scheduleCalls = 0
        var memoryCalls = 0
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                scheduleCalls++
                Result.success(listOf(aChatbotSchedule(scheduleId = "one").copy(status = "enabled", creatorId = A_SESSION_ID.value)))
            }
            getRoomWorkingMemoryResult = {
                memoryCalls++
                Result.success("memory")
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.schedules.isNotEmpty() && it.workingMemory == "memory" && !it.isLoadingSchedules && !it.isLoadingMemory }
            assertThat(loaded.schedules.single().stableId()).isEqualTo("one")
            assertThat(scheduleCalls).isEqualTo(1)
            assertThat(memoryCalls).isEqualTo(1)
            loaded.eventSink(RoomSchedulesEvents.OnAppear)
            assertThat(scheduleCalls).isEqualTo(1)
            assertThat(memoryCalls).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `state - displayed schedules hide disabled schedules from other users and sort enabled first`() = runTest {
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                Result.success(
                    listOf(
                        aChatbotSchedule(scheduleId = "other-disabled").copy(status = "disabled", creatorId = "@other:server"),
                        aChatbotSchedule(scheduleId = "mine-disabled").copy(status = "disabled", creatorId = A_SESSION_ID.value),
                        aChatbotSchedule(scheduleId = "mine-enabled").copy(status = "enabled", creatorId = A_SESSION_ID.value),
                    )
                )
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.schedules.size == 3 && !it.isLoadingSchedules }
            assertThat(loaded.displayedSchedules.map { it.stableId() }).containsExactly("mine-enabled", "mine-disabled").inOrder()
            assertThat(loaded.activeCount).isEqualTo(1)
            loaded.eventSink(RoomSchedulesEvents.ShowOnlyMineChanged(true))
            val mine = awaitStateWhere { it.showOnlyMine }
            assertThat(mine.displayedSchedules.map { it.stableId() }).containsExactly("mine-enabled", "mine-disabled").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - toggle schedule optimistically updates and rolls back by reloading on failure`() = runTest {
        var statusRequest: Pair<String, String>? = null
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = {
                Result.success(listOf(aChatbotSchedule(scheduleId = "toggle").copy(status = "enabled", creatorId = A_SESSION_ID.value)))
            }
            updateScheduleStatusResult = { id, status ->
                statusRequest = id to status
                Result.failure(RuntimeException("network"))
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.schedules.singleOrNull()?.isEnabled() == true }
            loaded.eventSink(RoomSchedulesEvents.ToggleSchedule(loaded.schedules.single()))
            val failed = awaitStateWhere { it.scheduleError?.contains("network") == true && it.schedules.single().isEnabled() }
            assertThat(statusRequest).isEqualTo("toggle" to "disabled")
            assertThat(failed.schedules.single().isEnabled()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - delete confirms then removes local schedule on success`() = runTest {
        val deleted = mutableListOf<String>()
        val service = FakeChatbotApiService().apply {
            listSchedulesResult = { Result.success(listOf(aChatbotSchedule(scheduleId = "delete").copy(creatorId = A_SESSION_ID.value))) }
            deleteScheduleResult = {
                deleted += it
                Result.success(Unit)
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.schedules.size == 1 }
            loaded.eventSink(RoomSchedulesEvents.RequestDeleteSchedule(loaded.schedules.single()))
            val confirming = awaitStateWhere { it.deleteConfirmationScheduleId == "delete" }
            confirming.eventSink(RoomSchedulesEvents.ConfirmDeleteSchedule)
            val deletedState = awaitStateWhere { it.schedules.isEmpty() && it.deleteConfirmationScheduleId == null }
            assertThat(deleted).containsExactly("delete")
            assertThat(deletedState.displayedSchedules).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `event - working memory save and cancel preserve iOS behavior`() = runTest {
        val saved = mutableListOf<String>()
        val service = FakeChatbotApiService().apply {
            getRoomWorkingMemoryResult = { Result.success("old") }
            updateRoomWorkingMemoryResult = { _, content ->
                saved += content
                Result.success(Unit)
            }
        }
        val presenter = createPresenter(service = service)

        presenter.test {
            awaitItem().eventSink(RoomSchedulesEvents.OnAppear)
            val loaded = awaitStateWhere { it.workingMemory == "old" }
            loaded.eventSink(RoomSchedulesEvents.StartEditingMemory)
            val editing = awaitStateWhere { it.isEditingMemory && it.editingMemoryText == "old" }
            editing.eventSink(RoomSchedulesEvents.EditingMemoryChanged("new"))
            val changed = awaitStateWhere { it.editingMemoryText == "new" }
            changed.eventSink(RoomSchedulesEvents.SaveMemory)
            val savedState = awaitStateWhere { !it.isEditingMemory && it.workingMemory == "new" }
            assertThat(saved).containsExactly("new")
            savedState.eventSink(RoomSchedulesEvents.StartEditingMemory)
            val editingAgain = awaitStateWhere { it.isEditingMemory }
            editingAgain.eventSink(RoomSchedulesEvents.CancelEditingMemory)
            awaitStateWhere { !it.isEditingMemory && it.editingMemoryText.isEmpty() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createPresenter(
        service: FakeChatbotApiService = FakeChatbotApiService(),
        room: FakeJoinedRoom = FakeJoinedRoom(),
        navigator: FakeRoomSchedulesNavigator = FakeRoomSchedulesNavigator(),
    ): RoomSchedulesPresenter {
        return RoomSchedulesPresenter(
            roomId = A_ROOM_ID,
            roomName = "Room",
            joinedRoom = room,
            chatbotApiService = service,
            navigator = navigator,
        )
    }
}

private class FakeRoomSchedulesNavigator : RoomSchedulesNavigator {
    val created = mutableListOf<Unit>()
    val edited = mutableListOf<String>()
    var doneCalls = 0
    var changedCalls = 0

    override fun onCreateSchedule() {
        created += Unit
    }

    override fun onEditSchedule(scheduleId: String) {
        edited += scheduleId
    }

    override fun onDone() {
        doneCalls++
    }

    override fun onSchedulesChanged() {
        changedCalls++
    }
}

private suspend fun TurbineTestContext<RoomSchedulesState>.awaitStateWhere(
    predicate: (RoomSchedulesState) -> Boolean,
): RoomSchedulesState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) {
            return state
        }
    }
}
