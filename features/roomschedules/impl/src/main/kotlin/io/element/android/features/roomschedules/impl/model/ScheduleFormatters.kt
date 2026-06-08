/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.model

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule

fun ChatbotSchedule.stableId(): String = scheduleId ?: name

fun ChatbotSchedule.isEnabled(): Boolean {
    return when (status) {
        "enabled" -> true
        "disabled" -> false
        else -> enabled ?: false
    }
}

fun ChatbotSchedule.withEnabledStatus(enabled: Boolean): ChatbotSchedule {
    return copy(status = if (enabled) "enabled" else "disabled", enabled = null)
}

fun ChatbotAgent.matrixUserId(): String {
    val local = localpart
    val server = serverName
    return if (!local.isNullOrBlank() && !server.isNullOrBlank()) {
        "@$local:$server"
    } else {
        botName
    }
}
