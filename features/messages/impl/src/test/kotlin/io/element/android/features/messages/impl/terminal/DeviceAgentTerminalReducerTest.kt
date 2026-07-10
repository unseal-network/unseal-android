/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.terminal

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.roomdata.RoomDeviceAgent
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DTarget
import org.junit.Test

class DeviceAgentTerminalReducerTest {
    @Test
    fun `collapse and reopen preserve terminal state`() {
        val connected = initialState().copy(
            status = DeviceAgentTerminalPanelState.Status.Connected,
            outputText = "Connected: zsh\nok\n",
            sessionId = "session-1",
        )

        val collapsed = DeviceAgentTerminalReducer.reduce(
            connected,
            DeviceAgentTerminalEvent.ExpandedChanged(false),
        )
        val reopened = DeviceAgentTerminalReducer.reduce(
            collapsed,
            DeviceAgentTerminalEvent.ExpandedChanged(true),
        )

        assertThat(collapsed.isExpanded).isFalse()
        assertThat(reopened.isExpanded).isTrue()
        assertThat(reopened.status).isEqualTo(connected.status)
        assertThat(reopened.sessionId).isEqualTo(connected.sessionId)
        assertThat(reopened.outputText).isEqualTo(connected.outputText)
    }

    @Test
    fun `open request moves state to opening and stores request id`() {
        val state = DeviceAgentTerminalReducer.reduce(
            initialState(),
            DeviceAgentTerminalEvent.OpenRequested(requestId = "request-1"),
        )

        assertThat(state.status).isEqualTo(DeviceAgentTerminalPanelState.Status.Opening)
        assertThat(state.pendingRequestId).isEqualTo("request-1")
        assertThat(state.outputText).contains("$ open remote terminal")
        assertThat(state.outputText).contains("Opening request sent: request-1")
    }

    @Test
    fun `ready ignores mismatched pending request`() {
        val opening = DeviceAgentTerminalReducer.reduce(
            initialState(),
            DeviceAgentTerminalEvent.OpenRequested(requestId = "request-1"),
        )

        val state = DeviceAgentTerminalReducer.reduce(
            opening,
            DeviceAgentTerminalEvent.Ready(requestId = "request-2", sessionId = "session-1", shell = "zsh"),
        )

        assertThat(state.status).isEqualTo(DeviceAgentTerminalPanelState.Status.Opening)
        assertThat(state.sessionId).isNull()
    }

    @Test
    fun `ready ignores missing pending request while opening`() {
        val opening = DeviceAgentTerminalReducer.reduce(
            initialState(),
            DeviceAgentTerminalEvent.OpenRequested(requestId = "request-1"),
        )

        val state = DeviceAgentTerminalReducer.reduce(
            opening,
            DeviceAgentTerminalEvent.Ready(requestId = null, sessionId = "session-1", shell = "zsh"),
        )

        assertThat(state.status).isEqualTo(DeviceAgentTerminalPanelState.Status.Opening)
        assertThat(state.sessionId).isNull()
    }

    @Test
    fun `ready accepts matching request and connects session`() {
        val opening = DeviceAgentTerminalReducer.reduce(
            initialState(),
            DeviceAgentTerminalEvent.OpenRequested(requestId = "request-1"),
        )

        val state = DeviceAgentTerminalReducer.reduce(
            opening,
            DeviceAgentTerminalEvent.Ready(requestId = "request-1", sessionId = "session-1", shell = "zsh"),
        )

        assertThat(state.status).isEqualTo(DeviceAgentTerminalPanelState.Status.Connected)
        assertThat(state.pendingRequestId).isNull()
        assertThat(state.sessionId).isEqualTo("session-1")
        assertThat(state.outputText).contains("Connected: zsh")
    }

    @Test
    fun `output only appends for matching session and strips ansi escapes`() {
        val connected = initialState().copy(
            status = DeviceAgentTerminalPanelState.Status.Connected,
            sessionId = "session-1",
        )

        val ignored = DeviceAgentTerminalReducer.reduce(
            connected,
            DeviceAgentTerminalEvent.Output(sessionId = "session-2", data = "ignored"),
        )
        val state = DeviceAgentTerminalReducer.reduce(
            ignored,
            DeviceAgentTerminalEvent.Output(sessionId = "session-1", data = "\u001B[32mok\u001B[0m\n"),
        )

        assertThat(state.outputText).doesNotContain("ignored")
        assertThat(state.outputText).contains("ok\n")
        assertThat(state.outputText).doesNotContain("\u001B")
    }

    @Test
    fun `closed only closes matching session`() {
        val connected = initialState().copy(
            status = DeviceAgentTerminalPanelState.Status.Connected,
            sessionId = "session-1",
        )

        val ignored = DeviceAgentTerminalReducer.reduce(
            connected,
            DeviceAgentTerminalEvent.Closed(sessionId = "session-2"),
        )
        val state = DeviceAgentTerminalReducer.reduce(
            ignored,
            DeviceAgentTerminalEvent.Closed(sessionId = "session-1"),
        )

        assertThat(ignored.status).isEqualTo(DeviceAgentTerminalPanelState.Status.Connected)
        assertThat(state.status).isEqualTo(DeviceAgentTerminalPanelState.Status.Closed)
        assertThat(state.sessionId).isNull()
        assertThat(state.outputText).contains("Session closed.")
    }

    @Test
    fun `output and closed are ignored before a session is connected`() {
        val ready = initialState()

        val withOutput = DeviceAgentTerminalReducer.reduce(
            ready,
            DeviceAgentTerminalEvent.Output(sessionId = "session-1", data = "ignored"),
        )
        val closed = DeviceAgentTerminalReducer.reduce(
            withOutput,
            DeviceAgentTerminalEvent.Closed(sessionId = "session-1"),
        )

        assertThat(withOutput.outputText).doesNotContain("ignored")
        assertThat(closed.status).isEqualTo(DeviceAgentTerminalPanelState.Status.ReadyToOpen)
    }

    @Test
    fun `target heartbeat does not reset an active terminal session`() {
        val connected = initialState().copy(
            target = target("DESKTOP_DEVICE"),
            status = DeviceAgentTerminalPanelState.Status.Connected,
            outputText = "Connected: zsh\nok\n",
            sessionId = "session-1",
        )

        val state = DeviceAgentTerminalReducer.reduce(
            connected,
            DeviceAgentTerminalEvent.TargetDetected(target("OTHER_DEVICE")),
        )

        assertThat(state.status).isEqualTo(DeviceAgentTerminalPanelState.Status.Connected)
        assertThat(state.target?.deviceId).isEqualTo("DESKTOP_DEVICE")
        assertThat(state.sessionId).isEqualTo("session-1")
        assertThat(state.outputText).contains("ok\n")
    }

    private fun initialState(): DeviceAgentTerminalPanelState {
        return DeviceAgentTerminalPanelState.ready(
            RoomDeviceAgent(
                boundDeviceId = "device-1",
                displayName = "MacBook Agent",
                matrixUserId = "@agent:example.org",
            )
        )
    }

    private fun target(deviceId: String): UnsealD2DTarget {
        return UnsealD2DTarget(
            userId = UserId("@alice:server.org"),
            deviceId = deviceId,
        )
    }
}
