/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption.roomkey

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import java.util.concurrent.ConcurrentHashMap

class MemberAwareRoomKeyForwardingPolicy : RoomKeyForwardingPolicy {
    private val activeMemberIdsByRoom = ConcurrentHashMap<RoomId, Set<UserId>>()

    fun updateRoomMembers(roomId: RoomId, activeMemberIds: Set<UserId>) {
        if (activeMemberIds.isEmpty()) {
            activeMemberIdsByRoom.remove(roomId)
        } else {
            activeMemberIdsByRoom[roomId] = activeMemberIds
        }
    }

    override fun allowForwarding(authorization: RoomKeyForwardingAuthorization): RoomKeyForwardingDecision {
        val activeMemberIds = activeMemberIdsByRoom[authorization.roomId].orEmpty()
        return if (authorization.requesterUserId in activeMemberIds) {
            RoomKeyForwardingDecision(allow = true)
        } else {
            RoomKeyForwardingDecision(
                allow = false,
                reason = "Requester is not an active member of the room",
            )
        }
    }
}
