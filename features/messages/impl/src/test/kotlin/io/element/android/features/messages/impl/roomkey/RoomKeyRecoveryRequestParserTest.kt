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

class RoomKeyRecoveryRequestParserTest {
    private val parser = RoomKeyRecoveryRequestParser()

    @Test
    fun `parse returns request from Megolm event JSON`() {
        val result = parser.parse(aMegolmJson())

        assertThat(result!!.roomId).isEqualTo(RoomId("!room:example.org"))
        assertThat(result.senderUserId).isEqualTo(UserId("@alice:example.org"))
        assertThat(result.senderDeviceId).isEqualTo("ALICEDEVICE")
        assertThat(result.senderKey).isEqualTo("senderKey")
        assertThat(result.sessionId).isEqualTo("session")
        assertThat(result.ciphertext).isEqualTo("ciphertext")
        assertThat(result.identityKey).isEqualTo("!room:example.org|session|senderKey")
    }

    @Test
    fun `parse uses fallback room id when event JSON omits room id`() {
        val result = parser.parse(
            originalJson = aMegolmJson(roomId = null),
            fallbackRoomId = RoomId("!fallback:example.org"),
        )

        assertThat(result!!.roomId).isEqualTo(RoomId("!fallback:example.org"))
        assertThat(result.identityKey).isEqualTo("!fallback:example.org|session|senderKey")
    }

    @Test
    fun `parse returns null for missing JSON`() {
        assertThat(parser.parse(null)).isNull()
    }

    @Test
    fun `parse returns null for invalid JSON`() {
        assertThat(parser.parse("not-json")).isNull()
    }

    @Test
    fun `parse returns null for wrong algorithm`() {
        assertThat(parser.parse(aMegolmJson(algorithm = "m.olm.v1.curve25519-aes-sha2"))).isNull()
    }

    @Test
    fun `parse returns null when required fields are missing`() {
        assertThat(parser.parse(aMegolmJson(roomId = null))).isNull()
        assertThat(parser.parse(aMegolmJson(sender = null))).isNull()
        assertThat(parser.parse(aMegolmJson(senderKey = null))).isNull()
        assertThat(parser.parse(aMegolmJson(sessionId = null))).isNull()
    }

    @Test
    fun `parse accepts missing optional fields`() {
        val result = parser.parse(aMegolmJson(deviceId = null, ciphertext = null))

        assertThat(result!!.senderDeviceId).isNull()
        assertThat(result.ciphertext).isNull()
    }

    private fun aMegolmJson(
        roomId: String? = "!room:example.org",
        sender: String? = "@alice:example.org",
        algorithm: String? = "m.megolm.v1.aes-sha2",
        senderKey: String? = "senderKey",
        deviceId: String? = "ALICEDEVICE",
        sessionId: String? = "session",
        ciphertext: String? = "ciphertext",
    ): String {
        val content = buildJsonObject(
            "algorithm" to algorithm,
            "sender_key" to senderKey,
            "device_id" to deviceId,
            "session_id" to sessionId,
            "ciphertext" to ciphertext,
        )
        return buildJsonObject(
            "room_id" to roomId,
            "sender" to sender,
            "content" to content,
        )
    }

    private fun buildJsonObject(vararg entries: Pair<String, String?>): String {
        return entries
            .mapNotNull { (name, value) ->
                value?.let {
                    if (value.startsWith("{")) {
                        "\"$name\":$value"
                    } else {
                        "\"$name\":\"$value\""
                    }
                }
            }
            .joinToString(prefix = "{", postfix = "}")
    }
}
