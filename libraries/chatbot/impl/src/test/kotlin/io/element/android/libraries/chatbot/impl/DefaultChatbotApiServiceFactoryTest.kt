/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class DefaultChatbotApiServiceFactoryTest {
    private lateinit var homeserver: MockWebServer
    private lateinit var unsealApi: MockWebServer

    @Before
    fun setUp() {
        homeserver = MockWebServer()
        unsealApi = MockWebServer()
        homeserver.start()
        unsealApi.start()
    }

    @After
    fun tearDown() {
        homeserver.shutdown()
        unsealApi.shutdown()
    }

    @Test
    fun `factory routes homeserver and ai-stream clients to homeserver and unseal clients to unseal api`() = runTest {
        val factory = DefaultChatbotApiServiceFactory(
            okHttpClient = { OkHttpClient() },
            tokenProvider = ChatbotAccessTokenProvider { "mx-token" },
            baseUrlResolver = FakeBaseUrlResolver(
                homeserverBaseUrl = homeserver.url("/").toString(),
                unsealApiBaseUrl = unsealApi.url("/").toString(),
            ),
        )
        val matrixClient = FakeMatrixClient(userIdServerNameLambda = { "keepsecret.io" })

        homeserver.enqueue(agentListResponse("home-agent"))
        homeserver.enqueue(agentListResponse("stream-agent"))
        unsealApi.enqueue(agentListResponse("unseal-agent"))

        assertThat(factory.createForHomeserver(matrixClient).listAgents().getOrThrow().single().botName).isEqualTo("home-agent")
        assertThat(factory.createForAiStream(matrixClient).listAgents().getOrThrow().single().botName).isEqualTo("stream-agent")
        assertThat(factory.createForUnsealApi(matrixClient).listAgents().getOrThrow().single().botName).isEqualTo("unseal-agent")

        assertThat(homeserver.requestCount).isEqualTo(2)
        assertThat(unsealApi.requestCount).isEqualTo(1)
        assertThat(homeserver.takeRequest().path).isEqualTo("/chatbot/v1/agents")
        assertThat(homeserver.takeRequest().path).isEqualTo("/chatbot/v1/agents")
        assertThat(unsealApi.takeRequest().path).isEqualTo("/chatbot/v1/agents")
    }
}

private fun agentListResponse(botName: String): MockResponse {
    return MockResponse()
        .setResponseCode(200)
        .setBody("""[{"bot_name":"$botName"}]""")
}

private class FakeBaseUrlResolver(
    private val homeserverBaseUrl: String,
    private val unsealApiBaseUrl: String,
) : ChatbotBaseUrlResolver {
    override suspend fun resolveUnsealApiBaseUrl(serverName: String?): String = unsealApiBaseUrl
    override suspend fun resolveHomeserverBaseUrl(serverName: String?): String = homeserverBaseUrl
}
