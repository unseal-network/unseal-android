/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.audience

internal data class AudiencePlaybackSource(
    val presentationId: String,
    val videoUrl: String?,
    val audioUrl: String?,
) {
    val mediaUrls: List<String>
        get() = listOfNotNull(videoUrl, audioUrl).distinct()
}

internal data class AudiencePlaybackTarget(
    val generation: Int,
    val sources: List<AudiencePlaybackSource>,
)

internal fun AudienceManifest.toPlaybackTarget(): AudiencePlaybackTarget {
    return AudiencePlaybackTarget(
        generation = generation,
        sources = presentations
            .map { presentation ->
                AudiencePlaybackSource(
                    presentationId = presentation.presentationId,
                    videoUrl = presentation.video?.playlistUrl,
                    audioUrl = presentation.audio?.playlistUrl,
                )
            }
            .sortedBy(AudiencePlaybackSource::presentationId),
    )
}

internal data class AudiencePlaybackCommit<T : Any>(
    val activePlayers: Map<String, T>,
    val previousPlayers: List<T>,
)

internal class AudiencePlaybackGenerationBarrier<T : Any> {
    private data class Generation<T : Any>(
        val target: AudiencePlaybackTarget,
        val players: MutableMap<String, T>,
        val readyPresentationIds: MutableSet<String> = mutableSetOf(),
    )

    private var active: Generation<T>? = null
    private var pending: Generation<T>? = null

    fun needsCandidate(target: AudiencePlaybackTarget): Boolean {
        val current = active
        return current?.target != target ||
            current.players.keys != target.sources.mapTo(mutableSetOf(), AudiencePlaybackSource::presentationId)
    }

    fun stage(target: AudiencePlaybackTarget, players: Map<String, T>): List<T> {
        val expectedIds = target.sources.mapTo(mutableSetOf(), AudiencePlaybackSource::presentationId)
        require(players.keys == expectedIds) { "Audience generation candidate does not match its playback target" }
        val discarded = pending?.players?.values.orEmpty().toList()
        pending = Generation(target, players.toMutableMap())
        return discarded
    }

    fun markReady(
        target: AudiencePlaybackTarget,
        presentationId: String,
        player: T,
    ): AudiencePlaybackCommit<T>? {
        val candidate = pending ?: return null
        if (candidate.target != target || candidate.players[presentationId] !== player) return null
        candidate.readyPresentationIds += presentationId
        if (candidate.readyPresentationIds.size != candidate.players.size) return null

        val previousPlayers = active?.players?.values.orEmpty().toList()
        active = candidate
        pending = null
        return AudiencePlaybackCommit(
            activePlayers = candidate.players.toMap(),
            previousPlayers = previousPlayers,
        )
    }

    fun failPending(
        target: AudiencePlaybackTarget,
        presentationId: String,
        player: T,
    ): List<T>? {
        val candidate = pending ?: return null
        if (candidate.target != target || candidate.players[presentationId] !== player) return null
        pending = null
        return candidate.players.values.toList()
    }

    fun removeFailedActive(
        target: AudiencePlaybackTarget,
        presentationId: String,
        player: T,
    ): Map<String, T>? {
        val current = active ?: return null
        if (current.target != target || current.players[presentationId] !== player) return null
        current.players.remove(presentationId)
        return current.players.toMap()
    }

    fun allPlayers(): List<T> {
        return active?.players?.values.orEmpty() + pending?.players?.values.orEmpty()
    }

    fun close(): List<T> {
        val players = allPlayers()
        active = null
        pending = null
        return players
    }
}
