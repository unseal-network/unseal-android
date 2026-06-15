/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.unseald2d

import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object UnsealD2DConstants {
    const val EVENT_TYPE = "io.unseal.d2d"
}

enum class UnsealD2DMsgType(val value: String) {
    ApprovalRequest("approval.request"),
    ApprovalResponse("approval.response"),
    DataSync("data.sync"),
    AgentRequest("agent.request"),
    AgentResponse("agent.response"),
    AgentStream("agent.stream"),
    Ping("d2d.ping"),
    Pong("d2d.pong"),
    Thinking("d2d.thinking"),
    SkillsInvalidate("skills.invalidate"),
    TerminalOpen("cmd.open"),
    TerminalReady("cmd.ready"),
    TerminalInput("cmd.input"),
    TerminalOutput("cmd.output"),
    TerminalResize("cmd.resize"),
    TerminalClose("cmd.close"),
    TerminalClosed("cmd.closed"),
    TerminalRtcOffer("cmd.rtc.offer"),
    TerminalRtcAnswer("cmd.rtc.answer"),
    TerminalRtcIce("cmd.rtc.ice"),
}

data class UnsealD2DTarget(
    val userId: UserId,
    val deviceId: String,
)

data class UnsealD2DOutboundMessage(
    val target: UnsealD2DTarget,
    val msgType: UnsealD2DMsgType,
    val content: JsonObject,
) {
    fun encodedToDeviceContent(): String {
        return buildJsonObject {
            put("msgtype", msgType.value)
            put("content", content)
        }.toString()
    }
}

data class UnsealD2DSendFailure(
    val userId: UserId,
    val deviceId: String,
)

data class UnsealD2DSendResult(
    val failures: List<UnsealD2DSendFailure>,
)
