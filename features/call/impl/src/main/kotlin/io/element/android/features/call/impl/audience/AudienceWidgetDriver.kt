/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.audience

import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.widget.MatrixWidgetDriver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class AudienceWidgetDriver(
    override val id: String,
    private val sessionId: SessionId,
    private val broadcastId: String,
    private val httpClient: AudienceBroadcastHttpClient,
) : MatrixWidgetDriver {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableIncomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 10)
    override val incomingMessages = mutableIncomingMessages.asSharedFlow()

    override suspend fun run() = Unit

    override suspend fun send(message: String) {
        val request = runCatching { json.parseToJsonElement(message) as? JsonObject }.getOrNull() ?: return
        if (request.string("api") != "fromWidget" || request.string("widgetId") != id) return
        val requestId = request.string("requestId") ?: return
        val action = request.string("action") ?: return
        val response = when (action) {
            "supported_api_versions" -> buildJsonObject {
                put("supported_versions", buildJsonArray { add(JsonPrimitive("0.0.1")) })
            }
            "content_loaded" -> JsonObject(emptyMap())
            AUDIENCE_REQUEST_ACTION -> handleAudienceRequest(request["data"] as? JsonObject)
            else -> buildJsonObject {
                put("error", buildJsonObject {
                    put("message", "Unsupported widget action")
                })
            }
        }
        mutableIncomingMessages.emit(
            buildJsonObject {
                put("api", "toWidget")
                put("widgetId", id)
                put("requestId", requestId)
                put("action", action)
                put("response", response)
            }.toString()
        )
    }

    private suspend fun handleAudienceRequest(data: JsonObject?): JsonElement {
        if (data == null) return failureEnvelope(400, "invalid_audience_widget_request", "Invalid audience request")
        if (data.keys.any { it !in AUDIENCE_REQUEST_FIELDS }) {
            return failureEnvelope(400, "invalid_audience_widget_request", "Invalid audience request")
        }
        val method = data.string("method") ?: return failureEnvelope(400, "invalid_audience_widget_request", "Missing method")
        val path = data.string("path") ?: return failureEnvelope(400, "invalid_audience_widget_request", "Missing path")
        return try {
            httpClient.requestAudienceWidget(
                sessionId = sessionId,
                broadcastId = broadcastId,
                method = method,
                path = path,
                body = data["body"]?.takeUnless { it is JsonNull },
            ).let { response ->
                if (!response.isSuccessful) {
                    val error = parseServerError(response.body)
                    return failureEnvelope(
                        status = response.code,
                        code = error?.string("code") ?: "unknown",
                        message = error?.string("message") ?: "Audience request failed",
                        retryable = error?.get("retryable")?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == true,
                        details = error?.get("details"),
                    )
                }
                buildJsonObject {
                    put("ok", true)
                    put(
                        "response",
                        response.body.takeIf(String::isNotBlank)
                            ?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() }
                            ?: JsonNull,
                    )
                }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: IllegalArgumentException) {
            failureEnvelope(
                status = 400,
                code = "invalid_audience_widget_request",
                message = "Invalid audience request",
            )
        } catch (failure: Throwable) {
            failureEnvelope(
                status = (failure as? AudienceHttpException)?.statusCode ?: 0,
                code = "audience_transport_unavailable",
                message = "Audience service is unavailable",
                retryable = true,
            )
        }
    }

    private fun parseServerError(raw: String): JsonObject? {
        val root = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        return root?.get("error") as? JsonObject
    }

    private fun failureEnvelope(
        status: Int,
        code: String,
        message: String,
        retryable: Boolean = false,
        details: JsonElement? = null,
    ): JsonObject = buildJsonObject {
        put("ok", false)
        put("error", buildJsonObject {
            put("status", status)
            put("code", code)
            put("message", message)
            put("retryable", retryable)
            put("details", details ?: JsonNull)
        })
    }

    override fun close() = Unit

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
}

private const val AUDIENCE_REQUEST_ACTION = "io.element.unseal.meeting_broadcast_request"
private val AUDIENCE_REQUEST_FIELDS = setOf("method", "path", "body")
