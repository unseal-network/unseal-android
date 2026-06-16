/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.gamepicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.gameapi.api.GameInfo
import io.element.android.libraries.gameapi.api.PlayingRoom
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.launch
import timber.log.Timber

@AssistedInject
class GamePickerPresenter(
    private val room: JoinedRoom,
    private val gameApiServiceProvider: RoomGameApiServiceProvider,
    @Assisted private val onNavigateToMiniApp: (appId: Long, remoteUrl: String?, meetId: String) -> Unit,
) : Presenter<GamePickerState> {

    @AssistedFactory
    interface Factory {
        fun create(onNavigateToMiniApp: (appId: Long, remoteUrl: String?, meetId: String) -> Unit): GamePickerPresenter
    }

    @Composable
    override fun present(): GamePickerState {
        val coroutineScope = rememberCoroutineScope()

        var allGames by remember { mutableStateOf<ImmutableList<GameInfo>?>(null) }
        var myPlaying by remember { mutableStateOf<ImmutableMap<Int, ImmutableList<PlayingRoom>>?>(null) }
        var creating by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var shouldDismiss by remember { mutableStateOf(false) }

        var gameApiHandle by remember { mutableStateOf<RoomGameApiServiceHandle?>(null) }
        LaunchedEffect(Unit) {
            gameApiServiceProvider.create()
                .onSuccess { gameApiHandle = it }
                .onFailure { cause ->
                    Timber.e(cause, "GamePicker: failed to resolve homeserver game API")
                    allGames = persistentListOf()
                    myPlaying = persistentMapOf()
                    error = cause.message ?: "Failed to load games"
                }
        }

        // Load games and my-playing rooms as soon as we have the homeserver game API.
        // On failure, fall through to empty lists so the UI exits the loading state.
        LaunchedEffect(gameApiHandle) {
            val service = gameApiHandle?.service ?: return@LaunchedEffect

            launch {
                service.fetchAppList()
                    .onSuccess { allGames = it.toImmutableList() }
                    .onFailure { cause ->
                        Timber.e(cause, "GamePicker: failed to fetch app list")
                        allGames = persistentListOf() // exit loading state
                        error = cause.message ?: "Failed to load games"
                    }
            }
            launch {
                service.fetchMyPlaying()
                    .onSuccess { rooms ->
                        myPlaying = rooms
                            .groupBy { it.gameAppId }
                            .mapValues { (_, v) -> v.toImmutableList() }
                            .toImmutableMap()
                    }
                    .onFailure { cause ->
                        Timber.e(cause, "GamePicker: failed to fetch my-playing")
                        myPlaying = persistentMapOf() // exit loading → hide section
                    }
            }
        }

        fun handleEvent(event: GamePickerEvent) {
            when (event) {
                GamePickerEvent.Dismiss -> Unit // handled by caller

                is GamePickerEvent.CreateGame -> {
                    val service = gameApiHandle?.service ?: return
                    if (creating) return
                    creating = true
                    coroutineScope.launch {
                        try {
                            service.createGameRoom(event.game.id, room.roomId.value)
                                .onSuccess { result ->
                                    service.sendGameInviteMessage(
                                        roomId = room.roomId.value,
                                        gameInfo = result.gameInfo,
                                        gameRoomId = result.gameRoomId,
                                        creatorUserId = result.creatorUserId,
                                    ).onSuccess {
                                        // Signal the UI to close the sheet NOW that all async work is done.
                                        // We must not dismiss before this point: the rememberCoroutineScope
                                        // is cancelled when the composition tears down, which would kill this
                                        // coroutine and prevent the message from ever being sent.
                                        shouldDismiss = true
                                    }.onFailure { cause ->
                                        Timber.e(cause, "GamePicker: failed to send game invite message")
                                        error = cause.message ?: "Failed to send game invite"
                                    }
                                }
                                .onFailure { cause ->
                                    Timber.e(cause, "GamePicker: failed to create game room")
                                    error = cause.message ?: "Failed to create game room"
                                }
                        } finally {
                            creating = false
                        }
                    }
                }

                is GamePickerEvent.EnterPlayingRoom -> {
                    val gameInfo = allGames?.find { it.id == event.appId }
                    onNavigateToMiniApp(event.appId.toLong(), gameInfo?.remoteUrl, event.room.meetId)
                }

                GamePickerEvent.DismissError -> error = null
            }
        }

        return GamePickerState(
            allGames = allGames,
            myPlaying = myPlaying,
            creating = creating,
            error = error,
            homeserverHost = gameApiHandle?.homeserverHost,
            shouldDismiss = shouldDismiss,
            eventSink = ::handleEvent,
        )
    }
}
