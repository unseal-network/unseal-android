/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.AgentRoomKeyRecoveryRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgentRoomKeyRecoveryRequestParser {
    fun parse(
        originalJson: String?,
        fallbackRoomId: RoomId?,
        fallbackSenderId: UserId,
        fallbackSessionId: String,
    ): AgentRoomKeyRecoveryRequest? {
        val root = originalJson
            ?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?: return null
        val content = root.jsonObjectOrNull("content") ?: return null
        if (content.optionalString("algorithm") != AgentRoomKeyRecoveryRequest.ALGORITHM) return null

        val senderUserId = UserId(root.optionalString("sender") ?: fallbackSenderId.value)
        val senderDeviceId = content.requiredString("device_id") ?: return null
        if (!isAgentSender(senderUserId, senderDeviceId)) return null

        val roomId = RoomId(root.optionalString("room_id") ?: fallbackRoomId?.value ?: return null)
        val senderKey = content.requiredString("sender_key") ?: return null
        val sessionId = content.requiredString("session_id") ?: fallbackSessionId

        return AgentRoomKeyRecoveryRequest(
            roomId = roomId,
            senderUserId = senderUserId,
            senderDeviceId = senderDeviceId,
            senderKey = senderKey,
            sessionId = sessionId,
        )
    }

    private fun isAgentSender(userId: UserId, deviceId: String): Boolean {
        val localpart = userId.value
            .substringBefore(":")
            .removePrefix("@")
            .lowercase()
        val normalizedDeviceId = deviceId.uppercase()

        return normalizedDeviceId.startsWith("BOT_") ||
            localpart == "agent" ||
            localpart.startsWith("agent-") ||
            localpart.startsWith("jelf-") ||
            localpart.contains("-jelf")
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
}
