/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.terminal

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.roomdata.RoomDeviceAgent
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DMsgType
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DOutboundMessage
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DSendResult
import io.element.android.libraries.matrix.test.A_DEVICE_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MatrixDeviceAgentTerminalTransportTest {
    @Test
    fun `openTerminal sends cmd open to the bound device`() = runTest {
        var captured: UnsealD2DOutboundMessage? = null
        val transport = MatrixDeviceAgentTerminalTransport(
            FakeMatrixClient(
                sendUnsealD2DMessageLambda = {
                    captured = it
                    Result.success(UnsealD2DSendResult(failures = emptyList()))
                }
            )
        )

        val result = transport.openTerminal(deviceAgent(), cols = 120, rows = 40)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isNotEmpty()
        val message = captured!!
        assertThat(message.target.userId.value).isEqualTo("@agent:example.org")
        assertThat(message.target.deviceId).isEqualTo("BOT_DEVICE")
        assertThat(message.msgType).isEqualTo(UnsealD2DMsgType.TerminalOpen)
        val encoded = message.encodedToDeviceContent()
        assertThat(encoded).contains("\"msgtype\":\"cmd.open\"")
        assertThat(encoded).contains("\"cols\":120")
        assertThat(encoded).contains("\"rows\":40")
        assertThat(encoded).contains("\"platform\":\"android\"")
        assertThat(encoded).contains("\"sender_device_id\":\"${A_DEVICE_ID.value}\"")
        assertThat(encoded).contains("\"request_id\"")
    }

    @Test
    fun `sendInput sends cmd input with session data`() = runTest {
        var captured: UnsealD2DOutboundMessage? = null
        val transport = MatrixDeviceAgentTerminalTransport(
            FakeMatrixClient(
                sendUnsealD2DMessageLambda = {
                    captured = it
                    Result.success(UnsealD2DSendResult(failures = emptyList()))
                }
            )
        )

        val result = transport.sendInput(deviceAgent(), sessionId = "session-1", data = "ls\n")

        assertThat(result.isSuccess).isTrue()
        val message = captured!!
        assertThat(message.msgType).isEqualTo(UnsealD2DMsgType.TerminalInput)
        val encoded = message.encodedToDeviceContent()
        assertThat(encoded).contains("\"session_id\":\"session-1\"")
        assertThat(encoded).contains("\"data\":\"ls\\n\"")
    }

    @Test
    fun `closeTerminal sends cmd close with session id`() = runTest {
        var captured: UnsealD2DOutboundMessage? = null
        val transport = MatrixDeviceAgentTerminalTransport(
            FakeMatrixClient(
                sendUnsealD2DMessageLambda = {
                    captured = it
                    Result.success(UnsealD2DSendResult(failures = emptyList()))
                }
            )
        )

        val result = transport.closeTerminal(deviceAgent(), sessionId = "session-1")

        assertThat(result.isSuccess).isTrue()
        val message = captured!!
        assertThat(message.msgType).isEqualTo(UnsealD2DMsgType.TerminalClose)
        assertThat(message.encodedToDeviceContent()).contains("\"session_id\":\"session-1\"")
    }

    @Test
    fun `openTerminal returns failure when device agent has no matrix user id`() = runTest {
        val transport = MatrixDeviceAgentTerminalTransport(
            FakeMatrixClient(
                sendUnsealD2DMessageLambda = {
                    error("send should not be called")
                }
            )
        )

        val result = transport.openTerminal(deviceAgent().copy(matrixUserId = null))

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("Matrix user id")
    }

    private fun deviceAgent() = RoomDeviceAgent(
        boundDeviceId = "BOT_DEVICE",
        displayName = "Device Agent",
        matrixUserId = "@agent:example.org",
    )
}
