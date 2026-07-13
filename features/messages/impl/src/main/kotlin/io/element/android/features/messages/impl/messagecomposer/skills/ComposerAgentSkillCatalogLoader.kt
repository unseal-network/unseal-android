/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.skills

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.messages.impl.roomdata.RoomAgentSkillCatalogDescriptor
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
    val catalogs: List<ComposerRoomAgentSkillCatalog> = emptyList(),
    val refreshCatalogs: List<ComposerRoomAgentSkillCatalog> = emptyList(),
)

data class ComposerRoomAgentSkillCatalog(
    val agentId: String,
    val target: ComposerAgentDescriptor,
    val catalog: RoomAgentSkillCatalogDescriptor,
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

        val roomCatalogResult = loadRoomAgentSkillCatalogs(catalogTargets, currentUserId)
        roomCatalogResult.exceptionOrNull()?.let { error ->
            Timber.w(error, "Failed loading room agent skill catalog")
            return@withContext ComposerAgentSkillCatalogLoadResult(
                candidates = emptyList(),
                error = error.message,
            )
        }

        val catalogs = roomCatalogResult.getOrDefault(emptyList())
        return@withContext ComposerAgentSkillCatalogLoadResult(
            candidates = candidatesFromCatalogs(catalogs),
            error = null,
            catalogs = catalogs,
            refreshCatalogs = catalogs.takeIf { entries ->
                entries.any { it.catalog.status != COMPLETE_STATUS && it.catalog.cacheKey.isNotEmpty() }
            }.orEmpty(),
        )
    }

    suspend fun refreshWorkspace(
        catalogs: List<ComposerRoomAgentSkillCatalog>,
        currentUserId: String,
        force: Boolean = false,
    ): ComposerAgentSkillCatalogLoadResult = withContext(dispatchers.io) {
        val refreshedCatalogs = catalogs.map { entry ->
            if ((!force && entry.catalog.status == COMPLETE_STATUS) || entry.catalog.cacheKey.isEmpty()) {
                return@map entry
            }
            roomUnsealDataClient
                .refreshRoomAgentSkills(room.roomId, entry.agentId, entry.catalog.cacheKey, currentUserId)
                .fold(
                    onSuccess = { catalog -> entry.copy(catalog = catalog) },
                    onFailure = { error ->
                        Timber.w(error, "Failed refreshing room agent skill catalog for ${entry.agentId}")
                        entry
                    },
                )
        }
        return@withContext ComposerAgentSkillCatalogLoadResult(
            candidates = candidatesFromCatalogs(refreshedCatalogs),
            error = null,
            catalogs = refreshedCatalogs,
        )
    }

    private suspend fun loadRoomAgentSkillCatalogs(
        targets: List<ComposerAgentDescriptor>,
        currentUserId: String,
    ): Result<List<ComposerRoomAgentSkillCatalog>> {
        val targetByAgentId = ComposerAgentSkillReducer.targetByRelationAgentId(targets)
        return runCatching {
            ComposerAgentSkillReducer.skillCatalogAgentIds(targets)
                .mapNotNull { agentId ->
                    val target = targetByAgentId[agentId] ?: return@mapNotNull null
                    val catalog = roomUnsealDataClient
                        .listRoomAgentSkills(room.roomId, agentId, currentUserId)
                        .getOrThrow()
                    ComposerRoomAgentSkillCatalog(agentId = agentId, target = target, catalog = catalog)
                }
        }
    }

    private fun candidatesFromCatalogs(catalogs: List<ComposerRoomAgentSkillCatalog>): List<ComposerAgentSkillCandidate> {
        return catalogs
            .flatMap { entry ->
                ComposerAgentSkillReducer.roomSkillCandidates(
                    target = entry.target,
                    agentId = entry.agentId,
                    catalog = entry.catalog,
                )
            }
            .sortedWith(
                compareBy<ComposerAgentSkillCandidate> { it.agent.label }
                    .thenBy { sourceRank(it.source) }
                    .thenBy { it.skillName }
            )
            .let(ComposerAgentSkillReducer::deduplicateSkillCandidates)
    }

    private fun sourceRank(source: ComposerAgentSkillSource): Int {
        return when (source) {
            ComposerAgentSkillSource.Workspace -> 0
            ComposerAgentSkillSource.S3 -> 1
            ComposerAgentSkillSource.Db -> 2
            ComposerAgentSkillSource.Bundled -> 3
        }
    }

    private companion object {
        const val COMPLETE_STATUS = "complete"
    }
}
