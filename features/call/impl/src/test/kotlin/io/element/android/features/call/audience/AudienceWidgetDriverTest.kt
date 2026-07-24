/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.audience

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.audience.AudienceBroadcastHttpClient
import io.element.android.features.call.impl.audience.AudienceHttpResponse
import io.element.android.features.call.impl.audience.AudienceWidgetDriver
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.mockk.coEvery
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
    fun `widget replies echo the fromWidget transport direction required by matrix widget api`() = runTest {
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
        assertThat(response["api"]?.jsonPrimitive?.content).isEqualTo("fromWidget")
        assertThat(response["widgetId"]?.jsonPrimitive?.content).isEqualTo("audience-widget")
        assertThat(response["requestId"]?.jsonPrimitive?.content).isEqualTo("request-1")
        assertThat(
            response["response"]
                ?.jsonObject
                ?.get("supported_versions")
                .toString()
        ).contains("org.matrix.msc4039")
    }

    @Test
    fun `widget preserves rolling audience events as raw SSE text`() = runTest {
        val sessionPath = "/meeting-broadcast/v1/broadcasts/bcast_demo/audience-sessions/aud_demo"
        val sse = "event: audience\ndata: {\"type\":\"heartbeat\"}\n\n"
        val httpClient = mockk<AudienceBroadcastHttpClient> {
            coEvery {
                requestAudienceWidget(
                    sessionId = A_SESSION_ID,
                    broadcastId = "bcast_demo",
                    method = "GET",
                    path = "$sessionPath/events?generation=1&after_revision=2",
                    body = null,
                )
            } returns AudienceHttpResponse(200, sse)
        }
        val driver = AudienceWidgetDriver(
            id = "audience-widget",
            sessionId = A_SESSION_ID,
            broadcastId = "bcast_demo",
            httpClient = httpClient,
        )
        val incoming = async(start = CoroutineStart.UNDISPATCHED) {
            driver.incomingMessages.first()
        }

        driver.send(
            """
            {
              "api": "fromWidget",
              "widgetId": "audience-widget",
              "requestId": "request-sse",
              "action": "io.element.unseal.meeting_broadcast_request",
              "data": {
                "method": "GET",
                "path": "$sessionPath/events?generation=1&after_revision=2"
              }
            }
            """.trimIndent()
        )

        val response = Json.parseToJsonElement(incoming.await()).jsonObject
        assertThat(
            response["response"]
                ?.jsonObject
                ?.get("response")
                ?.jsonPrimitive
                ?.content
        ).isEqualTo(sse)
    }

    @Test
    fun `widget downloads Matrix thumbnails as bounded data URLs`() = runTest {
        val httpClient = mockk<AudienceBroadcastHttpClient> {
            coEvery {
                loadAudienceThumbnail(
                    sessionId = A_SESSION_ID,
                    mxcUrl = "mxc://keepsecret.io/avatar",
                    size = 96,
                )
            } returns PNG_HEADER
        }
        val driver = AudienceWidgetDriver(
            id = "audience-widget",
            sessionId = A_SESSION_ID,
            broadcastId = "bcast_demo",
            httpClient = httpClient,
        )
        val incoming = async(start = CoroutineStart.UNDISPATCHED) {
            driver.incomingMessages.first()
        }

        driver.send(
            """
            {
              "api": "fromWidget",
              "widgetId": "audience-widget",
              "requestId": "request-2",
              "action": "org.matrix.msc4039.download_file",
              "data": {
                "content_uri": "mxc://keepsecret.io/avatar"
              }
            }
            """.trimIndent()
        )

        val response = Json.parseToJsonElement(incoming.await()).jsonObject
        assertThat(
            response["response"]
                ?.jsonObject
                ?.get("file")
                ?.jsonPrimitive
                ?.content
        ).isEqualTo("data:image/png;base64,iVBORw0KGgo=")
    }
}

private val PNG_HEADER = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)
