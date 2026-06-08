/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.ChatbotConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultChatbotBaseUrlResolverTest {
    @Test
    fun `resolveUnsealApiBaseUrl - returns fallback without server name`() = runTest {
        val resolver = DefaultChatbotBaseUrlResolver(ChatbotWellKnownFetcher { error("Should not fetch") })

        assertThat(resolver.resolveUnsealApiBaseUrl(null)).isEqualTo(ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL)
        assertThat(resolver.resolveUnsealApiBaseUrl(" ")).isEqualTo(ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL)
    }

    @Test
    fun `resolveUnsealApiBaseUrl - reads unseal api base url from Matrix well-known and caches it`() = runTest {
        var fetchCount = 0
        val resolver = DefaultChatbotBaseUrlResolver(
            ChatbotWellKnownFetcher { serverName ->
                fetchCount++
                assertThat(serverName).isEqualTo("matrix.example")
                """
                    {
                      "org.unseal.api": {
                        "base_url": "https://agent.example"
                      }
                    }
                """.trimIndent()
            }
        )

        assertThat(resolver.resolveUnsealApiBaseUrl(" matrix.example ")).isEqualTo("https://agent.example")
        assertThat(resolver.resolveUnsealApiBaseUrl("matrix.example")).isEqualTo("https://agent.example")
        assertThat(fetchCount).isEqualTo(1)
    }

    @Test
    fun `resolveUnsealApiBaseUrl - falls back when well-known is missing or invalid`() = runTest {
        val missingResolver = DefaultChatbotBaseUrlResolver(ChatbotWellKnownFetcher { null })
        val invalidResolver = DefaultChatbotBaseUrlResolver(ChatbotWellKnownFetcher { "not-json" })

        assertThat(missingResolver.resolveUnsealApiBaseUrl("matrix.example")).isEqualTo(ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL)
        assertThat(invalidResolver.resolveUnsealApiBaseUrl("matrix.example")).isEqualTo(ChatbotConfig.UNSEAL_API_FALLBACK_BASE_URL)
    }
}
