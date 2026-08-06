/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.ChatbotConfig
import kotlinx.coroutines.CancellationException
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
    fun `resolveUnsealApiBaseUrl - falls back to server host when well-known is missing or invalid`() = runTest {
        val missingResolver = DefaultChatbotBaseUrlResolver(ChatbotWellKnownFetcher { null })
        val invalidResolver = DefaultChatbotBaseUrlResolver(ChatbotWellKnownFetcher { "not-json" })

        assertThat(missingResolver.resolveUnsealApiBaseUrl("matrix.example")).isEqualTo("https://matrix.example")
        assertThat(invalidResolver.resolveUnsealApiBaseUrl("matrix.example")).isEqualTo("https://matrix.example")
    }

    @Test
    fun `resolveUnsealApiBaseUrl - does not cache a cancelled well-known lookup`() = runTest {
        var fetchCount = 0
        val resolver = DefaultChatbotBaseUrlResolver(
            ChatbotWellKnownFetcher {
                fetchCount++
                if (fetchCount == 1) throw CancellationException("screen left composition")
                """
                    {
                      "org.unseal.api": {
                        "base_url": "https://agent.example"
                      }
                    }
                """.trimIndent()
            }
        )

        runCatching { resolver.resolveUnsealApiBaseUrl("matrix.example") }

        assertThat(resolver.resolveUnsealApiBaseUrl("matrix.example")).isEqualTo("https://agent.example")
        assertThat(fetchCount).isEqualTo(2)
    }

    @Test
    fun `resolveHomeserverBaseUrl - returns stream fallback without server name`() = runTest {
        val resolver = DefaultChatbotBaseUrlResolver(ChatbotWellKnownFetcher { error("Should not fetch") })

        assertThat(resolver.resolveHomeserverBaseUrl(null)).isEqualTo(ChatbotConfig.AI_STREAM_BASE_URL)
        assertThat(resolver.resolveHomeserverBaseUrl(" ")).isEqualTo(ChatbotConfig.AI_STREAM_BASE_URL)
    }

    @Test
    fun `resolveHomeserverBaseUrl - reads homeserver base url from Matrix well-known and caches it`() = runTest {
        var fetchCount = 0
        val resolver = DefaultChatbotBaseUrlResolver(
            ChatbotWellKnownFetcher { serverName ->
                fetchCount++
                assertThat(serverName).isEqualTo("keepsecret.io")
                """
                    {
                      "m.homeserver": {
                        "base_url": "https://keepsecret.io"
                      },
                      "org.unseal.api": {
                        "base_url": "https://agent.keepsecret.io"
                      }
                    }
                """.trimIndent()
            }
        )

        assertThat(resolver.resolveHomeserverBaseUrl(" keepsecret.io ")).isEqualTo("https://keepsecret.io")
        assertThat(resolver.resolveHomeserverBaseUrl("keepsecret.io")).isEqualTo("https://keepsecret.io")
        assertThat(fetchCount).isEqualTo(1)
    }

    @Test
    fun `resolveHomeserverBaseUrl - falls back to server host when well-known is missing homeserver`() = runTest {
        val missingResolver = DefaultChatbotBaseUrlResolver(ChatbotWellKnownFetcher { null })
        val unsealOnlyResolver = DefaultChatbotBaseUrlResolver(
            ChatbotWellKnownFetcher {
                """
                    {
                      "org.unseal.api": {
                        "base_url": "https://agent.example"
                      }
                    }
                """.trimIndent()
            }
        )

        assertThat(missingResolver.resolveHomeserverBaseUrl("matrix.example")).isEqualTo("https://matrix.example")
        assertThat(unsealOnlyResolver.resolveHomeserverBaseUrl("matrix.example")).isEqualTo("https://matrix.example")
    }
}
