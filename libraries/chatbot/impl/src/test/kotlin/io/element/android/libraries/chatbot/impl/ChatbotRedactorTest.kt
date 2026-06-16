/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ChatbotRedactorTest {
    @Test
    fun `redact - hides chatbot secrets from JSON payloads`() {
        val raw = """
            {
              "api_key": "sk-test",
              "accessToken": "mx-token",
              "secretAccessKey": "aws-secret",
              "safe": "visible"
            }
        """.trimIndent()

        val redacted = ChatbotRedactor.redact(raw)

        assertThat(redacted).doesNotContain("sk-test")
        assertThat(redacted).doesNotContain("mx-token")
        assertThat(redacted).doesNotContain("aws-secret")
        assertThat(redacted).contains("visible")
        assertThat(redacted).contains("\"api_key\": \"[REDACTED]\"")
    }
}
