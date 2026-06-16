/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.skills

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.messages.impl.roomdata.RoomUnsealContext
import io.element.android.features.messages.impl.roomdata.RoomUnsealDataClient
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.withContext
import timber.log.Timber

data class ComposerAgentSkillCatalogLoadResult(
    val candidates: List<ComposerAgentSkillCandidate>,
    val error: String?,
)

@SingleIn(RoomScope::class)
@Inject
class ComposerAgentSkillCatalogLoader(
    private val room: JoinedRoom,
    private val roomUnsealDataClient: RoomUnsealDataClient,
    private val dispatchers: CoroutineDispatchers,
) {
    suspend fun load(
        context: RoomUnsealContext,
        targets: List<ComposerAgentDescriptor>,
        currentUserId: String,
        isDirectRoom: Boolean,
    ): ComposerAgentSkillCatalogLoadResult = withContext(dispatchers.io) {
        val knownAgents = ComposerAgentSkillReducer.agentDescriptors(context)
        val catalogTargets = ComposerAgentSkillReducer.catalogAgentDescriptors(
            targets = targets,
            knownAgents = knownAgents,
            isDirectRoom = isDirectRoom,
        )
        if (catalogTargets.isEmpty()) {
            return@withContext ComposerAgentSkillCatalogLoadResult(candidates = emptyList(), error = null)
        }

        val roomCatalogResult = loadRoomAgentSkillCandidates(catalogTargets, currentUserId)
        roomCatalogResult.exceptionOrNull()?.let { error ->
            Timber.w(error, "Failed loading room agent skill catalog")
            val fallback = loadLegacyInstalledSkillCandidates(targets)
            return@withContext ComposerAgentSkillCatalogLoadResult(
                candidates = fallback,
                error = error.message.takeIf { fallback.isEmpty() },
            )
        }

        val roomCandidates = roomCatalogResult.getOrDefault(emptyList())
        if (ComposerAgentSkillReducer.hasRuntimeVisibleSkillCandidates(roomCandidates)) {
            return@withContext ComposerAgentSkillCatalogLoadResult(candidates = roomCandidates, error = null)
        }

        return@withContext ComposerAgentSkillCatalogLoadResult(
            candidates = loadLegacyInstalledSkillCandidates(targets),
            error = null,
        )
    }

    private suspend fun loadRoomAgentSkillCandidates(
        targets: List<ComposerAgentDescriptor>,
        currentUserId: String,
    ): Result<List<ComposerAgentSkillCandidate>> {
        val targetByAgentId = ComposerAgentSkillReducer.targetByRelationAgentId(targets)
        return runCatching {
            ComposerAgentSkillReducer.skillCatalogAgentIds(targets)
                .flatMap { agentId ->
                    val target = targetByAgentId[agentId] ?: return@flatMap emptyList()
                    val skills = roomUnsealDataClient
                        .listRoomAgentSkills(room.roomId, agentId, currentUserId)
                        .getOrThrow()
                    ComposerAgentSkillReducer.roomSkillCandidates(target, skills)
                }
                .sortedWith(compareBy<ComposerAgentSkillCandidate> { it.agent.label }.thenBy { it.skillName })
        }
    }

    private suspend fun loadLegacyInstalledSkillCandidates(targets: List<ComposerAgentDescriptor>): List<ComposerAgentSkillCandidate> {
        return targets.flatMap { target ->
            val loadedSkills = ComposerAgentSkillReducer.legacyAgentSkillLookupIds(target)
                .firstNotNullOfOrNull { lookupId ->
                    roomUnsealDataClient.listLegacyAgentSkills(lookupId)
                        .onFailure { error -> Timber.w(error, "Legacy skill lookup failed for $lookupId") }
                        .getOrNull()
                        ?.takeIf { it.isNotEmpty() }
                }
                .orEmpty()
            ComposerAgentSkillReducer.legacyInstalledSkillCandidates(target, loadedSkills)
        }.sortedWith(compareBy<ComposerAgentSkillCandidate> { it.agent.label }.thenBy { it.skillName })
    }
}
