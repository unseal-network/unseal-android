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
    fun `finance data envelope is normalized for root tool cards`() {
        val payload = JSONObject(
            """
            {
              "data": {
                "summary": {
                  "title": "SpaceX",
                  "stock": "SPCX",
                  "exchange": "NASDAQ",
                  "extracted_price": 135,
                  "currency": "USD"
                },
                "news_results": [
                  {
                    "title": "SpaceX is public",
                    "source": "TechCrunch",
                    "date": "3 weeks ago",
                    "link": "https://techcrunch.com/spacex"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "finance")

        assertThat(transformed.hasCardContentFor("finance")).isTrue()
        assertThat(transformed.optJSONObject("quote")?.cardString("name")).isEqualTo("SpaceX")
        assertThat(transformed.cardObjects("news")).hasSize(1)
    }

    @Test
    fun `finance financials only payload is renderable`() {
        val payload = JSONObject(
            """
            {
              "financials": [
                {
                  "title": "Quarterly financials",
                  "results": [
                    {
                      "date": "Q2 2026",
                      "table": [
                        { "title": "Revenue", "value": "$2.1B" }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "finance")

        assertThat(transformed.cardObjects("financials")).hasSize(1)
        assertThat(transformed.hasCardContentFor("finance")).isTrue()
    }

    @Test
    fun `finance bare symbol payload is not treated as renderable card content`() {
        val payload = JSONObject(
            """
            {
              "symbol": "SPCX",
              "name": "SpaceX"
            }
            """.trimIndent()
        )

        assertThat(payload.hasCardContentFor("finance")).isFalse()
    }

    @Test
    fun `weather bare location payload is not treated as renderable card content`() {
        val payload = JSONObject(
            """
            {
              "city": "Shanghai",
              "condition": "Sunny"
            }
            """.trimIndent()
        )

        assertThat(payload.hasCardContentFor("weather")).isFalse()
    }

    @Test
    fun `headline data envelope is normalized for root tool cards`() {
        val payload = JSONObject(
            """
            {
              "data": {
                "news_results": [
                  {
                    "title": "SpaceX is public",
                    "source": "TechCrunch",
                    "date": "3 weeks ago",
                    "link": "https://techcrunch.com/spacex"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "headlineList")

        assertThat(transformed.hasCardContentFor("headlineList")).isTrue()
        assertThat(transformed.cardObjects("headlines")).hasSize(1)
    }

    @Test
    fun `product top level results array is normalized`() {
        val payload = JSONObject(
            """
            {
              "results": [
                {
                  "title": "SpaceX hoodie",
                  "price": "$65",
                  "source": "Shop",
                  "link": "https://example.com/hoodie"
                }
              ]
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "productList")

        assertThat(transformed.hasCardContentFor("productList")).isTrue()
        assertThat(transformed.cardObjects("products")).hasSize(1)
    }

    @Test
    fun `url content direct result arrays are accepted as renderable card content`() {
        val payload = JSONObject(
            """
            {
              "results": [
                {
                  "url": "https://example.com/article",
                  "title": "Fetched article"
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(payload.hasCardContentFor("urlContent")).isTrue()
    }

    @Test
    fun `breaking news requires a resolvable headline title`() {
        val emptyTitlePayload = JSONObject(
            """
            {
              "headlines": [
                {
                  "source": "Wire",
                  "url": "https://example.com/no-title"
                }
              ]
            }
            """.trimIndent()
        )
        val titledPayload = JSONObject(
            """
            {
              "headlines": [
                {
                  "title": "Markets open higher",
                  "source": "Wire"
                }
              ]
            }
            """.trimIndent()
        )

        assertThat(emptyTitlePayload.hasCardContentFor("breakingNews")).isFalse()
        assertThat(titledPayload.hasCardContentFor("breakingNews")).isTrue()
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
        assertThat(transformed.cardString("title")).isEqualTo("Files")
        assertThat(transformed.cardObjects("files")).hasSize(1)
        val file = transformed.cardObjects("files").first()
        assertThat(file.cardString("url")).isEqualTo("https://drive.google.com/file/report")
        assertThat(file.cardString("sizeLabel")).isNull()
        assertThat(file.cardString("icon")).isEqualTo("pdf")
    }

    @Test
    fun `news payload preserves title source date url and image for headline cards`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "news_results": [
                  {
                    "title": "AI helps doctors",
                    "snippet": "Medical residency story",
                    "source": "chronicleonline.com",
                    "date": "38 minutes ago",
                    "link": "https://chronicleonline.com/story",
                    "thumbnail": "https://example.com/news.png"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "headlineList")
        val headline = transformed.cardObjects("headlines").single()

        assertThat(transformed.hasCardContentFor("headlineList")).isTrue()
        assertThat(headline.cardString("title")).isEqualTo("AI helps doctors")
        assertThat(headline.cardString("source")).isEqualTo("chronicleonline.com")
        assertThat(headline.cardString("publishedAt")).isEqualTo("38 minutes ago")
        assertThat(headline.cardString("url")).isEqualTo("https://chronicleonline.com/story")
        assertThat(headline.cardString("imageUrl")).isEqualTo("https://example.com/news.png")
    }

    @Test
    fun `web search payload reads pagemap thumbnail for headline cards`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "organic_results": [
                  {
                    "title": "ZATO: Netflix Announces New Mystery Drama",
                    "source": "theeurotvplace.com",
                    "link": "https://example.com/zato",
                    "pagemap": {
                      "cse_thumbnail": [
                        { "src": "https://example.com/zato-thumb.jpg" }
                      ],
                      "cse_image": [
                        { "src": "https://example.com/zato-large.jpg" }
                      ]
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "headlineList")
        val headline = transformed.cardObjects("headlines").single()

        assertThat(headline.cardString("imageUrl")).isEqualTo("https://example.com/zato-thumb.jpg")
    }

    @Test
    fun `web search iso dates are compacted for headline cards`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "organic_results": [
                  {
                    "title": "Google I/O 2026 Sunsets Views UI For Jetpack Compose Push",
                    "source": "Talk Android",
                    "date": "2026-06-08T15:17:22.000Z",
                    "link": "https://talkandroid.com/story"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "headlineList")
        val headline = transformed.cardObjects("headlines").single()

        assertThat(transformed.hasCardContentFor("headlineList")).isTrue()
        assertThat(headline.cardString("publishedAt")).isEqualTo("2026-06-08")
    }

    @Test
    fun `image search payload accepts alternate image fields and title fallback`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "images_results": [
                  {
                    "title": "Chengdu skyline at night",
                    "image": "https://example.com/chengdu.jpg",
                    "link": "https://example.com/story",
                    "source": "Example Photos"
                  },
                  {
                    "title": "Chengdu skyline source only",
                    "source": "Example Photos"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "imageGrid")
        val images = transformed.cardObjects("images")

        assertThat(transformed.hasCardContentFor("imageGrid")).isTrue()
        assertThat(images).hasSize(2)
        assertThat(images.first().cardString("thumbnail")).isEqualTo("https://example.com/chengdu.jpg")
        assertThat(images.first().cardString("original")).isEqualTo("https://example.com/story")
        assertThat(images[1].cardString("title")).isEqualTo("Chengdu skyline source only")
    }

    @Test
    fun `shopping payload preserves thumbnail price rating reviews and merchant`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "shopping_results": [
                  {
                    "title": "Apple MacBook Pro",
                    "thumbnail": "https://example.com/macbook.png",
                    "price": "$1,899.00",
                    "rating": 4.8,
                    "reviews": 2600,
                    "source": "Apple",
                    "link": "https://apple.com/macbook"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "productList")
        val product = transformed.cardObjects("products").single()

        assertThat(transformed.hasCardContentFor("productList")).isTrue()
        assertThat(product.cardString("title")).isEqualTo("Apple MacBook Pro")
        assertThat(product.cardString("thumbnail")).isEqualTo("https://example.com/macbook.png")
        assertThat(product.cardString("price")).isEqualTo("$1,899.00")
        assertThat(product.optDouble("rating")).isEqualTo(4.8)
        assertThat(product.optInt("reviews")).isEqualTo(2600)
        assertThat(product.cardString("source")).isEqualTo("Apple")
        assertThat(product.cardString("url")).isEqualTo("https://apple.com/macbook")
    }

    @Test
    fun `shopping payload accepts alternate image fields`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "shopping_results": [
                  {
                    "title": "Framework Laptop",
                    "thumbnailUrl": "https://example.com/framework-thumb.png",
                    "price": "$1,499"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "productList")
        val product = transformed.cardObjects("products").single()

        assertThat(product.cardString("thumbnail")).isEqualTo("https://example.com/framework-thumb.png")
    }

    @Test
    fun `events payload preserves title date venue address url and image`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "events_results": [
                  {
                    "title": "AI Builders Shanghai",
                    "date": { "when": "Tomorrow, 2:30 - 4:30 PM" },
                    "venue": { "name": "West Bund Center" },
                    "address": ["Shanghai", "Xuhui"],
                    "thumbnail": "https://example.com/event.png",
                    "link": "https://example.com/events/ai-builders"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "eventList")
        val event = transformed.cardObjects("events").single()

        assertThat(transformed.hasCardContentFor("eventList")).isTrue()
        assertThat(event.cardString("title")).isEqualTo("AI Builders Shanghai")
        assertThat(event.cardString("when")).isEqualTo("Tomorrow, 2:30 - 4:30 PM")
        assertThat(event.cardString("venue")).isEqualTo("West Bund Center")
        assertThat(event.cardString("address")).isEqualTo("Shanghai, Xuhui")
        assertThat(event.cardString("thumbnail")).isEqualTo("https://example.com/event.png")
        assertThat(event.cardString("url")).isEqualTo("https://example.com/events/ai-builders")
    }

    @Test
    fun `events payload accepts alternate image fields`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "events_results": [
                  {
                    "title": "AI Conference Shanghai",
                    "imageUrl": "https://example.com/event-image.jpg"
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "eventList")
        val event = transformed.cardObjects("events").single()

        assertThat(event.cardString("thumbnail")).isEqualTo("https://example.com/event-image.jpg")
    }

    @Test
    fun `places payload preserves image gallery address rating review count and map url`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "local_results": [
                  {
                    "title": "Mr&Mrs Bund",
                    "type": "Western restaurant",
                    "rating": 4.5,
                    "reviews": 148,
                    "address": "The Bund No.18, Shanghai",
                    "images": [
                      { "thumbnail": "https://example.com/bund.png", "original_image": "https://example.com/bund-large.png" },
                      "https://example.com/bund-side.png"
                    ],
                    "gps_coordinates": { "latitude": 31.239, "longitude": 121.489 }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "placeList")
        val place = transformed.cardObjects("places").single()

        assertThat(transformed.hasCardContentFor("placeList")).isTrue()
        assertThat(place.cardString("name")).isEqualTo("Mr&Mrs Bund")
        assertThat(place.cardString("thumbnail")).isEqualTo("https://example.com/bund.png")
        assertThat(place.optJSONArray("imageUrls")?.length()).isEqualTo(2)
        assertThat(place.cardString("address")).isEqualTo("The Bund No.18, Shanghai")
        assertThat(place.optDouble("rating")).isEqualTo(4.5)
        assertThat(place.optInt("reviews")).isEqualTo(148)
        assertThat(place.cardString("url")).contains("google.com/maps")
    }

    @Test
    fun `hotel payload preserves image gallery amenities price rating and map url`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "properties": [
                  {
                    "name": "Buddha Zen Hotel Chengdu",
                    "rate_per_night": { "lowest": "$47" },
                    "total_rate": { "lowest": "$190" },
                    "overall_rating": 4.7,
                    "reviews": 80,
                    "hotel_class": 4,
                    "address": "Wenshu Yuan Monastery",
                    "images": [
                      { "thumbnail": "https://example.com/thumb.jpg", "original_image": "https://example.com/1.jpg" },
                      { "thumbnail": "https://example.com/thumb2.jpg", "original_image": "https://example.com/2.jpg" }
                    ],
                    "amenities": ["Breakfast", "Free Wi-Fi", "Parking"],
                    "gps_coordinates": { "latitude": 30.67, "longitude": 104.06 }
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "hotelBooking")
        val hotel = transformed.cardObjects("hotels").single()

        assertThat(transformed.hasCardContentFor("hotelBooking")).isTrue()
        assertThat(hotel.cardString("name")).isEqualTo("Buddha Zen Hotel Chengdu")
        assertThat(hotel.cardString("thumbnail")).isEqualTo("https://example.com/thumb.jpg")
        assertThat(hotel.cardString("imageUrl")).isEqualTo("https://example.com/thumb.jpg")
        assertThat(hotel.optJSONArray("images")?.length()).isEqualTo(2)
        assertThat(hotel.optJSONArray("imageUrls")?.length()).isEqualTo(2)
        assertThat(hotel.cardString("price")).isEqualTo("$47")
        assertThat(hotel.cardString("total")).isEqualTo("$190")
        assertThat(hotel.cardString("totalPrice")).isEqualTo("$190")
        assertThat(hotel.optDouble("rating")).isEqualTo(4.7)
        assertThat(hotel.optInt("reviews")).isEqualTo(80)
        assertThat(hotel.optInt("reviewCount")).isEqualTo(80)
        assertThat(hotel.optJSONArray("amenities")?.length()).isEqualTo(3)
        assertThat(transformed.optJSONArray("images")?.length()).isEqualTo(2)
        assertThat(transformed.optJSONArray("amenities")?.length()).isEqualTo(3)
        assertThat(hotel.cardString("mapUrl")).contains("google.com/maps")
        assertThat(hotel.cardString("mapsUrl")).contains("google.com/maps")
    }

    @Test
    fun `hotel payload keeps each hotel gallery scoped to that hotel`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "properties": [
                  {
                    "name": "Felton Gloria Grand Hotel",
                    "images": [
                      { "thumbnail": "https://example.com/felton-thumb.jpg", "original_image": "https://example.com/felton-1.jpg" },
                      { "thumbnail": "https://example.com/felton-thumb-2.jpg", "original_image": "https://example.com/felton-2.jpg" }
                    ]
                  },
                  {
                    "name": "Buddha Zen Hotel",
                    "images": [
                      { "thumbnail": "https://example.com/buddha-thumb.jpg", "original_image": "https://example.com/buddha-1.jpg" },
                      { "thumbnail": "https://example.com/buddha-thumb-2.jpg", "original_image": "https://example.com/buddha-2.jpg" },
                      { "thumbnail": "https://example.com/buddha-thumb-3.jpg", "original_image": "https://example.com/buddha-3.jpg" }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "hotelBooking")
        val hotels = transformed.cardObjects("hotels")
        val firstImages = hotels[0].optJSONArray("imageUrls")
        val secondImages = hotels[1].optJSONArray("imageUrls")

        assertThat(hotels).hasSize(2)
        assertThat(hotels[0].cardString("thumbnail")).isEqualTo("https://example.com/felton-thumb.jpg")
        assertThat(firstImages?.length()).isEqualTo(2)
        assertThat(firstImages?.getString(0)).isEqualTo("https://example.com/felton-thumb.jpg")
        assertThat(firstImages?.toString()).doesNotContain("buddha")
        assertThat(hotels[1].cardString("thumbnail")).isEqualTo("https://example.com/buddha-thumb.jpg")
        assertThat(secondImages?.length()).isEqualTo(3)
        assertThat(secondImages?.getString(0)).isEqualTo("https://example.com/buddha-thumb.jpg")
        assertThat(secondImages?.toString()).doesNotContain("felton")
    }

    @Test
    fun `hotel payload reads serp ads results`() {
        val payload = JSONObject(
            """
            {
              "results": {
                "ads": [
                  {
                    "name": "Hilton Chengdu Chenghua",
                    "price": "$90",
                    "overall_rating": 4.5,
                    "hotel_class": 5,
                    "amenities": ["Breakfast ($)", "Wi-Fi"],
                    "images": [
                      { "thumbnail": "https://example.com/hilton.jpg" }
                    ]
                  }
                ]
              }
            }
            """.trimIndent()
        )

        val transformed = CardTransforms.transform(payload, "hotelBooking")
        val hotel = transformed.cardObjects("hotels").single()

        assertThat(transformed.hasCardContentFor("hotelBooking")).isTrue()
        assertThat(hotel.cardString("name")).isEqualTo("Hilton Chengdu Chenghua")
        assertThat(hotel.cardString("price")).isEqualTo("$90")
        assertThat(hotel.optDouble("rating")).isEqualTo(4.5)
        assertThat(hotel.cardString("thumbnail")).isEqualTo("https://example.com/hilton.jpg")
        assertThat(hotel.optJSONArray("amenities")?.length()).isEqualTo(2)
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
