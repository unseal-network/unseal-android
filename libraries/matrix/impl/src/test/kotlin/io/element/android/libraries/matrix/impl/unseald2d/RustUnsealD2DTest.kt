/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.unseald2d

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DConstants
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DMsgType
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DOutboundMessage
import io.element.android.libraries.matrix.api.unseald2d.UnsealD2DTarget
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ToDeviceMessage
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class RustUnsealD2DTest {
    @Test
    fun `sendUnsealD2DMessage sends io unseal d2d to target device`() = runTest {
        val inner = FakeFfiClient()
        val client = createRustMatrixClient(inner)
        val message = UnsealD2DOutboundMessage(
            target = UnsealD2DTarget(UserId("@agent:example.org"), deviceId = "BOT_DEVICE"),
            msgType = UnsealD2DMsgType.TerminalOpen,
            content = buildJsonObject {
                put("request_id", "request-1")
                put("cols", 80)
                put("rows", 24)
                put("platform", "android")
            },
        )

        val result = client.sendUnsealD2DMessage(message)

        assertThat(result.isSuccess).isTrue()
        val call = inner.sendToDeviceEventCall!!
        assertThat(call.eventType).isEqualTo(UnsealD2DConstants.EVENT_TYPE)
        assertThat(call.userId).isEqualTo("@agent:example.org")
        assertThat(call.deviceId).isEqualTo("BOT_DEVICE")
        assertThat(call.content).contains("\"msgtype\":\"cmd.open\"")
        assertThat(call.content).contains("\"request_id\":\"request-1\"")
        assertThat(call.content).contains("\"platform\":\"android\"")
    }

    @Test
    fun `sendUnsealD2DMessage returns failure when SDK send fails`() = runTest {
        val inner = FakeFfiClient(
            sendToDeviceEventResult = {
                error("send failed")
            }
        )
        val client = createRustMatrixClient(inner)

        val result = client.sendUnsealD2DMessage(
            UnsealD2DOutboundMessage(
                target = UnsealD2DTarget(UserId("@agent:example.org"), deviceId = "BOT_DEVICE"),
                msgType = UnsealD2DMsgType.Ping,
                content = buildJsonObject { put("ping_id", "ping-1") },
            )
        )

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun `unsealD2DMessages emits observed SDK to-device messages`() = runTest {
        val inner = FakeFfiClient()
        val client = createRustMatrixClient(inner)

        val deferred = backgroundScope.launch {
            val message = client.unsealD2DMessages.first()
            assertThat(message.msgType).isEqualTo(UnsealD2DMsgType.TerminalReady)
            assertThat(message.sender.value).isEqualTo("@agent:example.org")
            assertThat(message.senderDeviceId).isEqualTo("DESKTOP")
            assertThat(message.stringContent("request_id")).isEqualTo("request-1")
            assertThat(message.stringContent("session_id")).isEqualTo("session-1")
        }
        runCurrent()

        inner.emitToDeviceMessage(
            ToDeviceMessage(
                eventType = UnsealD2DConstants.EVENT_TYPE,
                sender = "@agent:example.org",
                content = """{"msgtype":"cmd.ready","content":{"request_id":"request-1","session_id":"session-1","sender_device_id":"DESKTOP"}}""",
                rawJson = "{}",
                encryptionInfo = null,
            )
        )

        deferred.join()
    }

    @Test
    fun `parse prefers encrypted sender device over spoofable payload sender device`() {
        val message = io.element.android.libraries.matrix.api.unseald2d.UnsealD2DMessage.parse(
            eventType = UnsealD2DConstants.EVENT_TYPE,
            sender = "@alice:server.org",
            content = """{"msgtype":"d2d.ping","content":{"sender_device_id":"SPOOFED_DEVICE"}}""",
            rawJson = "{}",
            encryptedSenderDeviceId = "ENCRYPTED_DEVICE",
        )

        assertThat(message?.senderDeviceId).isEqualTo("ENCRYPTED_DEVICE")
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
        workManagerScheduler = FakeWorkManagerScheduler(submitLambda = {}),
        analyticsService = FakeAnalyticsService(),
        featureFlagService = FakeFeatureFlagService(),
    )
}
