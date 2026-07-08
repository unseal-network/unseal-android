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
        "weather" -> weather(raw)
        "eventList" -> event(raw)
        "placeList" -> place(raw)
        "urlContent" -> urlContent(raw)
        "githubIssuesList" -> githubIssues(raw)
        "repoList" -> githubRepos(raw)
        "fileAttachment" -> fileAttachment(raw)
        "commentThread" -> commentThread(raw)
        "socialPostFeed" -> socialPost(raw)
        "checkRuns" -> canonicalList(raw, "checkRuns", "check_runs", "items")
        "deployments" -> canonicalList(raw, "deployments", "items")
        "notifications" -> canonicalList(raw, "notifications", "items")
        "secretAlerts" -> canonicalList(raw, "alerts", "secretAlerts", "secret_alerts", "items")
        "workflows" -> canonicalList(raw, "workflows", "items")
        "orgsList" -> canonicalList(raw, "organizations", "orgs", "items")
        "contributors" -> canonicalList(raw, "contributors", "items")
        "linearIssuesList" -> canonicalList(raw, "items", "issues", "pull_requests")
        "release" -> release(raw)
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
        val results = raw.toolCardEnvelope()
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
        val source = raw.toolCardEnvelope()
        val items = source.objList("images_results")
            .ifEmpty { source.objList("images") }
            .ifEmpty { source.objList("items") }
        val images = JSONArray()
        items.forEach { item ->
            val img = JSONObject()
            (item.str("thumbnail") ?: item.str("image") ?: item.str("imageUrl"))?.let { img.put("thumbnail", it) }
            (item.str("original") ?: item.str("original_image") ?: item.str("url") ?: item.str("link"))?.let { img.put("original", it) }
            (item.str("title") ?: item.str("alt") ?: item.str("description"))?.let { img.put("title", it) }
            (item.str("source") ?: item.str("domain") ?: item.str("website"))?.let { img.put("source", it) }
            if (img.has("thumbnail") || img.has("original") || img.has("title")) images.put(img)
        }
        return JSONObject().put("images", images)
    }

    // MARK: - Shopping

    private fun product(raw: JSONObject): JSONObject {
        val source = raw.toolCardEnvelope()
        val items = source.objList("shopping_results")
            .ifEmpty { source.objList("products") }
            .ifEmpty { source.objList("organic_results") }
            .ifEmpty { source.objList("items") }
            .ifEmpty { source.objList("results") }
        val products = JSONArray()
        items.forEach { item ->
            val p = JSONObject()
            item.str("title")?.let { p.put("title", it) } ?: return@forEach
            (item.thumbnailImageUrl() ?: item.firstImageUrl())?.let { p.put("thumbnail", it) }
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
        val source = raw.toolCardEnvelope()
        val items = source.objList("events_results")
            .ifEmpty { source.objList("events") }
            .ifEmpty { source.objList("items") }
        val events = JSONArray()
        items.forEach { item ->
            val title = item.str("title") ?: return@forEach
            val e = JSONObject().put("title", title)
            item.obj("date")?.let { d -> (d.str("when") ?: d.str("start_date"))?.let { e.put("when", it) } }
                ?: item.str("when")?.let { e.put("when", it) }
            item.obj("venue")?.str("name")?.let { e.put("venue", it) } ?: item.str("venue")?.let { e.put("venue", it) }
            joinStrings(item.opt("address"))?.let { e.put("address", it) }
            (item.thumbnailImageUrl() ?: item.firstImageUrl())?.let { e.put("thumbnail", it) }
            item.str("link")?.let { e.put("url", it) }
            events.put(e)
        }
        return JSONObject().put("events", events)
    }

    // MARK: - Places

    private fun place(raw: JSONObject): JSONObject {
        val source = raw.toolCardEnvelope()
        var items = source.objList("local_results")
            .ifEmpty { source.objList("places") }
            .ifEmpty { source.objList("items") }
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
            val imageUrls = item.imageUrls()
            (item.thumbnailImageUrl() ?: imageUrls.firstOrNull())?.let { p.put("thumbnail", it) }
            if (imageUrls.isNotEmpty()) {
                p.put("imageUrls", JSONArray().also { urls -> imageUrls.forEach(urls::put) })
            }
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
        val source = raw.toolCardEnvelope()
        val items = source.objList("results").ifEmpty { source.objList("items") }
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

    // MARK: - Files / comments / social feeds

    private fun fileAttachment(raw: JSONObject): JSONObject {
        firstObjectList(raw, "files", "items")?.let { files ->
            return JSONObject()
                .put("title", raw.str("title") ?: "Files")
                .put("files", JSONArray((0 until files.length()).mapNotNull { files.optJSONObject(it)?.normalizedFileAttachment() }))
        }
        if (!raw.hasAny("name", "filename", "title", "mimeType", "mime_type", "webViewLink", "web_view_link", "url", "id")) return raw
        return JSONObject()
            .put("title", raw.str("title") ?: "Files")
            .put("files", JSONArray().put(raw.normalizedFileAttachment()))
    }

    private fun commentThread(raw: JSONObject): JSONObject {
        firstObjectList(raw, "comments", "items")?.let { return JSONObject().put("comments", it) }
        if (!raw.hasAny("body", "comment", "user", "author", "created_at", "createdAt", "html_url", "url")) return raw
        val comment = JSONObject()
        (raw.str("body") ?: raw.str("comment"))?.let { comment.put("body", it) }
        val user = raw.obj("user")
        (raw.str("author") ?: user?.str("login") ?: user?.str("name"))?.let { comment.put("author", it) }
        (raw.str("avatarUrl") ?: raw.str("avatar_url") ?: user?.str("avatar_url") ?: user?.str("avatarUrl"))?.let { comment.put("avatarUrl", it) }
        (raw.str("createdAt") ?: raw.str("created_at"))?.let { comment.put("createdAt", it) }
        (raw.str("association") ?: raw.str("author_association"))?.let { comment.put("association", it) }
        (raw.str("url") ?: raw.str("html_url"))?.let { comment.put("url", it) }
        return JSONObject().put("comments", JSONArray().put(comment))
    }

    private fun socialPost(raw: JSONObject): JSONObject {
        firstObjectList(raw, "posts", "tweets", "items", "data")?.let { return JSONObject().put("posts", it) }
        if (!raw.hasAny("text", "full_text", "body", "content", "id", "created_at", "createdAt", "author", "user")) return raw
        val post = JSONObject()
        (raw.str("text") ?: raw.str("full_text") ?: raw.str("body") ?: raw.str("content"))?.let { post.put("body", it) }
        raw.str("id")?.let { post.put("id", it) }
        (raw.str("createdAt") ?: raw.str("created_at"))?.let { post.put("createdAt", it) }
        val author = raw.obj("author") ?: raw.obj("user")
        author?.let { post.put("author", it) }
        (raw.str("url") ?: raw.str("link"))?.let { post.put("url", it) }
        return JSONObject().put("posts", JSONArray().put(post))
    }

    private fun release(raw: JSONObject): JSONObject {
        val out = JSONObject(raw.toString())
        (raw.str("tagName") ?: raw.str("tag_name"))?.let { out.put("tagName", it) }
        (raw.str("name") ?: raw.str("title"))?.let { out.put("name", it) }
        return out
    }

    private fun JSONObject.normalizedFileAttachment(): JSONObject {
        val name = str("name") ?: str("filename") ?: str("title") ?: "Attachment"
        val mimeType = str("mimeType") ?: str("mime_type") ?: str("mediaType") ?: str("type") ?: ""
        val sizeLabel = str("sizeLabel") ?: str("size") ?: str("fileSize") ?: ""
        val url = str("url") ?: str("downloadUrl") ?: str("webUrl") ?: str("webViewLink") ?: str("web_view_link") ?: str("alternateLink")
        val modifiedAt = str("modifiedAt") ?: str("modified_at") ?: str("modifiedTime") ?: str("modified_time")
        val owner = str("owner") ?: objList("owners").firstOrNull()?.str("displayName")
        val shared = opt("shared")
        return JSONObject()
            .put("name", name)
            .put("mimeType", mimeType)
            .put("sizeLabel", sizeLabel)
            .put("size", sizeLabel)
            .put("icon", fileIcon(name, mimeType))
            .apply {
                url?.let { put("url", it) }
                modifiedAt?.let { put("modifiedAt", it) }
                owner?.let { put("owner", it) }
                shared?.let { put("shared", it) }
            }
    }

    private fun fileIcon(name: String, mimeType: String): String {
        val lowerMime = mimeType.lowercase()
        val ext = name.substringAfterLast('.', "").lowercase()
        return when {
            lowerMime.startsWith("image/") -> "image"
            lowerMime.contains("pdf") || ext == "pdf" -> "pdf"
            lowerMime.contains("spreadsheet") || ext in setOf("xls", "xlsx", "csv") -> "spreadsheet"
            lowerMime.contains("presentation") || ext in setOf("ppt", "pptx") -> "presentation"
            lowerMime.contains("document") || ext in setOf("doc", "docx") -> "document"
            else -> "file"
        }
    }

    // MARK: - Hotels

    private fun hotel(raw: JSONObject): JSONObject {
        val source = raw.obj("data") ?: raw
        val results = raw.toolCardEnvelope()
        val checkIn = source.str("checkIn") ?: source.str("check_in") ?: raw.str("checkIn") ?: raw.str("check_in")
        val checkOut = source.str("checkOut") ?: source.str("check_out") ?: raw.str("checkOut") ?: raw.str("check_out")
        val items = results.objList("properties")
            .ifEmpty { results.objList("ads") }
            .ifEmpty { results.objList("hotels") }
            .ifEmpty { source.objList("hotels") }
            .ifEmpty { raw.objList("hotels") }
        if (items.isEmpty()) return raw
        val hotels = JSONArray()
        val topImages = JSONArray()
        val topAmenities = JSONArray()
        items.forEachIndexed { index, prop ->
            val hotel = JSONObject()
            prop.str("name")?.let { hotel.put("name", it) }
            prop.str("description")?.let { hotel.put("description", it) }
            val price = prop.obj("rate_per_night")?.str("lowest") ?: prop.str("price") ?: prop.str("priceFormatted")
            price?.let { hotel.put("price", it) }
            val total = prop.obj("total_rate")?.str("lowest") ?: prop.str("total") ?: prop.str("totalPrice")
            total?.let { hotel.put("total", it); hotel.put("totalPrice", it) }
            prop.doubleOrNull("overall_rating")?.let { hotel.put("rating", it) }
                ?: prop.doubleOrNull("rating")?.let { hotel.put("rating", it) }
            prop.intOrNull("reviews")?.let { hotel.put("reviewCount", it) }
            prop.intOrNull("reviews")?.let { hotel.put("reviews", it) }
            (prop.intOrNull("extracted_hotel_class") ?: prop.intOrNull("hotel_class"))?.let { hotel.put("stars", it) }
            val area = prop.str("area") ?: prop.str("address") ?: prop.objList("nearby_places").firstOrNull()?.str("name")
            area?.let { hotel.put("area", it); hotel.put("address", it) }
            val imageUrls = prop.galleryImageUrls()
            if (imageUrls.isNotEmpty()) {
                val thumbnail = prop.thumbnailImageUrl() ?: imageUrls.first()
                hotel.put("thumbnail", thumbnail)
                hotel.put("imageUrl", thumbnail)
                hotel.put("images", JSONArray(imageUrls))
                hotel.put("imageUrls", JSONArray(imageUrls))
                imageUrls.take(6 - topImages.length()).forEach { topImages.put(it) }
            }
            prop.str("link")?.let { hotel.put("url", it) }
            prop.str("name")?.let { name ->
                val coords = prop.obj("gps_coordinates")
                val lat = coords?.doubleOrNull("latitude")
                val lng = coords?.doubleOrNull("longitude")
                val q = name
                val mapUrl = if (lat != null && lng != null) "https://www.google.com/maps/search/?api=1&query=$q&center=$lat,$lng"
                else "https://www.google.com/maps/search/?api=1&query=$q"
                hotel.put("mapUrl", mapUrl)
                hotel.put("mapsUrl", mapUrl)
            }
            prop.optJSONArray("amenities")?.let { amenities ->
                hotel.put("amenities", amenities)
                if (index == 0) {
                    for (i in 0 until amenities.length()) topAmenities.put(amenities.opt(i))
                }
            }
            hotels.put(hotel)
        }
        return JSONObject()
            .put("hotels", hotels)
            .put("images", topImages)
            .put("amenities", topAmenities)
            .apply {
                checkIn?.let { put("checkIn", it) }
                checkOut?.let { put("checkOut", it) }
            }
    }

    // MARK: - Finance

    private fun finance(raw: JSONObject): JSONObject {
        val source = raw.toolCardEnvelope()
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
        source.objList("financials").let { financials ->
            val sections = JSONArray()
            financials.forEach { section ->
                val title = section.str("title") ?: return@forEach
                val latest = section.objList("results").firstOrNull() ?: return@forEach
                val rows = JSONArray()
                latest.objList("table").forEach { item ->
                    val label = item.str("title") ?: return@forEach
                    val value = item.str("value")?.takeIf { it != "—" } ?: return@forEach
                    val row = JSONObject()
                        .put("label", label)
                        .put("value", formatFinancialValue(value))
                    item.str("change")?.takeIf { it != "—" }?.let { row.put("change", it) }
                    rows.put(row)
                }
                if (rows.length() > 0) {
                    sections.put(
                        JSONObject()
                            .put("title", title)
                            .put("period", latest.str("date") ?: "")
                            .put("rows", JSONArray((0 until rows.length()).take(8).map { rows.getJSONObject(it) }))
                    )
                }
            }
            if (sections.length() > 0) props.put("financials", sections)
        }
        return props
    }

    // MARK: - Weather

    private fun weather(raw: JSONObject): JSONObject {
        val source = raw.toolCardEnvelope()
        val weatherResult = source.obj("weather_result") ?: source.obj("current") ?: source.obj("weather") ?: source
        val props = JSONObject()
        val location = source.str("location") ?: weatherResult.str("location") ?: source.str("city") ?: weatherResult.str("city")
        location?.let {
            val parts = it.split(",").map { part -> part.trim() }.filter { part -> part.isNotEmpty() }
            props.put("city", source.str("city") ?: weatherResult.str("city") ?: parts.firstOrNull().orEmpty())
            parts.drop(1).joinToString(", ").takeIf { country -> country.isNotEmpty() }?.let { country -> props.put("country", country) }
        }
        (source.str("country") ?: weatherResult.str("country"))?.let { props.put("country", it) }

        val current = JSONObject()
        numberFrom(weatherResult.opt("temperature") ?: weatherResult.opt("temp"))?.let { current.put("temperature", it) }
        numberFrom(weatherResult.opt("feelsLike") ?: weatherResult.opt("feels_like"))?.let { current.put("feelsLike", it) }
        numberFrom(weatherResult.opt("humidity"))?.let { current.put("humidity", it) }
        numberFrom(weatherResult.opt("windSpeed") ?: weatherResult.opt("wind_speed") ?: weatherResult.opt("wind"))?.let { current.put("windSpeed", it) }
        (weatherResult.str("condition") ?: weatherResult.str("weather") ?: weatherResult.str("description"))?.let { current.put("condition", it) }
        if (current.length() > 0) props.put("current", current)

        val forecastSource = source.objList("forecast")
            .ifEmpty { weatherResult.objList("forecast") }
            .ifEmpty { source.objList("daily_forecast") }
            .ifEmpty { weatherResult.objList("daily_forecast") }
        if (forecastSource.isNotEmpty()) {
            val forecast = JSONArray()
            forecastSource.forEach { item ->
                val row = JSONObject()
                (item.str("day") ?: item.str("weekday") ?: item.str("date"))?.let { row.put("day", it) }
                item.str("date")?.let { row.put("date", it) }
                numberFrom(item.opt("high") ?: item.opt("max") ?: item.opt("max_temp") ?: item.opt("temperature"))?.let { row.put("high", it) }
                numberFrom(item.opt("low") ?: item.opt("min") ?: item.opt("min_temp"))?.let { row.put("low", it) }
                (item.str("condition") ?: item.str("weather") ?: item.str("description"))?.let { row.put("condition", it) }
                numberFrom(item.opt("precipitation") ?: item.opt("rain_chance") ?: item.opt("precipitation_probability"))?.let { row.put("precipitation", it) }
                if (row.length() > 0) forecast.put(row)
            }
            if (forecast.length() > 0) props.put("forecast", forecast)
        }

        return if (props.length() > 0) props else raw
    }

    private fun applyMovement(movement: Any?, dict: JSONObject) {
        val m = movement as? JSONObject ?: return
        m.doubleOrNull("percentage")?.let { dict.put("changePercent", it) }
        m.str("movement")?.let { dict.put("up", it.lowercase() == "up") }
    }

    // MARK: - Web Search / News

    private fun search(raw: JSONObject): JSONObject {
        val source = raw.toolCardEnvelope()
        val items = when {
            source.objList("citations").isNotEmpty() -> source.objList("citations")
            source.objList("news_results").isNotEmpty() -> source.objList("news_results")
            source.objList("organic_results").isNotEmpty() -> source.objList("organic_results")
            source.objList("items").isNotEmpty() -> source.objList("items")
            source.objList("cards").isNotEmpty() -> source.objList("cards")
            else -> emptyList()
        }
        val headlines = JSONArray()
        items.forEach { item ->
            val h = JSONObject()
            (item.str("title") ?: item.str("name") ?: item.str("tag") ?: item.str("component") ?: item.str("type"))?.let { h.put("title", it) }
            (item.str("url") ?: item.str("link"))?.let { h.put("url", it) }
            (item.str("snippet") ?: item.str("text") ?: item.str("description") ?: item.str("status"))?.let { h.put("snippet", it) }
            (item.str("author") ?: item.str("source") ?: item.str("domain") ?: item.str("label"))?.let { h.put("source", it) }
            (item.str("date") ?: item.str("publishedAt") ?: item.str("published_at") ?: item.str("publishedDate") ?: item.str("published_date") ?: item.str("meta"))?.let {
                h.put("publishedAt", it.compactToolCardMetaDate())
            }
            val imageUrl = item.firstImageUrl()
            imageUrl?.takeIf { it.isNotEmpty() }?.let { h.put("imageUrl", it) }
            if (h.length() > 0) {
                headlines.put(h)
            }
        }
        val props = JSONObject().put("headlines", headlines)
        source.str("answer")?.let { props.put("summary", it) }
        return props
    }

    // MARK: - Helpers

    private fun JSONObject.toolCardEnvelope(): JSONObject {
        val data = obj("data")
        if (data != null) return data.obj("results") ?: data
        return obj("results") ?: this
    }

    private fun canonicalList(raw: JSONObject, canonicalKey: String, vararg sourceKeys: String): JSONObject {
        firstObjectList(raw, *sourceKeys)?.let { return JSONObject(raw.toString()).put(canonicalKey, it) }
        return raw
    }

    private fun firstObjectList(raw: JSONObject, vararg keys: String): JSONArray? {
        keys.forEach { key ->
            raw.optJSONArray(key)?.takeIf { it.length() > 0 }?.let { return it }
        }
        return null
    }

    private fun JSONObject.firstImageUrl(): String? {
        return imageUrls().firstOrNull()
    }

    private fun JSONObject.thumbnailImageUrl(): String? {
        listOf("thumbnail", "thumbnailUrl", "thumbnail_url", "image", "imageUrl", "image_url", "photo").forEach { key ->
            str(key)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        listOf("images", "imageUrls", "image_urls", "photos", "photo_images").forEach { key ->
            val array = optJSONArray(key) ?: return@forEach
            for (index in 0 until array.length()) {
                when (val item = array.opt(index)) {
                    is String -> if (item.isNotBlank()) return item
                    is JSONObject -> {
                        (item.str("thumbnail")
                            ?: item.str("thumbnail_url")
                            ?: item.str("thumbnailUrl")
                            ?: item.str("image")
                            ?: item.str("imageUrl")
                            ?: item.str("image_url")
                            ?: item.str("original_image")
                            ?: item.str("original")
                            ?: item.str("url"))?.let { return it }
                    }
                }
            }
        }
        obj("pagemap")?.let { pagemap ->
            pagemap.objList("cse_thumbnail").firstOrNull()?.str("src")?.let { return it }
            pagemap.objList("cse_image").firstOrNull()?.str("src")?.let { return it }
        }
        return null
    }

    private fun JSONObject.imageUrls(): List<String> {
        val urls = mutableListOf<String>()
        listOf("thumbnail", "thumbnailUrl", "thumbnail_url", "image", "imageUrl", "image_url", "photo", "original", "original_image").forEach { key ->
            str(key)?.takeIf { it.isNotBlank() }?.let(urls::add)
        }
        listOf("images", "imageUrls", "image_urls", "photos", "photo_images").forEach { key ->
            val array = optJSONArray(key) ?: return@forEach
            for (index in 0 until array.length()) {
                when (val item = array.opt(index)) {
                    is String -> if (item.isNotBlank()) urls.add(item)
                    is JSONObject -> {
                        (item.str("thumbnail")
                            ?: item.str("thumbnail_url")
                            ?: item.str("thumbnailUrl")
                            ?: item.str("original_image")
                            ?: item.str("original")
                            ?: item.str("url")
                            ?: item.str("imageUrl")
                            ?: item.str("image_url")
                            ?: item.str("image"))?.let(urls::add)
                    }
                }
            }
        }
        obj("pagemap")?.let { pagemap ->
            pagemap.objList("cse_thumbnail").firstOrNull()?.str("src")?.let(urls::add)
            pagemap.objList("cse_image").firstOrNull()?.str("src")?.let(urls::add)
        }
        obj("rich_snippet")?.let { snippet ->
            snippet.obj("top")?.imageUrls()?.firstOrNull()?.let(urls::add)
        }
        return urls.distinct()
    }

    private fun JSONObject.galleryImageUrls(): List<String> {
        val urls = mutableListOf<String>()
        listOf("imageUrls", "image_urls", "images", "photos", "photo_images", "gallery").forEach { key ->
            val array = optJSONArray(key) ?: return@forEach
            for (index in 0 until array.length()) {
                when (val item = array.opt(index)) {
                    is String -> if (item.isNotBlank()) urls.add(item)
                    is JSONObject -> {
                        (item.str("thumbnail")
                            ?: item.str("thumbnail_url")
                            ?: item.str("thumbnailUrl")
                            ?: item.str("original_image")
                            ?: item.str("original")
                            ?: item.str("url")
                            ?: item.str("imageUrl")
                            ?: item.str("image_url")
                            ?: item.str("image"))?.let(urls::add)
                    }
                }
            }
        }
        if (urls.isEmpty()) {
            firstImageUrl()?.let(urls::add)
        }
        return urls.distinct()
    }

    private fun JSONObject.hasAny(vararg keys: String): Boolean = keys.any { has(it) && !isNull(it) }

    private fun numberFrom(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        is String -> Regex("-?\\d+(\\.\\d+)?").find(value)?.value?.toDoubleOrNull()
        else -> null
    }

    private fun formatPrice(value: Double, currency: String?): String {
        val symbol = if (currency == null || currency == "USD") "$" else "$currency "
        return if (value % 1.0 == 0.0) "$symbol${value.toInt()}" else String.format("$symbol%.2f", value)
    }

    private fun formatFinancialValue(value: String): String {
        val n = value.toDoubleOrNull() ?: return value
        val abs = kotlin.math.abs(n)
        return when {
            abs >= 1e12 -> String.format("%.2fT", n / 1e12)
            abs >= 1e9 -> String.format("%.2fB", n / 1e9)
            abs >= 1e6 -> String.format("%.1fM", n / 1e6)
            else -> value
        }
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
