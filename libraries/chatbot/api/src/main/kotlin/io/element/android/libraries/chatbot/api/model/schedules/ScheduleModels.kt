/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.schedules

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatbotSchedule(
    @SerialName("schedule_id")
    val scheduleId: String? = null,
    val name: String,
    val cron: String,
    val action: String,
    @SerialName("agent_id")
    val agentId: String,
    @SerialName("room_id")
    val roomId: String,
    val timezone: String? = null,
    @SerialName("creator_id")
    val creatorId: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("last_modified")
    val lastModified: String? = null,
    val status: String? = null,
    val enabled: Boolean? = null,
)

@Serializable
data class ChatbotListSchedulesResponse(
    val schedules: List<ChatbotSchedule> = emptyList(),
    val count: Int? = null,
)

@Serializable
data class ChatbotCreateScheduleRequest(
    @SerialName("agent_id")
    val agentId: String,
    val name: String,
    val cron: String,
    val action: String,
    val timezone: String,
    @SerialName("room_id")
    val roomId: String,
)

@Serializable
data class ChatbotUpdateScheduleRequest(
    val cron: String,
    val action: String,
    val timezone: String,
)

@Serializable
data class ChatbotCreateScheduleResponse(
    val success: Boolean? = null,
    @SerialName("schedule_id")
    val scheduleId: String? = null,
    @SerialName("eb_schedule_id")
    val ebScheduleId: String? = null,
)

@Serializable
data class ChatbotScheduleStatusRequest(
    val status: String,
)
