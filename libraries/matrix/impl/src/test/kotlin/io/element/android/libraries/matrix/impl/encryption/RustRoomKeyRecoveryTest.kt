/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyForwardingDecision
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryRequest
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryScope
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryStage
import io.element.android.libraries.matrix.api.encryption.roomkey.RoomKeyRecoveryTarget
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiClient
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiEncryption
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiSyncService
import io.element.android.libraries.matrix.impl.sync.RustSyncService
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.matrix.rustcomponents.sdk.RoomKeyForwardingAuthorization as RustRoomKeyForwardingAuthorization
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryProgress as RustRoomKeyRecoveryProgress
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryScope as RustRoomKeyRecoveryScope
import org.matrix.rustcomponents.sdk.RoomKeyRecoveryStage as RustRoomKeyRecoveryStage

@OptIn(ExperimentalCoroutinesApi::class)
class RustRoomKeyRecoveryTest {
    @Test
    fun `configure room key recovery enables requests forwarding and installs policy`() = runTest {
        val inner = FakeFfiEncryption()
        val service = createRustEncryptionService(inner)

        val result = service.configureRoomKeyRecovery { authorization ->
            assertThat(authorization.roomId).isEqualTo(RoomId("!room:example.org"))
            assertThat(authorization.sessionId).isEqualTo("session")
            assertThat(authorization.senderKey).isEqualTo("senderKey")
            assertThat(authorization.requesterUserId).isEqualTo(UserId("@alice:example.org"))
            assertThat(authorization.requesterDeviceId).isEqualTo("DEVICE")
            assertThat(authorization.requestId).isEqualTo("request")
            assertThat(authorization.requestedMessageIndex).isEqualTo(7u)
            assertThat(authorization.responderFirstKnownIndex).isEqualTo(3u)
            RoomKeyForwardingDecision(allow = true, exportIndex = 4u, reason = "allowed")
        }

        assertThat(result.isSuccess).isTrue()
        assertThat(inner.roomKeyRequestsEnabled).isTrue()
        assertThat(inner.roomKeyForwardingEnabled).isTrue()
        val decision = inner.roomKeyForwardingPolicy!!.allowForwarding(
            RustRoomKeyForwardingAuthorization(
                roomId = "!room:example.org",
                sessionId = "session",
                senderKey = "senderKey",
                requesterUserId = "@alice:example.org",
                requesterDeviceId = "DEVICE",
                requestId = "request",
                requestedMessageIndex = 7u,
                responderFirstKnownIndex = 3u,
            )
        )
        assertThat(decision.allow).isTrue()
        assertThat(decision.exportIndex).isEqualTo(4u)
        assertThat(decision.reason).isEqualTo("allowed")
    }

    @Test
    fun `request room key recovery maps request targets scope and progress`() = runTest {
        val inner = FakeFfiEncryption(
            requestRoomKeyRecoveryResult = RustRoomKeyRecoveryProgress(
                roomId = "!room:example.org",
                sessionId = "session",
                senderKey = "senderKey",
                stage = RustRoomKeyRecoveryStage.MEMBERS_REQUESTED,
                message = "members requested",
                targetCount = 2u,
                manualRetryAvailable = true,
            )
        )
        val service = createRustEncryptionService(inner)
        val request = RoomKeyRecoveryRequest(
            roomId = RoomId("!room:example.org"),
            senderUserId = UserId("@bob:example.org"),
            senderDeviceId = "BOBDEVICE",
            senderKey = "senderKey",
            sessionId = "session",
            ciphertext = "ciphertext",
        )

        val result = service.requestRoomKeyRecovery(
            request = request,
            targets = listOf(
                RoomKeyRecoveryTarget(UserId("@alice:example.org"), "ALICEDEVICE"),
                RoomKeyRecoveryTarget(UserId("@carol:example.org"), null),
            ),
            scope = RoomKeyRecoveryScope.RoomMember,
        )

        assertThat(result.isSuccess).isTrue()
        val call = inner.requestRoomKeyRecoveryCall!!
        assertThat(call.roomId).isEqualTo("!room:example.org")
        assertThat(call.sessionId).isEqualTo("session")
        assertThat(call.senderKey).isEqualTo("senderKey")
        assertThat(call.ciphertext).isEqualTo("ciphertext")
        assertThat(call.scope).isEqualTo(RustRoomKeyRecoveryScope.ROOM_MEMBER)
        assertThat(call.targets.map { it.userId to it.deviceId }).containsExactly(
            "@alice:example.org" to "ALICEDEVICE",
            "@carol:example.org" to null,
        ).inOrder()

        val progress = result.getOrThrow()
        assertThat(progress.roomId).isEqualTo(RoomId("!room:example.org"))
        assertThat(progress.sessionId).isEqualTo("session")
        assertThat(progress.senderKey).isEqualTo("senderKey")
        assertThat(progress.stage).isEqualTo(RoomKeyRecoveryStage.MembersRequested)
        assertThat(progress.message).isEqualTo("members requested")
        assertThat(progress.targetCount).isEqualTo(2u)
        assertThat(progress.manualRetryAvailable).isTrue()
    }

    private fun TestScope.createRustEncryptionService(inner: FakeFfiEncryption): RustEncryptionService {
        val dispatchers = testCoroutineDispatchers()
        return RustEncryptionService(
            client = FakeFfiClient(encryption = inner),
            syncService = RustSyncService(
                inner = FakeFfiSyncService(),
                dispatcher = dispatchers.io,
                sessionCoroutineScope = backgroundScope,
            ),
            sessionCoroutineScope = backgroundScope,
            dispatchers = dispatchers,
        )
    }
}
