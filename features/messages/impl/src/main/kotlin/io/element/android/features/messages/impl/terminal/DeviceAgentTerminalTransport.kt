/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.terminal

import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DMsgType
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DOutboundMessage
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DTarget
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DSendResult
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

interface DeviceAgentTerminalTransport {
    suspend fun openTerminal(target: UnsealD2DTarget, requestId: String, cols: Int = 80, rows: Int = 24): Result<String>
    suspend fun sendInput(target: UnsealD2DTarget, sessionId: String, data: String): Result<Unit>
    suspend fun closeTerminal(target: UnsealD2DTarget, sessionId: String): Result<Unit>
}

class MatrixDeviceAgentTerminalTransport(
    private val matrixClient: MatrixClient,
) : DeviceAgentTerminalTransport {
    override suspend fun openTerminal(target: UnsealD2DTarget, requestId: String, cols: Int, rows: Int): Result<String> {
        val content = buildJsonObject {
            put("cols", cols)
            put("rows", rows)
            put("request_id", requestId)
            put("platform", "android")
            put("sender_device_id", matrixClient.deviceId.value)
        }
        return sendToDevice(target, UnsealD2DMsgType.TerminalOpen, content).mapCatching { result ->
            result.throwIfHasFailures()
            requestId
        }
    }

    override suspend fun sendInput(target: UnsealD2DTarget, sessionId: String, data: String): Result<Unit> {
        val content = buildJsonObject {
            put("session_id", sessionId)
            put("data", data)
            put("platform", "android")
            put("sender_device_id", matrixClient.deviceId.value)
        }
        return sendToDevice(target, UnsealD2DMsgType.TerminalInput, content).mapCatching { result ->
            result.throwIfHasFailures()
        }
    }

    override suspend fun closeTerminal(target: UnsealD2DTarget, sessionId: String): Result<Unit> {
        val content = buildJsonObject {
            put("session_id", sessionId)
            put("platform", "android")
            put("sender_device_id", matrixClient.deviceId.value)
        }
        return sendToDevice(target, UnsealD2DMsgType.TerminalClose, content).mapCatching { result ->
            result.throwIfHasFailures()
        }
    }

    private suspend fun sendToDevice(
        target: UnsealD2DTarget,
        msgType: UnsealD2DMsgType,
        content: kotlinx.serialization.json.JsonObject,
    ): Result<UnsealD2DSendResult> {
        return matrixClient.sendUnsealD2DMessage(
            UnsealD2DOutboundMessage(
                target = target,
                msgType = msgType,
                content = content,
            )
        )
    }
}

private fun UnsealD2DSendResult.throwIfHasFailures() {
    check(failures.isEmpty()) {
        "Failed to send Unseal D2D message: $failures"
    }
}
