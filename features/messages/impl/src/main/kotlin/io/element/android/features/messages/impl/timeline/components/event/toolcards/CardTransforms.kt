/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps a raw Composio/GitHub/etc. tool response into the props each card expects, mirroring iOS
 * `ToolCardsIOS/CardTransforms` field-for-field. Without this step the cards read post-transform
 * keys (e.g. `repositories`, `headlines`) against the raw snake_case response (`items`,
 * `organic_results`, …) and render nothing. Unknown card types pass through unchanged.
 */
internal object CardTransforms {
    fun transform(raw: JSONObject, cardType: String): JSONObject = when (cardType) {
        "flightAlert" -> flight(raw)
        "hotelBooking" -> hotel(raw)
        "headlineList", "breakingNews" -> search(raw)
        "imageGrid" -> imageGrid(raw)
        "productList" -> product(raw)
        "finance" -> finance(raw)
        "eventList" -> event(raw)
        "placeList" -> place(raw)
        "urlContent" -> urlContent(raw)
        "githubIssuesList" -> githubIssues(raw)
        "repoList" -> githubRepos(raw)
        else -> raw
    }

    // MARK: - GitHub Issues / PRs

    private fun githubIssues(raw: JSONObject): JSONObject {
        var forcePR = false
        val source = when {
            raw.objList("issues").isNotEmpty() -> raw.objList("issues")
            raw.objList("pull_requests").isNotEmpty() -> { forcePR = true; raw.objList("pull_requests") }
            raw.objList("items").isNotEmpty() -> raw.objList("items")
            else -> return raw
        }
        val items = JSONArray()
        source.forEach { issue ->
            val item = JSONObject()
            issue.intOrNull("number")?.let { item.put("number", it) }
            issue.str("title")?.let { item.put("title", it) }
            issue.str("state")?.let { item.put("state", it) }
            issue.str("html_url")?.let { item.put("url", it) }
            issue.obj("user")?.str("login")?.let { item.put("author", it) }
            issue.optJSONArray("labels")?.let { labels ->
                val out = JSONArray()
                for (i in 0 until labels.length()) {
                    val l = labels.optJSONObject(i) ?: continue
                    val mapped = JSONObject()
                    l.str("name")?.let { mapped.put("name", it) }
                    l.str("color")?.let { mapped.put("color", it) }
                    out.put(mapped)
                }
                item.put("labels", out)
            }
            issue.intOrNull("comments")?.let { item.put("commentCount", it) }
            issue.str("updated_at")?.let { item.put("updatedAt", it) }
            if (forcePR || !issue.isNull("pull_request") && issue.has("pull_request")) item.put("isPR", true)
            items.put(item)
        }
        return JSONObject().put("items", items)
    }

    // MARK: - GitHub Repos

    private fun githubRepos(raw: JSONObject): JSONObject {
        val rawItems = raw.objList("items")
        if (rawItems.isEmpty()) return raw
        val repos = JSONArray()
        rawItems.forEach { repo ->
            val r = JSONObject()
            (repo.str("full_name") ?: repo.str("fullName"))?.let { r.put("fullName", it) }
            repo.str("name")?.let { r.put("name", it) }
            repo.str("description")?.let { r.put("description", it) }
            repo.str("language")?.let { r.put("language", it) }
            (repo.intOrNull("stargazers_count") ?: repo.intOrNull("stargazersCount"))?.let { r.put("stargazersCount", it) }
            (repo.intOrNull("forks_count") ?: repo.intOrNull("forksCount"))?.let { r.put("forksCount", it) }
            repo.obj("owner")?.let { owner ->
                val o = JSONObject()
                owner.str("login")?.let { o.put("login", it) }
                (owner.str("avatar_url") ?: owner.str("avatarUrl"))?.let { o.put("avatarUrl", it) }
                r.put("owner", o)
            }
            (repo.str("html_url") ?: repo.str("htmlUrl"))?.let { r.put("htmlUrl", it) }
            repo.optJSONArray("topics")?.let { r.put("topics", it) }
            repo.str("visibility")?.let { r.put("visibility", it) }
            repos.put(r)
        }
        val result = JSONObject().put("repositories", repos)
        (raw.intOrNull("total_count") ?: raw.intOrNull("totalCount"))?.let { result.put("totalCount", it) }
        return result
    }

