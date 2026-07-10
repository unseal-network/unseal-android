/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.skills

import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillCatalogDescriptor
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillRelationDescriptor
import io.element.android.features.messages.impl.roomdata.RoomMemberRender
import io.element.android.features.messages.impl.roomdata.RoomUnsealContext
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillRelationKind
import io.element.android.libraries.chatbot.api.model.skills.ChatbotRoomAgentSkillSource
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet

@Immutable
data class ComposerAgentSkillState(
    val knownAgentMxids: ImmutableSet<String> = persistentSetOf(),
    val targets: ImmutableList<ComposerAgentDescriptor> = persistentListOf(),
    val candidates: ImmutableList<ComposerAgentSkillCandidate> = persistentListOf(),
    val selectedSkills: ImmutableList<ComposerSelectedAgentSkill> = persistentListOf(),
    val activeAgentMxid: String? = null,
    val isPresented: Boolean = false,
    val isCatalogLoading: Boolean = false,
    val isWorkspaceLoading: Boolean = false,
    val error: String? = null,
) {
    companion object {
        val Empty = ComposerAgentSkillState()
    }
}

@Immutable
data class ComposerAgentDescriptor(
    val agentId: String,
    val mxid: String,
    val label: String,
) {
    val id: String = mxid
}

@Immutable
data class ComposerAgentSkillCandidate(
    val agent: ComposerAgentDescriptor,
    val skillKey: String,
    val skillName: String,
    val source: ComposerAgentSkillSource,
    val relation: ComposerAgentSkillRelation,
    val path: String?,
    val directoryName: String?,
    val runtimeVisible: Boolean,
) {
    val id: String = "${agent.mxid}::$skillName::${source.value}"
}

@Immutable
data class ComposerSelectedAgentSkill(
    val agentId: String,
    val agentMxid: String,
    val agentLabel: String,
    val skillName: String,
    val path: String?,
    val directoryName: String?,
) {
    val id: String = "$agentMxid::$skillName"
}

enum class ComposerAgentSkillSource(val value: String) {
    Db("db"),
    S3("s3"),
    Workspace("workspace"),
    Bundled("bundled"),
}

enum class ComposerAgentSkillRelation(val value: String) {
    Installed("installed"),
    Available("available"),
    Runtime("runtime"),
    Bundled("bundled"),
}

object ComposerAgentSkillReducer {
    fun stateFromContext(
        context: RoomUnsealContext?,
        currentUserId: String,
        isDirectRoom: Boolean,
        mentionedUserIds: Set<String> = emptySet(),
        selectedSkills: List<ComposerSelectedAgentSkill> = emptyList(),
    ): ComposerAgentSkillState {
        context ?: return ComposerAgentSkillState.Empty
        val knownAgents = agentDescriptors(context)
        val directAgent = directAgentDescriptor(knownAgents, context.members, currentUserId).takeIf { isDirectRoom }
        val mentionedAgents = mentionedAgentDescriptors(
            mentionedUserIds = mentionedUserIds,
            knownAgents = knownAgents,
            members = context.members,
        )
        val targets = (listOfNotNull(directAgent) + mentionedAgents).distinctBy { it.mxid }
        return ComposerAgentSkillState(
            knownAgentMxids = knownAgents.keys.toImmutableSet(),
            targets = targets.toImmutableList(),
            selectedSkills = selectedSkills.toImmutableList(),
            activeAgentMxid = targets.singleOrNull()?.mxid,
            isPresented = false,
        )
    }

    fun agentDescriptors(context: RoomUnsealContext): Map<String, ComposerAgentDescriptor> {
        val contextTargets = context.agentSkillTargets.map { target ->
            ComposerAgentDescriptor(
                agentId = target.agentId,
                mxid = target.mxid,
                label = target.label,
            )
        }
        return (contextTargets + memberAgentDescriptors(context.members))
            .distinctBy { it.mxid }
            .sortedWith(compareBy<ComposerAgentDescriptor> { it.label }.thenBy { it.mxid })
            .associateBy { it.mxid }
    }

