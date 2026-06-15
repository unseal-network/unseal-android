/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.roomMembers

@SingleIn(RoomScope::class)
@Inject
class RoomUnsealContextLoader(
    private val room: JoinedRoom,
    private val roomUnsealDataClient: RoomUnsealDataClient,
) {
    suspend fun load(): RoomUnsealContext {
        if (room.membersStateFlow.value.roomMembers().isNullOrEmpty()) {
            room.updateMembers()
        }
        val members = room.membersStateFlow.value.roomMembers().orEmpty()
        val snapshot = roomUnsealDataClient.loadRoomData(room.roomId)
        return RoomUnsealContext.from(room.roomId, members, snapshot)
    }
}
