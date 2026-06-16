/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.gamepicker

import io.element.android.libraries.gameapi.api.AppBundleInfo
import io.element.android.libraries.gameapi.api.CreateGameRoomResult
import io.element.android.libraries.gameapi.api.GameApiService
import io.element.android.libraries.gameapi.api.GameInfo
import io.element.android.libraries.gameapi.api.PlayingRoom

class FakeRoomGameApiServiceProvider(
    var result: Result<RoomGameApiServiceHandle> = Result.success(
        RoomGameApiServiceHandle(
            service = FakeGameApiService(),
            homeserverHost = "keepsecret.io",
        )
    ),
) : RoomGameApiServiceProvider {
    override suspend fun create(): Result<RoomGameApiServiceHandle> = result
}

class FakeGameApiService : GameApiService {
    var fetchAppListResult: Result<List<GameInfo>> = Result.success(emptyList())
    var fetchMyPlayingResult: Result<List<PlayingRoom>> = Result.success(emptyList())
    var fetchAppBundleResult: (Int) -> Result<AppBundleInfo> = { appId ->
        Result.success(
            AppBundleInfo(
                appId = appId,
                version = "1.0.0",
                loadMode = AppBundleInfo.LoadMode.Remote,
                remoteUrl = "https://keepsecret.io/apps/$appId",
                zipUrl = null,
            )
        )
    }
    var createGameRoomResult: (Int, String) -> Result<CreateGameRoomResult> = { gameId, _ ->
        Result.success(
            CreateGameRoomResult(
                gameRoomId = "game-room",
                gameInfo = GameInfo(id = gameId, name = "Game $gameId"),
                creatorUserId = "@alice:example.org",
            )
        )
    }
    var sendGameInviteMessageResult: Result<Unit> = Result.success(Unit)

    override suspend fun fetchAppList(page: Int, size: Int): Result<List<GameInfo>> = fetchAppListResult

    override suspend fun fetchMyPlaying(page: Int, limit: Int): Result<List<PlayingRoom>> = fetchMyPlayingResult

    override suspend fun fetchAppBundle(appId: Int): Result<AppBundleInfo> = fetchAppBundleResult(appId)

    override suspend fun createGameRoom(gameId: Int, meetRoomId: String): Result<CreateGameRoomResult> = createGameRoomResult(gameId, meetRoomId)

    override suspend fun sendGameInviteMessage(
        roomId: String,
        gameInfo: GameInfo,
        gameRoomId: String,
        creatorUserId: String,
    ): Result<Unit> = sendGameInviteMessageResult
}
