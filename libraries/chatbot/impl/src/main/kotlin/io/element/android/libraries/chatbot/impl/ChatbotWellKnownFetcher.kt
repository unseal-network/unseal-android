/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

fun interface ChatbotWellKnownFetcher {
    suspend fun fetch(serverName: String): String?
}

@ContributesBinding(AppScope::class, binding = binding<ChatbotWellKnownFetcher>())
class DefaultChatbotWellKnownFetcher(
    private val okHttpClient: () -> OkHttpClient,
) : ChatbotWellKnownFetcher {
    override suspend fun fetch(serverName: String): String? = withContext(Dispatchers.IO) {
        val url = "https://$serverName/.well-known/matrix/client"
        val request = Request.Builder().url(url).build()
        okHttpClient().newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            response.body.string()
        }
    }
}
