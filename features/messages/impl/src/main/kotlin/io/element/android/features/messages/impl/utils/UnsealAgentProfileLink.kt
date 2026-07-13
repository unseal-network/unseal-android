/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils

import java.net.URI

data class UnsealAgentProfileLink(
    val botName: String,
    val profileUrl: String,
    val jsonUrl: String,
    val mdUrl: String,
    val matrixUserId: String? = null,
)

internal fun String.toUnsealAgentProfileLink(): UnsealAgentProfileLink? {
    val uri = runCatching { URI(this) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.rawUserInfo != null) return null
    if (uri.rawQuery != null || uri.rawFragment != null) return null

    val encodedPath = uri.rawPath ?: return null
    if (!encodedPath.startsWith("/@")) return null
    if (encodedPath.indexOf('/', startIndex = 1) != -1) return null

    val botName = encodedPath.removePrefix("/@").decodePathSegment()?.takeIf { it.isNotBlank() } ?: return null
    if ('/' in botName) return null
    if (':' in botName) return null
    if (botName.any { it.isWhitespace() || it.isISOControl() }) return null
    if (!botName.isSimpleAgentLocalpart()) return null
    if (botName.endsWith(".md", ignoreCase = true) || botName.endsWith(".json", ignoreCase = true)) return null

    val origin = uri.originString() ?: return null
    return UnsealAgentProfileLink(
        botName = botName,
        profileUrl = "$origin/@$botName",
        jsonUrl = "$origin/@$botName.json",
        mdUrl = "$origin/@$botName.md",
    )
}

private fun URI.normalizedPort(): Int {
    if (port != -1) return port
    return when (scheme?.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> -1
    }
}

private fun URI.originString(): String? {
    val scheme = scheme?.lowercase() ?: return null
    val host = host?.lowercase() ?: return null
    val explicitPort = port.takeIf { it != -1 }
    val portSuffix = explicitPort?.let { ":$it" }.orEmpty()
    return "$scheme://$host$portSuffix"
}

private fun String.decodePathSegment(): String? {
    val result = StringBuilder(length)
    var index = 0
    while (index < length) {
        if (this[index] != '%') {
            result.append(this[index])
            index++
        } else {
            val bytes = mutableListOf<Byte>()
            while (index < length && this[index] == '%') {
                if (index + 2 >= length) return null
                val high = this[index + 1].hexValue() ?: return null
                val low = this[index + 2].hexValue() ?: return null
                bytes.add(((high shl 4) + low).toByte())
                index += 3
            }
            result.append(String(bytes.toByteArray(), Charsets.UTF_8))
        }
    }
    return result.toString()
}

private fun Char.hexValue(): Int? {
    return when (this) {
        in '0'..'9' -> this - '0'
        in 'a'..'f' -> this - 'a' + 10
        in 'A'..'F' -> this - 'A' + 10
        else -> null
    }
}

private fun String.isSimpleAgentLocalpart(): Boolean {
    return all { character ->
        character in 'a'..'z' ||
            character in 'A'..'Z' ||
            character in '0'..'9' ||
            character == '-' ||
            character == '_'
    }
}