    fun agentDescriptorForUser(
        context: RoomUnsealContext,
        userId: String,
    ): ComposerAgentDescriptor? {
        return agentDescriptors(context)[userId]
            ?: context.members.firstOrNull { it.userId.value == userId && it.isActive && it.isAgent }?.toAgentDescriptor()
    }

    fun mentionedAgentDescriptors(
        mentionedUserIds: Set<String>,
        knownAgents: Map<String, ComposerAgentDescriptor>,
        members: List<RoomMemberRender>,
    ): List<ComposerAgentDescriptor> {
        val memberByUserId = members.associateBy { it.userId.value }
        return mentionedUserIds.mapNotNull { userId ->
            knownAgents[userId] ?: if (knownAgents.isEmpty()) {
                memberByUserId[userId]?.takeIf { it.isActive && it.isAgent }?.toAgentDescriptor()
            } else {
                null
            }
        }.sortedWith(compareBy<ComposerAgentDescriptor> { it.label }.thenBy { it.mxid })
    }

    fun directAgentDescriptor(
        knownAgents: Map<String, ComposerAgentDescriptor>,
        members: List<RoomMemberRender>,
        currentUserId: String,
    ): ComposerAgentDescriptor? {
        return knownAgents.values.sortedWith(compareBy<ComposerAgentDescriptor> { it.label }.thenBy { it.mxid }).firstOrNull()
            ?: members.firstOrNull { it.userId.value != currentUserId && it.isActive && it.isAgent }?.toAgentDescriptor()
    }

    fun hasUnresolvedTargets(
        targets: List<ComposerAgentDescriptor>,
        selectedSkills: List<ComposerSelectedAgentSkill>,
    ): Boolean {
        if (targets.isEmpty()) return false
        val selectedAgentMxids = selectedSkills.map { it.agentMxid }.toSet()
        return targets.any { it.mxid !in selectedAgentMxids }
    }

    fun visibleSkillCandidates(
        candidates: List<ComposerAgentSkillCandidate>,
        selectedSkills: List<ComposerSelectedAgentSkill>,
        activeAgentMxid: String?,
    ): List<ComposerAgentSkillCandidate> {
        val selectedIds = selectedSkills.map { it.id }.toSet()
        return candidates.filter { candidate ->
            "${candidate.agent.mxid}::${candidate.skillName}" !in selectedIds &&
                (activeAgentMxid == null || candidate.agent.mxid == activeAgentMxid)
        }
    }

    fun catalogAgentDescriptors(
        targets: List<ComposerAgentDescriptor>,
        knownAgents: Map<String, ComposerAgentDescriptor>,
        isDirectRoom: Boolean,
    ): List<ComposerAgentDescriptor> {
        if (targets.isEmpty()) return emptyList()
        val descriptorsByMxid = linkedMapOf<String, ComposerAgentDescriptor>()
        targets.forEach { target ->
            val knownAgent = knownAgents[target.mxid]
            descriptorsByMxid[target.mxid] = if (knownAgent == null) {
                target
            } else {
                target.copy(
                    agentId = target.agentId.ifBlank { knownAgent.agentId },
                    label = knownAgent.label,
                )
            }
        }
        if (isDirectRoom && descriptorsByMxid.isEmpty()) {
            return emptyList()
        }
        return descriptorsByMxid.values.sortedWith(compareBy<ComposerAgentDescriptor> { it.label }.thenBy { it.mxid })
    }

    fun skillCatalogAgentIds(targets: List<ComposerAgentDescriptor>): List<String> {
        return targets.map { it.mxid }.distinct().sorted()
    }

