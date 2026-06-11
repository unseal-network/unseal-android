/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import org.json.JSONArray
import org.json.JSONObject

/**
 * Renders the Composio Search family of tool cards in Material 3, mirroring the iOS
 * `ToolCardsIOS/ComposioSearch` cards (FlightAlert, HotelBooking, HeadlineList,
 * BreakingNews, ImageGrid, ProductList, Finance, EventList, PlaceList, UrlContent).
 *
 * Returns true when [cardType] matched and a card was rendered; false for unknown types
 * so the dispatcher can fall back. [onLinkClick] is invoked alongside opening a URL so the
 * caller can react (e.g. analytics); the actual navigation uses [LocalUriHandler].
 */
@Composable
internal fun composioSearchCard(cardType: String, data: JSONObject, onLinkClick: () -> Unit): Boolean {
    when (cardType) {
        "flightAlert" -> FlightAlertCard(data, onLinkClick)
        "hotelBooking" -> HotelBookingCard(data, onLinkClick)
        "headlineList" -> HeadlineListCard(data, onLinkClick)
        "breakingNews" -> BreakingNewsCard(data, onLinkClick)
        "imageGrid" -> ImageGridCard(data, onLinkClick)
        "productList" -> ProductListCard(data, onLinkClick)
        "finance" -> FinanceCard(data, onLinkClick)
        "eventList" -> EventListCard(data)
        "placeList" -> PlaceListCard(data, onLinkClick)
        "urlContent" -> UrlContentCard(data, onLinkClick)
        else -> return false
    }
    return true
}

private const val MAX_ITEMS = 6
private const val MAX_IMAGES = 12

// MARK: - Open-link helper

@Composable
private fun openLinkAction(url: String?, onLinkClick: () -> Unit): (() -> Unit)? {
    if (url.isNullOrBlank()) return null
    val uriHandler = LocalUriHandler.current
    return {
        onLinkClick()
        runCatching { uriHandler.openUri(url) }
    }
}

private fun Modifier.clickableIfLink(action: (() -> Unit)?): Modifier =
    if (action != null) this.clickable(onClick = action) else this

