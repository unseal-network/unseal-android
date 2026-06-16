/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal object ChatbotJson {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    inline fun <reified T> decode(data: String): T = json.decodeFromString(data)
    inline fun <reified T> encode(value: T): String = json.encodeToString(value)
}
