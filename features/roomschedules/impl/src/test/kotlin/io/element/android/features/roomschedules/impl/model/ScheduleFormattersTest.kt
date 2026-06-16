/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.model

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.test.aChatbotSchedule
import org.junit.Test

class ScheduleFormattersTest {
    @Test
    fun `isEnabled - status takes precedence and missing fields default disabled`() {
        assertThat(aChatbotSchedule(scheduleId = "s").copy(status = "enabled", enabled = false).isEnabled()).isTrue()
        assertThat(aChatbotSchedule(scheduleId = "s").copy(status = "disabled", enabled = true).isEnabled()).isFalse()
        assertThat(aChatbotSchedule(scheduleId = "s").copy(status = null, enabled = true).isEnabled()).isTrue()
        assertThat(aChatbotSchedule(scheduleId = "s").copy(status = null, enabled = null).isEnabled()).isFalse()
    }

    @Test
    fun `stableId - uses schedule id then name`() {
        assertThat(aChatbotSchedule(scheduleId = "schedule-id").stableId()).isEqualTo("schedule-id")
        assertThat(aChatbotSchedule(scheduleId = "schedule-id").copy(scheduleId = null, name = "Daily").stableId()).isEqualTo("Daily")
    }

    @Test
    fun `matrixUserId - builds from localpart and server name with bot fallback`() {
        assertThat(ChatbotAgent(botName = "bot", localpart = "agent", serverName = "example.com").matrixUserId()).isEqualTo("@agent:example.com")
        assertThat(ChatbotAgent(botName = "bot", localpart = null, serverName = "example.com").matrixUserId()).isEqualTo("bot")
    }
}
