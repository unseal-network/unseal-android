/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.utils

import android.net.Uri
import io.element.android.libraries.chatbot.api.ChatbotConfig

internal data class UnsealAgentProfileLink(
    val botName: String,
    val matrixUserId: String? = null,
)

internal fun String.toUnsealAgentProfileLink(): UnsealAgentProfileLink? {
    val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    val host = uri.host?.lowercase() ?: return null
    val websiteHost = Uri.parse(ChatbotConfig.WEBSITE_BASE_URL).host?.lowercase() ?: return null
    if (host != websiteHost) return null

    val pathSegments = uri.pathSegments.orEmpty()
    if (pathSegments.size != 1) return null
    val profileSegment = pathSegments.first()
    if (!profileSegment.startsWith("@")) return null

    val botName = profileSegment.removePrefix("@").takeIf { it.isNotBlank() } ?: return null
    if ('/' in botName) return null
    return UnsealAgentProfileLink(botName = botName)
}
