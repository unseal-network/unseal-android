/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

import io.element.android.features.roomschedules.impl.cron.CronPickerModel
import io.element.android.features.roomschedules.impl.model.matrixUserId
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

data class ScheduleEditState(
    val mode: ScheduleEditMode,
    val agents: ImmutableList<ChatbotAgent> = emptyList<ChatbotAgent>().toImmutableList(),
    val joinedMemberIds: ImmutableSet<String> = persistentSetOf(),
    val isSubmitting: Boolean = false,
    val name: String = "",
    val selectedAgentBotName: String = "",
    val action: String = "",
    val cronModel: CronPickerModel = CronPickerModel.Default,
    val error: String? = null,
    val eventSink: (ScheduleEditEvents) -> Unit,
) {
    val isCreate: Boolean = mode is ScheduleEditMode.Create
    val title: String = if (isCreate) "New Schedule" else "Edit Schedule"
    val selectedAgentIsInRoom: Boolean = selectedAgentBotName.isBlank() ||
        joinedMemberIds.isEmpty() ||
        agents.firstOrNull { it.botName == selectedAgentBotName }?.matrixUserId()?.let { it in joinedMemberIds } != false
}
