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
    private val cachedApiBaseUrls = mutableMapOf<String, String>()
    private val cachedHomeserverBaseUrls = mutableMapOf<String, String>()
    private val cachedWellKnown = mutableMapOf<String, InternalUnsealWellKnown?>()

    override suspend fun resolveUnsealApiBaseUrl(serverName: String?): String {
        val normalized = serverName?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL

        return mutex.withLock {
            cachedApiBaseUrls[normalized]?.let { return@withLock it }
            val resolved = wellKnown(normalized)?.unsealApi?.baseUrl?.takeIf { it.isNotBlank() }
                ?: ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL
            cachedApiBaseUrls[normalized] = resolved
            resolved
        }
    }

    override suspend fun resolveHomeserverBaseUrl(serverName: String?): String {
        val normalized = serverName?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ChatbotConfig.AI_STREAM_BASE_URL

        return mutex.withLock {
            cachedHomeserverBaseUrls[normalized]?.let { return@withLock it }
            val resolved = wellKnown(normalized)?.homeserver?.baseUrl?.takeIf { it.isNotBlank() }
                ?: "https://$normalized"
            cachedHomeserverBaseUrls[normalized] = resolved
            resolved
        }
    }

    /** Fetch + decode the `.well-known/matrix/client` once per server name (cached, mutex-guarded). */
    private suspend fun wellKnown(serverName: String): InternalUnsealWellKnown? {
        if (cachedWellKnown.containsKey(serverName)) return cachedWellKnown[serverName]
        val decoded = runCatching {
            val payload = wellKnownFetcher.fetch(serverName) ?: return@runCatching null
            ChatbotJson.decode<InternalUnsealWellKnown>(payload)
        }.onFailure {
            Timber.e(it, "Failed to fetch Unseal well-known for $serverName")
        }.getOrNull()
        cachedWellKnown[serverName] = decoded
        return decoded
    }
}
