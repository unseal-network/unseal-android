/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.audience

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.audience.AudienceBroadcastHttpClient
import io.element.android.features.call.impl.audience.AudienceWidgetDriver
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class AudienceWidgetDriverTest {
    @Test
    fun `widget replies use the toWidget transport direction`() = runTest {
        val driver = AudienceWidgetDriver(
            id = "audience-widget",
            sessionId = A_SESSION_ID,
            broadcastId = "bcast_demo",
            httpClient = mockk<AudienceBroadcastHttpClient>(relaxed = true),
        )
        val incoming = async(start = CoroutineStart.UNDISPATCHED) {
            driver.incomingMessages.first()
        }

        driver.send(
            """
            {
              "api": "fromWidget",
              "widgetId": "audience-widget",
              "requestId": "request-1",
              "action": "supported_api_versions",
              "data": {}
            }
            """.trimIndent()
        )

        val response = Json.parseToJsonElement(incoming.await()).jsonObject
        assertThat(response["api"]?.jsonPrimitive?.content).isEqualTo("toWidget")
        assertThat(response["widgetId"]?.jsonPrimitive?.content).isEqualTo("audience-widget")
        assertThat(response["requestId"]?.jsonPrimitive?.content).isEqualTo("request-1")
        assertThat(response["response"]?.jsonObject?.get("supported_versions")).isNotNull()
    }
}