    // MARK: - Flights

    private fun flight(raw: JSONObject): JSONObject {
        val results = raw.obj("results") ?: return raw
        val allOptions = results.objList("best_flights") + results.objList("other_flights")
        val flights = JSONArray()
        allOptions.forEach { option ->
            val segments = option.objList("flights")
            val firstSeg = segments.firstOrNull() ?: return@forEach
            val lastSeg = segments.lastOrNull() ?: return@forEach
            val dep = firstSeg.obj("departure_airport")
            val arr = lastSeg.obj("arrival_airport")
            val flight = JSONObject()
                .put("flightNumber", firstSeg.str("flight_number") ?: "")
                .put("airline", firstSeg.str("airline") ?: "")
            (option.str("airline_logo") ?: firstSeg.str("airline_logo"))?.let { flight.put("airlineLogo", it) }
            dep?.str("id")?.let { flight.put("departureCode", it) }
            arr?.str("id")?.let { flight.put("arrivalCode", it) }
            dep?.str("name")?.let { flight.put("departureAirport", it) }
            arr?.str("name")?.let { flight.put("arrivalAirport", it) }
            shortTime(dep?.str("time"))?.let { flight.put("departureTime", it) }
            shortTime(arr?.str("time"))?.let { flight.put("arrivalTime", it) }
            option.intOrNull("total_duration")?.let { d ->
                val h = d / 60; val m = d % 60
                flight.put("duration", if (m > 0) "${h}h ${m}m" else "${h}h")
            }
            flight.put("stops", option.objList("layovers").size.takeIf { option.has("layovers") } ?: maxOf(0, segments.size - 1))
            firstSeg.str("travel_class")?.let { flight.put("travelClass", it) }
            firstSeg.str("airplane")?.let { flight.put("airplane", it) }
            option.intOrNull("price")?.let { flight.put("price", it); flight.put("priceFormatted", "$$it") }
            flights.put(flight)
        }
        val props = JSONObject().put("flights", flights)
        results.obj("price_insights")?.str("price_level")?.let { props.put("priceLevel", it) }
        results.obj("search_metadata")?.str("google_flights_url")?.let { props.put("searchUrl", it) }
        return props
    }

    // MARK: - Image search

    private fun imageGrid(raw: JSONObject): JSONObject {
        val source = raw.obj("results") ?: raw
        val items = source.objList("images_results").ifEmpty { raw.objList("images_results") }.ifEmpty { source.objList("items") }
        val images = JSONArray()
        items.forEach { item ->
            val img = JSONObject()
            item.str("thumbnail")?.let { img.put("thumbnail", it) }
            item.str("original")?.let { img.put("original", it) }
            item.str("title")?.let { img.put("title", it) }
            item.str("source")?.let { img.put("source", it) }
            if (img.has("thumbnail") || img.has("original")) images.put(img)
        }
        return JSONObject().put("images", images)
    }

    // MARK: - Shopping

    private fun product(raw: JSONObject): JSONObject {
        val source = raw.obj("results") ?: raw
        val items = source.objList("shopping_results")
            .ifEmpty { source.objList("products") }
            .ifEmpty { source.objList("organic_results") }
            .ifEmpty { source.objList("items") }
            .ifEmpty { raw.objList("results") }
        val products = JSONArray()
        items.forEach { item ->
            val p = JSONObject()
            item.str("title")?.let { p.put("title", it) } ?: return@forEach
            (item.str("thumbnail") ?: item.str("image"))?.let { p.put("thumbnail", it) }
            val priceStr = item.str("price")
            when {
                priceStr != null -> p.put("price", priceStr)
                item.obj("primary_offer") != null -> {
                    val offer = item.obj("primary_offer")!!
                    (offer.doubleOrNull("offer_price") ?: offer.doubleOrNull("min_price"))?.let {
                        p.put("price", formatPrice(it, offer.str("currency")))
                    }
                }
                item.doubleOrNull("extracted_price") != null -> p.put("price", formatPrice(item.doubleOrNull("extracted_price")!!, null))
            }
            item.doubleOrNull("rating")?.let { p.put("rating", it) }
            item.intOrNull("reviews")?.let { p.put("reviews", it) }
            item.str("source")?.let { p.put("source", it) }
            (item.str("link") ?: item.str("product_link") ?: item.str("product_page_url"))?.let { p.put("url", it) }
            products.put(p)
        }
        return JSONObject().put("products", products)
    }

