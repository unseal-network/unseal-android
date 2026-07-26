/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.audience

import android.content.SharedPreferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.api.AudienceAccessMode
import io.element.android.features.call.api.AudienceRelayDesired
import io.element.android.features.call.api.AudienceRuntimePhase
import io.element.android.features.call.api.AudienceRuntimeStatus
import io.element.android.features.call.impl.audience.AudienceBroadcastHttpClient
import io.element.android.features.call.impl.audience.AudienceHttpException
import io.element.android.features.call.impl.audience.DefaultAudienceBroadcastService
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.FakeMatrixClientProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultAudienceBroadcastServiceTest {
    @Test
    fun `room discovery falls back to authoritative Matrix state when the local joined room is unavailable`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        every { httpClient.parseDiscovery("canonical") } returns aDiscovery()
        coEvery { httpClient.getMatrixDiscoveryState(A_SESSION_ID, A_ROOM_ID) } returns "canonical"
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } returns aRuntime(
            phase = AudienceRuntimePhase.Live,
            armed = true,
        )
        val service = createService(httpClient, FakeMatrixClient())

        service.observeRoomDiscovery(A_SESSION_ID, A_ROOM_ID).test {
            assertThat(awaitItem()).isNull()
            assertThat(awaitItem()?.broadcastId).isEqualTo("bcast_demo")
            coVerify(exactly = 1) { httpClient.getMatrixDiscoveryState(A_SESSION_ID, A_ROOM_ID) }
            coVerify(exactly = 1) { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `room discovery retries a cold Matrix state cache before polling the audience runtime`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        every { httpClient.parseDiscovery("canonical") } returns aDiscovery()
        coEvery { httpClient.getMatrixDiscoveryState(A_SESSION_ID, A_ROOM_ID) } returns null
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } returns aRuntime(
            phase = AudienceRuntimePhase.Live,
            armed = true,
        )
        var stateReads = 0
        val room = io.element.android.libraries.matrix.test.room.FakeJoinedRoom(
            baseRoom = io.element.android.libraries.matrix.test.room.FakeBaseRoom(
                getStateEventJsonResult = { _, _ ->
                    stateReads++
                    if (stateReads == 1) Result.failure(IllegalStateException("state cache is cold"))
                    else Result.success("canonical")
                },
            ),
        )
        val matrixClient = FakeMatrixClient().apply { givenGetRoomResult(A_ROOM_ID, room) }
        val service = createService(httpClient, matrixClient)

        service.observeRoomDiscovery(A_SESSION_ID, A_ROOM_ID).test {
            assertThat(awaitItem()).isNull()
            coVerify(exactly = 0) { httpClient.getRuntimeStatus(A_SESSION_ID, any()) }

            advanceTimeBy(1_000)
            assertThat(awaitItem()?.broadcastId).isEqualTo("bcast_demo")
            coVerify(exactly = 1) { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `room discovery retries a successful null from a cold custom state cache`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        every { httpClient.parseDiscovery("canonical") } returns aDiscovery()
        coEvery { httpClient.getMatrixDiscoveryState(A_SESSION_ID, A_ROOM_ID) } returns null
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } returns aRuntime(
            phase = AudienceRuntimePhase.Live,
            armed = true,
        )
        var stateReads = 0
        val room = io.element.android.libraries.matrix.test.room.FakeJoinedRoom(
            baseRoom = io.element.android.libraries.matrix.test.room.FakeBaseRoom(
                getStateEventJsonResult = { _, _ ->
                    stateReads++
                    Result.success(if (stateReads == 1) null else "canonical")
                },
            ),
        )
        val matrixClient = FakeMatrixClient().apply { givenGetRoomResult(A_ROOM_ID, room) }
        val service = createService(httpClient, matrixClient)

        service.observeRoomDiscovery(A_SESSION_ID, A_ROOM_ID).test {
            assertThat(awaitItem()).isNull()
            coVerify(exactly = 0) { httpClient.getRuntimeStatus(A_SESSION_ID, any()) }

            advanceTimeBy(1_000)
            assertThat(awaitItem()?.broadcastId).isEqualTo("bcast_demo")
            coVerify(exactly = 1) { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `room discovery stays idle when the room has no broadcast state event`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        coEvery { httpClient.getMatrixDiscoveryState(A_SESSION_ID, A_ROOM_ID) } returns null
        val room = io.element.android.libraries.matrix.test.room.FakeJoinedRoom(
            baseRoom = io.element.android.libraries.matrix.test.room.FakeBaseRoom(
                getStateEventJsonResult = { _, _ -> Result.success(null) },
            ),
        )
        val matrixClient = FakeMatrixClient().apply { givenGetRoomResult(A_ROOM_ID, room) }
        val service = createService(httpClient, matrixClient)

        service.observeRoomDiscovery(A_SESSION_ID, A_ROOM_ID).test {
            assertThat(awaitItem()).isNull()
            coVerify(exactly = 0) { httpClient.getRuntimeStatus(A_SESSION_ID, any()) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `room discovery fetches the Matrix state event before starting audience polling`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        every { httpClient.parseDiscovery("canonical") } returns aDiscovery()
        coEvery { httpClient.getMatrixDiscoveryState(A_SESSION_ID, A_ROOM_ID) } returns "canonical"
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } returns aRuntime(
            phase = AudienceRuntimePhase.Live,
            armed = true,
        )
        val room = io.element.android.libraries.matrix.test.room.FakeJoinedRoom(
            baseRoom = io.element.android.libraries.matrix.test.room.FakeBaseRoom(
                getStateEventJsonResult = { _, _ -> Result.success(null) },
            ),
        )
        val matrixClient = FakeMatrixClient().apply { givenGetRoomResult(A_ROOM_ID, room) }
        val service = createService(httpClient, matrixClient)

        service.observeRoomDiscovery(A_SESSION_ID, A_ROOM_ID).test {
            assertThat(awaitItem()).isNull()
            assertThat(awaitItem()?.broadcastId).isEqualTo("bcast_demo")
            coVerify(exactly = 1) { httpClient.getMatrixDiscoveryState(A_SESSION_ID, A_ROOM_ID) }
            coVerify(exactly = 1) { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `direct audience runtime resolution survives a transient API failure`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        var statusRequests = 0
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } answers {
            statusRequests++
            if (statusRequests == 1) throw AudienceHttpException(503, "restarting")
            aRuntime(armed = true)
        }
        val service = createService(httpClient, FakeMatrixClient())

        val result = service.awaitRuntimeStatus(A_SESSION_ID, "bcast_demo")

        assertThat(result.getOrThrow().broadcastId).isEqualTo("bcast_demo")
        assertThat(statusRequests).isEqualTo(2)
    }

    @Test
    fun `terminal runtime stops status polling until the room state changes`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        every { httpClient.parseDiscovery(any()) } returns aDiscovery()
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } returns aRuntime(
            phase = AudienceRuntimePhase.Ended,
            desired = AudienceRelayDesired.Left,
            armed = false,
        )
        val room = io.element.android.libraries.matrix.test.room.FakeJoinedRoom(
            baseRoom = io.element.android.libraries.matrix.test.room.FakeBaseRoom(
                getStateEventJsonResult = { _, _ -> Result.success("canonical") },
            ),
            syncUpdateFlow = MutableStateFlow(0L),
        )
        val matrixClient = FakeMatrixClient().apply { givenGetRoomResult(A_ROOM_ID, room) }
        val service = createService(httpClient, matrixClient)

        service.observeRoomDiscovery(A_SESSION_ID, A_ROOM_ID).test {
            assertThat(awaitItem()).isNull()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") }
    }

    @Test
    fun `discovery tombstone reconciles published host state while the meeting remains active`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        every { httpClient.parseDiscovery("canonical") } returns aDiscovery()
        every { httpClient.parseDiscovery("{}") } returns null
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } returns aRuntime(
            phase = AudienceRuntimePhase.Live,
            armed = true,
        )
        var stateEvent = "canonical"
        val syncUpdateFlow = MutableStateFlow(0L)
        val room = io.element.android.libraries.matrix.test.room.FakeJoinedRoom(
            baseRoom = io.element.android.libraries.matrix.test.room.FakeBaseRoom(
                getStateEventJsonResult = { _, _ -> Result.success(stateEvent) },
            ),
            syncUpdateFlow = syncUpdateFlow,
        )
        val matrixClient = FakeMatrixClient().apply { givenGetRoomResult(A_ROOM_ID, room) }
        val service = createService(httpClient, matrixClient)

        service.observeRoomDiscovery(A_SESSION_ID, A_ROOM_ID).test {
            assertThat(awaitItem()?.broadcastId).isEqualTo("bcast_demo")
            stateEvent = "{}"
            syncUpdateFlow.value++
            assertThat(awaitItem()).isNull()
            assertThat(service.observeHostControl(A_SESSION_ID, A_ROOM_ID).value.desired)
                .isEqualTo(AudienceRelayDesired.Left)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `temporary custom state cache miss retains a previously observed broadcast`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        every { httpClient.parseDiscovery("canonical") } returns aDiscovery()
        coEvery { httpClient.getRuntimeStatus(A_SESSION_ID, "bcast_demo") } returns aRuntime(
            phase = AudienceRuntimePhase.Live,
            armed = true,
        )
        var stateEvent: String? = "canonical"
        val syncUpdateFlow = MutableStateFlow(0L)
        val room = io.element.android.libraries.matrix.test.room.FakeJoinedRoom(
            baseRoom = io.element.android.libraries.matrix.test.room.FakeBaseRoom(
                getStateEventJsonResult = { _, _ -> Result.success(stateEvent) },
            ),
            syncUpdateFlow = syncUpdateFlow,
        )
        val matrixClient = FakeMatrixClient().apply { givenGetRoomResult(A_ROOM_ID, room) }
        val service = createService(httpClient, matrixClient)

        service.observeRoomDiscovery(A_SESSION_ID, A_ROOM_ID).test {
            assertThat(awaitItem()?.broadcastId).isEqualTo("bcast_demo")
            stateEvent = null
            syncUpdateFlow.value++
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `repeated enable keeps one meeting fence and updates access mode idempotently`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        val meetingIds = mutableListOf<String>()
        coEvery {
            httpClient.setRelayDesired(A_SESSION_ID, A_ROOM_ID, capture(meetingIds), AudienceRelayDesired.Joined, any())
        } answers { aRuntime(meetingInstanceId = thirdArg(), armed = false) }
        val service = createService(httpClient, FakeMatrixClient())

        val first = service.enableRelay(A_SESSION_ID, A_ROOM_ID, AudienceAccessMode.Authenticated).getOrThrow()
        val second = service.enableRelay(A_SESSION_ID, A_ROOM_ID, AudienceAccessMode.RoomMembers).getOrThrow()

        assertThat(meetingIds).hasSize(2)
        assertThat(meetingIds.distinct()).hasSize(1)
        assertThat(first.broadcastArmed).isFalse()
        assertThat(second.accessMode).isEqualTo(AudienceAccessMode.RoomMembers)
        coVerify(exactly = 0) { httpClient.getRuntimeStatus(A_SESSION_ID, any()) }
    }

    @Test
    fun `relay join acknowledgement does not poll runtime before discovery`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        coEvery {
            httpClient.setRelayDesired(A_SESSION_ID, A_ROOM_ID, any(), AudienceRelayDesired.Joined, any())
        } answers { aRuntime(meetingInstanceId = thirdArg(), armed = false) }
        val service = createService(httpClient, FakeMatrixClient())

        val result = service.enableRelay(A_SESSION_ID, A_ROOM_ID, AudienceAccessMode.Authenticated)

        assertThat(result.getOrThrow().phase).isEqualTo(AudienceRuntimePhase.Joining)
        assertThat(result.getOrThrow().broadcastArmed).isFalse()
        coVerify(exactly = 0) { httpClient.getRuntimeStatus(A_SESSION_ID, any()) }
    }

    @Test
    fun `relay leave acknowledgement does not poll runtime before discovery`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        coEvery {
            httpClient.setRelayDesired(A_SESSION_ID, A_ROOM_ID, any(), AudienceRelayDesired.Joined, any())
        } answers { aRuntime(meetingInstanceId = thirdArg(), armed = false) }
        coEvery {
            httpClient.setRelayDesired(A_SESSION_ID, A_ROOM_ID, any(), AudienceRelayDesired.Left, null)
        } answers {
            aRuntime(
                meetingInstanceId = thirdArg(),
                phase = AudienceRuntimePhase.Leaving,
                desired = AudienceRelayDesired.Left,
                armed = false,
            )
        }
        val service = createService(httpClient, FakeMatrixClient())
        service.enableRelay(A_SESSION_ID, A_ROOM_ID, AudienceAccessMode.Authenticated).getOrThrow()

        val result = service.disableRelay(A_SESSION_ID, A_ROOM_ID)

        assertThat(result.getOrThrow().phase).isEqualTo(AudienceRuntimePhase.Leaving)
        assertThat(result.getOrThrow().desired).isEqualTo(AudienceRelayDesired.Left)
        coVerify(exactly = 0) { httpClient.getRuntimeStatus(A_SESSION_ID, any()) }
    }

    @Test
    fun `failed and cancelled control operations restore the previous host state`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        coEvery {
            httpClient.setRelayDesired(A_SESSION_ID, A_ROOM_ID, any(), any(), any())
        } throws IllegalStateException("offline")
        val service = createService(httpClient, FakeMatrixClient())
        val publishedState = service.observeHostControl(A_SESSION_ID, A_ROOM_ID)

        val failed = service.enableRelay(A_SESSION_ID, A_ROOM_ID, AudienceAccessMode.Authenticated)

        assertThat(failed.isFailure).isTrue()
        assertThat(publishedState.value.desired).isEqualTo(AudienceRelayDesired.Left)
        assertThat(publishedState.value.isUpdating).isFalse()
        assertThat(publishedState.value.errorMessage).isEqualTo("offline")

        val requestStarted = CompletableDeferred<Unit>()
        coEvery {
            httpClient.setRelayDesired(A_SESSION_ID, A_ROOM_ID, any(), any(), any())
        } coAnswers {
            requestStarted.complete(Unit)
            awaitCancellation()
        }
        val control = launch {
            service.enableRelay(A_SESSION_ID, A_ROOM_ID, AudienceAccessMode.RoomMembers)
        }
        requestStarted.await()
        control.cancel()
        runCurrent()

        assertThat(publishedState.value.desired).isEqualTo(AudienceRelayDesired.Left)
        assertThat(publishedState.value.isUpdating).isFalse()
    }

    @Test
    fun `clearing a meeting resets the published state and creates a fresh UUID fence`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        val meetingIds = mutableListOf<String>()
        coEvery {
            httpClient.setRelayDesired(A_SESSION_ID, A_ROOM_ID, capture(meetingIds), AudienceRelayDesired.Joined, any())
        } answers { aRuntime(meetingInstanceId = thirdArg(), armed = true) }
        val service = createService(httpClient, FakeMatrixClient())
        val publishedState = service.observeHostControl(A_SESSION_ID, A_ROOM_ID)

        service.enableRelay(A_SESSION_ID, A_ROOM_ID, AudienceAccessMode.Authenticated).getOrThrow()
        service.clearMeetingFence(A_SESSION_ID, A_ROOM_ID, force = true)
        val samePublishedState = service.observeHostControl(A_SESSION_ID, A_ROOM_ID)
        service.enableRelay(A_SESSION_ID, A_ROOM_ID, AudienceAccessMode.Authenticated).getOrThrow()

        assertThat(samePublishedState).isSameInstanceAs(publishedState)
        assertThat(meetingIds).hasSize(2)
        assertThat(meetingIds.distinct()).hasSize(2)
    }

    @Test
    fun `relay control rejects acknowledgement from another meeting and restores the previous state`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient>()
        coEvery {
            httpClient.setRelayDesired(A_SESSION_ID, A_ROOM_ID, any(), AudienceRelayDesired.Joined, any())
        } returns aRuntime(meetingInstanceId = OTHER_MEETING_ID, armed = false)
        val service = createService(httpClient, FakeMatrixClient())

        val result = service.enableRelay(A_SESSION_ID, A_ROOM_ID, AudienceAccessMode.Authenticated)

        assertThat(result.isFailure).isTrue()
        assertThat(service.observeHostControl(A_SESSION_ID, A_ROOM_ID).value.desired).isEqualTo(AudienceRelayDesired.Left)
    }

    private fun createService(
        httpClient: AudienceBroadcastHttpClient,
        matrixClient: FakeMatrixClient,
    ): DefaultAudienceBroadcastService = DefaultAudienceBroadcastService(
        httpClient = httpClient,
        matrixClientProvider = FakeMatrixClientProvider { Result.success(matrixClient) },
        sharedPreferences = fakeSharedPreferences(),
    )

    private fun fakeSharedPreferences(): SharedPreferences {
        val values = mutableMapOf<String, String>()
        val editor = mockk<SharedPreferences.Editor>()
        every { editor.putString(any(), any()) } answers {
            values[firstArg()] = secondArg()
            editor
        }
        every { editor.remove(any()) } answers {
            values.remove(firstArg())
            editor
        }
        every { editor.apply() } returns Unit
        return mockk {
            every { getString(any(), any()) } answers { values[firstArg()] ?: secondArg() }
            every { edit() } returns editor
        }
    }
}

private fun aDiscovery() = io.element.android.features.call.api.AudienceBroadcastDiscovery(
    broadcastId = "bcast_demo",
    meetingInstanceId = MEETING_ID,
    accessMode = AudienceAccessMode.Authenticated,
    phase = AudienceRuntimePhase.Live,
)

private fun aRuntime(
    meetingInstanceId: String = MEETING_ID,
    phase: AudienceRuntimePhase = AudienceRuntimePhase.Joining,
    desired: AudienceRelayDesired = AudienceRelayDesired.Joined,
    armed: Boolean,
) = AudienceRuntimeStatus(
    broadcastId = "bcast_demo",
    roomId = A_ROOM_ID,
    meetingInstanceId = meetingInstanceId,
    phase = phase,
    desired = desired,
    agentInMeeting = armed,
    broadcastArmed = armed,
    playable = phase == AudienceRuntimePhase.Live,
    pollAfterMs = 250,
)

private const val MEETING_ID = "4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d"
private const val OTHER_MEETING_ID = "5e2d75b8-7e1b-4a57-9f47-6cc1034ec2b8"
