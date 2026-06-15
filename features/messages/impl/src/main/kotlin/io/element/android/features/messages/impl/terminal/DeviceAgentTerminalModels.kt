/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.terminal

import io.element.android.features.messages.impl.roomdata.RoomDeviceAgent

data class DeviceAgentTerminalPanelState(
    val deviceAgent: RoomDeviceAgent,
    val status: Status,
    val outputText: String,
    val inputText: String = "",
    val pendingRequestId: String? = null,
    val sessionId: String? = null,
    val shell: String? = null,
) {
    enum class Status {
        WaitingForDevice,
        ReadyToOpen,
        Opening,
        Connected,
        Closed,
        Failed,
    }

    val title: String = "Remote Terminal"

    val subtitle: String
        get() = when (status) {
            Status.WaitingForDevice -> "Waiting for Web or desktop heartbeat"
            Status.ReadyToOpen -> deviceAgent.boundDeviceId
            Status.Opening -> "Opening on ${deviceAgent.boundDeviceId}"
            Status.Connected -> sessionId ?: "Connected"
            Status.Closed -> "Session closed"
            Status.Failed -> "Terminal failed"
        }

    val statusTitle: String
        get() = when (status) {
            Status.WaitingForDevice -> "Terminal waiting"
            Status.ReadyToOpen -> "Terminal ready"
            Status.Opening -> "Terminal opening"
            Status.Connected -> "Terminal connected"
            Status.Closed -> "Terminal closed"
            Status.Failed -> "Terminal failed"
        }

    val hasActiveSession: Boolean
        get() = status in setOf(Status.Opening, Status.Connected, Status.Closed, Status.Failed)

    val canOpenTerminal: Boolean
        get() = sessionId == null && status != Status.Opening

    val canCloseTerminal: Boolean
        get() = sessionId != null

    val canSendInput: Boolean
        get() = sessionId != null && inputText.isNotBlank()

    companion object {
        fun ready(deviceAgent: RoomDeviceAgent): DeviceAgentTerminalPanelState {
            return DeviceAgentTerminalPanelState(
                deviceAgent = deviceAgent,
                status = Status.ReadyToOpen,
                outputText = buildString {
                    appendLine("Desktop target detected: ${deviceAgent.boundDeviceId}")
                    val displayName = deviceAgent.displayName?.takeIf { it.isNotBlank() }
                    if (displayName != null) {
                        appendLine("Agent: $displayName")
                    }
                    appendLine("Ready to open a remote terminal.")
                },
            )
        }
    }
}

sealed interface DeviceAgentTerminalEvent {
    data object TargetDetected : DeviceAgentTerminalEvent
    data class OpenRequested(val requestId: String?) : DeviceAgentTerminalEvent
    data class Ready(val requestId: String?, val sessionId: String?, val shell: String?) : DeviceAgentTerminalEvent
    data class Output(val sessionId: String?, val data: String) : DeviceAgentTerminalEvent
    data class Closed(val sessionId: String?) : DeviceAgentTerminalEvent
    data class Failed(val message: String) : DeviceAgentTerminalEvent
    data class InputChanged(val text: String) : DeviceAgentTerminalEvent
    data object InputSent : DeviceAgentTerminalEvent
}

object DeviceAgentTerminalReducer {
    fun reduce(
        state: DeviceAgentTerminalPanelState,
        event: DeviceAgentTerminalEvent,
    ): DeviceAgentTerminalPanelState {
        return when (event) {
            DeviceAgentTerminalEvent.TargetDetected -> state.copy(
                status = DeviceAgentTerminalPanelState.Status.ReadyToOpen,
                outputText = "Desktop target detected: ${state.deviceAgent.boundDeviceId}\n",
            )
            is DeviceAgentTerminalEvent.OpenRequested -> state.copy(
                status = DeviceAgentTerminalPanelState.Status.Opening,
                pendingRequestId = event.requestId,
                outputText = state.outputText.appendLine("$ open remote terminal")
                    .appendIfPresent(event.requestId) { "Opening request sent: $it" },
            )
            is DeviceAgentTerminalEvent.Ready -> {
                if (!state.matchesPendingRequest(event.requestId)) {
                    state
                } else {
                    val shell = event.shell ?: "shell"
                    state.copy(
                        status = DeviceAgentTerminalPanelState.Status.Connected,
                        pendingRequestId = null,
                        sessionId = event.sessionId,
                        shell = shell,
                        outputText = state.outputText.appendLine("Connected: $shell"),
                    )
                }
            }
            is DeviceAgentTerminalEvent.Output -> {
                if (!state.matchesSession(event.sessionId)) {
                    state
                } else {
                    state.copy(outputText = state.outputText.appendOutput(event.data))
                }
            }
            is DeviceAgentTerminalEvent.Closed -> {
                if (!state.matchesSession(event.sessionId)) {
                    state
                } else {
                    state.copy(
                        status = DeviceAgentTerminalPanelState.Status.Closed,
                        sessionId = null,
                        pendingRequestId = null,
                        outputText = state.outputText.appendLine("Session closed."),
                    )
                }
            }
            is DeviceAgentTerminalEvent.Failed -> state.copy(
                status = DeviceAgentTerminalPanelState.Status.Failed,
                outputText = state.outputText.appendLine(event.message),
            )
            is DeviceAgentTerminalEvent.InputChanged -> state.copy(inputText = event.text)
            DeviceAgentTerminalEvent.InputSent -> state.copy(
                inputText = "",
                outputText = state.outputText.appendLine("$ ${state.inputText}"),
            )
        }
    }
}

private fun DeviceAgentTerminalPanelState.matchesPendingRequest(requestId: String?): Boolean {
    val pending = pendingRequestId ?: return true
    return requestId == null || requestId == pending
}

private fun DeviceAgentTerminalPanelState.matchesSession(messageSessionId: String?): Boolean {
    val current = sessionId ?: return true
    return messageSessionId == null || messageSessionId == current
}

private fun String.appendLine(line: String): String = appendOutput("$line\n")

private fun String.appendIfPresent(value: String?, line: (String) -> String): String {
    return if (value == null) this else appendLine(line(value))
}

private fun String.appendOutput(text: String): String {
    val next = this + stripAnsiEscapes(text)
    return if (next.length > MaxOutputLength) next.takeLast(MaxOutputLength) else next
}

private fun stripAnsiEscapes(text: String): String {
    return text.replace(AnsiRegex, "")
}

private const val MaxOutputLength = 20_000

private val AnsiRegex = Regex(
    pattern = """(?:\u001B\[[0-?]*[ -/]*[@-~])|(?:\u001B\][\s\S]*?(?:\u0007|\u001B\\))|(?:\u001B[P^_][\s\S]*?\u001B\\)|(?:\u001B[@-_])""",
)
