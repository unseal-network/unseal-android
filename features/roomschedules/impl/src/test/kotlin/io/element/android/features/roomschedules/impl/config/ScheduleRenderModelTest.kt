/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.config

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.test.aChatbotSchedule
import org.junit.Test

class ScheduleRenderModelTest {
    @Test
    fun `toScheduleRenderModels - filters other disabled schedules and sorts enabled first`() {
        val schedules = listOf(
            aChatbotSchedule(scheduleId = "z-other-disabled").copy(name = "Zeta", status = "disabled", creatorId = "@other:server"),
            aChatbotSchedule(scheduleId = "b-mine-disabled").copy(name = "Beta", status = "disabled", creatorId = "@me:server"),
            aChatbotSchedule(scheduleId = "a-mine-enabled").copy(name = "Alpha", status = "enabled", creatorId = "@me:server"),
        )

        val models = schedules.toScheduleRenderModels(
            currentUserId = "@me:server",
            showOnlyMine = false,
        )

        assertThat(models.map { it.id }).containsExactly("a-mine-enabled", "b-mine-disabled").inOrder()
        assertThat(models.map { it.statusLabel }).containsExactly("Enabled", "Disabled").inOrder()
        assertThat(models.map { it.toggleLabel }).containsExactly("Disable", "Enable").inOrder()
        assertThat(models.all { it.isOwner }).isTrue()
    }

    @Test
    fun `toScheduleRenderModel - exposes stable display fields for UI`() {
        val schedule = aChatbotSchedule(scheduleId = "daily").copy(
            name = "Daily report",
            agentId = "@agent:server",
            cron = "cron(15 10 ? * * *)",
            action = "Send a report",
            status = "enabled",
            creatorId = "@owner:server",
        )

        val model = schedule.toScheduleRenderModel(currentUserId = "@other:server")

        assertThat(model.id).isEqualTo("daily")
        assertThat(model.title).isEqualTo("Daily report")
        assertThat(model.agentLabel).isEqualTo("@agent:server")
        assertThat(model.cronLabel).contains("10")
        assertThat(model.actionPreview).isEqualTo("Send a report")
        assertThat(model.isEnabled).isTrue()
        assertThat(model.isOwner).isFalse()
        assertThat(model.statusLabel).isEqualTo("Enabled")
        assertThat(model.toggleLabel).isEqualTo("Disable")
        assertThat(model.source).isEqualTo(schedule)
    }
}
