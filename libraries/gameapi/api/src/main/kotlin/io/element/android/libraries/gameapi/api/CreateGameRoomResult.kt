/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.gameapi.api

data class CreateGameRoomResult(
    val gameRoomId: String,
    val gameInfo: GameInfo,
    val creatorUserId: String,
)
