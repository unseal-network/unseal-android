/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.Locale

internal object ChatbotUrlBuilder {
    fun path(template: String, parameters: Map<String, String>): String {
        return parameters.entries.fold(template) { current, (key, value) ->
            current.replace("{$key}", encodePathSegment(value))
        }
    }

    fun query(parameters: Map<String, String?>): String {
        val present = parameters.filterValues { it != null }
        if (present.isEmpty()) return ""
        val builder = "https://example.org".toHttpUrl().newBuilder()
        present.forEach { (key, value) ->
            builder.addQueryParameter(key, value)
        }
        val encodedQuery = builder.build().encodedQuery.orEmpty()
        return if (encodedQuery.isEmpty()) "" else "?$encodedQuery"
    }

    private fun encodePathSegment(value: String): String {
        return value.toByteArray(Charsets.UTF_8).joinToString(separator = "") { byte ->
            val unsigned = byte.toInt() and 0xff
            when {
                unsigned in 'A'.code..'Z'.code -> unsigned.toChar().toString()
                unsigned in 'a'.code..'z'.code -> unsigned.toChar().toString()
                unsigned in '0'.code..'9'.code -> unsigned.toChar().toString()
                unsigned == '-'.code || unsigned == '.'.code || unsigned == '_'.code || unsigned == '~'.code -> unsigned.toChar().toString()
                else -> "%${unsigned.toString(16).uppercase(Locale.US).padStart(2, '0')}"
            }
        }
    }
}
