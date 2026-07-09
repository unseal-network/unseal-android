/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.terminal

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DMsgType
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DOutboundMessage
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DTarget
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DSendFailure
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

        val result = transport.openTerminal(target(), requestId = "request-1", cols = 120, rows = 40)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo("request-1")
        val message = captured!!
        assertThat(message.target.userId.value).isEqualTo("@alice:example.org")
        assertThat(message.target.deviceId).isEqualTo("DESKTOP_DEVICE")
        assertThat(message.msgType).isEqualTo(UnsealD2DMsgType.TerminalOpen)
        val encoded = message.encodedToDeviceContent()
        assertThat(encoded).contains("\"msgtype\":\"cmd.open\"")
        assertThat(encoded).contains("\"cols\":120")
        assertThat(encoded).contains("\"rows\":40")
        assertThat(encoded).contains("\"platform\":\"android\"")
        assertThat(encoded).contains("\"sender_device_id\":\"${A_DEVICE_ID.value}\"")
        assertThat(encoded).contains("\"request_id\":\"request-1\"")
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

        val result = transport.sendInput(target(), sessionId = "session-1", data = "ls\n")

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

        val result = transport.closeTerminal(target(), sessionId = "session-1")

        assertThat(result.isSuccess).isTrue()
        val message = captured!!
        assertThat(message.msgType).isEqualTo(UnsealD2DMsgType.TerminalClose)
        assertThat(message.encodedToDeviceContent()).contains("\"session_id\":\"session-1\"")
    }

    @Test
    fun `openTerminal returns failure when SDK reports a to-device failure`() = runTest {
        val transport = MatrixDeviceAgentTerminalTransport(
            FakeMatrixClient(
                sendUnsealD2DMessageLambda = {
                    Result.success(
                        UnsealD2DSendResult(
                            failures = listOf(UnsealD2DSendFailure(UserId("@alice:example.org"), "DESKTOP_DEVICE"))
                        )
                    )
                }
            )
        )

        val result = transport.openTerminal(target(), requestId = "request-1")

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).hasMessageThat().contains("Failed to send Unseal D2D message")
    }

    private fun target() = UnsealD2DTarget(
        userId = UserId("@alice:example.org"),
        deviceId = "DESKTOP_DEVICE",
    )
}
