/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 */

package io.element.android.features.broadcastusage.impl

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
import java.math.BigInteger

class BroadcastUsageServiceTest {
    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer() }
    @After fun tearDown() { server.close() }

    @Test
    fun `dashboard uses resolved API and bearer token without losing byte precision`() = runTest {
        server.enqueue(MockResponse().setBody(DASHBOARD))
        val service = createService()

        val result = service.dashboard(limit = 20, cursor = "opaque/value")

        assertThat(result.availableTrafficBytes).isEqualTo(BigInteger("18446744073709551616000"))
        assertThat(result.sessions.single().state).isEqualTo(BroadcastUsageSessionState.Live)
        assertThat(result.sessions.single().confirmedBytes).isEqualTo(BigInteger("9223372036854775808"))
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/api/broadcast-usage?limit=20&cursor=opaque%2Fvalue")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer runtime-token")
    }

    @Test
    fun `runtime parses presentation health and clamps polling hint`() = runTest {
        server.enqueue(MockResponse().setBody(RUNTIME))

        val result = createService().runtime("bcast_demo")

        assertThat(result.listenerCount).isEqualTo(7)
        assertThat(result.presentationHealthy).isEqualTo(1)
        assertThat(result.presentationTotal).isEqualTo(2)
        assertThat(result.pollAfterMs).isEqualTo(1_000)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/meeting-broadcast/v1/broadcasts/bcast_demo")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer runtime-token")
    }

    private fun createService(): BroadcastUsageService = BroadcastUsageService(
        matrixClient = FakeMatrixClient(
            userIdServerNameLambda = { "unseal.test" },
            currentAccessTokenLambda = { Result.success("runtime-token") },
        ),
        baseUrlResolver = object : ChatbotBaseUrlResolver {
            override suspend fun resolveUnsealApiBaseUrl(serverName: String?) = server.url("/").toString()
            override suspend fun resolveHomeserverBaseUrl(serverName: String?) = server.url("/").toString()
        },
        okHttpClient = { OkHttpClient() },
    )

    private companion object {
        val DASHBOARD = """
            {
              "userId":"@alice:unseal.test","availableTrafficBytes":"18446744073709551616000","pendingAllocationBytes":"0",
              "unallocatedTrafficBytes":"12","calculatedAt":"2026-08-05T10:00:00.000Z","sessionCount":1,
              "sessions":{"items":[{"sessionId":"11111111-1111-4111-8111-111111111111","broadcastId":"bcast_demo",
                "roomId":"!room:unseal.test","meetingInstanceId":"22222222-2222-4222-8222-222222222222","state":"live",
                "confirmedBytes":"9223372036854775808","allocatedBytes":"1","unallocatedBytes":"0","pendingAllocationBytes":"0",
                "syncedThrough":null,"openedAt":"2026-08-05T09:00:00.000Z","startedAt":"2026-08-05T09:01:00.000Z",
                "closedAt":null,"finalizedAt":null,"stopReason":null}],"nextCursor":"next"}
            }
        """.trimIndent()
        val RUNTIME = """
            {"version":1,"broadcast_id":"bcast_demo","room_id":"!room:unseal.test","meeting_instance_id":"22222222-2222-4222-8222-222222222222",
             "phase":"recovering","desired":"joined","agent_in_meeting":true,"broadcast_armed":true,"playable":false,
             "generation":1,"manifest_revision":2,"participant_count":3,"listener_count":7,
             "presentations":{"total":2,"healthy":1},"poll_after_ms":50,"access_mode":"authenticated"}
        """.trimIndent()
    }
}
