/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class RoomKeyRecoveryRequestParser {
    fun parse(
        originalJson: String?,
        fallbackRoomId: RoomId? = null,
        fallbackSenderId: UserId? = null,
        fallbackSessionId: String? = null,
    ): RoomKeyRecoveryRequest? {
        val root = originalJson
            ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: return null
        val content = root.jsonObjectOrNull("content") ?: return null
        if (content.optionalString("algorithm") != MEGOLM_ALGORITHM) return null

        val roomId = root.requiredString("room_id")?.let(::RoomId) ?: fallbackRoomId ?: return null
        val senderUserId = root.requiredString("sender")?.let(::UserId) ?: fallbackSenderId ?: return null
        val senderKey = content.requiredString("sender_key") ?: return null
        val sessionId = content.requiredString("session_id") ?: fallbackSessionId ?: return null

        return RoomKeyRecoveryRequest(
            roomId = roomId,
            senderUserId = senderUserId,
            senderDeviceId = content.optionalString("device_id"),
            senderKey = senderKey,
            sessionId = sessionId,
            ciphertext = content.optionalString("ciphertext"),
        )
    }

    private fun JsonObject.requiredString(name: String): String? {
        return optionalString(name)?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.optionalString(name: String): String? {
        return get(name)?.jsonPrimitive?.contentOrNull
    }

    private fun JsonObject.jsonObjectOrNull(name: String): JsonObject? {
        return runCatching { get(name)?.jsonObject }.getOrNull()
    }

    private companion object {
        const val MEGOLM_ALGORITHM = "m.megolm.v1.aes-sha2"
    }
}