@Composable
private fun MetaText(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// MARK: - Flight (flightAlert)

@Composable
private fun FlightAlertCard(data: JSONObject, onLinkClick: () -> Unit) {
    val flights = data.cardObjects("flights").take(MAX_ITEMS)
    ToolCardSurface {
        ToolCardHeader(title = "Flights", count = data.cardObjects("flights").size)
        if (flights.isEmpty()) {
            MetaText("No flights found")
        } else {
            DividedList(flights) { FlightRow(it) }
            data.cardString("searchUrl")?.let { searchUrl ->
                val action = openLinkAction(searchUrl, onLinkClick)
                Text(
                    text = "View on Google Flights",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun FlightRow(flight: JSONObject) {
    val airline = flight.cardString("airline") ?: "Flight"
    val flightNumber = flight.cardString("flightNumber") ?: "—"
    val depCode = flight.cardString("departureCode", "departureAirport") ?: "—"
    val arrCode = flight.cardString("arrivalCode", "arrivalAirport") ?: "—"
    val depTime = formatTime(flight.cardString("departureTime"))
    val arrTime = formatTime(flight.cardString("arrivalTime"))
    val duration = flight.cardString("duration")
    val stops = flight.cardInt("stops")
    val travelClass = flight.cardString("travelClass")
    val price = flight.cardString("priceFormatted")

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = airline,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = flightNumber,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            price?.let {
                Text(it, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(depCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(depTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                duration?.let { MetaText(it) }
                Text(
                    text = stopsLabel(stops),
                    style = MaterialTheme.typography.labelSmall,
                    color = if ((stops ?: 0) == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(arrCode, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(arrTime, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        travelClass?.let { CardChip(it) }
    }
}

private fun formatTime(raw: String?): String {
    if (raw.isNullOrEmpty()) return "--:--"
    val sep = when {
        raw.contains("T") -> "T"
        raw.contains(" ") -> " "
        else -> null
    }
    return if (sep != null) raw.substringAfter(sep).take(5) else raw.take(5)
}

private fun stopsLabel(stops: Int?): String = when {
    stops == null -> ""
    stops == 0 -> "Direct"
    stops == 1 -> "1 stop"
    else -> "$stops stops"
}

// MARK: - Hotels (hotelBooking)

@Composable
private fun HotelBookingCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("hotels")
    if (all.isEmpty()) return
    val hotels = all.take(MAX_ITEMS)
    ToolCardSurface {
        ToolCardHeader(title = data.cardString("destination") ?: "Hotels", count = all.size)
        DividedList(hotels) { HotelRow(it, onLinkClick) }
    }
}

@Composable
private fun HotelRow(hotel: JSONObject, onLinkClick: () -> Unit) {
    val name = hotel.cardString("name") ?: "Unknown Hotel"
    val address = hotel.cardString("address")
    val price = hotel.cardString("price")
    val totalPrice = hotel.cardString("totalPrice")
    val rating = hotel.cardString("rating")
    val reviewCount = hotel.cardInt("reviewCount")
    val imageUrl = hotel.cardString("imageUrl")
    val amenities = hotel.cardStrings("amenities")
    val url = hotel.cardString("url")
    val action = openLinkAction(url, onLinkClick)

    Row(
        modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardRemoteImage(url = imageUrl, modifier = Modifier.size(48.dp), corner = 8)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            address?.let { MetaText(it) }
            if (rating != null || reviewCount != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rating?.let {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(12.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    reviewCount?.let { MetaText("$it reviews") }
                }
            }
            if (amenities.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    amenities.take(3).forEach { CardChip(it) }
                    if (amenities.size > 3) MetaText("+${amenities.size - 3}")
                }
            }
        }
        if (price != null) {
            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                totalPrice?.let { MetaText("total $it") }
            }
        }
    }
}

// MARK: - Headlines (headlineList)

@Composable
private fun HeadlineListCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("headlines", "items")
    if (all.isEmpty()) return
    val headlines = all.take(MAX_ITEMS)
    ToolCardSurface {
        ToolCardHeader(title = "Headlines", count = all.size)
        DividedList(headlines) { HeadlineRow(it, onLinkClick) }
    }
}

@Composable
private fun HeadlineRow(item: JSONObject, onLinkClick: () -> Unit) {
    val title = item.cardString("title") ?: "Untitled"
    val snippet = item.cardString("snippet")
    val source = item.cardString("source", "label")
    val publishedAt = item.cardString("publishedAt", "meta")
    val url = item.cardString("url")
    val imageUrl = item.cardString("imageUrl")
    val action = openLinkAction(url, onLinkClick)

    Row(
        modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            snippet?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                source?.let { MetaText(it, MaterialTheme.colorScheme.primary) }
                publishedAt?.let { MetaText(it) }
                url?.let { MetaText(domainOf(it)) }
            }
        }
        if (!imageUrl.isNullOrBlank()) {
            CardRemoteImage(url = imageUrl, modifier = Modifier.size(width = 56.dp, height = 42.dp))
        }
    }
}

// MARK: - Breaking news (breakingNews)

@Composable
private fun BreakingNewsCard(data: JSONObject, onLinkClick: () -> Unit) {
    val headline = data.cardString("headline") ?: return
    val source = data.cardString("source")
    val publishedAt = data.cardString("publishedAt")
    val summary = data.cardString("summary")
    val imageUrl = data.cardString("imageUrl")
    val category = data.cardString("category")
    val url = data.cardString("url")
    val action = openLinkAction(url, onLinkClick)

    ToolCardSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Breaking News",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            category?.let { CardChip(it.uppercase(), color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) }
        }
        if (!imageUrl.isNullOrBlank()) {
            CardRemoteImage(url = imageUrl, modifier = Modifier.fillMaxWidth().height(160.dp), corner = 8)
        }
        Text(
            text = headline,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth().clickableIfLink(action),
        )
        val meta = listOfNotNull(source, publishedAt).joinToString(" · ")
        if (meta.isNotEmpty()) MetaText(meta)
        summary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}

// MARK: - Images (imageGrid)

@Composable
private fun ImageGridCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("images")
    ToolCardSurface {
        ToolCardHeader(title = "Images", count = all.size)
        if (all.isEmpty()) {
            MetaText("No images found")
            return@ToolCardSurface
        }
        val images = all.take(MAX_IMAGES)
        images.chunked(3).forEach { rowImages ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowImages.forEach { image ->
                    val thumb = image.cardString("thumbnail", "original")
                    val link = image.cardString("original", "url")
                    val action = openLinkAction(link, onLinkClick)
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickableIfLink(action)) {
                        CardRemoteImage(url = thumb, modifier = Modifier.fillMaxWidth().aspectRatio(1f), corner = 10)
                    }
                }
                repeat(3 - rowImages.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

// MARK: - Products (productList)

@Composable
private fun ProductListCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("products")
    ToolCardSurface {
        ToolCardHeader(title = "Shopping", count = all.size)
        if (all.isEmpty()) {
            MetaText("No products found")
            return@ToolCardSurface
        }
        DividedList(all.take(MAX_ITEMS)) { ProductRow(it, onLinkClick) }
    }
}

@Composable
private fun ProductRow(product: JSONObject, onLinkClick: () -> Unit) {
    val title = product.cardString("title") ?: ""
    val thumb = product.cardString("thumbnail")
    val price = product.cardString("price")
    val source = product.cardString("source")
    val rating = product.cardString("rating")
    val reviews = product.cardInt("reviews")
    val url = product.cardString("url")
    val action = openLinkAction(url, onLinkClick)

    Row(
        modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CardRemoteImage(url = thumb, modifier = Modifier.size(50.dp), corner = 8)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                source?.let { MetaText(it) }
                rating?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(11.dp))
                        Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (reviews != null && reviews > 0) MetaText("($reviews)")
                    }
                }
            }
        }
        price?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// MARK: - Finance (finance)

@Composable
private fun FinanceCard(data: JSONObject, onLinkClick: () -> Unit) {
    val quote = data.optJSONObject("quote")
    val markets = data.cardObjects("markets")
    val keyEvents = data.cardObjects("keyEvents")
    val news = data.cardObjects("news")
    val stats = data.cardStrings("stats")

    if (quote == null && markets.isEmpty() && keyEvents.isEmpty() && news.isEmpty() && stats.isEmpty()) return

    ToolCardSurface {
        ToolCardHeader(title = "Finance")
        quote?.let { QuoteHero(it) }
        if (markets.isNotEmpty()) {
            FinanceSectionHeader("Markets")
            DividedList(markets.take(MAX_ITEMS)) { MarketRow(it) }
        }
        if (news.isNotEmpty()) {
            FinanceSectionHeader("News")
            DividedList(news.take(MAX_ITEMS)) { NewsRow(it, onLinkClick) }
        }
        if (keyEvents.isNotEmpty()) {
            FinanceSectionHeader("Key Events")
            DividedList(keyEvents.take(MAX_ITEMS)) { KeyEventRow(it) }
        }
        if (stats.isNotEmpty()) {
            FinanceSectionHeader("Stats")
            stats.take(MAX_ITEMS).forEach { StatRow(it) }
        }
    }
}

@Composable
private fun FinanceSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun QuoteHero(quote: JSONObject) {
    val name = quote.cardString("name") ?: "—"
    val price = quote.cardString("price") ?: "—"
    val pct = quote.cardString("changePercent")
    val up = quote.cardBool("up") ?: (pct?.toDoubleOrNull()?.let { it >= 0 } ?: true)
    val subtitle = listOfNotNull(quote.cardString("ticker"), quote.cardString("exchange")).joinToString(" · ")

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) MetaText(subtitle)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            pct?.let { ChangeBadge(it, up) }
        }
    }
}

@Composable
private fun ChangeBadge(percent: String, up: Boolean) {
    val pct = percent.toDoubleOrNull()
    val label = if (pct != null) "%.2f%%".format(kotlin.math.abs(pct)) else percent
    val color = if (up) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Text(
        text = (if (up) "▲ " else "▼ ") + label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
}

@Composable
private fun MarketRow(market: JSONObject) {
    val name = market.cardString("name") ?: "—"
    val price = market.cardString("price")
    val pct = market.cardString("changePercent")
    val up = market.cardBool("up") ?: (pct?.toDoubleOrNull()?.let { it >= 0 } ?: true)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        price?.let { Text(it, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        pct?.let { ChangeBadge(it, up) }
    }
}

@Composable
private fun NewsRow(news: JSONObject, onLinkClick: () -> Unit) {
    val title = news.cardString("title") ?: ""
    val source = news.cardString("source")
    val date = news.cardString("date")
    val thumb = news.cardString("thumbnail")
    val url = news.cardString("url")
    val action = openLinkAction(url, onLinkClick)
    val meta = listOfNotNull(source, date).joinToString(" · ")

    Row(modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!thumb.isNullOrBlank()) {
            CardRemoteImage(url = thumb, modifier = Modifier.size(44.dp), corner = 7)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) MetaText(meta)
        }
    }
}

@Composable
private fun KeyEventRow(event: JSONObject) {
    val title = event.cardString("title") ?: ""
    val meta = listOfNotNull(event.cardString("date"), event.cardString("source")).joinToString(" · ")
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) MetaText(meta)
        }
    }
}

