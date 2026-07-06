/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.roomdata.FakeRoomUnsealDataClient
import io.element.android.features.messages.impl.roomdata.RoomAgentDescriptor
import io.element.android.features.messages.impl.roomdata.RoomUnsealDataSnapshot
import io.element.android.features.messages.impl.roomdata.RoomUnsealResource
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.BackupState
import io.element.android.libraries.matrix.api.encryption.roomkey.AgentRoomKeyRecoveryRequest
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
import io.element.android.libraries.matrix.test.A_USER_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
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
                roomId = A_ROOM_ID,
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
    fun `recoverVisibleItems - uses room id fallback when encrypted event JSON omits room id`() = runTest {
        val runner = createRunner()
        runner.statuses.test {
            assertThat(awaitItem()).isEmpty()

            runner.recoverVisibleItems(
                roomId = A_ROOM_ID,
                timelineItems = listOf(aUtdTimelineItem(originalJson = originalJson(roomId = null))),
                roomMembers = listOf(aRoomMember(userId = A_USER_ID, membership = RoomMembershipState.JOIN)),
                sessionVerifiedStatus = SessionVerifiedStatus.NotVerified,
                backupState = BackupState.ENABLED,
            )

            val statuses = awaitItem()
            assertThat(statuses.keys.single()).isEqualTo(ROOM_KEY_REQUEST.identityKey)
            assertThat(statuses.values.single()).isInstanceOf(RoomKeyRecoveryStatus.DeviceUnverified::class.java)
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
            roomId = A_ROOM_ID,
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

    @Test
    fun `recoverVisibleItems - excludes room agents from ordinary room member targets`() = runTest {
        val requestRoomKeyRecovery = lambdaRecorder<RoomKeyRecoveryRequest, List<RoomKeyRecoveryTarget>, RoomKeyRecoveryScope, Result<RoomKeyRecoveryProgress>> { request, targets, _ ->
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
            ),
            roomAgentResolver = RoomAgentResolver(roomUnsealDataClientWithAgent(AGENT_ID.value)),
        )

        runner.recoverVisibleItems(
            roomId = A_ROOM_ID,
            timelineItems = listOf(aUtdTimelineItem()),
            roomMembers = listOf(
                aRoomMember(userId = A_USER_ID, membership = RoomMembershipState.JOIN),
                aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN),
            ),
            sessionVerifiedStatus = SessionVerifiedStatus.Verified,
            backupState = BackupState.ENABLED,
        )
        advanceUntilIdle()

        requestRoomKeyRecovery.assertions()
            .isCalledOnce()
            .with(value(ROOM_KEY_REQUEST), value(listOf(RoomKeyRecoveryTarget(A_USER_ID, deviceId = null))), value(RoomKeyRecoveryScope.OwnDevices))
    }

    @Test
    fun `recoverVisibleItems - room member recovery targets match ios active member filtering`() = runTest {
        val joinedMember = UserId("@joined:server.org")
        val invitedMember = UserId("@invited:server.org")
        val knockingMember = UserId("@knocking:server.org")
        val leftMember = UserId("@left:server.org")
        val requestRoomKeyRecovery = lambdaRecorder<RoomKeyRecoveryRequest, List<RoomKeyRecoveryTarget>, RoomKeyRecoveryScope, Result<RoomKeyRecoveryProgress>> { request, targets, _ ->
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
            ),
            roomAgentResolver = RoomAgentResolver(roomUnsealDataClientWithAgent(AGENT_ID.value)),
        )

        runner.recoverVisibleItems(
            roomId = A_ROOM_ID,
            timelineItems = listOf(
                aUtdTimelineItem(
                    sender = A_USER_ID_2,
                    originalJson = originalJson(sender = A_USER_ID_2, deviceId = "SENDER_DEVICE_2"),
                )
            ),
            roomMembers = listOf(
                aRoomMember(userId = A_SESSION_ID, membership = RoomMembershipState.JOIN),
                aRoomMember(userId = A_USER_ID_2, membership = RoomMembershipState.JOIN),
                aRoomMember(userId = joinedMember, membership = RoomMembershipState.JOIN),
                aRoomMember(userId = invitedMember, membership = RoomMembershipState.INVITE),
                aRoomMember(userId = knockingMember, membership = RoomMembershipState.KNOCK),
                aRoomMember(userId = leftMember, membership = RoomMembershipState.LEAVE),
                aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN),
            ),
            sessionVerifiedStatus = SessionVerifiedStatus.Verified,
            backupState = BackupState.UNKNOWN,
        )
        advanceUntilIdle()

        requestRoomKeyRecovery.assertions()
            .isCalledExactly(2)
            .withSequence(
                listOf(
                    value(ROOM_KEY_REQUEST.copy(senderUserId = A_USER_ID_2, senderDeviceId = "SENDER_DEVICE_2")),
                    value(listOf(RoomKeyRecoveryTarget(A_USER_ID_2, deviceId = "SENDER_DEVICE_2"))),
                    value(RoomKeyRecoveryScope.Sender),
                ),
                listOf(
                    value(ROOM_KEY_REQUEST.copy(senderUserId = A_USER_ID_2, senderDeviceId = "SENDER_DEVICE_2")),
                    value(
                        listOf(
                            RoomKeyRecoveryTarget(joinedMember, deviceId = null),
                            RoomKeyRecoveryTarget(invitedMember, deviceId = null),
                            RoomKeyRecoveryTarget(knockingMember, deviceId = null),
                        )
                    ),
                    value(RoomKeyRecoveryScope.RoomMember),
                ),
            )
    }

    @Test
    fun `recoverVisibleItems - sender recovery falls back to user target when event sender device is stale`() = runTest {
        val requestRoomKeyRecovery = lambdaRecorder<RoomKeyRecoveryRequest, List<RoomKeyRecoveryTarget>, RoomKeyRecoveryScope, Result<RoomKeyRecoveryProgress>> { request, targets, _ ->
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
            ),
            senderDeviceResolver = RoomKeyRecoverySenderDeviceResolver { senderUserId, _ ->
                if (senderUserId == A_USER_ID_2) setOf("NEW_SENDER_DEVICE") else null
            },
        )

        runner.recoverVisibleItems(
            roomId = A_ROOM_ID,
            timelineItems = listOf(
                aUtdTimelineItem(
                    sender = A_USER_ID_2,
                    originalJson = originalJson(sender = A_USER_ID_2, deviceId = "OLD_SENDER_DEVICE"),
                )
            ),
            roomMembers = listOf(aRoomMember(userId = A_USER_ID_2, membership = RoomMembershipState.JOIN)),
            sessionVerifiedStatus = SessionVerifiedStatus.Verified,
            backupState = BackupState.UNKNOWN,
        )
        advanceUntilIdle()

        requestRoomKeyRecovery.assertions()
            .isCalledOnce()
            .with(
                value(ROOM_KEY_REQUEST.copy(senderUserId = A_USER_ID_2, senderDeviceId = "OLD_SENDER_DEVICE")),
                value(listOf(RoomKeyRecoveryTarget(A_USER_ID_2, deviceId = null))),
                value(RoomKeyRecoveryScope.Sender),
            )
    }

    @Test
    fun `recoverVisibleItems - sends direct agent room key request when session is verified`() = runTest {
        val requestAgentRoomKeyRecovery = lambdaRecorder<AgentRoomKeyRecoveryRequest, Result<Unit>> { Result.success(Unit) }
        val runner = createRunner(
            matrixClient = FakeMatrixClient(
                requestAgentRoomKeyRecoveryLambda = requestAgentRoomKeyRecovery,
            ),
            encryptionService = FakeEncryptionService(
                requestRoomKeyRecoveryResult = successRoomKeyRecovery(),
            ),
        )

        runner.recoverVisibleItems(
            roomId = A_ROOM_ID,
            timelineItems = listOf(aUtdTimelineItem(sender = AGENT_ID, originalJson = AGENT_ORIGINAL_JSON)),
            roomMembers = listOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN)),
            sessionVerifiedStatus = SessionVerifiedStatus.Verified,
            backupState = BackupState.ENABLED,
        )
        advanceUntilIdle()

        requestAgentRoomKeyRecovery.assertions()
            .isCalledOnce()
            .with(value(AGENT_ROOM_KEY_REQUEST))
    }

    @Test
    fun `recoverVisibleItems - does not send direct agent room key request when session is not verified`() = runTest {
        val requestAgentRoomKeyRecovery = lambdaRecorder<AgentRoomKeyRecoveryRequest, Result<Unit>> { Result.success(Unit) }
        val runner = createRunner(
            matrixClient = FakeMatrixClient(
                requestAgentRoomKeyRecoveryLambda = requestAgentRoomKeyRecovery,
            ),
        )

        runner.recoverVisibleItems(
            roomId = A_ROOM_ID,
            timelineItems = listOf(aUtdTimelineItem(sender = AGENT_ID, originalJson = AGENT_ORIGINAL_JSON)),
            roomMembers = listOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN)),
            sessionVerifiedStatus = SessionVerifiedStatus.NotVerified,
            backupState = BackupState.ENABLED,
        )
        advanceUntilIdle()

        requestAgentRoomKeyRecovery.assertions().isNeverCalled()
    }

    @Test
    fun `retry - clears direct agent pending request for matching room key recovery request`() = runTest {
        val requestAgentRoomKeyRecovery = lambdaRecorder<AgentRoomKeyRecoveryRequest, Result<Unit>> { Result.success(Unit) }
        val runner = createRunner(
            matrixClient = FakeMatrixClient(
                requestAgentRoomKeyRecoveryLambda = requestAgentRoomKeyRecovery,
            ),
            encryptionService = FakeEncryptionService(
                requestRoomKeyRecoveryResult = successRoomKeyRecovery(),
            ),
        )

        runner.recoverVisibleItems(
            roomId = A_ROOM_ID,
            timelineItems = listOf(aUtdTimelineItem(sender = AGENT_ID, originalJson = AGENT_ORIGINAL_JSON)),
            roomMembers = listOf(aRoomMember(userId = AGENT_ID, membership = RoomMembershipState.JOIN)),
            sessionVerifiedStatus = SessionVerifiedStatus.Verified,
            backupState = BackupState.ENABLED,
        )
        advanceUntilIdle()
        runner.retry(AGENT_ORDINARY_ROOM_KEY_REQUEST)
        advanceUntilIdle()

        requestAgentRoomKeyRecovery.assertions()
            .isCalledExactly(2)
            .withSequence(
                listOf(value(AGENT_ROOM_KEY_REQUEST)),
                listOf(value(AGENT_ROOM_KEY_REQUEST)),
            )
    }

    @Test
    fun `recoverVisibleItems - shares recovery stores across runner recreation`() = runTest {
        val stores = RoomKeyRecoveryStores()
        val requestRoomKeyRecovery = lambdaRecorder<RoomKeyRecoveryRequest, List<RoomKeyRecoveryTarget>, RoomKeyRecoveryScope, Result<RoomKeyRecoveryProgress>> { request, targets, _ ->
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
        val firstRunner = createRunner(
            encryptionService = FakeEncryptionService(
                requestRoomKeyRecoveryResult = requestRoomKeyRecovery,
            ),
            stores = stores,
        )
        val recreatedRunner = createRunner(
            encryptionService = FakeEncryptionService(
                requestRoomKeyRecoveryResult = requestRoomKeyRecovery,
            ),
            stores = stores,
        )

        firstRunner.recoverVisibleItems(
            roomId = A_ROOM_ID,
            timelineItems = listOf(aUtdTimelineItem()),
            roomMembers = listOf(aRoomMember(userId = A_USER_ID, membership = RoomMembershipState.JOIN)),
            sessionVerifiedStatus = SessionVerifiedStatus.Verified,
            backupState = BackupState.ENABLED,
        )
        advanceUntilIdle()
        recreatedRunner.recoverVisibleItems(
            roomId = A_ROOM_ID,
            timelineItems = listOf(aUtdTimelineItem()),
            roomMembers = listOf(aRoomMember(userId = A_USER_ID, membership = RoomMembershipState.JOIN)),
            sessionVerifiedStatus = SessionVerifiedStatus.Verified,
            backupState = BackupState.ENABLED,
        )
        advanceUntilIdle()

        requestRoomKeyRecovery.assertions().isCalledOnce()
    }

    private fun TestScope.createRunner(
        matrixClient: FakeMatrixClient = FakeMatrixClient(),
        encryptionService: FakeEncryptionService = FakeEncryptionService(),
        sessionVerificationService: FakeSessionVerificationService = FakeSessionVerificationService(),
        policy: MemberAwareRoomKeyForwardingPolicy = MemberAwareRoomKeyForwardingPolicy(),
        roomAgentResolver: RoomAgentResolver = RoomAgentResolver(FakeRoomUnsealDataClient()),
        decryptionRetrier: RoomKeyDecryptionRetrier = RoomKeyDecryptionRetrier { _, _ -> false },
        stores: RoomKeyRecoveryStores = RoomKeyRecoveryStores(),
        senderDeviceResolver: RoomKeyRecoverySenderDeviceResolver = RoomKeyRecoverySenderDeviceResolver { _, _ -> null },
    ): RoomKeyRecoveryTimelineRunner {
        return RoomKeyRecoveryTimelineRunner(
            matrixClient = matrixClient,
            encryptionService = encryptionService,
            sessionVerificationService = sessionVerificationService,
            sessionId = A_SESSION_ID,
            forwardingPolicy = policy,
            roomAgentResolver = roomAgentResolver,
            decryptionRetrier = decryptionRetrier,
            stores = stores,
            senderDeviceResolver = senderDeviceResolver,
            sessionCoroutineScope = this,
        )
    }

    private fun roomUnsealDataClientWithAgent(userId: String) = FakeRoomUnsealDataClient(
        snapshot = RoomUnsealDataSnapshot(
            roomAgents = RoomUnsealResource.success(
                listOf(
                    RoomAgentDescriptor(
                        userId = userId,
                        displayName = null,
                        avatarUrl = null,
                        userType = "agent",
                        membership = "join",
                    )
                )
            )
        )
    )

    private fun successRoomKeyRecovery(): (RoomKeyRecoveryRequest, List<RoomKeyRecoveryTarget>, RoomKeyRecoveryScope) -> Result<RoomKeyRecoveryProgress> {
        return { request, targets, _ ->
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
    }

    private fun aUtdTimelineItem(
        sender: UserId = A_USER_ID,
        originalJson: String = ORIGINAL_JSON,
    ): MatrixTimelineItem.Event {
        return MatrixTimelineItem.Event(
            uniqueId = A_UNIQUE_ID,
            event = anEventTimelineItem(
                sender = sender,
                content = UnableToDecryptContent(
                    data = UnableToDecryptContent.Data.MegolmV1AesSha2(
                        sessionId = SESSION_ID,
                        utdCause = UtdCause.Unknown,
                    ),
                    threadInfo = null,
                ),
                debugInfoProvider = { aTimelineItemDebugInfo(originalJson = originalJson) },
            )
        )
    }

    private companion object {
        const val SESSION_ID = "SESSION"
        const val SENDER_KEY = "SENDER_KEY"
        const val AGENT_DEVICE_ID = "BOT_AGENT_DEVICE"
        val AGENT_ID = UserId("@agent:example.org")
        val ROOM_KEY_REQUEST = RoomKeyRecoveryRequest(
            roomId = A_ROOM_ID,
            senderUserId = A_USER_ID,
            senderDeviceId = "SENDER_DEVICE",
            senderKey = SENDER_KEY,
            sessionId = SESSION_ID,
            ciphertext = "CIPHERTEXT",
        )
        val AGENT_ROOM_KEY_REQUEST = AgentRoomKeyRecoveryRequest(
            roomId = A_ROOM_ID,
            senderUserId = AGENT_ID,
            senderDeviceId = AGENT_DEVICE_ID,
            senderKey = SENDER_KEY,
            sessionId = SESSION_ID,
        )
        val AGENT_ORDINARY_ROOM_KEY_REQUEST = RoomKeyRecoveryRequest(
            roomId = A_ROOM_ID,
            senderUserId = AGENT_ID,
            senderDeviceId = AGENT_DEVICE_ID,
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
        fun originalJson(
            sender: UserId = A_USER_ID,
            deviceId: String = "SENDER_DEVICE",
            roomId: String? = A_ROOM_ID.value,
        ) = """
            {
              "type": "m.room.encrypted",
              ${roomId?.let { "\"room_id\": \"$it\"," }.orEmpty()}
              "sender": "${sender.value}",
              "content": {
                "algorithm": "m.megolm.v1.aes-sha2",
                "sender_key": "$SENDER_KEY",
                "session_id": "$SESSION_ID",
                "device_id": "$deviceId",
                "ciphertext": "CIPHERTEXT"
              }
            }
        """.trimIndent()
        val AGENT_ORIGINAL_JSON = """
            {
              "type": "m.room.encrypted",
              "sender": "${AGENT_ID.value}",
              "content": {
                "algorithm": "m.megolm.v1.aes-sha2",
                "sender_key": "$SENDER_KEY",
                "device_id": "$AGENT_DEVICE_ID",
                "ciphertext": "CIPHERTEXT"
              }
            }
        """.trimIndent()
    }
}
