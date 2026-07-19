/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.audience

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.call.api.AudienceRuntimePhase
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.time.Instant

interface AudiencePlaybackController {
    fun observe(
        sessionId: SessionId,
        broadcastId: String,
        audienceClientId: String,
    ): Flow<AudiencePlaybackState>
}

@ContributesBinding(AppScope::class)
class DefaultAudiencePlaybackController(
    private val httpClient: AudienceBroadcastHttpClient,
    private val systemClock: SystemClock,
) : AudiencePlaybackController {
    override fun observe(
        sessionId: SessionId,
        broadcastId: String,
        audienceClientId: String,
    ): Flow<AudiencePlaybackState> = flow {
        var audienceSession: AudienceSession? = null
        var activeManifest: AudienceManifest? = null
        var activeManifestUrl: String? = null
        var activeGrantExpiryMs: Long? = null
        var counts = AudiencePlaybackCounts()
        var transientFailureCount = 0
        var heartbeatRemainingMs = 0L
        var runtimePollAfterMs = DEFAULT_STATUS_POLL_MS
        suspend fun delayWithinGrant(requestedDelayMs: Long): Boolean {
            val expiryMs = activeGrantExpiryMs
            if (expiryMs == null) {
                delay(requestedDelayMs)
                return true
            }
            val remainingMs = expiryMs - systemClock.epochMillis()
            if (remainingMs <= 0) return false
            delay(minOf(requestedDelayMs, remainingMs))
            return systemClock.epochMillis() < expiryMs
        }
        fun live(manifest: AudienceManifest) = AudiencePlaybackState.Live(manifest, counts)
        fun reconnecting(manifest: AudienceManifest) = AudiencePlaybackState.Reconnecting(manifest, counts)
        emit(AudiencePlaybackState.Connecting)
        try {
            while (currentCoroutineContext().isActive) {
                if (activeManifest != null && activeGrantExpiryMs?.let { systemClock.epochMillis() >= it } == true) {
                    emit(AudiencePlaybackState.Ended)
                    return@flow
                }
                if (audienceSession == null) {
                    try {
                        val created = httpClient.createAudienceSession(sessionId, broadcastId, audienceClientId)
                        val expiresAtMs = created.expiresAt.toExpiryEpochMillis()
                        if (systemClock.epochMillis() >= expiresAtMs) {
                            emit(AudiencePlaybackState.Ended)
                            return@flow
                        }
                        val manifest = loadVerifiedManifest(created.manifestUrl, broadcastId, created.generation)
                        audienceSession = created
                        activeManifest = manifest
                        activeManifestUrl = created.manifestUrl
                        activeGrantExpiryMs = expiresAtMs
                        heartbeatRemainingMs = created.heartbeatIntervalMs
                        transientFailureCount = 0
                        emit(live(manifest))
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Throwable) {
                        val audienceFailure = failure.toAudienceFailure()
                        if (audienceFailure.endsPlayback) {
                            emit(AudiencePlaybackState.Ended)
                            return@flow
                        }
                        if (!audienceFailure.retryable) {
                            emit(AudiencePlaybackState.Failed(audienceFailure.code))
                            return@flow
                        }
                        transientFailureCount++
                        emit(activeManifest?.let(::reconnecting) ?: AudiencePlaybackState.Connecting)
                        if (!delayWithinGrant(audienceFailure.pollAfterMs ?: retryDelayMs(transientFailureCount))) {
                            emit(AudiencePlaybackState.Ended)
                            return@flow
                        }
                        continue
                    }
                }

                val currentSession = checkNotNull(audienceSession)
                val currentManifest = checkNotNull(activeManifest)
                val grantRemainingMs = activeGrantExpiryMs?.minus(systemClock.epochMillis()) ?: Long.MAX_VALUE
                if (grantRemainingMs <= 0) {
                    emit(AudiencePlaybackState.Ended)
                    return@flow
                }
                val waitMs = minOf(
                    runtimePollAfterMs.coerceAtMost(DEFAULT_STATUS_POLL_MS),
                    heartbeatRemainingMs.coerceAtLeast(0L),
                    grantRemainingMs,
                )
                if (waitMs > 0) delay(waitMs)
                if (activeGrantExpiryMs?.let { systemClock.epochMillis() >= it } == true) {
                    emit(AudiencePlaybackState.Ended)
                    return@flow
                }
                heartbeatRemainingMs -= waitMs
                if (heartbeatRemainingMs > 0) {
                    try {
                        val status = httpClient.getRuntimeStatus(sessionId, broadcastId)
                        transientFailureCount = 0
                        runtimePollAfterMs = status.pollAfterMs.coerceAtMost(DEFAULT_STATUS_POLL_MS)
                        counts = AudiencePlaybackCounts(status.participantCount, status.listenerCount)
                        when {
                            status.phase == AudienceRuntimePhase.Ended -> {
                                emit(AudiencePlaybackState.Ended)
                                return@flow
                            }
                            status.phase != AudienceRuntimePhase.Live || !status.playable -> {
                                emit(reconnecting(currentManifest))
                            }
                            status.generation != currentManifest.generation -> {
                                heartbeatRemainingMs = 0L
                            }
                            status.manifestRevision > currentManifest.revision && activeManifestUrl != null -> {
                                try {
                                    val manifest = loadVerifiedManifest(activeManifestUrl, broadcastId, status.generation)
                                    activeManifest = manifest
                                    emit(live(manifest))
                                } catch (failure: AudienceHttpException) {
                                    if (failure.statusCode != 401 && failure.statusCode != 403) throw failure
                                    heartbeatRemainingMs = 0L
                                    emit(reconnecting(currentManifest))
                                }
                            }
                            else -> emit(live(currentManifest))
                        }
                    } catch (failure: CancellationException) {
                        throw failure
                    } catch (failure: Throwable) {
                        val audienceFailure = failure.toAudienceFailure()
                        if (audienceFailure.endsPlayback) {
                            emit(AudiencePlaybackState.Ended)
                            return@flow
                        }
                        if (audienceFailure.code in TERMINAL_AUDIENCE_ERRORS || !audienceFailure.retryable) {
                            emit(AudiencePlaybackState.Failed(audienceFailure.code))
                            return@flow
                        }
                        transientFailureCount++
                        runtimePollAfterMs = (audienceFailure.pollAfterMs ?: retryDelayMs(transientFailureCount))
                            .coerceAtMost(DEFAULT_STATUS_POLL_MS)
                        emit(reconnecting(currentManifest))
                    }
                    continue
                }

                try {
                    val heartbeat = httpClient.heartbeatAudienceSession(
                        sessionId = sessionId,
                        broadcastId = broadcastId,
                        audienceSessionId = currentSession.sessionId,
                        generation = currentManifest.generation,
                    )
                    heartbeatRemainingMs = currentSession.heartbeatIntervalMs
                    transientFailureCount = 0
                    if (!heartbeat.active || heartbeat.phase == AudienceRuntimePhase.Ended) {
                        emit(AudiencePlaybackState.Ended)
                        return@flow
                    }
                    if (heartbeat.phase != AudienceRuntimePhase.Live) {
                        emit(reconnecting(currentManifest))
                        continue
                    }

                    val replacement = heartbeat.playback
                    val refreshedExpiry = (replacement?.expiresAt ?: heartbeat.expiresAt).toExpiryEpochMillis()
                    if (systemClock.epochMillis() >= refreshedExpiry) {
                        emit(AudiencePlaybackState.Ended)
                        return@flow
                    }
                    activeGrantExpiryMs = refreshedExpiry
                    val manifestUrl = replacement?.manifestUrl ?: activeManifestUrl
                    val expectedGeneration = replacement?.generation ?: heartbeat.generation
                    val manifestNeedsRefresh = replacement != null ||
                        heartbeat.manifestRevision > currentManifest.revision
                    if (manifestNeedsRefresh && manifestUrl != null && expectedGeneration > 0) {
                        val manifest = loadVerifiedManifest(manifestUrl, broadcastId, expectedGeneration)
                        activeManifest = manifest
                        activeManifestUrl = manifestUrl
                        audienceSession = currentSession.copy(
                            generation = expectedGeneration,
                            manifestUrl = manifestUrl,
                            expiresAt = replacement?.expiresAt ?: heartbeat.expiresAt,
                        )
                        emit(live(manifest))
                    } else {
                        audienceSession = currentSession.copy(expiresAt = heartbeat.expiresAt)
                        emit(live(currentManifest))
                    }
                } catch (failure: CancellationException) {
                    throw failure
                } catch (failure: Throwable) {
                    val audienceFailure = failure.toAudienceFailure()
                    when {
                        audienceFailure.endsPlayback -> {
                            emit(AudiencePlaybackState.Ended)
                            return@flow
                        }
                        audienceFailure.code == "audience_session_not_found" -> {
                            audienceSession = null
                            heartbeatRemainingMs = 0L
                            emit(reconnecting(currentManifest))
                        }
                        audienceFailure.code in TERMINAL_AUDIENCE_ERRORS || !audienceFailure.retryable -> {
                            emit(AudiencePlaybackState.Failed(audienceFailure.code))
                            return@flow
                        }
                        else -> {
                            transientFailureCount++
                            emit(reconnecting(currentManifest))
                            if (!delayWithinGrant(audienceFailure.pollAfterMs ?: retryDelayMs(transientFailureCount))) {
                                emit(AudiencePlaybackState.Ended)
                                return@flow
                            }
                        }
                    }
                }
            }
        } finally {
            val sessionToClose = audienceSession
            if (sessionToClose != null) {
                withContext(NonCancellable) {
                    runCatching {
                        httpClient.closeAudienceSession(sessionId, broadcastId, sessionToClose.sessionId)
                    }
                }
            }
        }
    }

    private suspend fun loadVerifiedManifest(
        manifestUrl: String,
        broadcastId: String,
        generation: Int,
    ): AudienceManifest {
        return httpClient.getAudienceManifest(manifestUrl).also { manifest ->
            require(manifest.broadcastId == broadcastId) { "Audience manifest broadcast does not match" }
            require(manifest.generation == generation) { "Audience manifest generation does not match" }
        }
    }
}

