/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.roomschedules.impl.R
import io.element.android.features.roomschedules.impl.cron.CronParser
import io.element.android.features.roomschedules.impl.cron.CronPickerModel
import io.element.android.features.roomschedules.impl.model.matrixUserId
import io.element.android.features.roomschedules.impl.model.stableId
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotCreateScheduleRequest
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotUpdateScheduleRequest
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import java.util.TimeZone

@AssistedInject
class ScheduleEditPresenter(
    @Assisted private val mode: ScheduleEditMode,
    @Assisted private val roomId: RoomId,
    @Assisted private val joinedRoom: JoinedRoom,
    @Assisted private val navigator: ScheduleEditNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : Presenter<ScheduleEditState> {
    @AssistedFactory
    interface Factory {
        fun create(
            mode: ScheduleEditMode,
            roomId: RoomId,
            joinedRoom: JoinedRoom,
            navigator: ScheduleEditNavigator,
        ): ScheduleEditPresenter
    }

    @Composable
    override fun present(): ScheduleEditState {
        val coroutineScope = rememberCoroutineScope()
        val initialSchedule = (mode as? ScheduleEditMode.Edit)?.schedule
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var agents by remember { mutableStateOf(emptyList<ChatbotAgent>()) }
        var joinedMemberIds by remember { mutableStateOf(emptySet<String>()) }
        var isSubmitting by remember { mutableStateOf(false) }
        var name by remember { mutableStateOf(initialSchedule?.name.orEmpty()) }
        var selectedAgentBotName by remember { mutableStateOf(initialSchedule?.agentId.orEmpty()) }
        var action by remember { mutableStateOf(initialSchedule?.action.orEmpty()) }
        var cronModel by remember { mutableStateOf(initialSchedule?.cron?.let(CronParser::toPickerModel) ?: CronPickerModel.Default) }
        var error by remember { mutableStateOf<String?>(null) }
        val loadError = stringResource(R.string.schedule_edit_error_load)
        val saveError = stringResource(R.string.schedule_edit_error_save)
        val emptyNameError = stringResource(R.string.schedule_edit_error_empty_name)
        val emptyActionError = stringResource(R.string.schedule_edit_error_empty_action)
        val emptyAgentError = stringResource(R.string.schedule_edit_error_empty_agent)
        val agentNotInRoomError = stringResource(R.string.schedule_edit_error_agent_not_in_room)

        fun errorMessage(throwable: Throwable, fallback: String): String {
            return throwable.message?.takeIf { it.isNotBlank() } ?: fallback
        }

        suspend fun api() = chatbotApiServiceFactory.createForHomeserver(matrixClient)

        fun selectedAgentIsInRoom(): Boolean {
            if (selectedAgentBotName.isBlank() || joinedMemberIds.isEmpty()) return true
            return agents.firstOrNull { it.botName == selectedAgentBotName }
                ?.matrixUserId()
                ?.let { it in joinedMemberIds } != false
        }

        fun loadInitialData() = coroutineScope.launch {
            api().listAgents()
                .onSuccess { loadedAgents ->
                    agents = loadedAgents
                    if (mode is ScheduleEditMode.Create && selectedAgentBotName.isBlank()) {
                        selectedAgentBotName = loadedAgents.firstOrNull()?.botName.orEmpty()
                    }
                }
                .onFailure { error = errorMessage(it, loadError) }

            runCatching { joinedRoom.getMembers(limit = Int.MAX_VALUE) }
                .getOrNull()
                ?.onSuccess { members ->
                    joinedMemberIds = members
                        .filter { it.membership == RoomMembershipState.JOIN }
                        .map { it.userId.value }
                        .toSet()
                }
        }

        fun validate(): Boolean {
            error = when {
                mode is ScheduleEditMode.Create && name.trim().isEmpty() -> emptyNameError
                action.trim().isEmpty() -> emptyActionError
                mode is ScheduleEditMode.Create && selectedAgentBotName.isBlank() -> emptyAgentError
                !selectedAgentIsInRoom() -> agentNotInRoomError
                else -> null
            }
            return error == null
        }

        fun submit() = coroutineScope.launch {
            if (!validate()) return@launch
            isSubmitting = true
            val timezone = TimeZone.getDefault().id
            val cron = CronParser.toCron(cronModel)
            val service = api()
            val result = when (val currentMode = mode) {
                ScheduleEditMode.Create -> {
                    val agentId = agents.firstOrNull { it.botName == selectedAgentBotName }?.matrixUserId() ?: selectedAgentBotName
                    service.createSchedule(
                        ChatbotCreateScheduleRequest(
                            agentId = agentId,
                            name = name.trim(),
                            cron = cron,
                            action = action.trim(),
                            timezone = timezone,
                            roomId = roomId.value,
                        )
                    ).map { Unit }
                }
                is ScheduleEditMode.Edit -> {
                    service.updateSchedule(
                        scheduleId = currentMode.schedule.stableId(),
                        request = ChatbotUpdateScheduleRequest(
                            cron = cron,
                            action = action.trim(),
                            timezone = timezone,
                        )
                    ).map { Unit }
                }
            }
            val isLenientCreateSuccess = mode is ScheduleEditMode.Create &&
                (result.exceptionOrNull() as? ChatbotApiError.HttpError)?.statusCode in setOf(200, 500)
            if (result.isSuccess || isLenientCreateSuccess) {
                error = null
                navigator.onSaved()
            } else {
                error = errorMessage(result.exceptionOrNull() ?: RuntimeException(), saveError)
            }
            isSubmitting = false
        }

        fun handleEvent(event: ScheduleEditEvents) {
            when (event) {
                ScheduleEditEvents.OnAppear -> if (!hasLoadedOnce) {
                    hasLoadedOnce = true
                    loadInitialData()
                }
                is ScheduleEditEvents.NameChanged -> name = event.name
                is ScheduleEditEvents.AgentChanged -> selectedAgentBotName = event.botName
                is ScheduleEditEvents.ActionChanged -> action = event.action
                is ScheduleEditEvents.CronModelChanged -> cronModel = event.model
                ScheduleEditEvents.Submit -> submit()
                ScheduleEditEvents.Cancel -> navigator.onCancelled()
                ScheduleEditEvents.ClearError -> error = null
            }
        }

        return ScheduleEditState(
            mode = mode,
            agents = agents.toImmutableList(),
            joinedMemberIds = joinedMemberIds.toImmutableSet(),
            isSubmitting = isSubmitting,
            name = name,
            selectedAgentBotName = selectedAgentBotName,
            action = action,
            cronModel = cronModel,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}
