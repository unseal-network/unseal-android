/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.audience

import dev.zacsweers.metro.Inject
import io.element.android.features.call.api.AudienceAccessMode
import io.element.android.features.call.api.AudienceBroadcastDiscovery
import io.element.android.features.call.api.AudienceRelayDesired
import io.element.android.features.call.api.AudienceRuntimePhase
import io.element.android.features.call.api.AudienceRuntimeStatus
import io.element.android.features.call.api.isValidAudienceBroadcastId
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.matrix.api.MatrixClientProvider
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

@Inject
class AudienceBroadcastHttpClient(
    private val matrixClientProvider: MatrixClientProvider,
    private val sessionStore: SessionStore,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
    private val okHttpClient: () -> OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = false }
    private val privateOkHttpClient by lazy {
        okHttpClient().newBuilder().apply {
            // Playback grants are bearer capabilities embedded in URLs and response bodies.
            // Keep them out of every application and network interceptor, including debug logging.
            interceptors().clear()
            networkInterceptors().clear()
        }.build()
    }

    suspend fun getRuntimeStatus(sessionId: SessionId, broadcastId: String): AudienceRuntimeStatus {
        require(broadcastId.isValidAudienceBroadcastId()) { "Invalid broadcast ID" }
        val response = request(
            sessionId = sessionId,
            method = "GET",
            path = "/meeting-broadcast/v1/broadcasts/$broadcastId",
        )
        if (!response.isSuccessful) throw AudienceHttpException(response.code, response.body)
        return parseRuntimeStatus(response.body, broadcastId)
    }

    /**
     * Reads the Relay-owned discovery event from the Matrix homeserver when the
     * sliding-sync cache has not hydrated this custom state event yet. This is
     * deliberately a Matrix state read, not an Audience API request: callers
     * must still validate the document before starting any broadcast polling.
     */
    suspend fun getMatrixDiscoveryState(sessionId: SessionId, roomId: RoomId): String? {
        val session = sessionStore.getSession(sessionId.value) ?: error("Session is unavailable")
        val baseUrl = session.homeserverUrl.toHttpUrl()
        require(baseUrl.scheme == "https" || baseUrl.host in LOCAL_API_HOSTS) { "Homeserver must use HTTPS" }
        require(baseUrl.username.isEmpty() && baseUrl.password.isEmpty()) { "Homeserver URL must not contain credentials" }
        val url = baseUrl.newBuilder()
            .addPathSegment("_matrix")
            .addPathSegment("client")
            .addPathSegment("v3")
            .addPathSegment("rooms")
            .addPathSegment(roomId.value)
            .addPathSegment("state")
            .addPathSegment(DISCOVERY_EVENT_TYPE)
            .addPathSegment(DISCOVERY_STATE_KEY)
            .build()
        val response = execute(
            sessionId,
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get(),
        )
        return when (response.code) {
            200 -> response.body
            404 -> null
            else -> throw AudienceHttpException(response.code, response.body)
        }
    }

    suspend fun setRelayDesired(
        sessionId: SessionId,
        roomId: RoomId,
        meetingInstanceId: String,
        desired: AudienceRelayDesired,
        accessMode: AudienceAccessMode?,
    ): AudienceRuntimeStatus? {
        require(UUID_PATTERN.matches(meetingInstanceId)) { "Invalid meeting instance ID" }
        val body = buildString {
            append("{\"desired\":\"")
            append(if (desired == AudienceRelayDesired.Joined) "joined" else "left")
            append('"')
            if (desired == AudienceRelayDesired.Joined) {
                val mode = requireNotNull(accessMode) { "Access mode is required when joining the relay" }
                append(",\"access_mode\":\"")
                append(if (mode == AudienceAccessMode.Authenticated) "authenticated" else "room_members")
                append('"')
            }
            append('}')
        }
        val response = request(
            sessionId = sessionId,
            method = "PUT",
            path = "/meeting-broadcast/v1/rooms/${roomId.value}/meetings/$meetingInstanceId/relay",
            body = body,
        )
        if (!response.isSuccessful) throw AudienceHttpException(response.code, response.body)
        if (response.body.isBlank() || response.body.trim() == "{}") return null
        return parseRelayStatus(response.body, roomId, meetingInstanceId)
    }

    suspend fun requestAudienceWidget(
        sessionId: SessionId,
        broadcastId: String,
        method: String,
        path: String,
        body: JsonElement?,
    ): AudienceHttpResponse {
        require(broadcastId.isValidAudienceBroadcastId()) { "Invalid broadcast ID" }
        require(isAllowedAudienceRequest(method, path, body, broadcastId)) { "Unsupported audience API request" }
        return request(sessionId, method, path, body?.toString())
    }

    suspend fun loadAudienceThumbnail(
        sessionId: SessionId,
        mxcUrl: String,
        size: Int,
    ): ByteArray {
        require(MXC_URL_PATTERN.matches(mxcUrl)) { "Invalid Matrix media URL" }
        require(size in MIN_AVATAR_SIZE..MAX_AVATAR_SIZE) { "Invalid thumbnail size" }
        val matrixClient = matrixClientProvider.getOrRestore(sessionId).getOrThrow()
        return matrixClient.matrixMediaLoader
            .loadMediaThumbnail(MediaSource(mxcUrl), size.toLong(), size.toLong())
            .getOrThrow()
            .also { bytes ->
                require(bytes.isNotEmpty() && bytes.size <= MAX_AVATAR_BYTES) { "Invalid thumbnail payload" }
            }
    }

    suspend fun createAudienceSession(
        sessionId: SessionId,
        broadcastId: String,
        audienceClientId: String,
    ): AudienceSession {
        require(broadcastId.isValidAudienceBroadcastId()) { "Invalid broadcast ID" }
        require(AUDIENCE_CLIENT_ID_PATTERN.matches(audienceClientId)) { "Invalid audience client ID" }
        val response = request(
            sessionId = sessionId,
            method = "POST",
            path = "/meeting-broadcast/v1/broadcasts/$broadcastId/audience-sessions",
            body = "{\"audience_client_id\":\"$audienceClientId\"}",
        )
        if (!response.isSuccessful) throw AudienceHttpException(response.code, response.body)
        return parseAudienceSession(response.body, broadcastId, audienceClientId)
    }

    suspend fun heartbeatAudienceSession(
        sessionId: SessionId,
        broadcastId: String,
        audienceSessionId: String,
        generation: Int,
    ): AudienceHeartbeat {
        require(AUDIENCE_SESSION_ID_PATTERN.matches(audienceSessionId)) { "Invalid audience session ID" }
        require(generation >= 0) { "Invalid audience generation" }
        val response = request(
            sessionId = sessionId,
            method = "PUT",
            path = "/meeting-broadcast/v1/broadcasts/$broadcastId/audience-sessions/$audienceSessionId/heartbeat",
            body = "{\"generation\":$generation}",
        )
        if (!response.isSuccessful) throw AudienceHttpException(response.code, response.body)
        return parseAudienceHeartbeat(response.body)
    }

    suspend fun closeAudienceSession(
        sessionId: SessionId,
        broadcastId: String,
        audienceSessionId: String,
    ) {
        require(AUDIENCE_SESSION_ID_PATTERN.matches(audienceSessionId)) { "Invalid audience session ID" }
        val response = request(
            sessionId = sessionId,
            method = "DELETE",
            path = "/meeting-broadcast/v1/broadcasts/$broadcastId/audience-sessions/$audienceSessionId",
        )
        if (!response.isSuccessful) throw AudienceHttpException(response.code, response.body)
    }

    suspend fun getAudienceManifest(manifestUrl: String): AudienceManifest {
        val url = manifestUrl.toHttpUrl()
        require(url.scheme == "https" || url.host in LOCAL_API_HOSTS) { "Audience manifest must use HTTPS" }
        require(url.username.isEmpty() && url.password.isEmpty()) { "Audience manifest URL must not contain credentials" }
        require(GRANT_MANIFEST_PATH_PATTERN.matches(url.encodedPath)) { "Invalid audience manifest URL" }
        val response = withContext(Dispatchers.IO) {
            try {
                privateOkHttpClient.newCall(
                    Request.Builder()
                        .url(url)
                        .header("Accept", "application/json")
                        .get()
                        .build()
                ).execute().use { httpResponse ->
                    AudienceHttpResponse(httpResponse.code, httpResponse.body.string())
                }
            } catch (failure: IOException) {
                throw AudienceHttpException(0, failure.message.orEmpty(), failure)
            }
        }
        if (!response.isSuccessful) throw AudienceHttpException(response.code, response.body)
        return parseAudienceManifest(response.body, url)
    }

    private suspend fun request(
        sessionId: SessionId,
        method: String,
        path: String,
        body: String? = null,
    ): AudienceHttpResponse {
        val matrixClient = matrixClientProvider.getOrRestore(sessionId).getOrThrow()
        val baseUrl = baseUrlResolver.resolveUnsealApiBaseUrl(matrixClient.userIdServerName()).toHttpUrl()
        require(baseUrl.scheme == "https" || baseUrl.host in LOCAL_API_HOSTS) { "Unseal API must use HTTPS" }
        require(baseUrl.username.isEmpty() && baseUrl.password.isEmpty()) { "Unseal API URL must not contain credentials" }
        val pathBits = path.removePrefix("/").split("/").filter(String::isNotEmpty)
        val url = baseUrl.newBuilder().apply {
            pathBits.forEach(::addPathSegment)
        }.build()
        val requestBody = when {
            body != null -> body.toRequestBody(JSON_MEDIA_TYPE)
            method == "POST" || method == "PUT" -> ByteArray(0).toRequestBody(JSON_MEDIA_TYPE)
            else -> null
        }
        return execute(
            sessionId,
            Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .method(method, requestBody),
        )
    }

    private suspend fun execute(sessionId: SessionId, builder: Request.Builder): AudienceHttpResponse {
        val session = sessionStore.getSession(sessionId.value) ?: error("Session is unavailable")
        val matrixClient = matrixClientProvider.getOrRestore(sessionId).getOrThrow()
        val token = matrixClient.currentAccessToken().getOrNull()
            ?.takeIf(String::isNotBlank)
            ?: session.accessToken.takeIf(String::isNotBlank)
            ?: error("Matrix access token is unavailable")
        val request = builder.header("Authorization", "Bearer $token").build()
        return withContext(Dispatchers.IO) {
            try {
                privateOkHttpClient.newCall(request).execute().use { response ->
                    AudienceHttpResponse(
                        code = response.code,
                        body = response.body.string(),
                    )
                }
            } catch (failure: IOException) {
                throw AudienceHttpException(0, failure.message.orEmpty(), failure)
            }
        }
    }

    internal fun parseDiscovery(raw: String): AudienceBroadcastDiscovery? = runCatching {
        val input = json.parseToJsonElement(raw) as? JsonObject ?: return@runCatching null
        if (input.isEmpty()) return@runCatching null
        if (input.keys != DISCOVERY_FIELDS) return@runCatching null
        if (input["version"]?.jsonPrimitive?.contentOrNull != "1") return@runCatching null
        val broadcastId = input.string("broadcast_id")?.takeIf(String::isValidAudienceBroadcastId) ?: return@runCatching null
        val meetingInstanceId = input.string("meeting_instance_id")?.takeIf(UUID_PATTERN::matches) ?: return@runCatching null
        val accessMode = when (input.string("access_mode")) {
            "authenticated" -> AudienceAccessMode.Authenticated
            "room_members" -> AudienceAccessMode.RoomMembers
            else -> return@runCatching null
        }
        AudienceBroadcastDiscovery(
            broadcastId = broadcastId,
            meetingInstanceId = meetingInstanceId,
            accessMode = accessMode,
            phase = AudienceRuntimePhase.Live,
        )
    }.getOrNull()

    private fun parseRuntimeStatus(raw: String, expectedBroadcastId: String): AudienceRuntimeStatus {
        val input = json.parseToJsonElement(raw) as? JsonObject ?: error("Invalid runtime status")
        if (!input.keys.containsAll(RUNTIME_STATUS_REQUIRED_FIELDS) || input.keys.any { it !in RUNTIME_STATUS_FIELDS }) {
            error("Runtime status has unexpected fields")
        }
        if (input["version"]?.jsonPrimitive?.contentOrNull != "1") error("Runtime status has an invalid version")
        val broadcastId = input.string("broadcast_id")
            ?.takeIf { it == expectedBroadcastId && it.isValidAudienceBroadcastId() }
            ?: error("Runtime status broadcast does not match")
        val roomId = input.string("room_id")?.let(::RoomId) ?: error("Runtime status has no room")
        val meetingInstanceId = input.string("meeting_instance_id")?.takeIf(UUID_PATTERN::matches)
            ?: error("Runtime status has an invalid meeting instance")
        val phase = when (input.string("phase")) {
            "idle" -> AudienceRuntimePhase.Idle
            "joining" -> AudienceRuntimePhase.Joining
            "publishing" -> AudienceRuntimePhase.Publishing
            "live" -> AudienceRuntimePhase.Live
            "recovering" -> AudienceRuntimePhase.Recovering
            "leaving" -> AudienceRuntimePhase.Leaving
            "ended" -> AudienceRuntimePhase.Ended
            else -> error("Runtime status has an invalid phase")
        }
        val desired = when (input.string("desired")) {
            "joined" -> AudienceRelayDesired.Joined
            "left" -> AudienceRelayDesired.Left
            else -> error("Runtime status has an invalid desired membership")
        }
        val agentInMeeting = input.boolean("agent_in_meeting")
            ?: error("Runtime status has no agent membership flag")
        val broadcastArmed = input.boolean("broadcast_armed")
            ?: error("Runtime status has no armed flag")
        val playable = input["playable"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
            ?: error("Runtime status has no playable flag")
        val pollAfterMs = input["poll_after_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            ?.coerceIn(MIN_POLL_MS, if (phase == AudienceRuntimePhase.Live) MAX_LIVE_POLL_MS else MAX_POLL_MS)
            ?: error("Runtime status has no polling hint")
        return AudienceRuntimeStatus(
            broadcastId = broadcastId,
            roomId = roomId,
            meetingInstanceId = meetingInstanceId,
            phase = phase,
            desired = desired,
            agentInMeeting = agentInMeeting,
            broadcastArmed = broadcastArmed,
            playable = playable,
            pollAfterMs = pollAfterMs,
            generation = input.nonNegativeInt("generation"),
            manifestRevision = input.nonNegativeInt("manifest_revision"),
            participantCount = input.nonNegativeInt("participant_count"),
            listenerCount = input.nonNegativeInt("listener_count"),
        )
    }

    private fun parseRelayStatus(raw: String, expectedRoomId: RoomId, expectedMeetingInstanceId: String): AudienceRuntimeStatus {
        val input = json.parseToJsonElement(raw) as? JsonObject ?: error("Invalid relay status")
        val broadcastId = input.string("broadcast_id")?.takeIf(String::isValidAudienceBroadcastId)
            ?: error("Relay status has no broadcast ID")
        val roomId = input.string("room_id")?.let(::RoomId)?.takeIf { it == expectedRoomId }
            ?: error("Relay status room does not match")
        val meetingInstanceId = input.string("meeting_instance_id")?.takeIf { it == expectedMeetingInstanceId }
            ?: error("Relay status meeting does not match")
        val phase = input.string("phase")?.toRuntimePhase() ?: error("Relay status has an invalid phase")
        val desired = when (input.string("desired")) {
            "joined" -> AudienceRelayDesired.Joined
            "left" -> AudienceRelayDesired.Left
            else -> error("Relay status has an invalid desired membership")
        }
        return AudienceRuntimeStatus(
            broadcastId = broadcastId,
            roomId = roomId,
            meetingInstanceId = meetingInstanceId,
            phase = phase,
            desired = desired,
            agentInMeeting = input.boolean("agent_in_meeting") ?: false,
            broadcastArmed = input.boolean("broadcast_armed") ?: false,
            playable = input.boolean("playable") ?: false,
            pollAfterMs = input["poll_after_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?.coerceIn(MIN_POLL_MS, MAX_POLL_MS) ?: MIN_POLL_MS,
        )
    }

    internal fun parseAudienceSession(raw: String, expectedBroadcastId: String, expectedClientId: String): AudienceSession {
        val input = json.parseToJsonElement(raw) as? JsonObject ?: error("Invalid audience session")
        if (input.keys != AUDIENCE_SESSION_FIELDS) error("Audience session has unexpected fields")
        val sessionId = input.string("session_id")?.takeIf(AUDIENCE_SESSION_ID_PATTERN::matches)
            ?: error("Audience session has an invalid ID")
        val clientId = input.string("audience_client_id")?.takeIf { it == expectedClientId }
            ?: error("Audience session client does not match")
        val broadcastId = input.string("broadcast_id")?.takeIf { it == expectedBroadcastId }
            ?: error("Audience session broadcast does not match")
        return AudienceSession(
            sessionId = sessionId,
            audienceClientId = clientId,
            broadcastId = broadcastId,
            generation = input.positiveInt("generation"),
            manifestUrl = input.string("manifest_url") ?: error("Audience session has no manifest URL"),
            expiresAt = input.string("expires_at") ?: error("Audience session has no expiry"),
            heartbeatIntervalMs = input["heartbeat_interval_ms"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?.coerceAtLeast(MIN_HEARTBEAT_MS) ?: error("Audience session has no heartbeat interval"),
        )
    }

    internal fun parseAudienceHeartbeat(raw: String): AudienceHeartbeat {
        val input = json.parseToJsonElement(raw) as? JsonObject ?: error("Invalid audience heartbeat")
        if (input.keys != AUDIENCE_HEARTBEAT_FIELDS) error("Audience heartbeat has unexpected fields")
        val phase = input.string("phase")?.toRuntimePhase() ?: error("Audience heartbeat has an invalid phase")
        val playback = when (val value = input["playback"]) {
            null, JsonNull -> null
            is JsonObject -> {
                if (value.keys != AUDIENCE_PLAYBACK_FIELDS) error("Audience playback has unexpected fields")
                AudiencePlaybackGrant(
                    generation = value.positiveInt("generation"),
                    manifestUrl = value.string("manifest_url") ?: error("Audience playback has no manifest URL"),
                    expiresAt = value.string("expires_at") ?: error("Audience playback has no expiry"),
                )
            }
            else -> error("Audience playback is invalid")
        }
        return AudienceHeartbeat(
            active = input.boolean("active") ?: error("Audience heartbeat has no active flag"),
            phase = phase,
            generation = input.nonNegativeInt("generation"),
            manifestRevision = input.nonNegativeInt("manifest_revision"),
            expiresAt = input.string("expires_at") ?: error("Audience heartbeat has no expiry"),
            playback = playback,
        )
    }

    internal fun parseAudienceManifest(raw: String, manifestUrl: okhttp3.HttpUrl): AudienceManifest {
        val input = json.parseToJsonElement(raw) as? JsonObject ?: error("Invalid audience manifest")
        if (input.keys != AUDIENCE_MANIFEST_FIELDS) error("Audience manifest has unexpected fields")
        if (input["version"]?.jsonPrimitive?.contentOrNull != "1") error("Audience manifest has an invalid version")
        input.string("generated_at") ?: error("Audience manifest has no generation timestamp")
        val broadcastId = input.string("broadcast_id")?.takeIf(String::isValidAudienceBroadcastId)
            ?: error("Audience manifest has an invalid broadcast ID")
        val presentations = input["presentations"] as? kotlinx.serialization.json.JsonArray
            ?: error("Audience manifest has no presentations")
        return AudienceManifest(
            broadcastId = broadcastId,
            meetingInstanceId = input.string("meeting_instance_id")?.takeIf(UUID_PATTERN::matches)
                ?: error("Audience manifest has an invalid meeting instance"),
            generation = input.positiveInt("generation"),
            revision = input.positiveInt("revision"),
            presentations = presentations.map { element ->
                val presentation = element as? JsonObject ?: error("Invalid audience presentation")
                if (!presentation.keys.containsAll(AUDIENCE_PRESENTATION_REQUIRED_FIELDS) ||
                    presentation.keys.any { it !in AUDIENCE_PRESENTATION_FIELDS }
                ) {
                    error("Audience presentation has unexpected fields")
                }
                val kind = when (presentation.string("kind")) {
                    "user" -> AudiencePresentationKind.User
                    "screen" -> AudiencePresentationKind.Screen
                    else -> error("Audience presentation has an invalid kind")
                }
                AudiencePresentation(
                    presentationId = presentation.string("presentation_id")?.takeIf(String::isNotBlank)
                        ?: error("Audience presentation has no ID"),
                    kind = kind,
                    matrixUserId = presentation.string("matrix_user_id")?.takeIf(MATRIX_USER_ID_PATTERN::matches)
                        ?: error("Audience presentation has an invalid Matrix user"),
                    matrixDeviceId = presentation.string("matrix_device_id")?.takeIf(String::isNotBlank)
                        ?: error("Audience presentation has no Matrix device"),
                    displayName = presentation.string("display_name") ?: error("Audience presentation has no display name"),
                    avatarUrl = presentation.stringOrNull("avatar_url"),
                    sourceId = presentation.stringOrNull("source_id"),
                    audio = parseRendition(presentation["audio"], manifestUrl),
                    video = parseRendition(presentation["video"], manifestUrl),
                    activeSpeaker = presentation.boolean("active_speaker") ?: false,
                ).also { parsed ->
                    require(parsed.audio != null || parsed.video != null) { "Audience presentation has no playable rendition" }
                }
            },
        ).also { manifest ->
            require(manifest.presentations.isNotEmpty()) { "Audience manifest has no playable presentations" }
            require(manifest.presentations.map(AudiencePresentation::presentationId).distinct().size == manifest.presentations.size) {
                "Audience manifest contains duplicate presentation IDs"
            }
        }
    }

    private fun parseRendition(value: JsonElement?, manifestUrl: okhttp3.HttpUrl): AudienceRendition? {
        if (value == null || value is JsonNull) return null
        val rendition = value as? JsonObject ?: error("Invalid audience rendition")
        if (!rendition.keys.contains("playlist_url") || rendition.keys.any { it !in AUDIENCE_RENDITION_FIELDS }) {
            error("Audience rendition has unexpected fields")
        }
        val relativePath = rendition.string("playlist_url")
            ?.takeIf(RELATIVE_PLAYLIST_PATH_PATTERN::matches)
            ?: error("Audience rendition has an invalid playlist path")
        val resolved = manifestUrl.resolve(relativePath) ?: error("Audience rendition cannot be resolved")
        require(resolved.scheme == manifestUrl.scheme && resolved.host == manifestUrl.host && resolved.port == manifestUrl.port) {
            "Audience rendition escaped its grant origin"
        }
        return AudienceRendition(
            playlistUrl = resolved.toString(),
            mimeType = rendition.string("mime_type"),
            width = rendition["width"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
            height = rendition["height"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 },
        )
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.stringOrNull(name: String): String? = when (val value = this[name]) {
        null, JsonNull -> null
        else -> value.jsonPrimitive.contentOrNull
    }
    private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
    private fun JsonObject.positiveInt(name: String): Int = this[name]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
        ?: error("$name must be positive")
    private fun JsonObject.nonNegativeInt(name: String): Int = this[name]?.jsonPrimitive?.intOrNull?.takeIf { it >= 0 }
        ?: error("$name must not be negative")

    private fun String.toRuntimePhase(): AudienceRuntimePhase? = when (this) {
        "idle" -> AudienceRuntimePhase.Idle
        "joining" -> AudienceRuntimePhase.Joining
        "publishing" -> AudienceRuntimePhase.Publishing
        "live" -> AudienceRuntimePhase.Live
        "recovering" -> AudienceRuntimePhase.Recovering
        "leaving" -> AudienceRuntimePhase.Leaving
        "ended" -> AudienceRuntimePhase.Ended
        else -> null
    }
}

data class AudienceHttpResponse(
    val code: Int,
    val body: String,
) {
    val isSuccessful: Boolean
        get() = code in 200..299
}

class AudienceHttpException(
    val statusCode: Int,
    val responseBody: String,
    cause: Throwable? = null,
) : Exception("Audience request failed ($statusCode)", cause)

data class AudienceSession(
    val sessionId: String,
    val audienceClientId: String,
    val broadcastId: String,
    val generation: Int,
    val manifestUrl: String,
    val expiresAt: String,
    val heartbeatIntervalMs: Long,
)

data class AudienceHeartbeat(
    val active: Boolean,
    val phase: AudienceRuntimePhase,
    val generation: Int,
    val manifestRevision: Int,
    val expiresAt: String,
    val playback: AudiencePlaybackGrant?,
)

data class AudiencePlaybackGrant(
    val generation: Int,
    val manifestUrl: String,
    val expiresAt: String,
)

data class AudienceManifest(
    val broadcastId: String,
    val meetingInstanceId: String,
    val generation: Int,
    val revision: Int,
    val presentations: List<AudiencePresentation>,
)

data class AudiencePresentation(
    val presentationId: String,
    val kind: AudiencePresentationKind,
    val matrixUserId: String,
    val matrixDeviceId: String,
    val displayName: String,
    val avatarUrl: String?,
    val sourceId: String?,
    val audio: AudienceRendition?,
    val video: AudienceRendition?,
    val activeSpeaker: Boolean,
)

enum class AudiencePresentationKind { User, Screen }

data class AudienceRendition(
    val playlistUrl: String,
    val mimeType: String?,
    val width: Int?,
    val height: Int?,
)

private fun isAllowedAudienceRequest(
    method: String,
    path: String,
    body: JsonElement?,
    broadcastId: String,
): Boolean {
    val root = "/meeting-broadcast/v1/broadcasts/$broadcastId"
    if (method == "GET" && path == root && body == null) return true
    if (method == "POST" && path == "$root/audience-sessions") {
        val input = body as? JsonObject ?: return false
        return input.keys == setOf("audience_client_id") &&
            input["audience_client_id"]?.jsonPrimitive?.contentOrNull?.matches(AUDIENCE_CLIENT_ID_PATTERN) == true
    }

    val match = AUDIENCE_SESSION_PATH_PATTERN.matchEntire(path) ?: return false
    if (match.groupValues[1] != broadcastId) return false
    return when {
        method == "DELETE" && match.groupValues[3].isEmpty() -> body == null
        method == "PUT" && match.groupValues[3] == "/heartbeat" -> {
            val input = body as? JsonObject ?: return false
            input.keys == setOf("generation") &&
                input["generation"]?.jsonPrimitive?.intOrNull?.let { it >= 0 } == true
        }
        method == "POST" && match.groupValues[3] == "/webrtc/offer" -> body == null
        method == "POST" && match.groupValues[3] == "/webrtc/answer" -> {
            val input = body as? JsonObject ?: return false
            val answer = input["answer"] as? JsonObject ?: return false
            input.keys == setOf("receiver_session_id", "answer") &&
                !input["receiver_session_id"]?.jsonPrimitive?.contentOrNull.isNullOrBlank() &&
                answer.keys == setOf("type", "sdp") &&
                answer["type"]?.jsonPrimitive?.contentOrNull == "answer" &&
                !answer["sdp"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
        }
        method == "POST" && match.groupValues[3] == "/webrtc/commit" -> {
            val input = body as? JsonObject ?: return false
            input.keys == setOf("receiver_session_id") &&
                !input["receiver_session_id"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()
        }
        else -> false
    }
}

private val DISCOVERY_FIELDS = setOf("version", "broadcast_id", "meeting_instance_id", "access_mode")
private const val DISCOVERY_EVENT_TYPE = "org.unseal.meeting.broadcast"
private const val DISCOVERY_STATE_KEY = "m.call"
private val RUNTIME_STATUS_REQUIRED_FIELDS = setOf(
    "version",
    "broadcast_id",
    "room_id",
    "meeting_instance_id",
    "phase",
    "desired",
    "agent_in_meeting",
    "broadcast_armed",
    "playable",
    "generation",
    "manifest_revision",
    "participant_count",
    "listener_count",
    "presentations",
    "poll_after_ms",
)
private val RUNTIME_STATUS_FIELDS = RUNTIME_STATUS_REQUIRED_FIELDS + "error"
private val UUID_PATTERN = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
private val AUDIENCE_CLIENT_ID_PATTERN = Regex("^client_[A-Za-z0-9_-]+$")
private val AUDIENCE_SESSION_ID_PATTERN = Regex("^aud_[A-Za-z0-9_-]+$")
private val MXC_URL_PATTERN = Regex("^mxc://[^/?#\\s]+/[^/?#\\s]+$")
private val AUDIENCE_SESSION_PATH_PATTERN =
    Regex("^/meeting-broadcast/v1/broadcasts/(bcast_[A-Za-z0-9_-]+)/audience-sessions/(aud_[A-Za-z0-9_-]+)(/heartbeat|/webrtc/offer|/webrtc/answer|/webrtc/commit)?$")
private const val MIN_POLL_MS = 250L
private const val MAX_LIVE_POLL_MS = 1_000L
private const val MAX_POLL_MS = 5_000L
private const val MIN_HEARTBEAT_MS = 1_000L
private const val MIN_AVATAR_SIZE = 16
private const val MAX_AVATAR_SIZE = 256
private const val MAX_AVATAR_BYTES = 512 * 1024
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val LOCAL_API_HOSTS = setOf("127.0.0.1", "10.0.2.2", "localhost")
private val GRANT_MANIFEST_PATH_PATTERN = Regex("^/live/g/[^/]+/bcast_[A-Za-z0-9_-]+/[1-9][0-9]*/manifest\\.json$")
private val RELATIVE_PLAYLIST_PATH_PATTERN = Regex("^media/[A-Za-z0-9_-]+/[A-Za-z0-9_-]+/master\\.m3u8$")
private val MATRIX_USER_ID_PATTERN = Regex("^@.+:.+$")
private val AUDIENCE_SESSION_FIELDS = setOf(
    "session_id",
    "audience_client_id",
    "broadcast_id",
    "generation",
    "manifest_url",
    "expires_at",
    "heartbeat_interval_ms",
)
private val AUDIENCE_HEARTBEAT_FIELDS = setOf("active", "phase", "generation", "manifest_revision", "expires_at", "playback")
private val AUDIENCE_PLAYBACK_FIELDS = setOf("generation", "manifest_url", "expires_at")
private val AUDIENCE_MANIFEST_FIELDS = setOf(
    "version",
    "broadcast_id",
    "meeting_instance_id",
    "generation",
    "revision",
    "generated_at",
    "presentations",
)
private val AUDIENCE_PRESENTATION_REQUIRED_FIELDS = setOf(
    "presentation_id",
    "kind",
    "matrix_user_id",
    "matrix_device_id",
    "display_name",
    "audio",
    "video",
)
private val AUDIENCE_PRESENTATION_FIELDS = AUDIENCE_PRESENTATION_REQUIRED_FIELDS +
    setOf("avatar_url", "source_id", "active_speaker")
private val AUDIENCE_RENDITION_FIELDS = setOf("playlist_url", "mime_type", "width", "height")
