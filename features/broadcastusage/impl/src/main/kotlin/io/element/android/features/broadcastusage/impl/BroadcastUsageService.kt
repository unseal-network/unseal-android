/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.broadcastusage.impl

import dev.zacsweers.metro.Inject
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.matrix.api.MatrixClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.math.BigInteger

@Inject
class BroadcastUsageService(
    private val matrixClient: MatrixClient,
    private val baseUrlResolver: ChatbotBaseUrlResolver,
    private val okHttpClient: () -> OkHttpClient,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val client by lazy {
        okHttpClient().newBuilder().apply {
            interceptors().clear()
            networkInterceptors().clear()
        }.build()
    }

    suspend fun dashboard(limit: Int = 20, cursor: String? = null): BroadcastUsageDashboard =
        parseDashboard(get("/api/broadcast-usage", mapOf("limit" to limit.toString(), "cursor" to cursor)))

    suspend fun session(broadcastId: String): BroadcastUsageSession =
        parseSession(objectValue(get("/api/broadcast-usage/sessions/$broadcastId")))

    suspend fun history(limit: Int = 20, cursor: String? = null): BroadcastHistoryPage =
        parseHistory(get("/api/broadcast-usage/history", mapOf("limit" to limit.toString(), "cursor" to cursor)))

    suspend fun historyDetail(broadcastId: String): BroadcastHistoryItem =
        parseHistoryItem(objectValue(get("/api/broadcast-usage/history/$broadcastId")))

    suspend fun activity(limit: Int = 20, cursor: String? = null): BroadcastActivityPage =
        parseActivity(get("/api/broadcast-usage/activity", mapOf("limit" to limit.toString(), "cursor" to cursor)))

    suspend fun grants(): BroadcastGrantList = parseGrants(get("/api/broadcast-usage/grants"))

    suspend fun runtime(broadcastId: String): BroadcastRuntimeStatus =
        parseRuntime(get("/meeting-broadcast/v1/broadcasts/$broadcastId"))

    private suspend fun get(path: String, query: Map<String, String?> = emptyMap()): String {
        val base = baseUrlResolver.resolveUnsealApiBaseUrl(matrixClient.userIdServerName()).toHttpUrl()
        require(base.scheme == "https" || base.host in setOf("localhost", "127.0.0.1", "10.0.2.2")) { "Unseal API must use HTTPS" }
        require(base.username.isEmpty() && base.password.isEmpty()) { "Unseal API URL must not contain credentials" }
        val url = base.newBuilder().apply {
            path.removePrefix("/").split('/').filter(String::isNotEmpty).forEach(::addPathSegment)
            query.forEach { (key, value) -> if (value != null) addQueryParameter(key, value) }
        }.build()
        val token = matrixClient.currentAccessToken().getOrNull()?.takeIf(String::isNotBlank)
            ?: throw BroadcastUsageHttpException(401, "Matrix access token is unavailable")
        val request = Request.Builder().url(url).header("Accept", "application/json").header("Authorization", "Bearer $token").get().build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (!response.isSuccessful) throw BroadcastUsageHttpException(response.code, body)
                    body
                }
            } catch (failure: IOException) {
                throw BroadcastUsageHttpException(0, failure.message.orEmpty(), failure)
            }
        }
    }

    internal fun parseDashboard(raw: String): BroadcastUsageDashboard {
        val root = objectValue(raw)
        val sessions = root.obj("sessions")
        return BroadcastUsageDashboard(
            availableTrafficBytes = root.big("availableTrafficBytes"),
            funding = root.obj("funding").let { funding ->
                BroadcastUsageFunding(
                    grantBytes = funding.big("grantBytes"),
                    balanceMicros = funding.big("balanceMicros"),
                    effectiveBalanceMicros = funding.big("effectiveBalanceMicros"),
                    pendingBroadcastMicros = funding.big("pendingBroadcastMicros"),
                    pendingOtherUsageMicros = funding.big("pendingOtherUsageMicros"),
                    balanceEquivalentBytes = funding.big("balanceEquivalentBytes"),
                    pricePerBytePicos = funding.big("pricePerBytePicos"),
                    pricePerGbMicros = funding.big("pricePerGbMicros"),
                    bytesPerGb = funding.big("bytesPerGb"),
                )
            },
            pendingAllocationBytes = root.big("pendingAllocationBytes"),
            unallocatedTrafficBytes = root.big("unallocatedTrafficBytes"),
            calculatedAt = root.string("calculatedAt"),
            sessionCount = root.int("sessionCount"),
            sessions = sessions.array("items").map { parseSession(it as JsonObject) },
            nextCursor = sessions.nullableString("nextCursor"),
        )
    }

    internal fun parseHistory(raw: String): BroadcastHistoryPage {
        val root = objectValue(raw)
        return BroadcastHistoryPage(
            items = root.array("items").map { parseHistoryItem(it as JsonObject) },
            nextCursor = root.nullableString("nextCursor"),
        )
    }

    private fun parseHistoryItem(item: JsonObject): BroadcastHistoryItem {
        val traffic = item.obj("traffic")
        val audience = item.obj("audience")
        val billing = item.obj("billing")
        return BroadcastHistoryItem(
            sessionId = item.string("sessionId"),
            broadcastId = item.string("broadcastId"),
            roomId = item.string("roomId"),
            openedAt = item.string("openedAt"),
            closedAt = item.string("closedAt"),
            finalizedAt = item.nullableString("finalizedAt"),
            traffic = BroadcastHistoryTraffic(
                confirmedBytes = traffic.big("confirmedBytes"),
                grantCoveredBytes = traffic.big("grantCoveredBytes"),
                balanceCoveredBytes = traffic.big("balanceCoveredBytes"),
                pendingAllocationBytes = traffic.big("pendingAllocationBytes"),
            ),
            audience = BroadcastHistoryAudience(
                uniqueViewerCount = audience.nullableBig("uniqueViewerCount"),
                viewerSessionCount = audience.nullableBig("viewerSessionCount"),
                peakConcurrentViewers = audience.nullableBig("peakConcurrentViewers"),
                finalizedAt = audience.nullableString("finalizedAt"),
            ),
            billing = BroadcastHistoryBilling(
                costMicros = billing.nullableBig("costMicros"),
                pricePerBytePicos = billing.big("pricePerBytePicos"),
                chargedAt = billing.nullableString("chargedAt"),
            ),
        )
    }

    internal fun parseActivity(raw: String): BroadcastActivityPage {
        val root = objectValue(raw)
        return BroadcastActivityPage(
            items = root.array("items").map { element ->
                val item = element as JsonObject
                BroadcastTrafficActivity(
                    id = item.string("id"), type = item.string("type"), amountBytes = item.big("amountBytes"),
                    occurredAt = item.string("occurredAt"), reasonCode = item.string("reasonCode"), note = item.nullableString("note"),
                    sourceReference = item.string("sourceReference"),
                    broadcastId = item.nullableString("broadcastId"),
                )
            },
            nextCursor = root.nullableString("nextCursor"),
        )
    }

    internal fun parseGrants(raw: String): BroadcastGrantList {
        val root = objectValue(raw)
        return BroadcastGrantList(
            availableTrafficBytes = root.big("availableTrafficBytes"),
            items = root.array("items").map { element ->
                val item = element as JsonObject
                BroadcastTrafficGrant(
                    id = item.string("id"), source = item.string("source"), sourceReference = item.string("sourceReference"), status = item.string("status"),
                    originalBytes = item.big("originalBytes"), consumedBytes = item.big("consumedBytes"), remainingBytes = item.big("remainingBytes"),
                    validFrom = item.string("validFrom"), expiresAt = item.string("expiresAt"), reasonCode = item.string("reasonCode"),
                    note = item.nullableString("note"),
                    revokedAt = item.nullableString("revokedAt"), revocationReasonCode = item.nullableString("revocationReasonCode"),
                    createdAt = item.string("createdAt"), updatedAt = item.string("updatedAt"),
                )
            },
        )
    }

    internal fun parseRuntime(raw: String): BroadcastRuntimeStatus {
        val root = objectValue(raw)
        val presentations = root.obj("presentations")
        return BroadcastRuntimeStatus(
            phase = root.string("phase"), playable = root.bool("playable"), participantCount = root.int("participant_count"),
            listenerCount = root["listener_count"]?.jsonPrimitive?.intOrNull ?: 0,
            presentationTotal = presentations.int("total"), presentationHealthy = presentations.int("healthy"),
            pollAfterMs = root.stringOrNumber("poll_after_ms").toLong().coerceIn(1_000, 60_000),
        )
    }

    private fun parseSession(item: JsonObject): BroadcastUsageSession = BroadcastUsageSession(
        sessionId = item.string("sessionId"), broadcastId = item.string("broadcastId"), roomId = item.string("roomId"),
        meetingInstanceId = item.string("meetingInstanceId"), state = item.string("state").toSessionState(),
        confirmedBytes = item.big("confirmedBytes"), allocatedBytes = item.big("allocatedBytes"), unallocatedBytes = item.big("unallocatedBytes"),
        pendingAllocationBytes = item.big("pendingAllocationBytes"), syncedThrough = item.nullableString("syncedThrough"),
        openedAt = item.string("openedAt"), startedAt = item.nullableString("startedAt"), closedAt = item.nullableString("closedAt"),
        finalizedAt = item.nullableString("finalizedAt"), stopReason = item.nullableString("stopReason"),
    )

    private fun objectValue(raw: String) = json.parseToJsonElement(raw) as? JsonObject ?: error("Expected JSON object")
    private fun JsonObject.obj(name: String) = this[name] as? JsonObject ?: error("$name must be an object")
    private fun JsonObject.array(name: String) = this[name] as? JsonArray ?: error("$name must be an array")
    private fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.contentOrNull ?: error("$name must be a string")
    private fun JsonObject.stringOrNumber(name: String) = this[name]?.jsonPrimitive?.contentOrNull ?: error("$name must be present")
    private fun JsonObject.nullableString(name: String): String? = this[name]?.let { if (it.toString() == "null") null else it.jsonPrimitive.contentOrNull }
    private fun JsonObject.big(name: String) = string(name).let { value -> require(INTEGER.matches(value)) { "$name must be an integer" }; BigInteger(value) }
    private fun JsonObject.nullableBig(name: String): BigInteger? = nullableString(name)?.let { value ->
        require(INTEGER.matches(value)) { "$name must be an integer" }
        BigInteger(value)
    }
    private fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull ?: error("$name must be an integer")
    private fun JsonObject.bool(name: String) = this[name]?.jsonPrimitive?.booleanOrNull ?: error("$name must be a boolean")
    private fun String.toSessionState() = when (this) {
        "open" -> BroadcastUsageSessionState.Open
        "live" -> BroadcastUsageSessionState.Live
        "closed_syncing" -> BroadcastUsageSessionState.ClosedSyncing
        "finalized" -> BroadcastUsageSessionState.Finalized
        "failed" -> BroadcastUsageSessionState.Failed
        else -> error("Unknown broadcast usage state")
    }

    private companion object { val INTEGER = Regex("^-?\\d+$") }
}

internal class BroadcastUsageHttpException(val statusCode: Int, responseBody: String, cause: Throwable? = null) :
    Exception(if (statusCode == 0) "Network request failed" else "Request failed ($statusCode): ${responseBody.take(120)}", cause)