    // MARK: - Events

    private fun event(raw: JSONObject): JSONObject {
        val source = raw.obj("results") ?: raw
        val items = source.objList("events_results").ifEmpty { source.objList("items") }
        val events = JSONArray()
        items.forEach { item ->
            val title = item.str("title") ?: return@forEach
            val e = JSONObject().put("title", title)
            item.obj("date")?.let { d -> (d.str("when") ?: d.str("start_date"))?.let { e.put("when", it) } }
                ?: item.str("when")?.let { e.put("when", it) }
            item.obj("venue")?.str("name")?.let { e.put("venue", it) } ?: item.str("venue")?.let { e.put("venue", it) }
            joinStrings(item.opt("address"))?.let { e.put("address", it) }
            item.str("thumbnail")?.let { e.put("thumbnail", it) }
            item.str("link")?.let { e.put("url", it) }
            events.put(e)
        }
        return JSONObject().put("events", events)
    }

    // MARK: - Places

    private fun place(raw: JSONObject): JSONObject {
        val source = raw.obj("results") ?: raw
        var items = source.objList("local_results").ifEmpty { source.objList("items") }
        if (items.isEmpty()) source.obj("place_results")?.let { items = listOf(it) }
        val places = JSONArray()
        items.forEach { item ->
            val name = item.str("title") ?: item.str("name") ?: return@forEach
            val p = JSONObject().put("name", name)
            (item.str("type") ?: item.strList("types").firstOrNull())?.let { p.put("type", it) }
            item.doubleOrNull("rating")?.let { p.put("rating", it) }
            item.intOrNull("reviews")?.let { p.put("reviews", it) }
            val address = joinStrings(item.opt("address"))
            address?.let { p.put("address", it) }
            item.str("thumbnail")?.let { p.put("thumbnail", it) }
            item.str("price")?.let { p.put("price", it) }
            item.str("open_state")?.let { p.put("openState", it) }
            item.str("phone")?.let { p.put("phone", it) }
            item.str("website")?.let { p.put("website", it) }
            p.put("url", mapsURL(name, address, item.obj("gps_coordinates")))
            places.put(p)
        }
        return JSONObject().put("places", places)
    }

    // MARK: - Fetched URL content

    private fun urlContent(raw: JSONObject): JSONObject {
        val source = raw.obj("results") ?: raw
        val items = source.objList("results").ifEmpty { raw.objList("results") }.ifEmpty { source.objList("items") }
        val articles = JSONArray()
        items.forEach { item ->
            if (!item.str("error").isNullOrEmpty()) return@forEach
            val url = item.str("url") ?: return@forEach
            val a = JSONObject().put("url", url)
            item.str("title")?.let { a.put("title", it) }
            articles.put(a)
        }
        return JSONObject().put("articles", articles)
    }

    // MARK: - Hotels

    private fun hotel(raw: JSONObject): JSONObject {
        val results = raw.obj("results") ?: return raw
        val hotels = JSONArray()
        results.objList("properties").forEach { prop ->
            val hotel = JSONObject()
            prop.str("name")?.let { hotel.put("name", it) }
            prop.obj("rate_per_night")?.let { rate ->
                rate.str("lowest")?.let { hotel.put("price", it) }
                prop.obj("total_rate")?.str("lowest")?.let { hotel.put("totalPrice", it) }
            }
            prop.doubleOrNull("overall_rating")?.let { hotel.put("rating", it) }
            prop.intOrNull("reviews")?.let { hotel.put("reviewCount", it) }
            (prop.intOrNull("extracted_hotel_class") ?: prop.intOrNull("hotel_class"))?.let { hotel.put("stars", it) }
            prop.str("address")?.let { hotel.put("address", it) }
                ?: prop.objList("nearby_places").firstOrNull()?.str("name")?.let { hotel.put("address", it) }
            val images = prop.objList("images")
            if (images.isNotEmpty()) {
                hotel.put("imageUrl", images[0].str("thumbnail") ?: images[0].str("original_image") ?: "")
                val urls = JSONArray()
                images.forEach { img -> (img.str("original_image") ?: img.str("thumbnail"))?.let { urls.put(it) } }
                hotel.put("imageUrls", urls)
            } else {
                prop.str("thumbnail")?.let { hotel.put("imageUrl", it); hotel.put("imageUrls", JSONArray().put(it)) }
            }
            prop.str("link")?.let { hotel.put("url", it) }
            prop.str("name")?.let { name ->
                val coords = prop.obj("gps_coordinates")
                val lat = coords?.doubleOrNull("latitude")
                val lng = coords?.doubleOrNull("longitude")
                val q = name
                hotel.put(
                    "mapsUrl",
                    if (lat != null && lng != null) "https://www.google.com/maps/search/?api=1&query=$q&center=$lat,$lng"
                    else "https://www.google.com/maps/search/?api=1&query=$q",
                )
            }
            prop.optJSONArray("amenities")?.let { hotel.put("amenities", it) }
            hotels.put(hotel)
        }
        return JSONObject().put("hotels", hotels)
    }

