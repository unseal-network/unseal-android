/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.skills

import androidx.compose.runtime.Immutable
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillDescriptor
import io.element.android.features.messages.impl.roomdata.RoomLegacyAgentSkillDescriptor
import io.element.android.features.messages.impl.roomdata.RoomMemberRender
import io.element.android.features.messages.impl.roomdata.RoomUnsealContext
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
        selectedSkills: List<ComposerSelectedAgentSkill> = emptyList(),
    ): ComposerAgentSkillState {
        context ?: return ComposerAgentSkillState.Empty
        val knownAgents = agentDescriptors(context)
        val directAgent = directAgentDescriptor(knownAgents, context.members, currentUserId).takeIf { isDirectRoom }
        val targets = listOfNotNull(directAgent)
        return ComposerAgentSkillState(
            knownAgentMxids = knownAgents.keys.toImmutableSet(),
            targets = targets.toImmutableList(),
            selectedSkills = selectedSkills.toImmutableList(),
            activeAgentMxid = targets.singleOrNull()?.mxid,
            isPresented = false,
        )
    }

    fun agentDescriptors(context: RoomUnsealContext): Map<String, ComposerAgentDescriptor> {
        val memberIds = context.members.map { it.userId.value }.toSet()
        val accountDescriptors = context.allAgents.mapNotNull { agent ->
            val mxid = agent.matrixUserId?.takeIf { it in memberIds } ?: return@mapNotNull null
            ComposerAgentDescriptor(
                agentId = mxid,
                mxid = mxid,
                label = agent.displayName ?: agent.botName,
            )
        }
        return (accountDescriptors + memberAgentDescriptors(context.members))
            .distinctBy { it.mxid }
            .sortedWith(compareBy<ComposerAgentDescriptor> { it.label }.thenBy { it.mxid })
            .associateBy { it.mxid }
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

    fun deduplicateSkillCandidates(candidates: List<ComposerAgentSkillCandidate>): List<ComposerAgentSkillCandidate> {
        val seen = mutableSetOf<String>()
        return candidates.filter { candidate ->
            seen.add("${candidate.agent.mxid}::${candidate.skillName}")
        }
    }

    fun roomSkillCandidates(
        target: ComposerAgentDescriptor,
        skills: List<RoomAgentSkillDescriptor>,
    ): List<ComposerAgentSkillCandidate> {
        return deduplicateSkillCandidates(
            skills.map { skill ->
                ComposerAgentSkillCandidate(
                    agent = target,
                    skillKey = skill.id ?: skill.name,
                    skillName = skill.name,
                    source = ComposerAgentSkillSource.Db,
                    relation = ComposerAgentSkillRelation.Runtime,
                    path = null,
                    directoryName = null,
                    runtimeVisible = skill.runtimeVisible,
                )
            }
        )
    }

    fun legacyInstalledSkillCandidates(
        target: ComposerAgentDescriptor,
        skills: List<RoomLegacyAgentSkillDescriptor>,
    ): List<ComposerAgentSkillCandidate> {
        return skills.map { skill ->
            ComposerAgentSkillCandidate(
                agent = target,
                skillKey = skill.name,
                skillName = skill.name,
                source = ComposerAgentSkillSource.S3,
                relation = ComposerAgentSkillRelation.Installed,
                path = null,
                directoryName = skill.name,
                runtimeVisible = true,
            )
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

}
