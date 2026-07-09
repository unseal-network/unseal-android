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
import io.element.android.libraries.matrix.api.room.RoomMembershipState
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
    val agentsInRoom: List<RoomAgentInRoomDescriptor>,
    val agentSkillTargets: List<RoomAgentSkillTargetDescriptor>,
    val hasAgentInRoom: Boolean,
    val deviceAgentInRoom: RoomDeviceAgent?,
    val schedules: List<RoomScheduleDescriptor>,
    val activeScheduleCount: Int,
    val webhookTriggers: List<RoomWebhookTriggerDescriptor>,
    val webhookSummary: RoomWebhookSummary,
    val workingMemory: String,
    val errors: List<Throwable>,
) {
    companion object {
        fun from(roomId: RoomId, members: List<RoomMember>, snapshot: RoomUnsealDataSnapshot): RoomUnsealContext {
            val enrichedMembers = RoomAgentMemberEnricher.enrich(members, snapshot.roomAgents.value)
            val joinedMembers = enrichedMembers.filter { it.membership == RoomMembershipState.JOIN }
            val explicitRoomAgents = mergeAgentsInRoom(
                roomAgents = joinedMembers.toRoomAgentsInRoom(),
                globalAgents = snapshot.roomAgents.value.toExplicitRoomAgentsInRoom(),
            )
            val agentsInRoom = mergeAgentsInRoom(
                roomAgents = explicitRoomAgents,
                globalAgents = snapshot.allAgents.value.toAgentsInRoom(
                    activeMembers = joinedMembers,
                    explicitRoomAgents = explicitRoomAgents,
                ),
            )
            val agentSkillTargets = mergeAgentSkillTargets(
                roomTargets = enrichedMembers.toMemberAgentSkillTargetCandidates(),
                globalTargets = agentsInRoom.map { it.toSkillTargetCandidate() },
            )
            val deviceAgent = agentsInRoom.firstNotNullOfOrNull { agent ->
                agent.boundDeviceId?.takeIf { agent.isDeviceAgent }?.let { boundDeviceId ->
                    RoomDeviceAgent(boundDeviceId = boundDeviceId, displayName = agent.label, matrixUserId = agent.mxid)
                }
            }
            return RoomUnsealContext(
                roomId = roomId,
                members = enrichedMembers,
                roomAgents = snapshot.roomAgents.value,
                allAgents = snapshot.allAgents.value,
                agentsInRoom = agentsInRoom,
                agentSkillTargets = agentSkillTargets,
                hasAgentInRoom = agentSkillTargets.isNotEmpty(),
                deviceAgentInRoom = deviceAgent,
                schedules = snapshot.schedules.value,
                activeScheduleCount = snapshot.schedules.value.count { it.isEnabled },
                webhookTriggers = snapshot.webhookTriggers.value,
                webhookSummary = snapshot.webhookTriggers.value.toWebhookSummary(),
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
    private val displayNameOverride: String? = null,
    private val avatarUrlOverride: String? = null,
) {
    val userId = member.userId
    val displayName = displayNameOverride ?: member.displayName
    val avatarUrl = avatarUrlOverride ?: member.avatarUrl
    val hasDisplayNameOverride = displayNameOverride != null
    val membership = member.membership
    val isAgent: Boolean = userType in AGENT_USER_TYPES
    val isActive: Boolean = member.membership.isActive()
}

data class RoomDeviceAgent(
    val boundDeviceId: String,
    val displayName: String?,
    val matrixUserId: String?,
)

data class RoomAgentInRoomDescriptor(
    val agentId: String,
    val mxid: String,
    val label: String,
    val avatarUrl: String?,
    val isDeviceAgent: Boolean,
    val boundDeviceId: String?,
)

data class RoomAgentSkillTargetDescriptor(
    val agentId: String,
    val mxid: String,
    val label: String,
)

private data class RoomAgentSkillTargetCandidate(
    val descriptor: RoomAgentSkillTargetDescriptor,
    val hasExplicitRoomLabel: Boolean,
)

data class RoomWebhookSummary(
    val totalCount: Int,
    val activeCount: Int,
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

internal fun RoomAgentDescriptor.isJoinedOrUnknownMembership(): Boolean {
    val membership = membership?.trim()
    return membership.isNullOrEmpty() || membership.equals("join", ignoreCase = true)
}

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

private fun List<AgentAccountDescriptor>.toAgentsInRoom(
    activeMembers: List<RoomMemberRender>,
    explicitRoomAgents: List<RoomAgentInRoomDescriptor>,
): List<RoomAgentInRoomDescriptor> {
    val activeMemberIds = activeMembers.map { it.userId.value }.toSet()
    val explicitRoomAgentIds = explicitRoomAgents.map { it.mxid }.toSet()
    val roomAgentIds = activeMemberIds + explicitRoomAgentIds
    return mapNotNull { agent ->
        val mxid = agent.matrixUserId?.takeIf { it in roomAgentIds }
            ?: agent.findActiveMemberMxid(activeMembers)
            ?: agent.findExplicitRoomAgentMxid(explicitRoomAgents)
            ?: return@mapNotNull null
        RoomAgentInRoomDescriptor(
            agentId = mxid,
            mxid = mxid,
            label = agent.displayName ?: agent.botName,
            avatarUrl = agent.avatarUrl,
            isDeviceAgent = agent.isDeviceAgent,
            boundDeviceId = agent.boundDeviceId,
        )
    }.sortedWith(compareBy<RoomAgentInRoomDescriptor> { it.label }.thenBy { it.mxid })
}

private fun List<RoomAgentDescriptor>.toExplicitRoomAgentsInRoom(): List<RoomAgentInRoomDescriptor> {
    return filter { it.isJoinedOrUnknownMembership() }
        .map { agent ->
            RoomAgentInRoomDescriptor(
                agentId = agent.userId,
                mxid = agent.userId,
                label = agent.displayName ?: agent.userId,
                avatarUrl = agent.avatarUrl,
                isDeviceAgent = false,
                boundDeviceId = null,
            )
        }
        .sortedWith(compareBy<RoomAgentInRoomDescriptor> { it.label }.thenBy { it.mxid })
}

private fun List<RoomMemberRender>.toRoomAgentsInRoom(): List<RoomAgentInRoomDescriptor> {
    return filter { it.membership == RoomMembershipState.JOIN && it.isAgent }
        .map { member ->
            RoomAgentInRoomDescriptor(
                agentId = member.userId.value,
                mxid = member.userId.value,
                label = member.displayName ?: member.userId.value,
                avatarUrl = member.avatarUrl,
                isDeviceAgent = false,
                boundDeviceId = null,
            )
        }
        .sortedWith(compareBy<RoomAgentInRoomDescriptor> { it.label }.thenBy { it.mxid })
}

private fun mergeAgentsInRoom(
    roomAgents: List<RoomAgentInRoomDescriptor>,
    globalAgents: List<RoomAgentInRoomDescriptor>,
): List<RoomAgentInRoomDescriptor> {
    val roomByMxid = roomAgents.associateBy { it.mxid }
    val globalByMxid = globalAgents.associateBy { it.mxid }
    return (roomAgents.map { it.mxid } + globalAgents.map { it.mxid })
        .distinct()
        .mapNotNull { mxid ->
            val roomAgent = roomByMxid[mxid]
            val globalAgent = globalByMxid[mxid]
            when {
                roomAgent == null -> globalAgent
                globalAgent == null -> roomAgent
                else -> globalAgent.copy(
                    agentId = roomAgent.agentId,
                    label = roomAgent.label.takeUnless { it == roomAgent.mxid } ?: globalAgent.label,
                    avatarUrl = roomAgent.avatarUrl ?: globalAgent.avatarUrl,
                )
            }
        }
        .sortedWith(compareBy<RoomAgentInRoomDescriptor> { it.label }.thenBy { it.mxid })
}

private fun AgentAccountDescriptor.findActiveMemberMxid(activeMembers: List<RoomMemberRender>): String? {
    val agentAliases = listOfNotNull(localpart, botName, displayName)
        .mapNotNull { it.normalizedAgentAlias() }
        .toSet()
        .takeIf { it.isNotEmpty() }
        ?: return null
    return activeMembers
        .filter { member ->
            val memberAliases = listOfNotNull(
                member.userId.value.substringAfter("@").substringBefore(":"),
                member.displayName,
            ).mapNotNull { it.normalizedAgentAlias() }
            memberAliases.any { it in agentAliases }
        }
        .singleOrNull()
        ?.userId
        ?.value
}

private fun AgentAccountDescriptor.findExplicitRoomAgentMxid(explicitRoomAgents: List<RoomAgentInRoomDescriptor>): String? {
    val agentAliases = listOfNotNull(localpart, botName, displayName)
        .mapNotNull { it.normalizedAgentAlias() }
        .toSet()
        .takeIf { it.isNotEmpty() }
        ?: return null
    return explicitRoomAgents
        .filter { roomAgent ->
            val roomAgentAliases = listOfNotNull(
                roomAgent.mxid.substringAfter("@").substringBefore(":"),
                roomAgent.label,
            ).mapNotNull { it.normalizedAgentAlias() }
            roomAgentAliases.any { it in agentAliases }
        }
        .singleOrNull()
        ?.mxid
}

private fun String.normalizedAgentAlias(): String? {
    return trim()
        .lowercase()
        .takeIf { it.isNotBlank() }
}

private fun mergeAgentSkillTargets(
    roomTargets: List<RoomAgentSkillTargetCandidate>,
    globalTargets: List<RoomAgentSkillTargetCandidate>,
): List<RoomAgentSkillTargetDescriptor> {
    val roomByMxid = roomTargets.associateBy { it.descriptor.mxid }
    val globalByMxid = globalTargets.associateBy { it.descriptor.mxid }
    return (roomTargets.map { it.descriptor.mxid } + globalTargets.map { it.descriptor.mxid })
        .distinct()
        .mapNotNull { mxid ->
            val roomTarget = roomByMxid[mxid]
            val globalTarget = globalByMxid[mxid]
            when {
                roomTarget == null -> globalTarget
                globalTarget == null -> roomTarget
                roomTarget.hasExplicitRoomLabel -> roomTarget
                else -> globalTarget
            }?.descriptor
        }
        .sortedWith(compareBy<RoomAgentSkillTargetDescriptor> { it.label }.thenBy { it.mxid })
}

private fun List<RoomMemberRender>.toMemberAgentSkillTargetCandidates(): List<RoomAgentSkillTargetCandidate> {
    return filter { it.membership == RoomMembershipState.JOIN && it.isAgent }
        .map { member ->
            RoomAgentSkillTargetCandidate(
                descriptor = RoomAgentSkillTargetDescriptor(
                    agentId = member.userId.value,
                    mxid = member.userId.value,
                    label = member.displayName ?: member.userId.value,
                ),
                hasExplicitRoomLabel = member.hasDisplayNameOverride,
            )
        }
}

private fun RoomAgentInRoomDescriptor.toSkillTargetCandidate(): RoomAgentSkillTargetCandidate {
    return RoomAgentSkillTargetCandidate(
        descriptor = RoomAgentSkillTargetDescriptor(
            agentId = agentId,
            mxid = mxid,
            label = label,
        ),
        hasExplicitRoomLabel = false,
    )
}

internal fun RoomWebhookTriggerDescriptor.isEnabled(): Boolean {
    return status.equals("enabled", ignoreCase = true) || status.equals("active", ignoreCase = true)
}

private fun List<RoomWebhookTriggerDescriptor>.toWebhookSummary(): RoomWebhookSummary {
    return RoomWebhookSummary(
        totalCount = size,
        activeCount = count { it.isEnabled() },
    )
}

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
