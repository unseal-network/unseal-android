/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.encryption.roomkey

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

data class AgentRoomKeyRecoveryTarget(
    val userId: UserId,
    val deviceId: String,
)

data class AgentRoomKeyRecoveryRequest(
    val roomId: RoomId,
    val senderUserId: UserId,
    val senderDeviceId: String,
    val senderKey: String,
    val sessionId: String,
) {
    val target: AgentRoomKeyRecoveryTarget = AgentRoomKeyRecoveryTarget(senderUserId, senderDeviceId)
    val identityKey: String = listOf(roomId.value, senderUserId.value, senderDeviceId, senderKey, sessionId).joinToString("|")

    fun encodedRoomKeyRequestContent(
        requestingDeviceId: String,
        requestId: String = UUID.randomUUID().toString().lowercase(),
    ): String {
        return buildJsonObject {
            put("action", "request")
            put("request_id", requestId)
            put("requesting_device_id", requestingDeviceId)
            put(
                "body",
                buildJsonObject {
                    put("algorithm", ALGORITHM)
                    put("room_id", roomId.value)
                    put("sender_key", senderKey)
                    put("session_id", sessionId)
                }
            )
        }.toString()
    }

    companion object {
        const val EVENT_TYPE = "m.room_key_request"
        const val ALGORITHM = "m.megolm.v1.aes-sha2"
    }
}
