/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.audience

import android.content.SharedPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.call.api.AudienceAccessMode
import io.element.android.features.call.api.AudienceBroadcastDiscovery
import io.element.android.features.call.api.AudienceBroadcastService
import io.element.android.features.call.api.AudienceHostControlState
import io.element.android.features.call.api.AudienceRelayDesired
import io.element.android.features.call.api.AudienceRuntimePhase
import io.element.android.features.call.api.AudienceRuntimeStatus
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudienceBroadcastService(
    private val httpClient: AudienceBroadcastHttpClient,
    private val matrixClientProvider: MatrixClientProvider,
    private val sharedPreferences: SharedPreferences,
) : AudienceBroadcastService {
    private val hostStates = ConcurrentHashMap<String, MutableStateFlow<AudienceHostControlState>>()
    private val hostLocks = ConcurrentHashMap<String, Mutex>()

    override suspend fun getRuntimeStatus(
        sessionId: SessionId,
        broadcastId: String,
    ): Result<AudienceRuntimeStatus> = cancellationSafeResult { httpClient.getRuntimeStatus(sessionId, broadcastId) }

    override suspend fun awaitRuntimeStatus(
        sessionId: SessionId,
        broadcastId: String,
    ): Result<AudienceRuntimeStatus> {
        var retryDelayMs = INITIAL_STATUS_RETRY_DELAY_MS
        while (currentCoroutineContext().isActive) {
            try {
                return Result.success(httpClient.getRuntimeStatus(sessionId, broadcastId))
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: AudienceHttpException) {
                if (!failure.isRetryableStatusFailure()) return Result.failure(failure)
                delay(retryDelayMs)
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_STATUS_RETRY_DELAY_MS)
            } catch (failure: Throwable) {
                return Result.failure(failure)
            }
        }
        throw CancellationException("Audience runtime resolution was cancelled")
    }

    override fun observeRoomDiscovery(
        sessionId: SessionId,
        roomId: RoomId,
    ): Flow<AudienceBroadcastDiscovery?> = flow {
        val matrixClient = matrixClientProvider.getOrRestore(sessionId).getOrThrow()
        // A room can be present in the timeline before the Rust SDK has materialised the
        // corresponding JoinedRoom object. The Matrix state endpoint remains authoritative in
        // that window, so a missing local room must not turn into a terminal flow failure.
        // In particular, callers intentionally swallow observation failures to keep the room
        // UI usable; throwing here would otherwise hide a live relay indefinitely.
        var room = matrixClient.getJoinedRoom(roomId)
        // The Matrix SDK may return a successful null before its custom-state cache has
        // hydrated. A non-null raw event is the only positive local observation: it is
        // either a discovery document or the Relay's `{}` tombstone. Until then, keep
        // retrying the local state read without ever starting Audience API polling.
        var hasObservedStateEvent = false
        while (currentCoroutineContext().isActive) {
            // Re-acquire after a cold start so the remote state fallback is temporary rather
            // than turning a local cache miss into a permanent one-request-per-second path.
            room = room ?: matrixClient.getJoinedRoom(roomId)
            // Capture the version before reading state. This makes an update that races with
            // the read observable on the next iteration instead of losing the discovery event.
            val observedSyncVersion = room?.syncUpdateFlow?.value
            var retryCacheRead = room == null
            if (room == null && !hasObservedStateEvent) {
                emit(null)
            }
            room?.getStateEventJson(DISCOVERY_EVENT_TYPE, DISCOVERY_STATE_KEY)
                ?.onSuccess { raw ->
                    if (raw != null) {
                        // A concrete JSON state event is authoritative, including the agent's
                        // `{}` tombstone. Cache misses, by contrast, arrive as failures and
                        // must not permanently hide an already-published Relay discovery event.
                        hasObservedStateEvent = true
                        emit(httpClient.parseDiscovery(raw))
                    } else if (!hasObservedStateEvent) {
                        // A successful null is indistinguishable from a cold custom-state cache.
                        // Publish the no-broadcast UI state but retry this Matrix-only read; a
                        // later positive event is still required before Audience API access.
                        emit(null)
                        retryCacheRead = true
                    }
                }
                ?.onFailure {
                    // Do not call the Audience API without discovery. On cold room entry the
                    // Matrix SDK may not have hydrated custom state yet, so retry just the local
                    // state read; after a successful snapshot, retain it through transient misses.
                    if (!hasObservedStateEvent) {
                        emit(null)
                        retryCacheRead = true
                    }
                }

            if (retryCacheRead) {
                // A subscribed room can still omit custom state from its local sliding-sync
                // cache. Fetch the exact same Matrix state event with the current user's
                // refreshed credential. This is not an Audience API call and does not relax
                // the discovery gate: only a valid document below can start runtime polling.
                val remoteState = runCatching {
                    httpClient.getMatrixDiscoveryState(sessionId, roomId)
                }.getOrNull()
                if (remoteState != null) {
                    hasObservedStateEvent = true
                    emit(httpClient.parseDiscovery(remoteState))
                } else {
                    delay(DISCOVERY_CACHE_RETRY_MS)
                }
            } else if (room != null) {
                room.syncUpdateFlow.first { it != observedSyncVersion }
            }
        }
    }.distinctUntilChanged().transformLatest { discovery ->
        if (discovery == null) {
            updateHostFromAbsentDiscovery(sessionId, roomId)
            emit(null)
            return@transformLatest
        }
        val activeDiscovery = requireNotNull(discovery)
        updateHostFromDiscovery(sessionId, roomId, activeDiscovery)
        var retained = activeDiscovery
        while (currentCoroutineContext().isActive) {
            val runtime = getRuntimeStatus(sessionId, activeDiscovery.broadcastId).getOrNull()
            when {
                runtime != null &&
                    (runtime.roomId != roomId || runtime.meetingInstanceId != activeDiscovery.meetingInstanceId) -> {
                    emit(null)
                    return@transformLatest
                }
                runtime?.phase == AudienceRuntimePhase.Ended -> {
                    updateHostFromRuntime(sessionId, roomId, runtime, activeDiscovery.accessMode)
                    emit(null)
                    return@transformLatest
                }
                runtime != null -> {
                    retained = activeDiscovery.copy(
                        phase = runtime.phase,
                        listenerCount = runtime.listenerCount,
                    )
                    updateHostFromRuntime(sessionId, roomId, runtime, activeDiscovery.accessMode)
                    emit(retained)
                    delay(runtime.pollAfterMs)
                }
                else -> {
                    retained = retained.copy(phase = AudienceRuntimePhase.Recovering)
                    emit(retained)
                    delay(DISCOVERY_RETRY_MS)
                }
            }
        }
    }.distinctUntilChanged()

    override fun observeHostControl(sessionId: SessionId, roomId: RoomId): StateFlow<AudienceHostControlState> =
        stateFor(sessionId, roomId)

    override suspend fun enableRelay(
        sessionId: SessionId,
        roomId: RoomId,
        accessMode: AudienceAccessMode,
    ): Result<AudienceHostControlState> = controlLock(sessionId, roomId).withLock {
        val state = stateFor(sessionId, roomId)
        val previous = state.value
        val meetingInstanceId = meetingFence(sessionId, roomId)
        state.value = state.value.copy(
            meetingInstanceId = meetingInstanceId,
            accessMode = accessMode,
            desired = AudienceRelayDesired.Joined,
            phase = AudienceRuntimePhase.Joining,
            isUpdating = true,
            errorMessage = null,
        )
        convergeControl(state, previous) {
            val initial = requireNotNull(httpClient.setRelayDesired(
                sessionId = sessionId,
                roomId = roomId,
                meetingInstanceId = meetingInstanceId,
                desired = AudienceRelayDesired.Joined,
                accessMode = accessMode,
            )) { "Relay join returned no runtime" }
            requireRuntimeMatches(initial, roomId, meetingInstanceId)
            updateHostFromRuntime(sessionId, roomId, initial, accessMode)
            state.value
        }
    }

    override suspend fun disableRelay(sessionId: SessionId, roomId: RoomId): Result<AudienceHostControlState> =
        controlLock(sessionId, roomId).withLock {
            val state = stateFor(sessionId, roomId)
            val previous = state.value
            val meetingInstanceId = state.value.meetingInstanceId ?: storedMeetingFence(sessionId, roomId)
                ?: return@withLock Result.success(state.value)
            state.value = state.value.copy(
                meetingInstanceId = meetingInstanceId,
                desired = AudienceRelayDesired.Left,
                phase = AudienceRuntimePhase.Leaving,
                isUpdating = true,
                errorMessage = null,
            )
            convergeControl(state, previous) {
                val initial = httpClient.setRelayDesired(
                    sessionId = sessionId,
                    roomId = roomId,
                    meetingInstanceId = meetingInstanceId,
                    desired = AudienceRelayDesired.Left,
                    accessMode = null,
                )
                if (initial == null) {
                    return@convergeControl state.value.copy(
                        desired = AudienceRelayDesired.Left,
                        phase = AudienceRuntimePhase.Ended,
                        broadcastArmed = false,
                        isUpdating = false,
                    )
                }
                requireRuntimeMatches(initial, roomId, meetingInstanceId)
                updateHostFromRuntime(sessionId, roomId, initial, state.value.accessMode)
                state.value.copy(isUpdating = false, desired = AudienceRelayDesired.Left)
            }
        }

    override fun clearMeetingFence(sessionId: SessionId, roomId: RoomId, force: Boolean) {
        val key = key(sessionId, roomId)
        val state = stateFor(sessionId, roomId)
        if (!force && (state.value.isUpdating || state.value.isEnabled)) return
        sharedPreferences.edit().remove(preferenceKey(key)).apply()
        state.value = AudienceHostControlState()
    }

    private suspend fun convergeControl(
        state: MutableStateFlow<AudienceHostControlState>,
        previous: AudienceHostControlState,
        block: suspend () -> AudienceHostControlState,
    ): Result<AudienceHostControlState> = try {
        val result = block().copy(isUpdating = false, errorMessage = null)
        state.value = result
        Result.success(result)
    } catch (failure: CancellationException) {
        state.value = previous.copy(isUpdating = false)
        throw failure
    } catch (failure: Throwable) {
        val message = (failure as? AudienceHttpException)?.userMessage() ?: failure.message ?: "Unable to update listener access"
        state.value = previous.copy(isUpdating = false, errorMessage = message)
        Result.failure(failure)
    }

    private fun updateHostFromDiscovery(
        sessionId: SessionId,
        roomId: RoomId,
        discovery: AudienceBroadcastDiscovery,
    ) {
        persistMeetingFence(sessionId, roomId, discovery.meetingInstanceId)
        val state = stateFor(sessionId, roomId)
        state.value = state.value.copy(
            meetingInstanceId = discovery.meetingInstanceId,
            broadcastId = discovery.broadcastId,
            accessMode = discovery.accessMode,
            phase = discovery.phase,
            desired = AudienceRelayDesired.Joined,
            errorMessage = null,
        )
    }

    private fun updateHostFromAbsentDiscovery(sessionId: SessionId, roomId: RoomId) {
        val state = stateFor(sessionId, roomId)
        if (state.value.isUpdating || state.value.broadcastId == null) return
        state.value = state.value.copy(
            broadcastId = null,
            phase = AudienceRuntimePhase.Ended,
            desired = AudienceRelayDesired.Left,
            broadcastArmed = false,
            errorMessage = null,
        )
    }

    private fun updateHostFromRuntime(
        sessionId: SessionId,
        roomId: RoomId,
        runtime: AudienceRuntimeStatus,
        accessMode: AudienceAccessMode?,
        isUpdating: Boolean = false,
    ) {
        persistMeetingFence(sessionId, roomId, runtime.meetingInstanceId)
        val state = stateFor(sessionId, roomId)
        state.value = state.value.copy(
            meetingInstanceId = runtime.meetingInstanceId,
            broadcastId = runtime.broadcastId,
            accessMode = accessMode ?: state.value.accessMode,
            phase = runtime.phase,
            desired = runtime.desired,
            broadcastArmed = runtime.broadcastArmed,
            isUpdating = isUpdating,
            errorMessage = null,
        )
    }

    private fun stateFor(sessionId: SessionId, roomId: RoomId): MutableStateFlow<AudienceHostControlState> {
        val key = key(sessionId, roomId)
        return hostStates.getOrPut(key) {
            MutableStateFlow(AudienceHostControlState(meetingInstanceId = storedMeetingFence(sessionId, roomId)))
        }
    }

    private fun controlLock(sessionId: SessionId, roomId: RoomId): Mutex = hostLocks.getOrPut(key(sessionId, roomId)) { Mutex() }

    private fun meetingFence(sessionId: SessionId, roomId: RoomId): String =
        storedMeetingFence(sessionId, roomId) ?: UUID.randomUUID().toString().also {
            persistMeetingFence(sessionId, roomId, it)
        }

    private fun storedMeetingFence(sessionId: SessionId, roomId: RoomId): String? =
        sharedPreferences.getString(preferenceKey(key(sessionId, roomId)), null)

    private fun persistMeetingFence(sessionId: SessionId, roomId: RoomId, value: String) {
        sharedPreferences.edit().putString(preferenceKey(key(sessionId, roomId)), value).apply()
    }

    private fun key(sessionId: SessionId, roomId: RoomId): String = "${sessionId.value}|${roomId.value}"
    private fun preferenceKey(key: String): String = "meeting_broadcast_fence|$key"

    private fun requireRuntimeMatches(runtime: AudienceRuntimeStatus, roomId: RoomId, meetingInstanceId: String?) {
        require(runtime.roomId == roomId) { "Broadcast relay room does not match" }
        require(runtime.meetingInstanceId == meetingInstanceId) { "Broadcast relay meeting does not match" }
    }
}

private suspend inline fun <T> cancellationSafeResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (failure: CancellationException) {
    throw failure
} catch (failure: Throwable) {
    Result.failure(failure)
}

private fun AudienceHttpException.userMessage(): String? = runCatching {
    Regex("\"message\"\\s*:\\s*\"([^\"]+)\"").find(responseBody)?.groupValues?.get(1)
}.getOrNull()

private fun AudienceHttpException.isRetryableStatusFailure(): Boolean =
    statusCode == 0 || statusCode == 429 || statusCode >= 500

private const val INITIAL_STATUS_RETRY_DELAY_MS = 500L
private const val MAX_STATUS_RETRY_DELAY_MS = 4_000L

private const val DISCOVERY_EVENT_TYPE = "org.unseal.meeting.broadcast"
private const val DISCOVERY_STATE_KEY = "m.call"
private const val DISCOVERY_CACHE_RETRY_MS = 1_000L
private const val DISCOVERY_RETRY_MS = 5_000L
