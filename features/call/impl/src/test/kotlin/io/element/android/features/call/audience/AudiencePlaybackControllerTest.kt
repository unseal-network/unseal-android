/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.audience

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.api.AudienceRelayDesired
import io.element.android.features.call.api.AudienceRuntimePhase
import io.element.android.features.call.api.AudienceRuntimeStatus
import io.element.android.features.call.impl.audience.AudienceBroadcastHttpClient
import io.element.android.features.call.impl.audience.AudienceHeartbeat
import io.element.android.features.call.impl.audience.AudienceHttpException
import io.element.android.features.call.impl.audience.AudienceManifest
import io.element.android.features.call.impl.audience.AudiencePlaybackGrant
import io.element.android.features.call.impl.audience.AudiencePlaybackState
import io.element.android.features.call.impl.audience.AudienceSession
import io.element.android.features.call.impl.audience.DefaultAudiencePlaybackController
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.services.toolbox.api.systemclock.SystemClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AudiencePlaybackControllerTest {
    @Test
    fun `generation replacement switches the native presentation manifest in the same audience session`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        val firstManifest = aManifest(generation = 1, revision = 1)
        val secondManifest = aManifest(generation = 2, revision = 1)
        coEvery { httpClient.createAudienceSession(A_SESSION_ID, "bcast_demo", "client_demo") } returns AudienceSession(
            sessionId = "aud_demo",
            audienceClientId = "client_demo",
            broadcastId = "bcast_demo",
            generation = 1,
            manifestUrl = "https://keepsecret.io/live/g/one/bcast_demo/1/manifest.json",
            expiresAt = "2026-07-18T15:05:00Z",
            heartbeatIntervalMs = 1_000,
        )
        coEvery { httpClient.getAudienceManifest("https://keepsecret.io/live/g/one/bcast_demo/1/manifest.json") } returns firstManifest
        coEvery { httpClient.heartbeatAudienceSession(A_SESSION_ID, "bcast_demo", "aud_demo", 1) } returns AudienceHeartbeat(
            active = true,
            phase = AudienceRuntimePhase.Live,
            generation = 2,
            manifestRevision = 1,
            expiresAt = "2026-07-18T15:05:15Z",
            playback = AudiencePlaybackGrant(
                generation = 2,
                manifestUrl = "https://keepsecret.io/live/g/two/bcast_demo/2/manifest.json",
                expiresAt = "2026-07-18T15:05:15Z",
            ),
        )
        coEvery { httpClient.getAudienceManifest("https://keepsecret.io/live/g/two/bcast_demo/2/manifest.json") } returns secondManifest
        coEvery { httpClient.closeAudienceSession(A_SESSION_ID, "bcast_demo", "aud_demo") } returns Unit
        val controller = controller(httpClient)

        controller.observe(A_SESSION_ID, "bcast_demo", "client_demo").test {
            assertThat(awaitItem()).isEqualTo(AudiencePlaybackState.Connecting)
            assertThat((awaitItem() as AudiencePlaybackState.Live).manifest.generation).isEqualTo(1)

            advanceTimeBy(1_000)
            runCurrent()

            assertThat((awaitItem() as AudiencePlaybackState.Live).manifest.generation).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { httpClient.createAudienceSession(A_SESSION_ID, "bcast_demo", "client_demo") }
        coVerify(exactly = 1) { httpClient.closeAudienceSession(A_SESSION_ID, "bcast_demo", "aud_demo") }
    }

    @Test
    fun `runtime polling refreshes active speaker revisions before the audience heartbeat`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        val manifestUrl = "https://keepsecret.io/live/g/one/bcast_demo/1/manifest.json"
        val firstManifest = aManifest(generation = 1, revision = 1)
        val revisedManifest = aManifest(generation = 1, revision = 2)
        coEvery { httpClient.createAudienceSession(A_SESSION_ID, "bcast_demo", "client_demo") } returns AudienceSession(
            sessionId = "aud_demo",
            audienceClientId = "client_demo",
            broadcastId = "bcast_demo",
            generation = 1,
            manifestUrl = manifestUrl,
            expiresAt = "2026-07-18T15:05:00Z",
            heartbeatIntervalMs = 15_000,
        )
        coEvery { httpClient.getAudienceManifest(manifestUrl) } returnsMany listOf(firstManifest, revisedManifest)
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } returns AudienceRuntimeStatus(
            broadcastId = "bcast_demo",
            roomId = A_ROOM_ID,
            meetingInstanceId = firstManifest.meetingInstanceId,
            phase = AudienceRuntimePhase.Live,
            desired = AudienceRelayDesired.Joined,
            agentInMeeting = true,
            broadcastArmed = true,
            playable = true,
            pollAfterMs = 1_000,
            generation = 1,
            manifestRevision = 2,
            participantCount = 3,
            listenerCount = 7,
        )
        coEvery { httpClient.closeAudienceSession(A_SESSION_ID, "bcast_demo", "aud_demo") } returns Unit
        val controller = controller(httpClient)

        controller.observe(A_SESSION_ID, "bcast_demo", "client_demo").test {
            assertThat(awaitItem()).isEqualTo(AudiencePlaybackState.Connecting)
            assertThat((awaitItem() as AudiencePlaybackState.Live).manifest.revision).isEqualTo(1)

            advanceTimeBy(1_000)
            runCurrent()

            val revised = awaitItem() as AudiencePlaybackState.Live
            assertThat(revised.manifest.revision).isEqualTo(2)
            assertThat(revised.counts.participantCount).isEqualTo(3)
            assertThat(revised.counts.listenerCount).isEqualTo(7)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") }
        coVerify(exactly = 0) { httpClient.heartbeatAudienceSession(A_SESSION_ID, "bcast_demo", "aud_demo", 1) }
    }

    @Test
    fun `playback ends at the signed grant expiry without another status request`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        val manifestUrl = "https://keepsecret.io/live/g/one/bcast_demo/1/manifest.json"
        coEvery { httpClient.createAudienceSession(A_SESSION_ID, "bcast_demo", "client_demo") } returns AudienceSession(
            sessionId = "aud_demo",
            audienceClientId = "client_demo",
            broadcastId = "bcast_demo",
            generation = 1,
            manifestUrl = manifestUrl,
            expiresAt = "2026-07-18T15:00:01Z",
            heartbeatIntervalMs = 15_000,
        )
        coEvery { httpClient.getAudienceManifest(manifestUrl) } returns aManifest(1, 1)
        coEvery { httpClient.closeAudienceSession(A_SESSION_ID, "bcast_demo", "aud_demo") } returns Unit
        val controller = controller(httpClient)

        controller.observe(A_SESSION_ID, "bcast_demo", "client_demo").test {
            assertThat(awaitItem()).isEqualTo(AudiencePlaybackState.Connecting)
            assertThat(awaitItem()).isInstanceOf(AudiencePlaybackState.Live::class.java)

            advanceTimeBy(1_000)
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AudiencePlaybackState.Ended)
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") }
        coVerify(exactly = 0) {
            httpClient.heartbeatAudienceSession(A_SESSION_ID, "bcast_demo", "aud_demo", 1)
        }
    }

    @Test
    fun `expired audience session response maps to ended`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        val manifestUrl = "https://keepsecret.io/live/g/one/bcast_demo/1/manifest.json"
        coEvery { httpClient.createAudienceSession(A_SESSION_ID, "bcast_demo", "client_demo") } returns AudienceSession(
            sessionId = "aud_demo",
            audienceClientId = "client_demo",
            broadcastId = "bcast_demo",
            generation = 1,
            manifestUrl = manifestUrl,
            expiresAt = "2026-07-18T15:05:00Z",
            heartbeatIntervalMs = 15_000,
        )
        coEvery { httpClient.getAudienceManifest(manifestUrl) } returns aManifest(1, 1)
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } throws AudienceHttpException(
            401,
            """{"error":{"code":"audience_session_expired","retryable":false}}""",
        )
        coEvery { httpClient.closeAudienceSession(A_SESSION_ID, "bcast_demo", "aud_demo") } returns Unit
        val controller = controller(httpClient)

        controller.observe(A_SESSION_ID, "bcast_demo", "client_demo").test {
            assertThat(awaitItem()).isEqualTo(AudiencePlaybackState.Connecting)
            assertThat(awaitItem()).isInstanceOf(AudiencePlaybackState.Live::class.java)

            advanceTimeBy(1_000)
            runCurrent()

            assertThat(awaitItem()).isEqualTo(AudiencePlaybackState.Ended)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        httpClient: AudienceBroadcastHttpClient,
    ): DefaultAudiencePlaybackController {
        val startMs = Instant.parse("2026-07-18T15:00:00Z").toEpochMilli()
        return DefaultAudiencePlaybackController(
            httpClient = httpClient,
            systemClock = SystemClock { startMs + testScheduler.currentTime },
        )
    }

    private fun aManifest(generation: Int, revision: Int) = AudienceManifest(
        broadcastId = "bcast_demo",
        meetingInstanceId = "4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d",
        generation = generation,
        revision = revision,
        presentations = emptyList(),
    )
}
