/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.impl.live

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.features.location.api.Location
import io.element.android.features.location.api.live.ActiveLiveLocationShareManager
import io.element.android.features.location.impl.live.service.LiveLocationReceiver
import io.element.android.features.location.impl.live.service.LiveLocationSharingCoordinator
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.location.BeaconId
import io.element.android.libraries.matrix.api.room.location.LiveLocationException
import io.element.android.libraries.sessionstorage.api.observer.SessionListener
import io.element.android.libraries.sessionstorage.api.observer.SessionObserver
import io.element.android.services.toolbox.api.systemclock.SystemClock
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import timber.log.Timber
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

private val LIVE_LOCATION_SEND_RETRY_DELAYS = listOf(2.seconds, 4.seconds, 8.seconds)
private val LIVE_LOCATION_SHARE_SYNC_TIMEOUT = 10.seconds
private const val MATRIX_BEACON_INFO_EVENT_TYPE = "org.matrix.msc3672.beacon_info"
private const val MATRIX_BEACON_EVENT_TYPE = "org.matrix.msc3672.beacon"
private val MATRIX_JSON = "application/json; charset=utf-8".toMediaType()

private data class LiveLocationStartResult(
    val beaconInfoEventId: EventId,
    val shouldWaitForSdkSync: Boolean,
)

