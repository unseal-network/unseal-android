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
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Test
import org.matrix.rustcomponents.sdk.Client
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
