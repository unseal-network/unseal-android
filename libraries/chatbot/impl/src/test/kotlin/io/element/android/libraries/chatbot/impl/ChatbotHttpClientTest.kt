/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.chatbot.api.ChatbotApiError
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.matrix.test.FakeMatrixClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class ChatbotHttpClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `requestRaw - fails before network when token is missing`() = runTest {
        val client = aClient(token = null)

        val result = client.requestRaw("/chatbot/v1/agents", ChatbotHttpMethod.GET)

        assertThat(result.exceptionOrNull()).isInstanceOf(ChatbotApiError.MissingAccessToken::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `requestRaw - sends bearer token and JSON headers`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"ok":true}"""))
        val client = aClient()

        val result = client.requestRaw("/chatbot/v1/agents?search=ai%20bot", ChatbotHttpMethod.GET)

        assertThat(result.getOrThrow()).isEqualTo("""{"ok":true}""")
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/api-root/chatbot/v1/agents?search=ai%20bot")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer mx-token")
        assertThat(request.getHeader("Accept")).isEqualTo("application/json")
    }

    @Test
    fun `requestRaw - sends JSON body for POST`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
        val client = aClient()

        client.requestRaw("/chatbot/v1/agents", ChatbotHttpMethod.POST, """{"bot_name":"helper"}""").getOrThrow()

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.getHeader("Content-Type")).contains("application/json")
        assertThat(request.body.readUtf8()).isEqualTo("""{"bot_name":"helper"}""")
    }

    @Test
    fun `requestRaw - maps non successful response and redacts secret fields`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"api_key":"secret","message":"bad"}"""))
        val client = aClient()

        val error = client.requestRaw("/chatbot/v1/agents", ChatbotHttpMethod.GET).exceptionOrNull()

        assertThat(error).isInstanceOf(ChatbotApiError.HttpError::class.java)
        val httpError = error as ChatbotApiError.HttpError
        assertThat(httpError.statusCode).isEqualTo(401)
        assertThat(httpError.body).doesNotContain("secret")
        assertThat(httpError.body).contains("[REDACTED]")
    }

    @Test
    fun `requestJson - maps decode failure and redacts raw body`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"api_key":"secret"}"""))
        val client = aClient()

        val error = client.requestJson<ChatbotAgent>("/chatbot/v1/agents/helper", ChatbotHttpMethod.GET).exceptionOrNull()

        assertThat(error).isInstanceOf(ChatbotApiError.DecodingError::class.java)
        val decodingError = error as ChatbotApiError.DecodingError
        assertThat(decodingError.bodySnippet).doesNotContain("secret")
        assertThat(decodingError.bodySnippet).contains("[REDACTED]")
    }

    @Test
    fun `streamRaw - dispatches SSE lines to callback`() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody("data: one\n\ndata: two\n")
        )
        val client = aClient()
        val chunks = mutableListOf<String>()

        val result = client.streamRaw("/chatbot/v1/stream/stream-1") { chunk ->
            chunks += chunk
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(chunks).containsExactly("data: one\n", "\n", "data: two\n").inOrder()
        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.getHeader("Accept")).isEqualTo("text/event-stream")
    }

    @Test
    fun `streamRaw - cancels underlying call when coroutine is cancelled`() = runTest {
        val executeStarted = CompletableDeferred<Unit>()
        val releaseExecute = CountDownLatch(1)
        val callWasCancelled = AtomicBoolean(false)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor { chain ->
                    executeStarted.complete(Unit)
                    releaseExecute.await(5, TimeUnit.SECONDS)
                    callWasCancelled.set(chain.call().isCanceled())
                    throw IOException("released")
                }
            )
            .build()
        val client = aClient(okHttpClient = okHttpClient)

        val streamJob = async(Dispatchers.IO) {
            client.streamRaw("/chatbot/v1/stream/stream-1") {
            }.getOrThrow()
        }
        withTimeout(5_000) {
            executeStarted.await()
        }

        streamJob.cancel()

        releaseExecute.countDown()
        streamJob.cancelAndJoin()
        assertThat(callWasCancelled.get()).isTrue()
    }

    @Test
    fun `streamRaw - does not execute call when coroutine is already cancelled`() = runTest {
        val interceptorWasCalled = AtomicBoolean(false)
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(
                Interceptor {
                    interceptorWasCalled.set(true)
                    throw IOException("unexpected execution")
                }
            )
            .build()
        val client = aClient(okHttpClient = okHttpClient)

        val streamJob = async(Dispatchers.IO) {
            cancel()
            client.streamRaw("/chatbot/v1/stream/stream-1") {
            }.getOrThrow()
        }

        streamJob.cancelAndJoin()
        assertThat(interceptorWasCalled.get()).isFalse()
    }

    private fun aClient(
        token: String? = "mx-token",
        okHttpClient: OkHttpClient = OkHttpClient(),
    ): ChatbotHttpClient {
        return ChatbotHttpClient(
            baseUrl = server.url("/api-root/").toString(),
            matrixClient = FakeMatrixClient(),
            okHttpClient = okHttpClient,
            tokenProvider = ChatbotAccessTokenProvider { token },
        )
    }
}
