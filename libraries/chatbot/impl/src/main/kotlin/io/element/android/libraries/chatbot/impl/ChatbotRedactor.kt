/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

internal object ChatbotRedactor {
    private val quotedValuePatterns = listOf(
        "api_key",
        "apiKey",
        "access_token",
        "accessToken",
        "secret_access_key",
        "secretAccessKey",
        "session_token",
        "sessionToken",
        "Authorization",
        "authorization",
    ).map { key ->
        Regex("""("$key"\s*:\s*")[^"]*(")""")
    }

    fun redact(text: String): String {
        return quotedValuePatterns.fold(text) { current, pattern ->
            current.replace(pattern, "$1[REDACTED]$2")
        }
    }
}