    fun targetByRelationAgentId(targets: List<ComposerAgentDescriptor>): Map<String, ComposerAgentDescriptor> {
        return buildMap {
            targets.forEach { target ->
                put(target.agentId, target)
                put(target.mxid, target)
                agentLocalpart(target.mxid)?.let { localpart ->
                    put(localpart, target)
                }
            }
        }
    }

    fun deduplicateSkillCandidates(candidates: List<ComposerAgentSkillCandidate>): List<ComposerAgentSkillCandidate> {
        val seen = mutableSetOf<String>()
        return candidates.filter { candidate ->
            seen.add("${candidate.agent.mxid}::${candidate.skillName}")
        }
    }

    fun roomSkillCandidates(
        target: ComposerAgentDescriptor,
        agentId: String,
        catalog: RoomAgentSkillCatalogDescriptor,
    ): List<ComposerAgentSkillCandidate> {
        val relationMap = relationMapForTarget(agentId, target, catalog)
        return deduplicateSkillCandidates(
            relationMap.map { (skillKey, relation) ->
                val skill = catalog.skills[skillKey]
                ComposerAgentSkillCandidate(
                    agent = target,
                    skillKey = skillKey,
                    skillName = skill?.name ?: skillKey,
                    source = relation.source.toComposerSource(),
                    relation = relation.relation.toComposerRelation(),
                    path = relation.path,
                    directoryName = relation.directoryName,
                    runtimeVisible = relation.runtimeVisible,
                )
            }
        )
    }

    private fun relationMapForTarget(
        agentId: String,
        target: ComposerAgentDescriptor,
        catalog: RoomAgentSkillCatalogDescriptor,
    ): Map<String, RoomAgentSkillRelationDescriptor> {
        val relationAgentIds = buildList {
            add(agentId)
            add(target.agentId)
            add(target.mxid)
            agentLocalpart(target.mxid)?.let(::add)
        }
        return relationAgentIds
            .asSequence()
            .filter { it.isNotEmpty() }
            .mapNotNull { catalog.relations[it] }
            .firstOrNull()
            .orEmpty()
    }

    private fun ChatbotRoomAgentSkillSource.toComposerSource(): ComposerAgentSkillSource {
        return when (this) {
            ChatbotRoomAgentSkillSource.Workspace -> ComposerAgentSkillSource.Workspace
            ChatbotRoomAgentSkillSource.S3 -> ComposerAgentSkillSource.S3
            ChatbotRoomAgentSkillSource.Db -> ComposerAgentSkillSource.Db
            ChatbotRoomAgentSkillSource.Bundled -> ComposerAgentSkillSource.Bundled
        }
    }

    private fun ChatbotRoomAgentSkillRelationKind.toComposerRelation(): ComposerAgentSkillRelation {
        return when (this) {
            ChatbotRoomAgentSkillRelationKind.Installed -> ComposerAgentSkillRelation.Installed
            ChatbotRoomAgentSkillRelationKind.Available -> ComposerAgentSkillRelation.Available
            ChatbotRoomAgentSkillRelationKind.Runtime -> ComposerAgentSkillRelation.Runtime
            ChatbotRoomAgentSkillRelationKind.Bundled -> ComposerAgentSkillRelation.Bundled
        }
    }

    private fun memberAgentDescriptors(members: List<RoomMemberRender>): List<ComposerAgentDescriptor> {
        return members.mapNotNull { member ->
            if (!member.isActive || !member.isAgent) return@mapNotNull null
            member.toAgentDescriptor()
        }
    }

    private fun RoomMemberRender.toAgentDescriptor(): ComposerAgentDescriptor {
        return ComposerAgentDescriptor(
            agentId = userId.value,
            mxid = userId.value,
            label = displayName ?: userId.value.removePrefix("@"),
        )
    }

    private fun agentLocalpart(mxid: String): String? {
        if (!mxid.startsWith("@")) return null
        val colonIndex = mxid.indexOf(':')
        if (colonIndex <= 1) return null
        return mxid.substring(startIndex = 1, endIndex = colonIndex)
    }
}
