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
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.chatbot.api.ChatbotConfig
import io.element.android.libraries.chatbot.impl.model.InternalUnsealWellKnown
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@ContributesBinding(AppScope::class, binding = binding<ChatbotBaseUrlResolver>())
class DefaultChatbotBaseUrlResolver(
    private val wellKnownFetcher: ChatbotWellKnownFetcher,
) : ChatbotBaseUrlResolver {
    private val mutex = Mutex()
    private val cachedBaseUrls = mutableMapOf<String, String>()

    override suspend fun resolveUnsealApiBaseUrl(serverName: String?): String {
        val normalized = serverName?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL

        return mutex.withLock {
            cachedBaseUrls[normalized]?.let { return@withLock it }
            val resolved = fetchFromWellKnown(normalized) ?: ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL
            cachedBaseUrls[normalized] = resolved
            resolved
        }
    }

    private suspend fun fetchFromWellKnown(serverName: String): String? {
        return runCatching {
            val payload = wellKnownFetcher.fetch(serverName) ?: return@runCatching null
            ChatbotJson.decode<InternalUnsealWellKnown>(payload).unsealApi?.baseUrl?.takeIf { it.isNotBlank() }
        }.onFailure {
            Timber.e(it, "Failed to fetch Unseal API well-known for $serverName")
        }.getOrNull()
    }
}
