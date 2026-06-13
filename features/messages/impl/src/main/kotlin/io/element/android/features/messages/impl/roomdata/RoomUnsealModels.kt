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
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembersState
import io.element.android.libraries.matrix.api.room.roomMembers
import kotlinx.serialization.json.JsonPrimitive

data class RoomUnsealDataSnapshot(
    val roomAgents: RoomUnsealResource<List<RoomAgentDescriptor>> = RoomUnsealResource.success(emptyList()),
    val allAgents: RoomUnsealResource<List<AgentAccountDescriptor>> = RoomUnsealResource.success(emptyList()),
    val schedules: RoomUnsealResource<List<RoomScheduleDescriptor>> = RoomUnsealResource.success(emptyList()),
    val webhookTriggers: RoomUnsealResource<List<RoomWebhookTriggerDescriptor>> = RoomUnsealResource.success(emptyList()),
    val workingMemory: RoomUnsealResource<String> = RoomUnsealResource.success(""),
)

data class RoomUnsealContext(
    val roomId: RoomId,
    val members: List<RoomMemberRender>,
    val roomAgents: List<RoomAgentDescriptor>,
    val allAgents: List<AgentAccountDescriptor>,
    val hasAgentInRoom: Boolean,
    val deviceAgentInRoom: RoomDeviceAgent?,
    val schedules: List<RoomScheduleDescriptor>,
    val activeScheduleCount: Int,
    val webhookTriggers: List<RoomWebhookTriggerDescriptor>,
    val workingMemory: String,
    val errors: List<Throwable>,
) {
    companion object {
        fun from(roomId: RoomId, members: List<RoomMember>, snapshot: RoomUnsealDataSnapshot): RoomUnsealContext {
            val enrichedMembers = RoomAgentMemberEnricher.enrich(members, snapshot.roomAgents.value)
            val memberIds = enrichedMembers.map { it.userId.value }.toSet()
            val agentsInRoom = snapshot.allAgents.value.filter { agent -> agent.matrixUserId in memberIds }
            val deviceAgent = agentsInRoom.firstNotNullOfOrNull { agent ->
                agent.boundDeviceId?.takeIf { agent.isDeviceAgent }?.let { boundDeviceId ->
                    RoomDeviceAgent(boundDeviceId = boundDeviceId, displayName = agent.displayName ?: agent.botName, matrixUserId = agent.matrixUserId)
                }
            }
            return RoomUnsealContext(
                roomId = roomId,
                members = enrichedMembers,
                roomAgents = snapshot.roomAgents.value,
                allAgents = snapshot.allAgents.value,
                hasAgentInRoom = agentsInRoom.isNotEmpty() || enrichedMembers.any { it.isAgent },
                deviceAgentInRoom = deviceAgent,
                schedules = snapshot.schedules.value,
                activeScheduleCount = snapshot.schedules.value.count { it.isEnabled },
                webhookTriggers = snapshot.webhookTriggers.value,
                workingMemory = snapshot.workingMemory.value,
                errors = listOfNotNull(
                    snapshot.roomAgents.error,
                    snapshot.allAgents.error,
                    snapshot.schedules.error,
                    snapshot.webhookTriggers.error,
                    snapshot.workingMemory.error,
                ),
            )
        }
    }
}

data class RoomMemberRender(
    val member: RoomMember,
    val userType: String?,
) {
    val userId = member.userId
    val displayName = member.displayName
    val avatarUrl = member.avatarUrl
    val membership = member.membership
    val isAgent: Boolean = userType in AGENT_USER_TYPES
    val isActive: Boolean = member.membership.isActive()
}

data class RoomDeviceAgent(
    val boundDeviceId: String,
    val displayName: String?,
    val matrixUserId: String?,
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

internal fun RoomMembersState.roomUnsealMemberSignature(): String? {
    return roomMembers()
        ?.sortedBy { it.userId.value }
        ?.joinToString(separator = "\n") { member ->
            listOf(
                member.userId.value,
                member.membership.name,
                member.displayName.orEmpty(),
                member.avatarUrl.orEmpty(),
            ).joinToString(separator = "|")
        }
}

private val AGENT_USER_TYPES = setOf("agent", "bot", "external_bot", "trusted_external_bot")

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
