/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.gameapi.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlayingRoom(
    /** Game room ID (Matrix room ID for the game session). */
    @SerialName("roomId") val roomId: String,
    /** Chat room ID (Matrix room ID the game was started in). */
    @SerialName("meetId") val meetId: String,
    @SerialName("gameAppId") val gameAppId: Int,
    @SerialName("isAdmin") val isAdmin: Boolean = false,
)
