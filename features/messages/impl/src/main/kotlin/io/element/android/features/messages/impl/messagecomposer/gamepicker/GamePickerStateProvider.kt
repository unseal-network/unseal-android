/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.gamepicker

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.gameapi.api.GameInfo
import io.element.android.libraries.gameapi.api.PlayingRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

class GamePickerStateProvider : PreviewParameterProvider<GamePickerState> {
    override val values: Sequence<GamePickerState>
        get() = sequenceOf(
            aGamePickerState(),
            aGamePickerState(allGames = null, myPlaying = null),
            aGamePickerState(creating = true),
            aGamePickerState(error = "Failed to create game room"),
            aGamePickerState(
                myPlaying = mapOf(
                    1 to persistentListOf(
                        aPlayingRoom(appId = 1, isAdmin = true),
                    ),
                    2 to persistentListOf(
                        aPlayingRoom(appId = 2),
                        aPlayingRoom(appId = 2, meetId = "meet-xyz-456"),
                    ),
                ).mapValues { it.value }.toImmutableMap(),
            ),
        )
}

internal fun aGamePickerState(
    allGames: ImmutableList<GameInfo>? = persistentListOf(
        aGameInfo(id = 1, name = "Chess"),
        aGameInfo(id = 2, name = "Poker"),
        aGameInfo(id = 3, name = "Tetris"),
        aGameInfo(id = 4, name = "Go"),
        aGameInfo(id = 5, name = "Word Games"),
    ),
    myPlaying: ImmutableMap<Int, ImmutableList<PlayingRoom>>? = persistentMapOf(),
    creating: Boolean = false,
    error: String? = null,
    homeserverHost: String? = null,
    shouldDismiss: Boolean = false,
    eventSink: (GamePickerEvent) -> Unit = {},
) = GamePickerState(
    allGames = allGames,
    myPlaying = myPlaying,
    creating = creating,
    error = error,
    homeserverHost = homeserverHost,
    shouldDismiss = shouldDismiss,
    eventSink = eventSink,
)

internal fun aGameInfo(
    id: Int = 1,
    name: String = "Chess",
    brief: String? = "Classic board game",
    icon: String? = null,
    remoteUrl: String? = null,
) = GameInfo(id = id, name = name, brief = brief, icon = icon, remoteUrl = remoteUrl)

internal fun aPlayingRoom(
    appId: Int = 1,
    roomId: String = "!gameroom:matrix.org",
    meetId: String = "meet-abc-123",
    isAdmin: Boolean = false,
) = PlayingRoom(roomId = roomId, meetId = meetId, gameAppId = appId, isAdmin = isAdmin)