@Composable
private fun StatRow(text: String) {
    val parts = text.split(":", limit = 2).map { it.trim() }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (parts.size == 2) {
            MetaText(parts[0])
            Text(parts[1], style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        } else {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// MARK: - Events (eventList)

@Composable
private fun EventListCard(data: JSONObject) {
    val all = data.cardObjects("events")
    ToolCardSurface {
        ToolCardHeader(title = "Events", count = all.size)
        if (all.isEmpty()) {
            MetaText("No events found")
            return@ToolCardSurface
        }
        DividedList(all.take(MAX_ITEMS)) { EventRow(it) }
    }
}

@Composable
private fun EventRow(event: JSONObject) {
    val title = event.cardString("title") ?: ""
    val whenText = event.cardString("when")
    val location = event.cardString("venue", "address")
    val thumb = event.cardString("thumbnail")

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CardRemoteImage(url = thumb, modifier = Modifier.size(50.dp), corner = 8)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            whenText?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    MetaText(it, MaterialTheme.colorScheme.primary)
                }
            }
            location?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
                    MetaText(it)
                }
            }
        }
    }
}

// MARK: - Places (placeList)

@Composable
private fun PlaceListCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("places")
    ToolCardSurface {
        ToolCardHeader(title = "Places", count = all.size)
        if (all.isEmpty()) {
            MetaText("No places found")
            return@ToolCardSurface
        }
        DividedList(all.take(MAX_ITEMS)) { PlaceRow(it, onLinkClick) }
    }
}

