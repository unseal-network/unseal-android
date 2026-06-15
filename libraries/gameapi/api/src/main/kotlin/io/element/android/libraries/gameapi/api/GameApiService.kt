/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.gameapi.api

interface GameApiService {
    /**
     * Fetches the list of available games.
     * GET {homeserver}/app-mgr/room/api/json?method=pkg.app.list&page={page}&size={size}&classify={classify}
     * Header: APP-U: s={homeserverHost}
     * classify = 3 for keepsecret.io, 2 otherwise.
     */
    suspend fun fetchAppList(page: Int = 1, size: Int = 20): Result<List<GameInfo>>

    /**
     * Fetches the list of game rooms the current user is playing in.
     * GET {homeserver}/app-mgr/room/api/rooms/my-playing?page={page}&limit={limit}
     * Headers: APP-U + UnsealToken.
     * Retries up to 3 times on 401.
     */
    suspend fun fetchMyPlaying(page: Int = 1, limit: Int = 10): Result<List<PlayingRoom>>

    /**
     * Creates a new game room for the given game in the given chat room.
     * POST {homeserver}/app-mgr/room/api/rooms/create
     * Body: {"appId": gameId, "meetId": meetRoomId}
     * Headers: APP-U + UnsealToken.
     * TODO: verify endpoint path against web utils/gameApi.ts createGameRoom.
     */
    suspend fun createGameRoom(gameId: Int, meetRoomId: String): Result<CreateGameRoomResult>

    /**
     * Fetches bundle information for a single game app, including the ZIP download URL
     * and load mode.
     *
     * GET {homeserver}/app-mgr/package/json?method=pkg.app.check.update&app_id={appId}
     * Header: APP-U: s={homeserverHost}
     *
     * Mirrors iOS `requestAppModelFromServer(appId:)` which calls `pkg.app.check.update`
     * and returns an `AppModel` used to decide between remote load and local ZIP bundle.
     */
    suspend fun fetchAppBundle(appId: Int): Result<AppBundleInfo>

    /**
     * Sends a game invite message to the chat room via the Matrix REST API.
     * PUT {homeserver}/_matrix/client/v3/rooms/{roomId}/send/m.room.message/{txnId}
     * Header: Authorization: Bearer {accessToken}
     */
    suspend fun sendGameInviteMessage(
        roomId: String,
        gameInfo: GameInfo,
        gameRoomId: String,
        creatorUserId: String,
    ): Result<Unit>
}
