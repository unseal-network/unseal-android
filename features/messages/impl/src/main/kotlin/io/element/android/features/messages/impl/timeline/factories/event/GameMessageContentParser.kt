/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import dev.zacsweers.metro.Inject
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGameContent
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Parses an Unseal game invite message (msgtype = "m.game.v1") from a Matrix event's
 * original JSON.
 *
 * Mirrors the web [GameMessageFactory]: detects the custom msgtype and extracts
 * [TimelineItemGameContent] fields from the custom `m.game.info`, `m.game.roomid`, and
 * `m.game.creator` keys.  Returns null for all non-game events so callers can fall
 * through to normal rendering.
 *
 * Icon URL resolution mirrors [DefaultGameApiService.resolveIconUrl]:
 * S3 assets (unseal-app.s3.eu-west-1.amazonaws.com/...) are rewritten to the homeserver
 * sign proxy endpoint which returns the image bytes directly.  The homeserver host is
 * derived from the event sender's Matrix user ID (server part after ":").
 */
@Inject
class GameMessageContentParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        originalJson: String?,
        senderUserId: UserId,
    ): TimelineItemGameContent? {
        val raw = originalJson?.takeIf { it.isNotBlank() } ?: return null
        val root = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
        val content = root["content"] as? JsonObject ?: return null

        // Only handle m.game.v1 messages
        if (content.string("msgtype") != MSGTYPE_GAME) return null

        val gameInfoObj = content["m.game.info"] as? JsonObject
        val gameRoomId = content.string("m.game.roomid") ?: return null
        val fallbackBody = content.string("body").orEmpty()

        // Derive homeserver URL from the sender's Matrix user ID (e.g. "@alice:matrix.example.com")
        val homeserverHost = senderUserId.value
            .substringAfterLast(':')
            .takeIf { it.isNotBlank() }
        val homeserverUrl = homeserverHost?.let { "https://$it" }

        val rawIconUrl = gameInfoObj?.string("icon")
        val resolvedIconUrl = if (rawIconUrl != null && homeserverUrl != null) {
            resolveIconUrl(rawIconUrl, homeserverUrl)
        } else {
            rawIconUrl
        }

        return TimelineItemGameContent(
            gameName = gameInfoObj?.string("name") ?: fallbackBody.ifBlank { "Game" },
            gameBrief = gameInfoObj?.string("brief")?.takeIf { it.isNotBlank() },
            resolvedIconUrl = resolvedIconUrl,
            homeserverHost = homeserverHost,
            gameRoomId = gameRoomId,
            gameId = gameInfoObj?.int("id") ?: 0,
            creatorUserId = content.string("m.game.creator"),
            fallbackBody = fallbackBody,
        )
    }

    /**
     * Mirrors [DefaultGameApiService.resolveIconUrl]:
     * - Relative paths are prepended with [homeserverUrl].
     * - S3 URLs (unseal-app.s3.eu-west-1.amazonaws.com) are rewritten to the homeserver sign
     *   proxy endpoint which returns the image bytes when called with an APP-U header.
     * - All other absolute URLs are returned as-is.
     */
    private fun resolveIconUrl(raw: String, homeserverUrl: String): String? {
        if (raw.isBlank()) return null
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            return "$homeserverUrl${if (raw.startsWith("/")) raw else "/$raw"}"
        }
        val s3Key = when {
            raw.startsWith(S3_PREFIX_HTTPS) -> raw.removePrefix(S3_PREFIX_HTTPS)
            raw.startsWith(S3_PREFIX_HTTP) -> raw.removePrefix(S3_PREFIX_HTTP)
            else -> return raw
        }
        val encodedKey = java.net.URLEncoder.encode(s3Key, "UTF-8")
        return "$homeserverUrl/app-mgr/upload/sign?key=$encodedKey"
    }

    // ── JSON helpers ────────────────────────────────────────────────────────

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.int(key: String): Int? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()

    private companion object {
        const val MSGTYPE_GAME = "m.game.v1"
        const val S3_PREFIX_HTTPS = "https://unseal-app.s3.eu-west-1.amazonaws.com/"
        const val S3_PREFIX_HTTP = "http://unseal-app.s3.eu-west-1.amazonaws.com/"
    }
}
