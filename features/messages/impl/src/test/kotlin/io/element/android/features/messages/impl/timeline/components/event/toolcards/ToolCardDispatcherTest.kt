/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class ToolCardDispatcherTest {
    @Test
    fun `gmail fetch list payload is not swallowed by single email card`() {
        val payload = JSONObject(
            """
            {
              "messages": [
                {
                  "subject": "Welcome",
                  "from": "team@example.com",
                  "snippet": "Thanks for joining"
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(payload.hasCardContentFor("composeEmail")).isFalse()
        assertThat(payload.cardObjects("messages")).isNotEmpty()
    }

    @Test
    fun `single gmail payload can render as compose email card`() {
        val payload = JSONObject(
            """
            {
              "subject": "Welcome",
              "from": { "email": "team@example.com" },
              "body": "Thanks for joining"
            }
            """.trimIndent()
        )

        assertThat(payload.hasCardContentFor("composeEmail")).isTrue()
    }
}