sealed interface AudiencePlaybackState {
    data object Connecting : AudiencePlaybackState
    data class Live(
        val manifest: AudienceManifest,
        val counts: AudiencePlaybackCounts = AudiencePlaybackCounts(),
    ) : AudiencePlaybackState
    data class Reconnecting(
        val manifest: AudienceManifest,
        val counts: AudiencePlaybackCounts = AudiencePlaybackCounts(),
    ) : AudiencePlaybackState
    data object Ended : AudiencePlaybackState
    data class Failed(val code: String) : AudiencePlaybackState
}

data class AudiencePlaybackCounts(
    val participantCount: Int = 0,
    val listenerCount: Int = 0,
)

private data class AudienceFailure(
    val code: String,
    val retryable: Boolean,
    val pollAfterMs: Long?,
)

private fun Throwable.toAudienceFailure(): AudienceFailure {
    if (this is IllegalArgumentException || this is IllegalStateException) {
        return AudienceFailure("invalid_audience_contract", retryable = false, pollAfterMs = null)
    }
    val httpFailure = this as? AudienceHttpException
        ?: return AudienceFailure("audience_transport_unavailable", retryable = true, pollAfterMs = null)
    val error = runCatching {
        Json.parseToJsonElement(httpFailure.responseBody).jsonObject["error"]?.jsonObject
    }.getOrNull()
    return AudienceFailure(
        code = error?.string("code") ?: if (httpFailure.statusCode in 500..599 || httpFailure.statusCode == 0) {
            "audience_transport_unavailable"
        } else {
            "audience_request_failed"
        },
        retryable = error?.get("retryable")?.jsonPrimitive?.booleanOrNull
            ?: (httpFailure.statusCode == 0 || httpFailure.statusCode == 408 || httpFailure.statusCode == 429 || httpFailure.statusCode in 500..599),
        pollAfterMs = error?.get("details")?.let { details ->
            (details as? JsonObject)?.get("poll_after_ms")?.jsonPrimitive?.longOrNull
        }?.coerceIn(MIN_RETRY_MS, MAX_RETRY_MS),
    )
}

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull

private fun retryDelayMs(failureCount: Int): Long =
    (MIN_RETRY_MS * (1L shl (failureCount - 1).coerceIn(0, 4))).coerceAtMost(MAX_RETRY_MS)

private val TERMINAL_AUDIENCE_ERRORS = setOf(
    "audience_access_denied",
    "broadcast_not_found",
)
private val AudienceFailure.endsPlayback: Boolean
    get() = code == "audience_session_expired"

private fun String.toExpiryEpochMillis(): Long = runCatching { Instant.parse(this).toEpochMilli() }
    .getOrElse { error("Audience playback has an invalid expiry") }

private const val MIN_RETRY_MS = 500L
private const val MAX_RETRY_MS = 5_000L
private const val DEFAULT_STATUS_POLL_MS = 1_000L
