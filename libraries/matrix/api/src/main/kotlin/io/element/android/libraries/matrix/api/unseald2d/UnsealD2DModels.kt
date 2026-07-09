/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.unseald2d

import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

data class UnsealD2DMessage(
    val eventType: String,
    val sender: UserId,
    val msgTypeValue: String,
    val content: JsonObject,
    val rawContent: String,
    val rawJson: String,
    val senderDeviceId: String?,
) {
    val msgType: UnsealD2DMsgType?
        get() = UnsealD2DMsgType.entries.firstOrNull { it.value == msgTypeValue }

    val isTerminalMessage: Boolean
        get() = msgTypeValue.startsWith("cmd.")

    fun stringContent(key: String): String? {
        return content[key]?.jsonPrimitive?.contentOrNull
    }

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
        }

        fun parse(
            eventType: String,
            sender: String,
            content: String,
            rawJson: String,
            encryptedSenderDeviceId: String?,
        ): UnsealD2DMessage? {
            if (eventType != UnsealD2DConstants.EVENT_TYPE) return null
            val root = runCatching {
                json.parseToJsonElement(content).jsonObject
            }.getOrNull() ?: return null
            val msgTypeValue = root["msgtype"]?.jsonPrimitive?.contentOrNull ?: return null
            val payload = runCatching {
                root["content"]?.jsonObject
            }.getOrNull() ?: buildJsonObject {}
            return UnsealD2DMessage(
                eventType = eventType,
                sender = UserId(sender),
                msgTypeValue = msgTypeValue,
                content = payload,
                rawContent = content,
                rawJson = rawJson,
                senderDeviceId = encryptedSenderDeviceId
                    ?: payload["sender_device_id"]?.jsonPrimitive?.contentOrNull
                    ?: payload["senderDeviceId"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }
}

data class UnsealD2DSendFailure(
    val userId: UserId,
    val deviceId: String,
)

data class UnsealD2DSendResult(
    val failures: List<UnsealD2DSendFailure>,
)