@Composable
private fun PlaceRow(place: JSONObject, onLinkClick: () -> Unit) {
    val name = place.cardString("name") ?: ""
    val type = place.cardString("type")
    val address = place.cardString("address")
    val price = place.cardString("price")
    val thumb = place.cardString("thumbnail")
    val rating = place.cardString("rating")
    val reviews = place.cardInt("reviews")
    val openState = place.cardString("openState")
    val url = place.cardString("url")
    val action = openLinkAction(url, onLinkClick)

    Column(
        modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!thumb.isNullOrBlank()) {
            CardRemoteImage(url = thumb, modifier = Modifier.fillMaxWidth().height(132.dp), corner = 12)
        }
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val tail = listOfNotNull(reviews?.let { "$it reviews" }, price, type).joinToString("  ·  ")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rating?.let {
                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(14.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            if (tail.isNotEmpty()) MetaText(tail)
        }
        openState?.let {
            val closed = it.lowercase().let { s -> s.contains("closed") || s.contains("temporarily") || s.contains("permanently") }
            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (closed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        address?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// MARK: - URL content (urlContent)

@Composable
private fun UrlContentCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("articles")
    ToolCardSurface {
        ToolCardHeader(title = "Web Content", count = all.size)
        if (all.isEmpty()) {
            MetaText("No content fetched")
            return@ToolCardSurface
        }
        DividedList(all.take(MAX_ITEMS)) { UrlRow(it, onLinkClick) }
    }
}

@Composable
private fun UrlRow(article: JSONObject, onLinkClick: () -> Unit) {
    val urlString = article.cardString("url") ?: ""
    val title = article.cardString("title")
    val action = openLinkAction(urlString, onLinkClick)

    Row(
        modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title ?: urlString, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (title != null && urlString.isNotEmpty()) MetaText(domainOf(urlString))
        }
    }
}

// MARK: - Previews

private fun previewArray(vararg objects: JSONObject): JSONArray = JSONArray().apply { objects.forEach { put(it) } }

private fun jsonOf(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
    pairs.forEach { (k, v) -> if (v != null) put(k, v) }
}

@PreviewsDayNight
@Composable
internal fun ComposioFlightCardPreview() = ElementPreview {
    composioSearchCard(
        cardType = "flightAlert",
        data = jsonOf(
            "flights" to previewArray(
                jsonOf("airline" to "Spring Airlines", "flightNumber" to "9C 8815", "departureCode" to "CAN", "arrivalCode" to "BKK", "departureTime" to "2026-04-29 06:00", "arrivalTime" to "2026-04-29 08:45", "duration" to "2h 45m", "stops" to 0, "travelClass" to "Economy", "priceFormatted" to "$147"),
                jsonOf("airline" to "Hainan Airlines", "flightNumber" to "HU 7012", "departureCode" to "CAN", "arrivalCode" to "HAK", "departureTime" to "2026-04-29 09:10", "arrivalTime" to "2026-04-29 15:25", "duration" to "6h 15m", "stops" to 1, "travelClass" to "Business", "priceFormatted" to "$167"),
            ),
            "searchUrl" to "https://www.google.com/travel/flights",
        ),
        onLinkClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun ComposioHeadlineCardPreview() = ElementPreview {
    composioSearchCard(
        cardType = "headlineList",
        data = jsonOf(
            "headlines" to previewArray(
                jsonOf("title" to "OpenAI announces GPT-5 with major reasoning improvements", "snippet" to "Significant gains in math, coding, and reasoning across benchmarks.", "source" to "TechCrunch", "url" to "https://techcrunch.com/2026/05/gpt5", "publishedAt" to "2h ago"),
                jsonOf("title" to "EU passes comprehensive AI regulation framework", "source" to "Reuters", "url" to "https://reuters.com/eu-ai", "publishedAt" to "5h ago"),
            ),
        ),
        onLinkClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun ComposioPlaceCardPreview() = ElementPreview {
    composioSearchCard(
        cardType = "placeList",
        data = jsonOf(
            "places" to previewArray(
                jsonOf("name" to "Abanico Coffee Roasters", "type" to "Coffee shop", "rating" to "4.6", "reviews" to 273, "address" to "2121 Mission St, San Francisco", "price" to "\$10–20", "openState" to "Closed · Opens 7:30 AM", "url" to "https://maps.google.com"),
                jsonOf("name" to "Grand Coffee", "type" to "Cafe", "rating" to "4.5", "reviews" to 157, "address" to "2544 Mission St, San Francisco", "price" to "\$1–10", "openState" to "Open · Closes 6 PM", "url" to "https://maps.google.com"),
            ),
        ),
        onLinkClick = {},
    )
}
