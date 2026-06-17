/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.gamepicker

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.gameapi.api.CreateGameRoomResult
import io.element.android.libraries.gameapi.api.GameInfo
import io.element.android.libraries.gameapi.api.PlayingRoom
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class GamePickerPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - loads app list and my playing rooms`() = runTest {
        val game = aGameInfo(id = 7, name = "Chess")
        val room = aPlayingRoom(gameAppId = 7, meetId = "meet-a")
        val service = FakeGameApiService().apply {
            fetchAppListResult = Result.success(listOf(game))
            fetchMyPlayingResult = Result.success(listOf(room))
        }
        createPresenter(service = service).test {
            awaitItem()

            val loaded = awaitState { it.allGames != null && it.myPlaying != null }
            assertThat(loaded.allGames).containsExactly(game)
            assertThat(loaded.myPlaying).containsExactly(7, listOf(room))
            assertThat(loaded.homeserverHost).isEqualTo("keepsecret.io")
            assertThat(service.fetchAppListCalls).containsExactly(1 to 20)
            assertThat(service.fetchMyPlayingCalls).containsExactly(1 to 10)
        }
    }

    @Test
    fun `present - create game sends invite before dismissing`() = runTest {
        val game = aGameInfo(id = 42, name = "Wolf")
        val createdGame = game.copy(remoteUrl = "https://keepsecret.io/game/42")
        val service = FakeGameApiService().apply {
            fetchAppListResult = Result.success(listOf(game))
            createGameRoomResult = { gameId, meetRoomId ->
                Result.success(
                    CreateGameRoomResult(
                        gameRoomId = "game-room-$gameId",
                        gameInfo = createdGame,
                        creatorUserId = "@alice:keepsecret.io",
                    )
                )
            }
        }
        createPresenter(service = service).test {
            val initial = awaitItem()
            val loaded = awaitState { it.allGames != null && it.myPlaying != null }

            assertThat(initial.shouldDismiss).isFalse()
            loaded.eventSink(GamePickerEvent.CreateGame(game))

            val finished = awaitState { it.shouldDismiss }
            assertThat(finished.creating).isFalse()
            assertThat(service.createGameRoomCalls).containsExactly(42 to A_ROOM_ID.value)
            assertThat(service.sendGameInviteMessageCalls).containsExactly(
                SendGameInviteMessageCall(
                    roomId = A_ROOM_ID.value,
                    gameInfo = createdGame,
                    gameRoomId = "game-room-42",
                    creatorUserId = "@alice:keepsecret.io",
                )
            )
        }
    }

    @Test
    fun `present - send invite failure keeps sheet open and exposes error`() = runTest {
        val game = aGameInfo(id = 12, name = "Puzzle")
        val service = FakeGameApiService().apply {
            fetchAppListResult = Result.success(listOf(game))
            sendGameInviteMessageResult = Result.failure(IllegalStateException("invite failed"))
        }
        createPresenter(service = service).test {
            val loaded = awaitState { it.allGames != null && it.myPlaying != null }

            loaded.eventSink(GamePickerEvent.CreateGame(game))

            val failed = awaitState { it.error == "invite failed" }
            assertThat(failed.shouldDismiss).isFalse()
            assertThat(failed.creating).isFalse()
            assertThat(service.sendGameInviteMessageCalls).hasSize(1)

            failed.eventSink(GamePickerEvent.DismissError)
            assertThat(awaitState { it.error == null }.shouldDismiss).isFalse()
        }
    }

    @Test
    fun `present - enter playing room navigates with game remote url and meet id`() = runTest {
        val game = aGameInfo(id = 8, name = "Sudoku", remoteUrl = "https://keepsecret.io/apps/sudoku")
        val room = aPlayingRoom(gameAppId = 8, meetId = "meet-room")
        val service = FakeGameApiService().apply {
            fetchAppListResult = Result.success(listOf(game))
            fetchMyPlayingResult = Result.success(listOf(room))
        }
        val navigations = mutableListOf<Navigation>()
        createPresenter(
            service = service,
            onNavigateToMiniApp = { appId, remoteUrl, meetId ->
                navigations += Navigation(appId, remoteUrl, meetId)
            },
        ).test {
            val loaded = awaitState { it.allGames != null && it.myPlaying != null }

            loaded.eventSink(GamePickerEvent.EnterPlayingRoom(appId = 8, room = room))

            assertThat(navigations).containsExactly(
                Navigation(
                    appId = 8L,
                    remoteUrl = "https://keepsecret.io/apps/sudoku",
                    meetId = "meet-room",
                )
            )
        }
    }

    private fun createPresenter(
        service: FakeGameApiService = FakeGameApiService(),
        onNavigateToMiniApp: (appId: Long, remoteUrl: String?, meetId: String) -> Unit = { _, _, _ -> },
    ): GamePickerPresenter {
        val provider = FakeRoomGameApiServiceProvider(
            Result.success(
                RoomGameApiServiceHandle(
                    service = service,
                    homeserverHost = "keepsecret.io",
                )
            )
        )
        return GamePickerPresenter(
            room = FakeJoinedRoom(),
            gameApiServiceProvider = provider,
            onNavigateToMiniApp = onNavigateToMiniApp,
        )
    }
}

private suspend fun app.cash.turbine.TurbineTestContext<GamePickerState>.awaitState(
    predicate: (GamePickerState) -> Boolean,
): GamePickerState {
    while (true) {
        val state = awaitItem()
        if (predicate(state)) return state
    }
}

private fun aGameInfo(
    id: Int = 1,
    name: String = "Game",
    remoteUrl: String? = null,
) = GameInfo(
    id = id,
    name = name,
    remoteUrl = remoteUrl,
)

private fun aPlayingRoom(
    roomId: String = "!game:keepsecret.io",
    meetId: String = "!meet:keepsecret.io",
    gameAppId: Int = 1,
) = PlayingRoom(
    roomId = roomId,
    meetId = meetId,
    gameAppId = gameAppId,
)

private data class Navigation(
    val appId: Long,
    val remoteUrl: String?,
    val meetId: String,
)
