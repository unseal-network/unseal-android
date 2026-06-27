/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.event

import androidx.compose.runtime.Immutable

/**
 * Timeline content for an Unseal game invite message (msgtype = "m.game.v1").
 *
 * The message structure (sent by [DefaultGameApiService.sendGameInviteMessage]):
 * ```json
 * {
 *   "msgtype": "m.game.v1",
 *   "type": "tool-output-available",
 *   "body": "Invite everyone to start a Chess game",
 *   "m.game.info": { "id": 1, "name": "Chess", "brief": "...", "icon": "..." },
 *   "m.game.roomid": "game-room-xyz",
 *   "m.game.creator": "@alice:matrix.example.com"
 * }
 * ```
 *
 * [resolvedIconUrl] has already been transformed through the sign-proxy URL resolution
 * (S3 URLs → homeserver sign endpoint), so Coil only needs the APP-U header interceptor.
 * [homeserverHost] (e.g. "matrix.example.com") is stored here so the Composable can build
 * the correct APP-U header without any extra DI wiring.
 */
@Immutable
data class TimelineItemGameContent(
    /** Display name of the game. */
    val gameName: String,
    /** Optional one-line description, shown below the name. */
    val gameBrief: String?,
    /**
     * Sign-proxy icon URL ready for Coil (null when no icon was provided).
     * e.g. "https://matrix.example.com/app-mgr/upload/sign?key=games%2Fchess.png"
     */
    val resolvedIconUrl: String?,
    /**
     * Homeserver host without scheme (e.g. "matrix.example.com").
     * Used to build the APP-U header when loading [resolvedIconUrl].
     */
    val homeserverHost: String?,
    /** Unseal game-server room ID (not a Matrix room ID). */
    val gameRoomId: String,
    /** Numeric game app ID in the Unseal game catalogue. */
    val gameId: Int,
    /**
     * WebView URL from `m.game.info.remote_url`.
     * Null for older messages that predate the remote_url field.
     */
    val remoteUrl: String?,
    /** Matrix user ID of the room creator. */
    val creatorUserId: String?,
    /** Fallback plain text body. */
    val fallbackBody: String,
) : TimelineItemEventContent {
    override val type: String = "m.game.v1"
}
