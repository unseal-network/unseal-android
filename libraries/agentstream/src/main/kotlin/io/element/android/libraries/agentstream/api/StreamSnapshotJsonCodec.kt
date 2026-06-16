/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.agentstream.api

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class StreamSnapshotJsonCodec(
    private val parser: StreamSnapshotParser = StreamSnapshotParser(),
) {
    fun encode(snapshot: StreamSnapshot): String {
        val encoded = buildJsonObject {
            put("schemaVersion", snapshot.schemaVersion)
            put("streamId", snapshot.streamId)
            put("status", snapshot.status.wireValue)
            put("updatedAtMs", snapshot.updatedAtMs)
            snapshot.completedAtMs?.let { put("completedAtMs", it) }
            snapshot.error?.let { put("error", it.toJsonElement()) }
            put("parts", buildJsonArray {
                snapshot.parts.forEach { add(it.toJsonElement()) }
            })
            put("rawEvents", buildJsonArray {
                snapshot.rawEvents.forEach { add(it.toJsonElement()) }
            })
        }
        return Json.encodeToString(encoded)
    }

    fun decode(value: String): StreamSnapshot = parser.parse(value).normalizedForTerminalState()

    private fun StreamPart.toJsonElement(): JsonElement {
        return buildJsonObject {
            put("id", id)
            put("type", type)
            state?.let { put("state", it) }
            when (val part = this@toJsonElement) {
                is StreamPart.Text -> {
                    put("text", part.text)
                }
                is StreamPart.Reasoning -> {
                    put("text", part.text)
                }
                is StreamPart.Tool -> {
                    put("toolState", part.toolState)
                    part.toolName?.let { put("toolName", it) }
                    part.toolCallId?.let { put("toolCallId", it) }
                    part.input?.let { put("input", it) }
                    part.rawInput?.let { put("rawInput", it) }
                    part.output?.let { put("output", it) }
                    part.error?.let { put("error", it.toJsonElement()) }
                    part.title?.let { put("title", it) }
                }
                is StreamPart.Data -> {
                    put("data", part.data)
                }
                is StreamPart.Source -> {
                    part.sourceType?.let { put("sourceType", it) }
                    part.title?.let { put("title", it) }
                    part.url?.let { put("url", it) }
                    part.payload?.let { put("payload", it) }
                }
                is StreamPart.File -> {
                    part.mediaType?.let { put("mediaType", it) }
                    part.filename?.let { put("filename", it) }
                    part.url?.let { put("url", it) }
                    part.data?.let { put("data", it) }
                }
                is StreamPart.Step -> {
                    part.title?.let { put("title", it) }
                    part.payload?.let { put("payload", it) }
                }
                is StreamPart.Error -> {
                    put("error", part.error.toJsonElement())
                }
                is StreamPart.Custom -> {
                    put("payload", part.payload)
                }
            }
        }
    }

    private fun RawStreamEvent.toJsonElement(): JsonElement {
        return buildJsonObject {
            put("sequence", sequence)
            put("eventType", eventType)
            partId?.let { put("partId", it) }
            put("payload", payload)
            put("receivedAtMs", receivedAtMs)
        }
    }

    private fun StreamError.toJsonElement(): JsonElement {
        return buildJsonObject {
            put("message", message)
            code?.let { put("code", it) }
            raw?.let { put("raw", it) }
        }
    }
}

private val StreamStatus.wireValue: String
    get() = when (this) {
        StreamStatus.Idle -> "idle"
        StreamStatus.Loading -> "loading"
        StreamStatus.Streaming -> "streaming"
        StreamStatus.Completed -> "completed"
        StreamStatus.Failed -> "failed"
        StreamStatus.Cancelled -> "cancelled"
    }
