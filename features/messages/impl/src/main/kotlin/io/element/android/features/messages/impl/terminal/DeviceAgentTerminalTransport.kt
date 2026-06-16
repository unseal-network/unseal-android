/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.terminal

import io.element.android.features.messages.impl.roomdata.RoomDeviceAgent
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DMsgType
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DOutboundMessage
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DTarget
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DSendResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

interface DeviceAgentTerminalTransport {
    suspend fun openTerminal(deviceAgent: RoomDeviceAgent, cols: Int = 80, rows: Int = 24): Result<String>
    suspend fun sendInput(deviceAgent: RoomDeviceAgent, sessionId: String, data: String): Result<Unit>
    suspend fun closeTerminal(deviceAgent: RoomDeviceAgent, sessionId: String): Result<Unit>
}

class MatrixDeviceAgentTerminalTransport(
    private val matrixClient: MatrixClient,
) : DeviceAgentTerminalTransport {
    override suspend fun openTerminal(deviceAgent: RoomDeviceAgent, cols: Int, rows: Int): Result<String> {
        val requestId = UUID.randomUUID().toString().lowercase()
        val content = buildJsonObject {
            put("cols", cols)
            put("rows", rows)
            put("request_id", requestId)
            put("platform", "android")
            put("sender_device_id", matrixClient.deviceId.value)
        }
        return sendToDevice(deviceAgent, UnsealD2DMsgType.TerminalOpen, content).mapCatching { result ->
            result.throwIfHasFailures()
            requestId
        }
    }

    override suspend fun sendInput(deviceAgent: RoomDeviceAgent, sessionId: String, data: String): Result<Unit> {
        val content = buildJsonObject {
            put("session_id", sessionId)
            put("data", data)
            put("platform", "android")
            put("sender_device_id", matrixClient.deviceId.value)
        }
        return sendToDevice(deviceAgent, UnsealD2DMsgType.TerminalInput, content).mapCatching { result ->
            result.throwIfHasFailures()
        }
    }

    override suspend fun closeTerminal(deviceAgent: RoomDeviceAgent, sessionId: String): Result<Unit> {
        val content = buildJsonObject {
            put("session_id", sessionId)
            put("platform", "android")
            put("sender_device_id", matrixClient.deviceId.value)
        }
        return sendToDevice(deviceAgent, UnsealD2DMsgType.TerminalClose, content).mapCatching { result ->
            result.throwIfHasFailures()
        }
    }

    private suspend fun sendToDevice(
        deviceAgent: RoomDeviceAgent,
        msgType: UnsealD2DMsgType,
        content: kotlinx.serialization.json.JsonObject,
    ): Result<UnsealD2DSendResult> {
        return runCatching {
            deviceAgent.toD2DMessage(msgType, content)
        }.fold(
            onSuccess = { message -> matrixClient.sendUnsealD2DMessage(message) },
            onFailure = { error -> Result.failure(error) },
        )
    }
}

private fun RoomDeviceAgent.toD2DMessage(
    msgType: UnsealD2DMsgType,
    content: kotlinx.serialization.json.JsonObject,
): UnsealD2DOutboundMessage {
    val userId = matrixUserId?.takeIf { it.isNotBlank() }
        ?: error("Device agent is missing a Matrix user id")
    return UnsealD2DOutboundMessage(
        target = UnsealD2DTarget(UserId(userId), boundDeviceId),
        msgType = msgType,
        content = content,
    )
}

private fun UnsealD2DSendResult.throwIfHasFailures() {
    check(failures.isEmpty()) {
        "Failed to send Unseal D2D message: $failures"
    }
}