    // MARK: - Finance

    private fun finance(raw: JSONObject): JSONObject {
        val source = raw.obj("results") ?: raw
        val props = JSONObject()
        source.obj("summary")?.let { summary ->
            val quote = JSONObject()
            quote.put("name", summary.str("title") ?: summary.str("name") ?: "")
            summary.str("stock")?.let { quote.put("ticker", it) }
            summary.str("exchange")?.let { quote.put("exchange", it) }
            summary.doubleOrNull("extracted_price")?.let { quote.put("price", formatPrice(it, summary.str("currency"))) }
                ?: summary.str("price")?.let { quote.put("price", it) }
            applyMovement(summary.opt("price_movement"), quote)
            props.put("quote", quote)
        }
        source.obj("markets")?.let { markets ->
            val rows = JSONArray()
            val keys = markets.keys()
            while (keys.hasNext()) {
                val arr = markets.optJSONArray(keys.next()) ?: continue
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val row = JSONObject()
                    row.put("name", item.str("name") ?: item.str("title") ?: "")
                    (item.str("price") ?: item.doubleOrNull("extracted_price")?.let { formatPrice(it, null) })?.let { row.put("price", it) }
                    applyMovement(item.opt("price_movement"), row)
                    if (!row.optString("name").isNullOrEmpty()) rows.put(row)
                }
            }
            if (rows.length() > 0) props.put("markets", rows)
        }
        source.objList("graph").let { graph ->
            val points = JSONArray()
            graph.forEach { g ->
                val price = g.doubleOrNull("price") ?: return@forEach
                val p = JSONObject().put("price", price)
                g.str("date")?.let { p.put("date", it) }
                g.intOrNull("volume")?.let { p.put("volume", it) }
                points.put(p)
            }
            if (points.length() > 0) props.put("graph", points)
        }
        source.str("currency")?.let { props.put("currency", it) }
        source.objList("key_events").let { events ->
            val mapped = JSONArray()
            events.forEach { e ->
                val title = e.str("title") ?: return@forEach
                val d = JSONObject().put("title", title)
                (e.str("source_date") ?: e.str("date"))?.let { d.put("date", it) }
                e.str("source")?.let { d.put("source", it) }
                e.str("link")?.let { d.put("url", it) }
                mapped.put(d)
            }
            if (mapped.length() > 0) props.put("keyEvents", mapped)
        }
        source.objList("news_results").let { groups ->
            val news = JSONArray()
            groups.forEach { g ->
                val firstItem = g.objList("items").firstOrNull()
                val title = g.str("title") ?: g.str("snippet") ?: firstItem?.str("snippet") ?: return@forEach
                val n = JSONObject().put("title", title)
                (g.str("source") ?: firstItem?.str("source"))?.let { n.put("source", it) }
                (g.str("date") ?: firstItem?.str("date"))?.let { n.put("date", it) }
                (g.str("thumbnail") ?: firstItem?.str("thumbnail"))?.let { n.put("thumbnail", it) }
                (g.str("link") ?: firstItem?.str("link"))?.let { n.put("url", it) }
                news.put(n)
            }
            if (news.length() > 0) props.put("news", news)
        }
        source.obj("summary")?.strList("extensions")?.takeIf { it.isNotEmpty() }?.let { stats ->
            props.put("stats", JSONArray(stats))
        }
        return props
    }

    private fun applyMovement(movement: Any?, dict: JSONObject) {
        val m = movement as? JSONObject ?: return
        m.doubleOrNull("percentage")?.let { dict.put("changePercent", it) }
        m.str("movement")?.let { dict.put("up", it.lowercase() == "up") }
    }

    // MARK: - Web Search / News

    private fun search(raw: JSONObject): JSONObject {
        val source = raw.obj("results") ?: raw
        val items = when {
            source.objList("citations").isNotEmpty() -> source.objList("citations")
            source.objList("news_results").isNotEmpty() -> source.objList("news_results")
            source.objList("organic_results").isNotEmpty() -> source.objList("organic_results")
            source.objList("items").isNotEmpty() -> source.objList("items")
            else -> emptyList()
        }
        val headlines = JSONArray()
        items.forEach { item ->
            val h = JSONObject()
            item.str("title")?.let { h.put("title", it) }
            (item.str("url") ?: item.str("link"))?.let { h.put("url", it) }
            (item.str("snippet") ?: item.str("text") ?: item.str("publishedDate"))?.let { h.put("snippet", it) }
            (item.str("author") ?: item.str("source") ?: item.str("domain") ?: item.str("label"))?.let { h.put("source", it) }
            (item.str("date") ?: item.str("publishedAt") ?: item.str("published_at") ?: item.str("meta"))?.let { h.put("publishedAt", it) }
            var imageUrl = item.str("thumbnail") ?: item.str("image") ?: item.str("original") ?: item.str("imageUrl")
            if (imageUrl == null) {
                item.optJSONArray("images")?.takeIf { it.length() > 0 }?.let { imgs ->
                    imageUrl = (imgs.opt(0) as? String)
                        ?: imgs.optJSONObject(0)?.str("url")
                        ?: imgs.optJSONObject(0)?.str("thumbnail")
                }
            }
            imageUrl?.takeIf { it.isNotEmpty() }?.let { h.put("imageUrl", it) }
            headlines.put(h)
        }
        val props = JSONObject().put("headlines", headlines)
        source.str("answer")?.let { props.put("summary", it) }
        return props
    }

    // MARK: - Helpers

    private fun formatPrice(value: Double, currency: String?): String {
        val symbol = if (currency == null || currency == "USD") "$" else "$currency "
        return if (value % 1.0 == 0.0) "$symbol${value.toInt()}" else String.format("$symbol%.2f", value)
    }

    private fun mapsURL(name: String, address: String?, gps: JSONObject?): String {
        val query = listOfNotNull(name, address).joinToString(", ")
        val rawQuery = if (query.isEmpty() && gps != null) {
            val lat = gps.doubleOrNull("latitude"); val lng = gps.doubleOrNull("longitude")
            if (lat != null && lng != null) "$lat,$lng" else query
        } else {
            query
        }
        val encoded = java.net.URLEncoder.encode(rawQuery, "UTF-8")
        return "https://www.google.com/maps/search/?api=1&query=$encoded"
    }

    private fun shortTime(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        val sep = when { raw.contains("T") -> "T"; raw.contains(" ") -> " "; else -> null }
        if (sep != null) {
            val idx = raw.indexOf(sep)
            if (idx >= 0) return raw.substring(idx + 1).take(5)
        }
        return raw.take(5)
    }

    private fun joinStrings(value: Any?): String? = when (value) {
        is String -> value.takeIf { it.isNotEmpty() }
        is JSONArray -> (0 until value.length()).mapNotNull { value.opt(it) as? String }
            .joinToString(", ").takeIf { it.isNotEmpty() }
        else -> null
    }

    // org.json convenience extensions
    private fun JSONObject.objList(key: String): List<JSONObject> =
        optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) } } ?: emptyList()

    private fun JSONObject.strList(key: String): List<String> =
        optJSONArray(key)?.let { a -> (0 until a.length()).mapNotNull { a.opt(it) as? String } } ?: emptyList()

    private fun JSONObject.str(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotEmpty() } else null

    private fun JSONObject.intOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) (opt(key) as? Number)?.toInt() ?: (opt(key) as? String)?.toIntOrNull() else null

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) (opt(key) as? Number)?.toDouble() ?: (opt(key) as? String)?.toDoubleOrNull() else null

    private fun JSONObject.obj(key: String): JSONObject? = optJSONObject(key)
}
