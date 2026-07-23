/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.audience

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.api.AudienceAccessMode
import io.element.android.features.call.api.AudienceRelayDesired
import io.element.android.features.call.api.AudienceRuntimePhase
import io.element.android.features.call.impl.audience.AudienceBroadcastHttpClient
import io.element.android.libraries.chatbot.api.ChatbotBaseUrlResolver
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class AudienceBroadcastHttpClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `runtime status uses Matrix bearer token and parses room identity`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "version": 1,
                  "broadcast_id": "bcast_demo",
                  "room_id": "${A_ROOM_ID.value}",
                  "meeting_instance_id": "4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d",
                  "phase": "live",
                  "desired": "joined",
                  "agent_in_meeting": true,
                  "broadcast_armed": true,
                  "playable": true,
                  "generation": 1,
                  "manifest_revision": 1,
                  "participant_count": 1,
                  "listener_count": 0,
                  "presentations": {"total": 1, "healthy": 1},
                  "poll_after_ms": 5000
                }
                """.trimIndent()
            )
        )
        val client = createClient()

        val status = client.getRuntimeStatus(A_SESSION_ID, "bcast_demo")

        assertThat(status.roomId).isEqualTo(A_ROOM_ID)
        assertThat(status.phase).isEqualTo(AudienceRuntimePhase.Live)
        assertThat(status.playable).isTrue()
        assertThat(status.generation).isEqualTo(1)
        assertThat(status.manifestRevision).isEqualTo(1)
        assertThat(status.pollAfterMs).isEqualTo(1_000)
        assertThat(status.participantCount).isEqualTo(1)
        assertThat(status.listenerCount).isEqualTo(0)
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/meeting-broadcast/v1/broadcasts/bcast_demo")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer runtime-token")
    }

    @Test
    fun `request fails when the current Matrix token is unavailable instead of using a stored token`() = runTest {
        val client = createClient(
            currentAccessToken = Result.failure(IllegalStateException("expired")),
        )

        val result = runCatching {
            client.getRuntimeStatus(A_SESSION_ID, "bcast_demo")
        }

        assertThat(result.exceptionOrNull()?.message).isEqualTo("Matrix access token is unavailable")
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `room discovery parser accepts the canonical state event and tombstone`() = runTest {
        val client = createClient()

        val discovery = client.parseDiscovery(
            """
            {
              "version": 1,
              "broadcast_id": "bcast_demo",
              "meeting_instance_id": "4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d",
              "access_mode": "room_members"
            }
            """.trimIndent()
        )
        val tombstone = client.parseDiscovery("{}")
        val malformed = client.parseDiscovery("{")
        val wrongType = client.parseDiscovery(
            """{"version":{},"broadcast_id":"bcast_demo","meeting_instance_id":"4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d","access_mode":"room_members"}"""
        )

        assertThat(discovery?.broadcastId).isEqualTo("bcast_demo")
        assertThat(discovery?.accessMode).isEqualTo(AudienceAccessMode.RoomMembers)
        assertThat(tombstone).isNull()
        assertThat(malformed).isNull()
        assertThat(wrongType).isNull()
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `widget bridge only forwards contract method path and body combinations`() = runTest {
        val client = createClient()
        val heartbeatPath =
            "/meeting-broadcast/v1/broadcasts/bcast_demo/audience-sessions/aud_demo/heartbeat"

        val wrongMethod = runCatching {
            client.requestAudienceWidget(
                sessionId = A_SESSION_ID,
                broadcastId = "bcast_demo",
                method = "DELETE",
                path = heartbeatPath,
                body = null,
            )
        }.exceptionOrNull()
        val wrongClientId = runCatching {
            client.requestAudienceWidget(
                sessionId = A_SESSION_ID,
                broadcastId = "bcast_demo",
                method = "POST",
                path = "/meeting-broadcast/v1/broadcasts/bcast_demo/audience-sessions",
                body = buildJsonObject { put("audience_client_id", "other") },
            )
        }.exceptionOrNull()
        val obsoleteRenegotiation = runCatching {
            client.requestAudienceWidget(
                sessionId = A_SESSION_ID,
                broadcastId = "bcast_demo",
                method = "POST",
                path = "/meeting-broadcast/v1/broadcasts/bcast_demo/audience-sessions/aud_demo/webrtc/renegotiate",
                body = null,
            )
        }.exceptionOrNull()

        assertThat(wrongMethod).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(wrongClientId).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(obsoleteRenegotiation).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(server.requestCount).isEqualTo(0)
    }

    @Test
    fun `widget bridge forwards Cloudflare receiver offer answer and first-media commit signaling`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        val client = createClient()
        val sessionPath = "/meeting-broadcast/v1/broadcasts/bcast_demo/audience-sessions/aud_demo"

        client.requestAudienceWidget(
            sessionId = A_SESSION_ID,
            broadcastId = "bcast_demo",
            method = "POST",
            path = "$sessionPath/webrtc/offer",
            body = null,
        )
        client.requestAudienceWidget(
            sessionId = A_SESSION_ID,
            broadcastId = "bcast_demo",
            method = "POST",
            path = "$sessionPath/webrtc/answer",
            body = buildJsonObject {
                put("receiver_session_id", "receiver-2")
                put("answer", buildJsonObject {
                    put("type", "answer")
                    put("sdp", "v=0\\r\\n")
                })
            },
        )
        client.requestAudienceWidget(
            sessionId = A_SESSION_ID,
            broadcastId = "bcast_demo",
            method = "POST",
            path = "$sessionPath/webrtc/commit",
            body = buildJsonObject { put("receiver_session_id", "receiver-2") },
        )

        assertThat(server.takeRequest().path).isEqualTo("$sessionPath/webrtc/offer")
        assertThat(server.takeRequest().path).isEqualTo("$sessionPath/webrtc/answer")
        assertThat(server.takeRequest().path).isEqualTo("$sessionPath/webrtc/commit")
    }

    @Test
    fun `relay control uses Unseal API origin and canonical desired body`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "broadcast_id": "bcast_demo",
                  "room_id": "${A_ROOM_ID.value}",
                  "meeting_instance_id": "4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d",
                  "desired": "joined",
                  "phase": "joining",
                  "agent_in_meeting": false,
                  "broadcast_armed": false,
                  "playable": false
                }
                """.trimIndent()
            )
        )
        val client = createClient()

        client.setRelayDesired(
            A_SESSION_ID,
            A_ROOM_ID,
            "4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d",
            AudienceRelayDesired.Joined,
            AudienceAccessMode.RoomMembers,
        )

        val request = server.takeRequest()
        assertThat(request.path).contains("/meeting-broadcast/v1/rooms/")
        assertThat(request.body.readUtf8()).isEqualTo("{\"desired\":\"joined\",\"access_mode\":\"room_members\"}")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer runtime-token")
    }

    private fun createClient(
        okHttpClient: OkHttpClient = OkHttpClient(),
        currentAccessToken: Result<String> = Result.success("runtime-token"),
    ): AudienceBroadcastHttpClient {
        val sessionStore = InMemorySessionStore(
            initialList = listOf(
                aSessionData(
                    sessionId = A_SESSION_ID.value,
                    accessToken = "stored-token",
                ).copy(homeserverUrl = "https://matrix.invalid")
            )
        )
        return AudienceBroadcastHttpClient(
            matrixClientProvider = FakeMatrixClientProvider {
                Result.success(
                    FakeMatrixClient(
                        userIdServerNameLambda = { "keepsecret.io" },
                        currentAccessTokenLambda = { currentAccessToken },
                    )
                )
            },
            sessionStore = sessionStore,
            baseUrlResolver = object : ChatbotBaseUrlResolver {
                override suspend fun resolveUnsealApiBaseUrl(serverName: String?): String = server.url("/").toString()
                override suspend fun resolveHomeserverBaseUrl(serverName: String?): String = server.url("/").toString()
            },
            okHttpClient = { okHttpClient },
        )
    }
}
