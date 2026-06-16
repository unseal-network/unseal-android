/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import org.junit.Test

class AgentRoomKeyRecoveryRequestParserTest {
    private val parser = AgentRoomKeyRecoveryRequestParser()

    @Test
    fun `parse accepts agent sender with bot device`() {
        val request = parser.parse(
            originalJson = anOriginalJson(sender = "@someone:example.org", deviceId = "BOT_DEVICE"),
            fallbackRoomId = RoomId("!fallback:example.org"),
            fallbackSenderId = UserId("@fallback:example.org"),
            fallbackSessionId = "FALLBACK_SESSION",
        )

        assertThat(request!!.roomId).isEqualTo(RoomId("!room:example.org"))
        assertThat(request.senderUserId).isEqualTo(UserId("@someone:example.org"))
        assertThat(request.senderDeviceId).isEqualTo("BOT_DEVICE")
        assertThat(request.senderKey).isEqualTo("SENDER_KEY")
        assertThat(request.sessionId).isEqualTo("SESSION")
    }

    @Test
    fun `parse accepts iOS agent localpart rules`() {
        val senders = listOf(
            "@agent:example.org",
            "@agent-helper:example.org",
            "@jelf-worker:example.org",
            "@room-jelf:example.org",
        )

        val parsedSenders = senders.map { sender ->
            parser.parse(
                originalJson = anOriginalJson(sender = sender, deviceId = "DEVICE"),
                fallbackRoomId = null,
                fallbackSenderId = UserId("@fallback:example.org"),
                fallbackSessionId = "FALLBACK_SESSION",
            )?.senderUserId
        }

        assertThat(parsedSenders).containsExactlyElementsIn(senders.map(::UserId)).inOrder()
    }

    @Test
    fun `parse uses fallback room sender and session fields`() {
        val request = parser.parse(
            originalJson = """
                {
                  "content": {
                    "algorithm": "m.megolm.v1.aes-sha2",
                    "sender_key": "SENDER_KEY",
                    "device_id": "BOT_DEVICE"
                  }
                }
            """.trimIndent(),
            fallbackRoomId = RoomId("!fallback:example.org"),
            fallbackSenderId = UserId("@agent:example.org"),
            fallbackSessionId = "FALLBACK_SESSION",
        )

        assertThat(request!!.roomId).isEqualTo(RoomId("!fallback:example.org"))
        assertThat(request.senderUserId).isEqualTo(UserId("@agent:example.org"))
        assertThat(request.sessionId).isEqualTo("FALLBACK_SESSION")
    }

    @Test
    fun `parse rejects non-agent sender`() {
        val request = parser.parse(
            originalJson = anOriginalJson(sender = "@alice:example.org", deviceId = "ALICEDEVICE"),
            fallbackRoomId = RoomId("!fallback:example.org"),
            fallbackSenderId = UserId("@fallback:example.org"),
            fallbackSessionId = "FALLBACK_SESSION",
        )

        assertThat(request).isNull()
    }

    @Test
    fun `parse rejects invalid or incomplete JSON`() {
        val invalidInputs = listOf(
            null,
            "{",
            anOriginalJson(algorithm = "m.olm.v1.curve25519-aes-sha2"),
            anOriginalJson(deviceId = null),
            anOriginalJson(senderKey = null),
        )

        invalidInputs.forEach { originalJson ->
            assertThat(
                parser.parse(
                    originalJson = originalJson,
                    fallbackRoomId = RoomId("!fallback:example.org"),
                    fallbackSenderId = UserId("@agent:example.org"),
                    fallbackSessionId = "FALLBACK_SESSION",
                )
            ).isNull()
        }

        assertThat(
            parser.parse(
                originalJson = anOriginalJson(roomId = null),
                fallbackRoomId = null,
                fallbackSenderId = UserId("@agent:example.org"),
                fallbackSessionId = "FALLBACK_SESSION",
            )
        ).isNull()
    }

    private fun anOriginalJson(
        roomId: String? = "!room:example.org",
        sender: String? = "@agent:example.org",
        algorithm: String = "m.megolm.v1.aes-sha2",
        senderKey: String? = "SENDER_KEY",
        sessionId: String? = "SESSION",
        deviceId: String? = "BOT_DEVICE",
    ): String {
        val contentFields = buildList {
            add("\"algorithm\":\"$algorithm\"")
            senderKey?.let { add("\"sender_key\":\"$it\"") }
            sessionId?.let { add("\"session_id\":\"$it\"") }
            deviceId?.let { add("\"device_id\":\"$it\"") }
        }
        val rootFields = buildList {
            roomId?.let { add("\"room_id\":\"$it\"") }
            sender?.let { add("\"sender\":\"$it\"") }
            add("\"content\":{${contentFields.joinToString(",")}}")
        }
        return "{${rootFields.joinToString(",")}}"
    }
}
