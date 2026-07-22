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
import java.util.Base64

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
                put("supported_versions", buildJsonArray {
                    add(JsonPrimitive("0.0.1"))
                    add(JsonPrimitive(MSC4039_API_VERSION))
                })
            }
            "content_loaded" -> JsonObject(emptyMap())
            AUDIENCE_REQUEST_ACTION -> handleAudienceRequest(request["data"] as? JsonObject)
            DOWNLOAD_FILE_ACTION -> handleDownloadFile(request["data"] as? JsonObject)
            else -> buildJsonObject {
                put("error", buildJsonObject {
                    put("message", "Unsupported widget action")
                })
            }
        }
        mutableIncomingMessages.emit(
            buildJsonObject {
                // matrix-widget-api matches responses against the request transport
                // direction. Widget-originated requests and their responses both use
                // `fromWidget`; using `toWidget` makes the SDK silently discard the
                // response and eventually time out during content_loaded.
                put("api", "fromWidget")
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

    private suspend fun handleDownloadFile(data: JsonObject?): JsonElement {
        val mxcUrl = data
            ?.takeIf { it.keys == setOf("content_uri") }
            ?.string("content_uri")
            ?: return widgetError("Invalid Matrix media request")
        return try {
            val bytes = httpClient.loadAudienceThumbnail(
                sessionId = sessionId,
                mxcUrl = mxcUrl,
                size = AVATAR_THUMBNAIL_SIZE,
            )
            val mimeType = bytes.imageMimeType()
                ?: return widgetError("Unsupported Matrix media type")
            buildJsonObject {
                put("file", "data:$mimeType;base64,${Base64.getEncoder().encodeToString(bytes)}")
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: Throwable) {
            widgetError("Unable to load Matrix media")
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

    private fun widgetError(message: String): JsonObject = buildJsonObject {
        put("error", buildJsonObject {
            put("message", message)
        })
    }

    override fun close() = Unit

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
}

private const val AUDIENCE_REQUEST_ACTION = "io.element.unseal.meeting_broadcast_request"
private const val DOWNLOAD_FILE_ACTION = "org.matrix.msc4039.download_file"
private const val MSC4039_API_VERSION = "org.matrix.msc4039"
private const val AVATAR_THUMBNAIL_SIZE = 96
private val AUDIENCE_REQUEST_FIELDS = setOf("method", "path", "body")

private fun ByteArray.imageMimeType(): String? = when {
    size >= 8 &&
        this[0] == 0x89.toByte() &&
        this[1] == 0x50.toByte() &&
        this[2] == 0x4E.toByte() &&
        this[3] == 0x47.toByte() &&
        this[4] == 0x0D.toByte() &&
        this[5] == 0x0A.toByte() &&
        this[6] == 0x1A.toByte() &&
        this[7] == 0x0A.toByte() -> "image/png"
    size >= 3 &&
        this[0] == 0xFF.toByte() &&
        this[1] == 0xD8.toByte() &&
        this[2] == 0xFF.toByte() -> "image/jpeg"
    size >= 6 && decodeToString(0, 6) in setOf("GIF87a", "GIF89a") -> "image/gif"
    size >= 12 && decodeToString(0, 4) == "RIFF" && decodeToString(8, 12) == "WEBP" -> "image/webp"
    else -> null
}
