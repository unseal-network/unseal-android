/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import okhttp3.OkHttpClient
import okhttp3.Request

internal fun interface ChatbotWellKnownFetcher {
    suspend fun fetch(serverName: String): String?
}

@ContributesBinding(AppScope::class)
internal class DefaultChatbotWellKnownFetcher(
    private val okHttpClient: () -> OkHttpClient,
) : ChatbotWellKnownFetcher {
    override suspend fun fetch(serverName: String): String? {
        val url = "https://$serverName/.well-known/matrix/client"
        val request = Request.Builder().url(url).build()
        return okHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body.string()
        }
    }
}
