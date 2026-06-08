/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.encryption

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.encryption.roomkey.AgentRoomKeyRecoveryRequest
import io.element.android.libraries.matrix.impl.RustMatrixClient
import io.element.android.libraries.matrix.impl.aRustClientSessionDelegate
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiClient
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiSyncService
import io.element.android.libraries.matrix.impl.room.FakeTimelineEventFilterFactory
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.workmanager.test.FakeWorkManagerScheduler
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.services.toolbox.test.systemclock.FakeSystemClock
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.matrix.rustcomponents.sdk.Client
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RustAgentRoomKeyRecoveryTest {
    @Test
    fun `request agent room key recovery sends to-device room key request to agent device`() = runTest {
        val inner = FakeFfiClient(deviceId = "OWNDEVICE")
        val client = createRustMatrixClient(inner)
        val request = AgentRoomKeyRecoveryRequest(
            roomId = RoomId("!room:example.org"),
            senderUserId = UserId("@agent:example.org"),
            senderDeviceId = "BOT_DEVICE",
            senderKey = "SENDER_KEY",
            sessionId = "SESSION",
        )

        val result = client.requestAgentRoomKeyRecovery(request)

        assertThat(result.isSuccess).isTrue()
        val call = inner.sendToDeviceEventCall!!
        assertThat(call.eventType).isEqualTo("m.room_key_request")
        assertThat(call.userId).isEqualTo("@agent:example.org")
        assertThat(call.deviceId).isEqualTo("BOT_DEVICE")
        assertThat(call.content).contains("\"requesting_device_id\":\"OWNDEVICE\"")
        assertThat(call.content).contains("\"algorithm\":\"m.megolm.v1.aes-sha2\"")
        assertThat(call.content).contains("\"room_id\":\"!room:example.org\"")
        assertThat(call.content).contains("\"sender_key\":\"SENDER_KEY\"")
        assertThat(call.content).contains("\"session_id\":\"SESSION\"")
    }

    @Test
    fun `request agent room key recovery returns failure when SDK send fails`() = runTest {
        val inner = FakeFfiClient(
            sendToDeviceEventResult = {
                error("send failed")
            }
        )
        val client = createRustMatrixClient(inner)

        val result = client.requestAgentRoomKeyRecovery(
            AgentRoomKeyRecoveryRequest(
                roomId = RoomId("!room:example.org"),
                senderUserId = UserId("@agent:example.org"),
                senderDeviceId = "BOT_DEVICE",
                senderKey = "SENDER_KEY",
                sessionId = "SESSION",
            )
        )

        assertThat(result.isFailure).isTrue()
    }

    private fun TestScope.createRustMatrixClient(
        client: Client,
        sessionStore: SessionStore = InMemorySessionStore(
            updateUserProfileResult = { _, _, _ -> },
        ),
    ) = RustMatrixClient(
        innerClient = client,
        sessionStore = sessionStore,
        appCoroutineScope = backgroundScope,
        sessionDelegate = aRustClientSessionDelegate(
            sessionStore = sessionStore,
        ),
        innerSyncService = FakeFfiSyncService(),
        dispatchers = testCoroutineDispatchers(),
        baseCacheDirectory = File(""),
        clock = FakeSystemClock(),
        timelineEventFilterFactory = FakeTimelineEventFilterFactory(),
        featureFlagService = FakeFeatureFlagService(),
        analyticsService = FakeAnalyticsService(),
        workManagerScheduler = FakeWorkManagerScheduler(submitLambda = {}),
    )
}
