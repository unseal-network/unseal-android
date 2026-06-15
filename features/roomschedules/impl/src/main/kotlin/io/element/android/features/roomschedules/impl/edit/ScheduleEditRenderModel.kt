/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

import io.element.android.features.roomschedules.impl.cron.CronParser
import io.element.android.features.roomschedules.impl.cron.CronPickerMode
import io.element.android.features.roomschedules.impl.cron.CronPickerModel
import io.element.android.features.roomschedules.impl.model.matrixUserId
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class ScheduleEditRenderModel(
    val title: String,
    val isCreate: Boolean,
    val nameLabel: String,
    val agentLabel: String,
    val selectedAgentLabel: String,
    val agentOptions: ImmutableList<ScheduleAgentOption>,
    val actionLabel: String,
    val repeatLabel: String,
    val cronSummary: String,
    val cronModeOptions: ImmutableList<CronModeOption>,
    val canSubmit: Boolean,
    val submitLabel: String,
    val outOfRoomAgentWarning: String?,
)

data class ScheduleAgentOption(
    val botName: String,
    val label: String,
    val matrixUserId: String,
    val isSelected: Boolean,
    val isInRoom: Boolean,
)

data class CronModeOption(
    val mode: CronPickerMode,
    val label: String,
    val isSelected: Boolean,
)

fun ScheduleEditState.toRenderModel(): ScheduleEditRenderModel {
    return ScheduleEditRenderModel(
        title = title,
        isCreate = isCreate,
        nameLabel = "Schedule name",
        agentLabel = "Agent",
        selectedAgentLabel = selectedAgentLabel(),
        agentOptions = agents.toAgentOptions(
            selectedAgentBotName = selectedAgentBotName,
            joinedMemberIds = joinedMemberIds,
        ),
        actionLabel = "Action",
        repeatLabel = "Repeat",
        cronSummary = CronParser.toReadable(CronParser.toCron(cronModel)),
        cronModeOptions = CronPickerMode.entries.map { mode ->
            CronModeOption(
                mode = mode,
                label = mode.displayLabel(),
                isSelected = cronModel.mode == mode,
            )
        }.toImmutableList(),
        canSubmit = !isSubmitting,
        submitLabel = if (isSubmitting) "Saving" else "Save",
        outOfRoomAgentWarning = if (selectedAgentIsInRoom) null else "Selected agent is not in this room.",
    )
}

fun List<ChatbotAgent>.toAgentOptions(
    selectedAgentBotName: String,
    joinedMemberIds: Set<String>,
): ImmutableList<ScheduleAgentOption> {
    return map { agent ->
        val matrixUserId = agent.matrixUserId()
        ScheduleAgentOption(
            botName = agent.botName,
            label = agent.displayName?.takeIf { it.isNotBlank() } ?: matrixUserId,
            matrixUserId = matrixUserId,
            isSelected = selectedAgentBotName == agent.botName,
            isInRoom = joinedMemberIds.isEmpty() || matrixUserId in joinedMemberIds,
        )
    }.toImmutableList()
}

fun CronPickerMode.displayLabel(): String = when (this) {
    CronPickerMode.Workdays -> "Workdays"
    CronPickerMode.EveryDay -> "Every day"
    CronPickerMode.EveryNHours -> "Every N hours"
    CronPickerMode.EveryHourAtMinute -> "Hourly"
    CronPickerMode.Weekday -> "Weekday"
}

fun weekdayDisplayLabel(weekday: Int): String = when (weekday) {
    1 -> "Sun"
    2 -> "Mon"
    3 -> "Tue"
    4 -> "Wed"
    5 -> "Thu"
    6 -> "Fri"
    7 -> "Sat"
    else -> weekday.toString()
}

private fun ScheduleEditState.selectedAgentLabel(): String {
    return agents.firstOrNull { it.botName == selectedAgentBotName }
        ?.let { it.displayName?.takeIf(String::isNotBlank) ?: it.matrixUserId() }
        ?: selectedAgentBotName
}

fun CronPickerModel.withMode(mode: CronPickerMode): CronPickerModel = copy(mode = mode)
