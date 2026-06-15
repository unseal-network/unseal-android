/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

import com.google.common.truth.Truth.assertThat
import io.element.android.features.roomschedules.impl.cron.CronPickerMode
import io.element.android.features.roomschedules.impl.cron.CronPickerModel
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.junit.Test

class ScheduleEditRenderModelTest {
    @Test
    fun `toRenderModel - exposes stable create labels and agent options`() {
        val state = aState(
            agents = listOf(
                ChatbotAgent(botName = "bot-a", displayName = "Bot A", localpart = "a", serverName = "server"),
                ChatbotAgent(botName = "bot-b", displayName = null, localpart = "b", serverName = "server"),
            ),
            joinedMemberIds = persistentSetOf("@a:server"),
            selectedAgentBotName = "bot-b",
        )

        val model = state.renderModel

        assertThat(model.title).isEqualTo("New Schedule")
        assertThat(model.isCreate).isTrue()
        assertThat(model.nameLabel).isEqualTo("Schedule name")
        assertThat(model.agentLabel).isEqualTo("Agent")
        assertThat(model.selectedAgentLabel).isEqualTo("@b:server")
        assertThat(model.agentOptions.map { it.label }).containsExactly("Bot A", "@b:server").inOrder()
        assertThat(model.agentOptions.map { it.isSelected }).containsExactly(false, true).inOrder()
        assertThat(model.agentOptions.map { it.isInRoom }).containsExactly(true, false).inOrder()
        assertThat(model.outOfRoomAgentWarning).isEqualTo("Selected agent is not in this room.")
    }

    @Test
    fun `toRenderModel - exposes cron options and submit state`() {
        val state = aState(
            cronModel = CronPickerModel(CronPickerMode.EveryNHours, hour = 9, minute = 0, intervalHours = 4, weekday = 2),
            isSubmitting = true,
        )

        val model = state.renderModel

        assertThat(model.cronSummary).isEqualTo("Every 4h")
        assertThat(model.cronModeOptions.single { it.mode == CronPickerMode.EveryNHours }.isSelected).isTrue()
        assertThat(model.canSubmit).isFalse()
        assertThat(model.submitLabel).isEqualTo("Saving")
    }

    @Test
    fun `display helpers - expose stable labels`() {
        assertThat(CronPickerMode.Workdays.displayLabel()).isEqualTo("Workdays")
        assertThat(CronPickerMode.EveryDay.displayLabel()).isEqualTo("Every day")
        assertThat(CronPickerMode.EveryNHours.displayLabel()).isEqualTo("Every N hours")
        assertThat(CronPickerMode.EveryHourAtMinute.displayLabel()).isEqualTo("Hourly")
        assertThat(CronPickerMode.Weekday.displayLabel()).isEqualTo("Weekday")
        assertThat(weekdayDisplayLabel(1)).isEqualTo("Sun")
        assertThat(weekdayDisplayLabel(7)).isEqualTo("Sat")
    }

    private fun aState(
        agents: List<ChatbotAgent> = emptyList(),
        joinedMemberIds: Set<String> = emptySet(),
        selectedAgentBotName: String = "",
        cronModel: CronPickerModel = CronPickerModel.Default,
        isSubmitting: Boolean = false,
    ) = ScheduleEditState(
        mode = ScheduleEditMode.Create,
        agents = agents.toImmutableList(),
        joinedMemberIds = joinedMemberIds.toImmutableSetCompat(),
        selectedAgentBotName = selectedAgentBotName,
        cronModel = cronModel,
        isSubmitting = isSubmitting,
        eventSink = {},
    )
}

private fun Set<String>.toImmutableSetCompat() = toImmutableSet()
