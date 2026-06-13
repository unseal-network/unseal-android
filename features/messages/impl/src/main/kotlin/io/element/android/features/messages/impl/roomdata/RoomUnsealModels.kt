/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkill
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import kotlinx.serialization.json.JsonPrimitive

data class RoomUnsealDataSnapshot(
    val roomAgents: RoomUnsealResource<List<RoomAgentDescriptor>> = RoomUnsealResource.success(emptyList()),
    val allAgents: RoomUnsealResource<List<AgentAccountDescriptor>> = RoomUnsealResource.success(emptyList()),
    val schedules: RoomUnsealResource<List<RoomScheduleDescriptor>> = RoomUnsealResource.success(emptyList()),
    val webhookTriggers: RoomUnsealResource<List<RoomWebhookTriggerDescriptor>> = RoomUnsealResource.success(emptyList()),
    val workingMemory: RoomUnsealResource<String> = RoomUnsealResource.success(""),
)

data class RoomUnsealResource<T>(
    val value: T,
    val error: Throwable? = null,
) {
    val isSuccess: Boolean
        get() = error == null

    companion object {
        fun <T> success(value: T): RoomUnsealResource<T> = RoomUnsealResource(value = value)
        fun <T> failure(defaultValue: T, error: Throwable): RoomUnsealResource<T> = RoomUnsealResource(value = defaultValue, error = error)
    }
}

data class RoomAgentDescriptor(
    val userId: String,
    val displayName: String?,
    val avatarUrl: String?,
    val userType: String?,
    val membership: String?,
)

data class AgentAccountDescriptor(
    val botName: String,
    val localpart: String?,
    val serverName: String?,
    val matrixUserId: String?,
    val displayName: String?,
    val avatarUrl: String?,
    val isDeviceAgent: Boolean,
    val boundDeviceId: String?,
)

data class RoomScheduleDescriptor(
    val id: String,
    val name: String,
    val cron: String,
    val action: String,
    val agentId: String,
    val roomId: String,
    val timezone: String?,
    val isEnabled: Boolean,
)

data class RoomAgentSkillDescriptor(
    val id: String?,
    val name: String,
    val description: String?,
    val runtimeVisible: Boolean,
)

data class RoomLegacyAgentSkillDescriptor(
    val id: String,
    val name: String,
    val description: String?,
)

data class RoomWebhookTriggerDescriptor(
    val id: String,
    val agentId: String,
    val name: String,
    val source: String?,
    val roomId: String,
    val status: String,
    val actionPrompt: String,
)

internal fun ChatbotRoomAgent.toRoomAgentDescriptor(): RoomAgentDescriptor {
    return RoomAgentDescriptor(
        userId = mxid?.takeIf { it.isNotBlank() } ?: agentId,
        displayName = displayName,
        avatarUrl = avatarUrl,
        userType = userType,
        membership = membership,
    )
}

internal fun ChatbotAgent.toAgentAccountDescriptor(): AgentAccountDescriptor {
    return AgentAccountDescriptor(
        botName = botName,
        localpart = localpart,
        serverName = serverName,
        matrixUserId = matrixUserId(),
        displayName = displayName,
        avatarUrl = avatarUrl,
        isDeviceAgent = metadataString("agent_kind") == "device",
        boundDeviceId = metadataString("bound_device_id"),
    )
}

internal fun ChatbotSchedule.toRoomScheduleDescriptor(): RoomScheduleDescriptor {
    return RoomScheduleDescriptor(
        id = scheduleId ?: name,
        name = name,
        cron = cron,
        action = action,
        agentId = agentId,
        roomId = roomId,
        timezone = timezone,
        isEnabled = when (status) {
            "enabled" -> true
            "disabled" -> false
            else -> enabled ?: false
        },
    )
}

internal fun ChatbotRoomAgentSkill.toRoomAgentSkillDescriptor(): RoomAgentSkillDescriptor {
    return RoomAgentSkillDescriptor(
        id = id,
        name = name,
        description = description,
        runtimeVisible = runtimeVisible,
    )
}

internal fun ChatbotUserSkill.toRoomLegacyAgentSkillDescriptor(): RoomLegacyAgentSkillDescriptor {
    return RoomLegacyAgentSkillDescriptor(
        id = id,
        name = name,
        description = description,
    )
}

internal fun ChatbotWebhookTrigger.toRoomWebhookTriggerDescriptor(): RoomWebhookTriggerDescriptor {
    return RoomWebhookTriggerDescriptor(
        id = triggerId,
        agentId = agentId,
        name = name,
        source = source,
        roomId = roomId,
        status = status.name,
        actionPrompt = actionPrompt,
    )
}

internal fun ChatbotAgent.matrixUserId(): String? {
    val local = localpart?.takeIf { it.isNotBlank() }
    val server = serverName?.takeIf { it.isNotBlank() }
    return if (local != null && server != null) {
        "@$local:$server"
    } else {
        null
    }
}

private fun ChatbotAgent.metadataString(key: String): String? {
    return (metadata?.get(key) as? JsonPrimitive)?.content
}
