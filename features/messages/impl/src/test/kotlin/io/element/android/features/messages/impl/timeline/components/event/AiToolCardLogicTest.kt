/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import org.json.JSONObject
import org.junit.Test

class AiToolCardLogicTest {
    @Test
    fun `renderUI hotel cards normalize object image arrays`() {
        val part = AiToolStreamPart(
            id = "render-hotel",
            state = "output-available",
            toolName = "renderUI",
            title = null,
            input = """
                {
                  "spec": {
                    "component": "HotelBookingCard",
                    "cards": [
                      {
                        "name": "Buddha Zen Hotel",
                        "images": [
                          {
                            "thumbnail": "https://example.com/thumb-1.jpg",
                            "original_image": "https://example.com/original-1.jpg"
                          },
                          {
                            "thumbnail": "https://example.com/thumb-2.jpg",
                            "original_image": "https://example.com/original-2.jpg"
                          }
                        ]
                      }
                    ]
                  }
                }
            """.trimIndent(),
            output = null,
            errorText = null,
        )

        val entry = part.toToolCardEntries().single()
        val hotel = JSONObject(entry.props).getJSONArray("hotels").getJSONObject(0)

        assertThat(entry.cardType).isEqualTo("hotelBooking")
        assertThat(hotel.getString("imageUrl")).isEqualTo("https://example.com/thumb-1.jpg")
        assertThat(hotel.getJSONArray("imageUrls").length()).isEqualTo(2)
        assertThat(hotel.getJSONArray("imageUrls").getString(0)).isEqualTo("https://example.com/thumb-1.jpg")
    }

    @Test
    fun `failed tool card entries keep output error detail`() {
        val part = AiToolStreamPart(
            id = "hotel-failed",
            state = "output-error",
            toolName = "COMPOSIO_SEARCH_HOTELS",
            title = null,
            input = null,
            output = """{"status":"failed","message":"API quota exhausted"}""",
            errorText = null,
        )

        val entry = part.toToolCardEntries().single()
        val props = JSONObject(entry.props)

        assertThat(entry.state).isEqualTo("error")
        assertThat(props.getString("errorText")).isEqualTo("API quota exhausted")
        assertThat(props.getString("status")).isEqualTo("failed")
        assertThat(props.getString("rawOutput")).contains("API quota exhausted")
    }
}
