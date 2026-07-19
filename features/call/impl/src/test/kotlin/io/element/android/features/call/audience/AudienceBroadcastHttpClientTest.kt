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
import okhttp3.Interceptor
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

        assertThat(wrongMethod).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(wrongClientId).isInstanceOf(IllegalArgumentException::class.java)
        assertThat(server.requestCount).isEqualTo(0)
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

    @Test
    fun `native audience session and manifest resolve grant scoped presentation playlists`() = runTest {
        val manifestUrl = server.url("/live/g/grant_demo/bcast_demo/2/manifest.json")
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "session_id": "aud_demo",
                  "audience_client_id": "client_demo",
                  "broadcast_id": "bcast_demo",
                  "generation": 2,
                  "manifest_url": "$manifestUrl",
                  "expires_at": "2026-07-18T15:05:00Z",
                  "heartbeat_interval_ms": 15000
                }
                """.trimIndent()
            )
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "version": 1,
                  "broadcast_id": "bcast_demo",
                  "meeting_instance_id": "4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d",
                  "generation": 2,
                  "revision": 4,
                  "generated_at": "2026-07-18T14:00:00Z",
                  "presentations": [
                    {
                      "presentation_id": "user:alice",
                      "kind": "user",
                      "matrix_user_id": "@alice:keepsecret.io",
                      "matrix_device_id": "ALICE1",
                      "display_name": "Alice",
                      "avatar_url": null,
                      "source_id": null,
                      "audio": {"playlist_url": "media/alice/audio/master.m3u8"},
                      "video": {"playlist_url": "media/alice/video/master.m3u8", "width": 1280, "height": 720},
                      "active_speaker": true
                    }
                  ]
                }
                """.trimIndent()
            )
        )
        val client = createClient()

        val session = client.createAudienceSession(A_SESSION_ID, "bcast_demo", "client_demo")
        val manifest = client.getAudienceManifest(session.manifestUrl)

        assertThat(manifest.generation).isEqualTo(2)
        assertThat(manifest.presentations.single().audio?.playlistUrl)
            .isEqualTo(server.url("/live/g/grant_demo/bcast_demo/2/media/alice/audio/master.m3u8").toString())
        assertThat(manifest.presentations.single().video?.playlistUrl)
            .isEqualTo(server.url("/live/g/grant_demo/bcast_demo/2/media/alice/video/master.m3u8").toString())
        assertThat(server.takeRequest().getHeader("Authorization")).isEqualTo("Bearer runtime-token")
        assertThat(server.takeRequest().getHeader("Authorization")).isNull()
    }

    @Test
    fun `heartbeat parses generation replacement without creating another session`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "active": true,
                  "phase": "live",
                  "generation": 3,
                  "manifest_revision": 8,
                  "expires_at": "2026-07-18T15:05:15Z",
                  "playback": {
                    "generation": 3,
                    "manifest_url": "${server.url("/live/g/rotated/bcast_demo/3/manifest.json")}",
                    "expires_at": "2026-07-18T15:05:15Z"
                  }
                }
                """.trimIndent()
            )
        )
        val client = createClient()

        val heartbeat = client.heartbeatAudienceSession(A_SESSION_ID, "bcast_demo", "aud_demo", 2)

        assertThat(heartbeat.playback?.generation).isEqualTo(3)
        assertThat(server.takeRequest().body.readUtf8()).isEqualTo("{\"generation\":2}")
    }

    @Test
    fun `audience requests remove application interceptors before receiving signed grant URLs`() = runTest {
        val manifestUrl = server.url("/live/g/grant_demo/bcast_demo/2/manifest.json")
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "session_id": "aud_demo",
                  "audience_client_id": "client_demo",
                  "broadcast_id": "bcast_demo",
                  "generation": 2,
                  "manifest_url": "$manifestUrl",
                  "expires_at": "2026-07-18T15:05:00Z",
                  "heartbeat_interval_ms": 15000
                }
                """.trimIndent()
            )
        )
        val interceptedUrls = mutableListOf<String>()
        val loggingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                interceptedUrls += chain.request().url.toString()
                chain.proceed(chain.request())
            })
            .build()
        val client = createClient(loggingClient)

        client.createAudienceSession(A_SESSION_ID, "bcast_demo", "client_demo")

        assertThat(server.requestCount).isEqualTo(1)
        assertThat(interceptedUrls).isEmpty()
    }

    private fun createClient(okHttpClient: OkHttpClient = OkHttpClient()): AudienceBroadcastHttpClient {
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
                        currentAccessTokenLambda = { Result.success("runtime-token") },
                    )
                )
            },
            sessionStore = sessionStore,
            baseUrlResolver = object : ChatbotBaseUrlResolver {
                override suspend fun resolveUnsealApiBaseUrl(serverName: String?): String = server.url("/").toString()
                override suspend fun resolveHomeserverBaseUrl(serverName: String?): String = "https://matrix.invalid"
            },
            okHttpClient = { okHttpClient },
        )
    }
}
