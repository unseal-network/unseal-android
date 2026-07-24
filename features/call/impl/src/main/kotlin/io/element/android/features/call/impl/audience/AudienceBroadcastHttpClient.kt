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
import java.net.URI

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
            // Matrix bearer credentials are attached only at the final request boundary.
            // Keep them out of application and network interceptors, including debug logging.
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
        val target = URI(path)
        require(target.scheme == null && target.rawAuthority == null && target.rawFragment == null) { "Invalid audience API target" }
        val pathBits = target.path.removePrefix("/").split("/").filter(String::isNotEmpty)
        val url = baseUrl.newBuilder().apply {
            pathBits.forEach(::addPathSegment)
            target.rawQuery
                ?.split("&")
                ?.map { parameter ->
                    val parts = parameter.split("=", limit = 2)
                    require(parts.size == 2) { "Invalid audience API query" }
                    parts[0] to parts[1]
                }
                ?.forEach { (name, value) -> addQueryParameter(name, value) }
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
                .header("Accept", if (isAudienceEventRequest(method, path, broadcastIdFromPath(path))) {
                    "text/event-stream"
                } else {
                    "application/json"
                })
                .method(method, requestBody),
        )
    }

    private suspend fun execute(sessionId: SessionId, builder: Request.Builder): AudienceHttpResponse {
        val matrixClient = matrixClientProvider.getOrRestore(sessionId).getOrThrow()
        val token = matrixClient.currentAccessToken().getOrNull()
            ?.takeIf(String::isNotBlank)
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

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
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
    if (method == "GET") {
        val eventMatch = AUDIENCE_EVENT_PATH_PATTERN.matchEntire(path) ?: return false
        return eventMatch.groupValues[1] == broadcastId
    }

    val match = AUDIENCE_SESSION_PATH_PATTERN.matchEntire(path) ?: return false
    if (match.groupValues[1] != broadcastId) return false
    return when {
        method == "DELETE" && match.groupValues[3].isEmpty() -> body == null
        method == "POST" && match.groupValues[3] == "/webrtc/offer" -> body == null
        method == "POST" && match.groupValues[3] == "/webrtc/renegotiate" -> body == null
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

private fun isAudienceEventRequest(method: String, path: String, broadcastId: String?): Boolean =
    method == "GET" &&
        broadcastId != null &&
        AUDIENCE_EVENT_PATH_PATTERN.matchEntire(path)?.groupValues?.get(1) == broadcastId

private fun broadcastIdFromPath(path: String): String? =
    AUDIENCE_BROADCAST_PATH_PATTERN.find(path)?.groupValues?.get(1)

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
    Regex("^/meeting-broadcast/v1/broadcasts/(bcast_[A-Za-z0-9_-]+)/audience-sessions/(aud_[A-Za-z0-9_-]+)(/webrtc/offer|/webrtc/renegotiate|/webrtc/answer|/webrtc/commit)?$")
private val AUDIENCE_EVENT_PATH_PATTERN =
    Regex("^/meeting-broadcast/v1/broadcasts/(bcast_[A-Za-z0-9_-]+)/audience-sessions/(aud_[A-Za-z0-9_-]+)/events\\?generation=(0|[1-9][0-9]*)&after_revision=(0|[1-9][0-9]*)$")
private val AUDIENCE_BROADCAST_PATH_PATTERN =
    Regex("^/meeting-broadcast/v1/broadcasts/(bcast_[A-Za-z0-9_-]+)(?:/|$)")
private const val MIN_POLL_MS = 250L
private const val MAX_LIVE_POLL_MS = 1_000L
private const val MAX_POLL_MS = 5_000L
private const val MIN_AVATAR_SIZE = 16
private const val MAX_AVATAR_SIZE = 256
private const val MAX_AVATAR_BYTES = 512 * 1024
private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private val LOCAL_API_HOSTS = setOf("127.0.0.1", "10.0.2.2", "localhost")
