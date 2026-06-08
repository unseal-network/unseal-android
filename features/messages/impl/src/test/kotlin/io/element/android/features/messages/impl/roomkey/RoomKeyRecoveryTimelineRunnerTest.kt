/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.roomkey.MemberAwareRoomKeyForwardingPolicy
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyForwardingAuthorization
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryProgress
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryScope
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryStage
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryTarget
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.UtdCause
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_UNIQUE_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.encryption.FakeEncryptionService
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import io.element.android.libraries.matrix.test.verification.FakeSessionVerificationService
import io.element.android.tests.testutils.lambda.assert
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomKeyRecoveryTimelineRunnerTest {
    @Test
    fun `recoverVisibleItems - publishes device unverified status and updates forwarding policy members`() = runTest {
        val policy = MemberAwareRoomKeyForwardingPolicy()
        val runner = createRunner(policy = policy)
        runner.statuses.test {
            assertThat(awaitItem()).isEmpty()

            runner.recoverVisibleItems(
                timelineItems = listOf(aUtdTimelineItem()),
                roomMembers = listOf(aRoomMember(userId = A_USER_ID, membership = RoomMembershipState.JOIN)),
                sessionVerifiedStatus = SessionVerifiedStatus.NotVerified,
                backupState = BackupState.ENABLED,
            )

            val statuses = awaitItem()
            assertThat(statuses.values.single()).isInstanceOf(RoomKeyRecoveryStatus.DeviceUnverified::class.java)
            assertThat(
                policy.allowForwarding(
                    RoomKeyForwardingAuthorization(
                        roomId = A_ROOM_ID,
                        sessionId = SESSION_ID,
                        senderKey = SENDER_KEY,
                        requesterUserId = A_USER_ID,
                        requesterDeviceId = "DEVICE",
                        requestId = "REQUEST",
                        requestedMessageIndex = 1u,
                        responderFirstKnownIndex = 0u,
                    )
                ).allow
            ).isTrue()
        }
    }

    @Test
    fun `verifyCurrentSession - requests device verification`() = runTest {
        val requestDeviceVerification = lambdaRecorder<Unit> {}
        val runner = createRunner(
            sessionVerificationService = FakeSessionVerificationService(
                requestDeviceVerificationLambda = requestDeviceVerification,
            )
        )

        runner.verifyCurrentSession()
        advanceUntilIdle()

        requestDeviceVerification.assertions().isCalledOnce()
    }

    @Test
    fun `retry - calls SDK room key recovery request`() = runTest {
        val requestRoomKeyRecovery = lambdaRecorder<RoomKeyRecoveryRequest, List<RoomKeyRecoveryTarget>, RoomKeyRecoveryScope, Result<RoomKeyRecoveryProgress>> { request, targets, scope ->
            Result.success(
                RoomKeyRecoveryProgress(
                    roomId = request.roomId,
                    sessionId = request.sessionId,
                    senderKey = request.senderKey,
                    stage = RoomKeyRecoveryStage.SenderRequested,
                    message = null,
                    targetCount = targets.size.toUInt(),
                    manualRetryAvailable = true,
                )
            )
        }
        val runner = createRunner(
            encryptionService = FakeEncryptionService(
                requestRoomKeyRecoveryResult = requestRoomKeyRecovery,
            )
        )
        val request = ROOM_KEY_REQUEST

        runner.recoverVisibleItems(
            timelineItems = listOf(aUtdTimelineItem()),
            roomMembers = listOf(aRoomMember(userId = A_USER_ID, membership = RoomMembershipState.JOIN)),
            sessionVerifiedStatus = SessionVerifiedStatus.Verified,
            backupState = BackupState.ENABLED,
        )
        advanceUntilIdle()
        runner.retry(request)
        advanceUntilIdle()

        requestRoomKeyRecovery.assertions()
            .isCalledExactly(2)
            .withSequence(
                listOf(value(request), value(listOf(RoomKeyRecoveryTarget(A_USER_ID, deviceId = null))), value(RoomKeyRecoveryScope.OwnDevices)),
                listOf(value(request), value(listOf(RoomKeyRecoveryTarget(A_USER_ID, deviceId = null))), value(RoomKeyRecoveryScope.OwnDevices)),
            )
    }

    private fun TestScope.createRunner(
        encryptionService: FakeEncryptionService = FakeEncryptionService(),
        sessionVerificationService: FakeSessionVerificationService = FakeSessionVerificationService(),
        policy: MemberAwareRoomKeyForwardingPolicy = MemberAwareRoomKeyForwardingPolicy(),
    ): RoomKeyRecoveryTimelineRunner {
        return RoomKeyRecoveryTimelineRunner(
            encryptionService = encryptionService,
            sessionVerificationService = sessionVerificationService,
            sessionId = A_SESSION_ID,
            forwardingPolicy = policy,
            sessionCoroutineScope = this,
        )
    }

    private fun aUtdTimelineItem(): MatrixTimelineItem.Event {
        return MatrixTimelineItem.Event(
            uniqueId = A_UNIQUE_ID,
            event = anEventTimelineItem(
                sender = A_USER_ID,
                content = UnableToDecryptContent(
                    data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                        sessionId = SESSION_ID,
                        utdCause = UtdCause.Unknown,
                    ),
                    threadInfo = null,
                ),
                debugInfoProvider = { aTimelineItemDebugInfo(originalJson = ORIGINAL_JSON) },
            )
        )
    }

    private companion object {
        const val SESSION_ID = "SESSION"
        const val SENDER_KEY = "SENDER_KEY"
        val ROOM_KEY_REQUEST = RoomKeyRecoveryRequest(
            roomId = A_ROOM_ID,
            senderUserId = A_USER_ID,
            senderDeviceId = "SENDER_DEVICE",
            senderKey = SENDER_KEY,
            sessionId = SESSION_ID,
            ciphertext = "CIPHERTEXT",
        )
        val ORIGINAL_JSON = """
            {
              "type": "m.room.encrypted",
              "room_id": "${A_ROOM_ID.value}",
              "sender": "${A_USER_ID.value}",
              "content": {
                "algorithm": "m.megolm.v1.aes-sha2",
                "sender_key": "$SENDER_KEY",
                "session_id": "$SESSION_ID",
                "device_id": "SENDER_DEVICE",
                "ciphertext": "CIPHERTEXT"
              }
            }
        """.trimIndent()
    }
}
