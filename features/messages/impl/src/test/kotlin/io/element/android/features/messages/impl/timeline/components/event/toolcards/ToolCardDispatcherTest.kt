/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import com.google.common.truth.Truth.assertThat
import org.json.JSONObject
import org.junit.Test

class ToolCardDispatcherTest {
    @Test
    fun `gmail fetch list payload renders as compose email card`() {
        val payload = JSONObject(
            """
            {
              "messages": [
                {
                  "subject": "Welcome",
                  "from": "team@example.com",
                  "snippet": "Thanks for joining"
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(payload.hasCardContentFor("composeEmail")).isTrue()
        assertThat(payload.cardObjects("messages")).isNotEmpty()
    }

    @Test
    fun `single gmail payload can render as compose email card`() {
        val payload = JSONObject(
            """
            {
              "subject": "Welcome",
              "from": { "email": "team@example.com" },
              "body": "Thanks for joining"
            }
            """.trimIndent()
        )

        assertThat(payload.hasCardContentFor("composeEmail")).isTrue()
    }

    @Test
    fun `transformed finance payload keeps renderable finance fields`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "summary": {
                  "title": "Apple Inc.",
                  "stock": "AAPL",
                  "exchange": "NASDAQ",
                  "extracted_price": 214.5,
                  "currency": "USD"
                }
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "finance")

        assertThat(transformed.hasCardContentFor("finance")).isTrue()
        assertThat(transformed.optJSONObject("quote")?.cardString("name")).isEqualTo("Apple Inc.")
    }

    @Test
    fun `finance graph payload is kept for chart rendering`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "graph": [
                  { "price": 210.0, "date": "t1" },
                  { "price": 214.0, "date": "t2" }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "finance")

        assertThat(transformed.hasCardContentFor("finance")).isTrue()
        assertThat(transformed.cardObjects("graph")).hasSize(2)
    }

    @Test
    fun `weather tool is registered and normalizes current forecast data`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "location": "Shanghai, China",
                "weather_result": {
                  "temperature": "26°C",
                  "feels_like": "28°C",
                  "humidity": "70%",
                  "wind": "12 km/h",
                  "weather": "Clear"
                },
                "forecast": [
                  { "day": "Sunday", "high": "29", "low": "22", "weather": "Rain", "precipitation": "60%" }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "weather")

        assertThat(TOOL_CARD_REGISTRY["COMPOSIO_SEARCH_WEATHER"]).isEqualTo("weather")
        assertThat(transformed.hasCardContentFor("weather")).isTrue()
        assertThat(transformed.optJSONObject("current")?.cardString("condition")).isEqualTo("Clear")
        assertThat(transformed.cardObjects("forecast")).hasSize(1)
    }

    @Test
    fun `getWeather stream payload unwraps data envelope and renders weather card`() {
        val payload = JSONObject(
            """
            {
              "data": {
                "city": "London",
                "country": "United Kingdom",
                "current": {
                  "condition": "Sunny",
                  "feelsLike": 16,
                  "humidity": 55,
                  "temperature": 19,
                  "windSpeed": 21
                },
                "forecast": [
                  { "condition": "Overcast", "date": "2026-06-12", "day": "Fri", "high": 22, "low": 16, "precipitation": 24 },
                  { "condition": "Overcast", "date": "2026-06-13", "day": "Sat", "high": 23, "low": 13, "precipitation": 0 }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "weather")

        assertThat(TOOL_CARD_REGISTRY["getWeather"]).isEqualTo("weather")
        assertThat(transformed.hasCardContentFor("weather")).isTrue()
        assertThat(transformed.cardString("city")).isEqualTo("London")
        assertThat(transformed.cardString("country")).isEqualTo("United Kingdom")
        assertThat(transformed.optJSONObject("current")?.optDouble("temperature")).isEqualTo(19.0)
        assertThat(transformed.cardObjects("forecast")).hasSize(2)
    }

    @Test
    fun `url content articles are treated as card content`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "results": [
                  { "url": "https://example.com/story", "title": "Story" }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "urlContent")

        assertThat(transformed.hasCardContentFor("urlContent")).isTrue()
        assertThat(transformed.cardObjects("articles")).hasSize(1)
    }

    @Test
    fun `single drive file payload is normalized to files list`() {
        val payload = JSONObject(
            """
            {
              "name": "Report.pdf",
              "mime_type": "application/pdf",
              "web_view_link": "https://drive.google.com/file/report"
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "fileAttachment")

        assertThat(transformed.hasCardContentFor("fileAttachment")).isTrue()
        assertThat(transformed.cardObjects("files")).hasSize(1)
        assertThat(transformed.cardObjects("files").first().cardString("url")).isEqualTo("https://drive.google.com/file/report")
    }

    @Test
    fun `single comment payload is normalized to comment thread`() {
        val payload = JSONObject(
            """
            {
              "body": "Looks good",
              "user": { "login": "rayson", "avatar_url": "https://example.com/avatar.png" },
              "created_at": "2026-06-13T01:00:00Z"
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "commentThread")

        assertThat(transformed.hasCardContentFor("commentThread")).isTrue()
        assertThat(transformed.cardObjects("comments")).hasSize(1)
        assertThat(transformed.cardObjects("comments").first().cardString("author")).isEqualTo("rayson")
    }

    @Test
    fun `single social post payload is normalized to posts list`() {
        val payload = JSONObject(
            """
            {
              "text": "hello stream",
              "author": { "username": "tester12", "name": "Tester" },
              "created_at": "2026-06-13T01:00:00Z"
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "socialPostFeed")

        assertThat(transformed.hasCardContentFor("socialPostFeed")).isTrue()
        assertThat(transformed.cardObjects("posts")).hasSize(1)
        assertThat(transformed.cardObjects("posts").first().cardString("body")).isEqualTo("hello stream")
    }

    @Test
    fun `snake case github activity lists are normalized to renderer props`() {
        val checkRuns = CardTransforms.transform(
            JSONObject("""{ "check_runs": [{ "name": "unit", "status": "completed", "conclusion": "success" }] }"""),
            "checkRuns",
        )
        val secretAlerts = CardTransforms.transform(
            JSONObject("""{ "secret_alerts": [{ "state": "open", "secret_type": "token" }] }"""),
            "secretAlerts",
        )

        assertThat(checkRuns.hasCardContentFor("checkRuns")).isTrue()
        assertThat(checkRuns.cardObjects("checkRuns")).hasSize(1)
        assertThat(secretAlerts.hasCardContentFor("secretAlerts")).isTrue()
        assertThat(secretAlerts.cardObjects("alerts")).hasSize(1)
    }
}