@OptIn(ExperimentalAtomicApi::class)
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ActiveLiveLocationShareManager>())
class DefaultActiveLiveLocationShareManager(
    private val matrixClient: MatrixClient,
    private val coordinator: LiveLocationSharingCoordinator,
    private val liveLocationStore: LiveLocationStore,
    private val clock: SystemClock,
    private val sessionObserver: SessionObserver,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
    private val okHttpClient: () -> OkHttpClient,
) : ActiveLiveLocationShareManager, LiveLocationReceiver {
    private val isSetup = AtomicBoolean(false)
    private val cachedRooms = ConcurrentHashMap<RoomId, JoinedRoom>()
    private val timeoutJobs = ConcurrentHashMap<RoomId, Job>()
    private val liveLocationShareSubscriptions = ConcurrentHashMap<RoomId, Job>()
    private val beaconInfoEventIds = ConcurrentHashMap<RoomId, EventId>()
    private val beaconInfoLocationPublishedRoomIds = ConcurrentHashMap.newKeySet<RoomId>()
    private val activeShareExpiresAt = ConcurrentHashMap<RoomId, Instant>()
    private val syncedActiveShareIds = MutableStateFlow<Set<BeaconId>>(emptySet())
    private val localSharingRoomIds = MutableStateFlow<Set<RoomId>>(emptySet())
    override val sharingRoomIds: StateFlow<Set<RoomId>> = localSharingRoomIds

    override suspend fun setup() = withContext(NonCancellable) {
        if (isSetup.compareAndSet(expectedValue = false, newValue = true)) {
            Timber.d("ActiveLiveLocationShareManager setup manager.")

            recoverPersistedShares()

            matrixClient.ownBeaconInfoUpdates
                .onEach { update ->
                    Timber.d("Received beaconInfoUpdate:$update")
                    // First cancel the local share in this room if any.
                    if (update.roomId in localSharingRoomIds.value) {
                        stopLocalShare(roomId = update.roomId)
                    }
                    syncedActiveShareIds.update {
                        if (update.isLive) {
                            it + update.beaconId
                        } else {
                            it - update.beaconId
                        }
                    }
                }
                .launchIn(matrixClient.sessionCoroutineScope)

            sessionObserver.addListener(sessionListener)
        }
    }

    private val sessionListener: SessionListener = object : SessionListener {
        override suspend fun onSessionDeleted(userId: String, wasLastSession: Boolean) {
            if (matrixClient.sessionId.value == userId) {
                clear()
            }
        }
    }

    override suspend fun startShare(roomId: RoomId, duration: Duration): Result<Unit> = withContext(NonCancellable) {
        Timber.d("ActiveLiveLocationShareManager starting share for room $roomId with duration ${duration.inWholeSeconds}s")
        val room = cachedRooms.getOrPut(roomId) {
            matrixClient.getJoinedRoom(roomId) ?: return@withContext Result.failure(IllegalStateException("No room found for $roomId"))
        }
        keepLiveLocationSharesSubscribed(room, roomId)
        // Before starting a new location share, stop the current one if any is active.
        room.stopLiveLocationShare()

        startRemoteLiveLocationShare(room, roomId, duration)
            .onSuccess { startResult ->
                beaconInfoEventIds[roomId] = startResult.beaconInfoEventId
                if (startResult.shouldWaitForSdkSync) {
                    waitForOwnLiveLocationShare(room, roomId)
                }
                val expiresAt = Instant.fromEpochMilliseconds(clock.epochMillis() + duration.inWholeMilliseconds)
                activeShareExpiresAt[roomId] = expiresAt
                startLocalShare(roomId, expiresAt)
            }
            .onFailure {
                Timber.e(it, "ActiveLiveLocationShareManager failed to start share for room $roomId")
                stopLocalShare(roomId)
            }
            .map { }
    }

    private suspend fun startRemoteLiveLocationShare(room: JoinedRoom, roomId: RoomId, duration: Duration): Result<LiveLocationStartResult> {
        createBeaconInfo(roomId, duration.inWholeMilliseconds)
            .onSuccess { eventId ->
                Timber.d("ActiveLiveLocationShareManager created beacon_info $eventId for room $roomId")
                return Result.success(LiveLocationStartResult(eventId, shouldWaitForSdkSync = false))
            }
            .onFailure { error ->
                Timber.w(error, "ActiveLiveLocationShareManager failed to create beacon_info over Matrix HTTP, falling back to SDK")
            }
        return room.startLiveLocationShare(duration.inWholeMilliseconds)
            .map { eventId -> LiveLocationStartResult(eventId, shouldWaitForSdkSync = true) }
    }

    private suspend fun createBeaconInfo(roomId: RoomId, durationMillis: Long): Result<EventId> = runCatching {
        val now = clock.epochMillis()
        val body = JSONObject()
            .put("description", "Live location")
            .put("live", true)
            .put("timeout", durationMillis)
            .put("org.matrix.msc3488.ts", now)
            .put("m.ts", now)
            .put("m.asset", JSONObject().put("type", "m.self"))
        val url = "${homeserverBaseUrl()}/_matrix/client/v3/rooms/${roomId.value.urlPath()}/state/$MATRIX_BEACON_INFO_EVENT_TYPE/${matrixClient.sessionId.value.urlPath()}"
        val response = executeMatrixJsonRequest(
            Request.Builder()
                .url(url)
                .put(body.toString().toRequestBody(MATRIX_JSON))
        )
        EventId(JSONObject(response).getString("event_id"))
    }

    private suspend fun waitForOwnLiveLocationShare(room: JoinedRoom, roomId: RoomId) {
        val ownSessionId = matrixClient.sessionId
        val didSync = withTimeoutOrNull(LIVE_LOCATION_SHARE_SYNC_TIMEOUT) {
            room.subscribeToLiveLocationShares()
                .first { shares ->
                    shares.any { share ->
                        share.userId == ownSessionId && share.endTimestamp > clock.epochMillis()
                    }
                }
        } != null
        if (didSync) {
            Timber.d("ActiveLiveLocationShareManager own beacon is visible to SDK for room $roomId")
        } else {
            Timber.w("ActiveLiveLocationShareManager timed out waiting for own beacon in room $roomId; starting local share anyway")
        }
    }

    private fun keepLiveLocationSharesSubscribed(room: JoinedRoom, roomId: RoomId) {
        liveLocationShareSubscriptions.computeIfAbsent(roomId) {
            matrixClient.sessionCoroutineScope.launch {
                room.subscribeToLiveLocationShares()
                    .collect { shares ->
                        val ownActiveShare = shares.firstOrNull { share ->
                            share.userId == matrixClient.sessionId && share.endTimestamp > clock.epochMillis()
                        }
                        if (ownActiveShare != null) {
                            Timber.d("ActiveLiveLocationShareManager observed own live location share ${ownActiveShare.beaconId} in room $roomId")
                            syncedActiveShareIds.update { it + ownActiveShare.beaconId }
                        } else {
                            syncedActiveShareIds.update { activeShareIds ->
                                activeShareIds - shares
                                    .filter { it.userId == matrixClient.sessionId }
                                    .map { it.beaconId }
                                    .toSet()
                            }
                        }
                    }
            }
        }
    }

    override suspend fun stopShare(roomId: RoomId): Result<Unit> = withContext(NonCancellable) {
        Timber.d("ActiveLiveLocationShareManager stopping share for room $roomId")
        val room = cachedRooms.getOrPut(roomId) {
            matrixClient.getJoinedRoom(roomId) ?: return@withContext Result.failure(IllegalStateException("No room found for $roomId"))
        }
        room.stopLiveLocationShare()
            .onSuccess {
                Timber.d("ActiveLiveLocationShareManager share stopped successfully for room $roomId")
            }
            .onFailure {
                Timber.e(it, "ActiveLiveLocationShareManager failed to stop share for room $roomId")
                stopBeaconInfo(roomId)
                    .onFailure { fallbackError ->
                        Timber.e(fallbackError, "ActiveLiveLocationShareManager failed to stop share over Matrix HTTP for room $roomId")
                    }
            }
            .also {
                stopLocalShare(roomId)
            }
    }

    override suspend fun onUnrecoverableError() {
        Timber.d("ActiveLiveLocationShareManager unrecoverable error, stopping all shares")
        localSharingRoomIds.value.toList().forEach { stopShare(it) }
    }

    override suspend fun onLocationUpdate(location: Location) {
        val activeSharesCount = localSharingRoomIds.value.size
        Timber.d("ActiveLiveLocationShareManager received location update for $activeSharesCount active share(s)")
        localSharingRoomIds.value.forEach { roomId ->
            Timber.d("ActiveLiveLocationShareManager sending location to room $roomId")
            sendLiveLocation(roomId, location)
                .onFailure {
                    Timber.e(it, "ActiveLiveLocationShareManager failed to send location to room $roomId")
                }
        }
    }

    private suspend fun sendLiveLocation(roomId: RoomId, location: Location): Result<Unit> {
        val room = cachedRooms.getOrPut(roomId) {
            matrixClient.getJoinedRoom(roomId) ?: return Result.failure(IllegalStateException("No room found for $roomId"))
        }
        val geoUri = location.toGeoUri()
        beaconInfoEventIds[roomId]?.let { beaconInfoEventId ->
            return sendBeaconLocation(roomId, beaconInfoEventId, geoUri)
        }
        var result = room.sendLiveLocation(geoUri)
        for (retryDelay in LIVE_LOCATION_SEND_RETRY_DELAYS) {
            if (result.isSuccess || !result.exceptionOrNull().isTransientBeaconNotFound()) {
                break
            }
            Timber.d("ActiveLiveLocationShareManager beacon not ready for room $roomId, retrying live location send in $retryDelay")
            delay(retryDelay)
            result = room.sendLiveLocation(geoUri)
        }
        return result.recoverCatching { exception ->
                when (exception) {
                    is LiveLocationException.NotLive -> {
                        stopLocalShare(roomId)
                        throw exception
                    }
                    else -> throw exception
                }
            }
    }

    private suspend fun sendBeaconLocation(roomId: RoomId, beaconInfoEventId: EventId, geoUri: String): Result<Unit> = runCatching {
        val now = clock.epochMillis()
        val location = JSONObject().put("uri", geoUri)
        val body = JSONObject()
            .put(
                "m.relates_to",
                JSONObject()
                    .put("rel_type", "m.reference")
                    .put("event_id", beaconInfoEventId.value)
            )
            .put("m.location", location)
            .put("org.matrix.msc3488.location", location)
            .put("org.matrix.msc3488.ts", now)
            .put("m.ts", now)
        val txnId = "live-location-${now}-${UUID.randomUUID()}"
        val url = "${homeserverBaseUrl()}/_matrix/client/v3/rooms/${roomId.value.urlPath()}/send/$MATRIX_BEACON_EVENT_TYPE/${txnId.urlPath()}"
        executeMatrixJsonRequest(
            Request.Builder()
                .url(url)
                .put(body.toString().toRequestBody(MATRIX_JSON))
        )
        if (beaconInfoLocationPublishedRoomIds.add(roomId)) {
            updateBeaconInfoLocation(roomId, geoUri)
                .onFailure { error ->
                    Timber.w(error, "ActiveLiveLocationShareManager failed to update beacon_info location for room $roomId")
                }
        }
        Timber.d("ActiveLiveLocationShareManager sent beacon location for room $roomId")
    }

    private suspend fun updateBeaconInfoLocation(roomId: RoomId, geoUri: String): Result<EventId> = runCatching {
        val now = clock.epochMillis()
        val timeoutMillis = activeShareExpiresAt[roomId]
            ?.let { (it.toEpochMilliseconds() - now).coerceAtLeast(0L) }
            ?: 0L
        val location = JSONObject().put("uri", geoUri)
        val body = JSONObject()
            .put("description", "Live location")
            .put("live", true)
            .put("timeout", timeoutMillis)
            .put("org.matrix.msc3488.ts", now)
            .put("m.ts", now)
            .put("m.asset", JSONObject().put("type", "m.self"))
            .put("m.location", location)
            .put("org.matrix.msc3488.location", location)
        val url = "${homeserverBaseUrl()}/_matrix/client/v3/rooms/${roomId.value.urlPath()}/state/$MATRIX_BEACON_INFO_EVENT_TYPE/${matrixClient.sessionId.value.urlPath()}"
        val response = executeMatrixJsonRequest(
            Request.Builder()
                .url(url)
                .put(body.toString().toRequestBody(MATRIX_JSON))
        )
        EventId(JSONObject(response).getString("event_id"))
    }

    private suspend fun stopBeaconInfo(roomId: RoomId): Result<EventId> = runCatching {
        val now = clock.epochMillis()
        val body = JSONObject()
            .put("description", "Live location")
            .put("live", false)
            .put("timeout", 0L)
            .put("org.matrix.msc3488.ts", now)
            .put("m.ts", now)
            .put("m.asset", JSONObject().put("type", "m.self"))
        val url = "${homeserverBaseUrl()}/_matrix/client/v3/rooms/${roomId.value.urlPath()}/state/$MATRIX_BEACON_INFO_EVENT_TYPE/${matrixClient.sessionId.value.urlPath()}"
        val response = executeMatrixJsonRequest(
            Request.Builder()
                .url(url)
                .put(body.toString().toRequestBody(MATRIX_JSON))
        )
        EventId(JSONObject(response).getString("event_id"))
    }

    private suspend fun executeMatrixJsonRequest(requestBuilder: Request.Builder): String = withContext(Dispatchers.IO) {
        val accessToken = matrixClient.currentAccessToken().getOrNull()
            ?: throw IllegalStateException("No Matrix access token available")
        val request = requestBuilder
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .build()
        okHttpClient().newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            if (!response.isSuccessful) {
                throw IllegalStateException("Matrix request failed with HTTP ${response.code}")
            }
            responseBody
        }
    }

    private suspend fun homeserverBaseUrl(): String {
        return baseUrlResolver.resolveHomeserverBaseUrl(matrixClient.userIdServerName()).trimEnd('/')
    }

    private fun String.urlPath(): String {
        return URLEncoder.encode(this, UTF_8.name()).replace("+", "%20")
    }

    private fun Throwable?.isTransientBeaconNotFound(): Boolean {
        return this?.message?.contains("NotFound", ignoreCase = true) == true ||
            this?.cause.isTransientBeaconNotFound()
    }

    private suspend fun startLocalShare(roomId: RoomId, expiresAt: Instant) {
        val wasEmpty = localSharingRoomIds.value.isEmpty()
        Timber.d("ActiveLiveLocationShareManager share started successfully for room $roomId (wasEmpty=$wasEmpty)")
        localSharingRoomIds.update { it + roomId }
        liveLocationStore.setLiveLocationExpiry(roomId, expiresAt)
        scheduleTimeout(roomId, expiresAt)
        if (wasEmpty) {
            Timber.d("ActiveLiveLocationShareManager registering with coordinator for session ${matrixClient.sessionId}")
            coordinator.register(matrixClient.sessionId, this@DefaultActiveLiveLocationShareManager)
        }
    }

    private suspend fun recoverPersistedShares() {
        val now = Instant.fromEpochMilliseconds(clock.epochMillis())
        liveLocationStore.getLiveLocationExpiries().forEach { (roomId, expiresAt) ->
            if (expiresAt > now) {
                val room = cachedRooms.getOrPut(roomId) {
                    matrixClient.getJoinedRoom(roomId) ?: return@forEach
                }
                keepLiveLocationSharesSubscribed(room, roomId)
                waitForOwnLiveLocationShare(room, roomId)
                startLocalShare(roomId, expiresAt)
            } else {
                // Explicitly stop the share on the server.
                stopShare(roomId)
            }
        }
    }

    private fun scheduleTimeout(roomId: RoomId, expiresAt: Instant) {
        timeoutJobs.remove(roomId)?.cancel()
        val delayMillis = expiresAt.toEpochMilliseconds() - clock.epochMillis()
        timeoutJobs[roomId] = matrixClient.sessionCoroutineScope.launch {
            delay(delayMillis)
            stopShare(roomId)
                .onFailure { error ->
                    Timber.e(error, "ActiveLiveLocationShareManager failed to stop timed out share for room $roomId")
                }
        }
    }

    private suspend fun stopLocalShare(roomId: RoomId) {
        Timber.d("ActiveLiveLocationShareManager stop local share in $roomId")
        timeoutJobs.remove(roomId)?.cancel()
        liveLocationShareSubscriptions.remove(roomId)?.cancel()
        beaconInfoEventIds.remove(roomId)
        beaconInfoLocationPublishedRoomIds.remove(roomId)
        activeShareExpiresAt.remove(roomId)
        val wasSharing = localSharingRoomIds.getAndUpdate { it - roomId }.isNotEmpty()
        cachedRooms.remove(roomId)?.close()
        liveLocationStore.removeLiveLocationExpiry(roomId)
        if (wasSharing && localSharingRoomIds.value.isEmpty()) {
            Timber.d("ActiveLiveLocationShareManager unregistering from coordinator for session ${matrixClient.sessionId}")
            coordinator.unregister(matrixClient.sessionId)
        }
    }

    private suspend fun clear() {
        Timber.d("ActiveLiveLocationShareManager clear state")
        sessionObserver.removeListener(sessionListener)
        coordinator.unregister(matrixClient.sessionId)
        liveLocationStore.clear()
        for (room in cachedRooms.values) {
            room.close()
            timeoutJobs[room.roomId]?.cancel()
            liveLocationShareSubscriptions[room.roomId]?.cancel()
        }
        timeoutJobs.clear()
        liveLocationShareSubscriptions.clear()
        beaconInfoEventIds.clear()
        beaconInfoLocationPublishedRoomIds.clear()
        activeShareExpiresAt.clear()
        cachedRooms.clear()
        localSharingRoomIds.value = emptySet()
        syncedActiveShareIds.value = emptySet()
    }
}
