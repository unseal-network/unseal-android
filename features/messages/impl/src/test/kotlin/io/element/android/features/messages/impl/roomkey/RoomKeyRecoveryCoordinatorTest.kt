/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryProgress
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryScope
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryStage
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryTarget
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class RoomKeyRecoveryCoordinatorTest {
    private val clock = MutableClock()
    private val pendingStore = RoomKeyRecoveryPendingStore(clock = clock)
    private val progressStore = RoomKeyRecoveryProgressStore(clock = clock)
    private val requester = FakeRoomKeyRecoveryRequester()

    @Test
    fun `recover returns checking status when device verification is unknown`() = runRoomKeyRecoveryTest {
        val result = aCoordinator().recover(
            aInput(verificationState = RoomKeyRecoveryVerificationState.Unknown)
        )

        assertThat(result.statusFor(aRequest())).isInstanceOf(RoomKeyRecoveryStatus.CheckingDeviceVerification::class.java)
        assertThat(requester.requests).isEmpty()
    }

    @Test
    fun `recover returns unverified status when device is not verified`() = runRoomKeyRecoveryTest {
        val result = aCoordinator().recover(
            aInput(verificationState = RoomKeyRecoveryVerificationState.NotVerified)
        )

        assertThat(result.statusFor(aRequest())).isInstanceOf(RoomKeyRecoveryStatus.DeviceUnverified::class.java)
        assertThat(requester.requests).isEmpty()
    }

    @Test
    fun `recover suppresses active request`() = runRoomKeyRecoveryTest {
        progressStore.startStage(
            stage = RoomKeyRecoveryDisplayStage.Sender,
            request = aRequest(),
            planStages = listOf(RoomKeyRecoveryDisplayStage.Sender),
            duration = 60.seconds,
        )

        val result = aCoordinator().recover(aInput())

        val status = result.statusFor(aRequest()) as RoomKeyRecoveryStatus.Active
        assertThat(status.currentStage).isEqualTo(RoomKeyRecoveryDisplayStage.Sender)
        assertThat(status.remaining).isEqualTo(60.seconds)
        assertThat(requester.requests).isEmpty()
    }

    @Test
    fun `recover suppresses pending request`() = runRoomKeyRecoveryTest {
        pendingStore.markPendingIfNeeded(aRequest())

        val result = aCoordinator().recover(aInput())

        assertThat(result.statusFor(aRequest())).isInstanceOf(RoomKeyRecoveryStatus.Pending::class.java)
        assertThat(requester.requests).isEmpty()
    }

    @Test
    fun `recover resumes expired progress from next stage even when request is pending`() = runRoomKeyRecoveryTest {
        val memberTarget = RoomKeyRecoveryTarget(UserId("@bob:example.org"), null)
        val planStages = listOf(RoomKeyRecoveryDisplayStage.Sender, RoomKeyRecoveryDisplayStage.Members)
        pendingStore.markPendingIfNeeded(aRequest())
        progressStore.startStage(
            stage = RoomKeyRecoveryDisplayStage.Sender,
            request = aRequest(),
            planStages = planStages,
            duration = 60.seconds,
        )
        clock.advanceBy(60.seconds)

        val result = aCoordinator().recover(
            aInput(canUseKeyBackup = false, roomMemberTargets = listOf(memberTarget))
        )

        assertThat(result.statusFor(aRequest())).isInstanceOf(RoomKeyRecoveryStatus.Failed::class.java)
        assertThat(requester.requests.map { it.scope }).containsExactly(RoomKeyRecoveryScope.RoomMember)
        assertThat(requester.requests.single().targets).containsExactly(memberTarget)
    }

    @Test
    fun `recover coalesces duplicate requests`() = runRoomKeyRecoveryTest {
        val result = aCoordinator(waitForDecryption = { _, _ -> true }).recover(
            aInput(requests = listOf(aRequest(), aRequest()))
        )

        val status = result.statusFor(aRequest()) as RoomKeyRecoveryStatus.Resolved
        assertThat(status.eventCount).isEqualTo(2)
    }

    @Test
    fun `recover requests sender when backup is unavailable`() = runRoomKeyRecoveryTest {
        val result = aCoordinator().recover(
            aInput(canUseKeyBackup = false)
        )

        assertThat(result.statusFor(aRequest())).isInstanceOf(RoomKeyRecoveryStatus.Failed::class.java)
        assertThat(requester.requests.map { it.scope }).containsExactly(RoomKeyRecoveryScope.Sender)
        assertThat(requester.requests.single().targets).containsExactly(
            RoomKeyRecoveryTarget(UserId("@alice:example.org"), "ALICEDEVICE")
        )
    }

    @Test
    fun `recover falls through backup sender and members`() = runRoomKeyRecoveryTest {
        val memberTarget = RoomKeyRecoveryTarget(UserId("@bob:example.org"), null)

        aCoordinator().recover(
            aInput(roomMemberTargets = listOf(memberTarget))
        )

        assertThat(requester.requests.map { it.scope }).containsExactly(
            RoomKeyRecoveryScope.Sender,
            RoomKeyRecoveryScope.RoomMember,
        ).inOrder()
        assertThat(requester.requests.last().targets).containsExactly(memberTarget)
    }

    @Test
    fun `recover resolves when decryption succeeds`() = runRoomKeyRecoveryTest {
        val result = aCoordinator(waitForDecryption = { _, _ -> true }).recover(aInput(canUseKeyBackup = false))

        assertThat(result.statusFor(aRequest())).isInstanceOf(RoomKeyRecoveryStatus.Resolved::class.java)
        assertThat(progressStore.get(aRequest())).isNull()
        assertThat(pendingStore.remainingInterval(aRequest())).isNull()
    }

    @Test
    fun `recover marks failed when sdk request fails`() = runRoomKeyRecoveryTest {
        requester.result = Result.failure(IllegalStateException("nope"))

        val result = aCoordinator().recover(aInput(canUseKeyBackup = false))

        val status = result.statusFor(aRequest()) as RoomKeyRecoveryStatus.Failed
        assertThat(status.planStages).containsExactly(RoomKeyRecoveryDisplayStage.Sender)
        assertThat(progressStore.get(aRequest())!!.currentStage).isEqualTo(RoomKeyRecoveryDisplayStage.Failed)
    }

    @Test
    fun `manualRetry clears failed state and restarts`() = runRoomKeyRecoveryTest {
        progressStore.markFailed(aRequest(), listOf(RoomKeyRecoveryDisplayStage.Sender))
        pendingStore.markPendingIfNeeded(aRequest())

        val result = aCoordinator(waitForDecryption = { _, _ -> true }).manualRetry(
            input = aInput(canUseKeyBackup = false),
            identityKey = aRequest().identityKey,
        )

        assertThat(result.statusFor(aRequest())).isInstanceOf(RoomKeyRecoveryStatus.Resolved::class.java)
        assertThat(requester.requests.map { it.scope }).containsExactly(RoomKeyRecoveryScope.Sender)
    }

    private fun aCoordinator(
        waitForDecryption: suspend (RoomKeyRecoveryRequest, Duration) -> Boolean = { _, _ -> false },
    ) = RoomKeyRecoveryCoordinator(
        pendingStore = pendingStore,
        progressStore = progressStore,
        requestRoomKeyRecovery = requester::request,
        waitForDecryption = waitForDecryption,
    )

    private fun aInput(
        requests: List<RoomKeyRecoveryRequest> = listOf(aRequest()),
        verificationState: RoomKeyRecoveryVerificationState = RoomKeyRecoveryVerificationState.Verified,
        canUseKeyBackup: Boolean = true,
        roomMemberTargets: List<RoomKeyRecoveryTarget> = emptyList(),
    ) = RoomKeyRecoveryCoordinatorInput(
        requests = requests,
        ownUserId = UserId("@me:example.org"),
        verificationState = verificationState,
        canUseKeyBackup = canUseKeyBackup,
        roomMemberTargets = roomMemberTargets,
    )

    private fun RoomKeyRecoveryCoordinatorResult.statusFor(request: RoomKeyRecoveryRequest): RoomKeyRecoveryStatus {
        return statuses.getValue(request.identityKey)
    }
}

private class FakeRoomKeyRecoveryRequester {
    var result: Result<RoomKeyRecoveryProgress> = Result.success(
        RoomKeyRecoveryProgress(
            roomId = RoomId("!room:example.org"),
            sessionId = "session",
            senderKey = "senderKey",
            stage = RoomKeyRecoveryStage.SenderRequested,
            message = null,
            targetCount = 1u,
            manualRetryAvailable = true,
        )
    )
    val requests = mutableListOf<RecordedRoomKeyRecoveryRequest>()

    suspend fun request(
        request: RoomKeyRecoveryRequest,
        targets: List<RoomKeyRecoveryTarget>,
        scope: RoomKeyRecoveryScope,
    ): Result<RoomKeyRecoveryProgress> {
        requests += RecordedRoomKeyRecoveryRequest(request, targets, scope)
        return result
    }
}

private data class RecordedRoomKeyRecoveryRequest(
    val request: RoomKeyRecoveryRequest,
    val targets: List<RoomKeyRecoveryTarget>,
    val scope: RoomKeyRecoveryScope,
)

private fun runRoomKeyRecoveryTest(block: suspend () -> Unit) {
    kotlinx.coroutines.test.runTest {
        block()
    }
}
