/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.gamepicker

import io.element.android.libraries.gameapi.api.GameInfo
import io.element.android.libraries.gameapi.api.PlayingRoom

sealed interface GamePickerEvent {
    /** User dismissed the picker without selecting a game. */
    data object Dismiss : GamePickerEvent

    /** User tapped a game from the "all games" list — creates a new room. */
    data class CreateGame(val game: GameInfo) : GamePickerEvent

    /** User tapped an existing playing room to re-enter it. */
    data class EnterPlayingRoom(val appId: Int, val room: PlayingRoom) : GamePickerEvent

    /** Dismiss the error snackbar. */
    data object DismissError : GamePickerEvent
}
