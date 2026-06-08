/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.config

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.roomschedules.impl.model.isEnabled
import io.element.android.features.roomschedules.impl.model.stableId
import io.element.android.features.roomschedules.impl.model.withEnabledStatus
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.powerlevels.canEditRolesAndPermissions
import io.element.android.libraries.matrix.api.room.powerlevels.use
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@AssistedInject
class RoomSchedulesPresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val roomName: String,
    @Assisted private val joinedRoom: JoinedRoom,
    @Assisted private val navigator: RoomSchedulesNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<RoomSchedulesState> {
    @AssistedFactory
    interface Factory {
        fun create(
            roomId: RoomId,
            roomName: String,
            joinedRoom: JoinedRoom,
            navigator: RoomSchedulesNavigator,
        ): RoomSchedulesPresenter
    }

    @Composable
    override fun present(): RoomSchedulesState {
        val coroutineScope = rememberCoroutineScope()
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var selectedTab by remember { mutableStateOf(RoomSchedulesTab.Schedules) }
        var schedules by remember { mutableStateOf(emptyList<ChatbotSchedule>()) }
        var isLoadingSchedules by remember { mutableStateOf(false) }
        var showOnlyMine by remember { mutableStateOf(false) }
        var scheduleError by remember { mutableStateOf<String?>(null) }
        var workingMemory by remember { mutableStateOf("") }
        var editingMemoryText by remember { mutableStateOf("") }
        var isEditingMemory by remember { mutableStateOf(false) }
        var isLoadingMemory by remember { mutableStateOf(false) }
        var isSavingMemory by remember { mutableStateOf(false) }
        var canEditMemory by remember { mutableStateOf(true) }
        var memoryError by remember { mutableStateOf<String?>(null) }
        var deleteConfirmationScheduleId by remember { mutableStateOf<String?>(null) }

        fun errorMessage(throwable: Throwable): String {
            return throwable.message ?: throwable::class.simpleName ?: throwable.toString()
        }

        suspend fun api() = chatbotApiServiceFactory.createForUnsealApi(matrixClient)

        fun loadSchedules() = coroutineScope.launch {
            isLoadingSchedules = true
            api().listSchedules(roomId.value)
                .onSuccess {
                    schedules = it
                    scheduleError = null
                }
                .onFailure {
                    scheduleError = errorMessage(it)
                }
            isLoadingSchedules = false
        }

        fun loadMemory() = coroutineScope.launch {
            isLoadingMemory = true
            api().getRoomWorkingMemory(roomId.value)
                .onSuccess {
                    workingMemory = it
                    memoryError = null
                }
                .onFailure {
                    memoryError = errorMessage(it)
                }
            isLoadingMemory = false
        }

        fun checkPermission() = coroutineScope.launch {
            canEditMemory = joinedRoom.roomPermissions().use(default = true) { permissions ->
                permissions.canEditRolesAndPermissions()
            }
        }

        fun toggle(schedule: ChatbotSchedule) = coroutineScope.launch {
            val id = schedule.stableId()
            val newEnabled = !schedule.isEnabled()
            schedules = schedules.map { if (it.stableId() == id) it.withEnabledStatus(newEnabled) else it }
            val service = api()
            service.updateScheduleStatus(id, if (newEnabled) "enabled" else "disabled")
                .onSuccess {
                    scheduleError = null
                    navigator.onSchedulesChanged()
                }
                .onFailure {
                    scheduleError = errorMessage(it)
                    service.listSchedules(roomId.value)
                        .onSuccess { reloaded -> schedules = reloaded }
                }
        }

        fun deleteConfirmed() = coroutineScope.launch {
            val id = deleteConfirmationScheduleId ?: return@launch
            api().deleteSchedule(id)
                .onSuccess {
                    schedules = schedules.filterNot { it.stableId() == id }
                    deleteConfirmationScheduleId = null
                    scheduleError = null
                    navigator.onSchedulesChanged()
                }
                .onFailure {
                    scheduleError = errorMessage(it)
                }
        }

        fun saveMemory() = coroutineScope.launch {
            isSavingMemory = true
            api().updateRoomWorkingMemory(roomId.value, editingMemoryText)
                .onSuccess {
                    workingMemory = editingMemoryText
                    editingMemoryText = ""
                    isEditingMemory = false
                    memoryError = null
                }
                .onFailure {
                    memoryError = errorMessage(it)
                }
            isSavingMemory = false
        }

        fun handleEvent(event: RoomSchedulesEvents) {
            when (event) {
                RoomSchedulesEvents.OnAppear -> if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    loadSchedules()
                    loadMemory()
                    checkPermission()
                }
                RoomSchedulesEvents.Refresh -> {
                    if (selectedTab == RoomSchedulesTab.Schedules) {
                        loadSchedules()
                    } else {
                        loadMemory()
                    }
                }
                is RoomSchedulesEvents.SelectTab -> selectedTab = event.tab
                is RoomSchedulesEvents.ShowOnlyMineChanged -> showOnlyMine = event.showOnlyMine
                RoomSchedulesEvents.CreateSchedule -> navigator.onCreateSchedule()
                is RoomSchedulesEvents.EditSchedule -> navigator.onEditSchedule(event.schedule)
                is RoomSchedulesEvents.ToggleSchedule -> toggle(event.schedule)
                is RoomSchedulesEvents.RequestDeleteSchedule -> deleteConfirmationScheduleId = event.schedule.stableId()
                RoomSchedulesEvents.ConfirmDeleteSchedule -> deleteConfirmed()
                RoomSchedulesEvents.DismissDeleteConfirmation -> deleteConfirmationScheduleId = null
                RoomSchedulesEvents.RefreshMemory -> loadMemory()
                RoomSchedulesEvents.StartEditingMemory -> {
                    editingMemoryText = workingMemory
                    isEditingMemory = true
                }
                is RoomSchedulesEvents.EditingMemoryChanged -> editingMemoryText = event.text
                RoomSchedulesEvents.CancelEditingMemory -> {
                    editingMemoryText = ""
                    isEditingMemory = false
                }
                RoomSchedulesEvents.SaveMemory -> saveMemory()
                RoomSchedulesEvents.ClearError -> {
                    scheduleError = null
                    memoryError = null
                }
                RoomSchedulesEvents.Dismiss -> {
                    navigator.onSchedulesChanged()
                    navigator.onDone()
                }
            }
        }

        return RoomSchedulesState(
            roomId = roomId,
            roomName = roomName,
            currentUserId = joinedRoom.sessionId.value,
            selectedTab = selectedTab,
            schedules = schedules.toImmutableList(),
            isLoadingSchedules = isLoadingSchedules,
            showOnlyMine = showOnlyMine,
            scheduleError = scheduleError,
            workingMemory = workingMemory,
            editingMemoryText = editingMemoryText,
            isEditingMemory = isEditingMemory,
            isLoadingMemory = isLoadingMemory,
            isSavingMemory = isSavingMemory,
            canEditMemory = canEditMemory,
            memoryError = memoryError,
            deleteConfirmationScheduleId = deleteConfirmationScheduleId,
            eventSink = ::handleEvent,
        )
    }
}
