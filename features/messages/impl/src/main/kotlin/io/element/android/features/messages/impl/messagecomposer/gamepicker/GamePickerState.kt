/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer.gamepicker

import io.element.android.libraries.gameapi.api.GameInfo
import io.element.android.libraries.gameapi.api.PlayingRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

data class GamePickerState(
    /** All available games. Null = loading, empty = loaded but none. */
    val allGames: ImmutableList<GameInfo>?,
    /** My active game rooms, grouped by appId. Null = loading. */
    val myPlaying: ImmutableMap<Int, ImmutableList<PlayingRoom>>?,
    /** True while a game room is being created. */
    val creating: Boolean,
    /** Non-null when an error should be shown to the user. */
    val error: String?,
    /**
     * Homeserver host (no scheme, no trailing slash), e.g. "matrix.example.com".
     * Used by the UI to build the `APP-U: s=<host>` header for icon image requests
     * going through the homeserver sign proxy.
     */
    val homeserverHost: String?,
    /**
     * Flips to true once a game room has been created and the invite message sent
     * successfully. The UI observes this to close the sheet. Using a flag rather than
     * dispatching Dismiss from the presenter keeps the coroutine alive until the work
     * is fully done (rememberCoroutineScope is cancelled when the composition tears down,
     * so we must NOT dismiss the sheet until AFTER the async work succeeds).
     */
    val shouldDismiss: Boolean,
    val eventSink: (GamePickerEvent) -> Unit,
)
