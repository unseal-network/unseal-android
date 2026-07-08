/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import android.text.format.DateFormat as AndroidDateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeviceThermostat
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.components.SelectedStatePill
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
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
        "weather" -> WeatherCard(data)
        "eventList" -> EventListCard(data, onLinkClick)
        "placeList" -> PlaceListCard(data, onLinkClick)
        "urlContent" -> UrlContentCard(data, onLinkClick)
        else -> return false
    }
    return true
}

private const val MAX_ITEMS = MAX_CARD_ITEMS
private const val MAX_IMAGES = 12
private val FinanceUpColor = Color(0xFF219E66)
private val FinanceDownColor = Color(0xFFE64235)
private val ShoppingPriceColor = Color(0xFF219966)
private val ShoppingStarColor = Color(0xFFFF9E2C)
private val NewsSourceColor = Color(0xFF31D76B)
private val PlaceAccent = Color(0xFF339973)
private val PlaceOpenColor = Color(0xFF219E66)
private val PlaceClosedColor = Color(0xFFE64235)

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

private fun Modifier.clickableIfLink(
    action: (() -> Unit)?,
    shape: RoundedCornerShape = RoundedCornerShape(8.dp),
): Modifier =
    if (action != null) this.clip(shape).clickable(onClick = action) else this

private fun JSONObject.cardDouble(vararg keys: String): Double? {
    keys.forEach { key ->
        when (val value = opt(key)) {
            is Number -> return value.toDouble()
            is String -> value.cardDoubleValue()?.let { return it }
        }
    }
    return null
}

private fun String.cardDoubleValue(): Double? = Regex("-?\\d+(\\.\\d+)?").find(this)?.value?.toDoubleOrNull()

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
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_flights), count = data.cardObjects("flights").size)
        if (flights.isEmpty()) {
            MetaText(stringResource(R.string.screen_room_timeline_tool_card_no_flights_found))
        } else {
            DividedList(flights) { FlightRow(it) }
            data.cardString("searchUrl")?.let { searchUrl ->
                val action = openLinkAction(searchUrl, onLinkClick)
                Text(
                    text = stringResource(R.string.screen_room_timeline_tool_card_view_google_flights),
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
    val airline = flight.cardString("airline") ?: stringResource(R.string.screen_room_timeline_tool_card_flight)
    val airlineLogo = flight.cardString("airlineLogo")
    val flightNumber = flight.cardString("flightNumber") ?: "—"
    val depCode = flight.cardString("departureCode", "departureAirport") ?: "—"
    val arrCode = flight.cardString("arrivalCode", "arrivalAirport") ?: "—"
    val depTime = formatTime(flight.cardString("departureTime"))
    val arrTime = formatTime(flight.cardString("arrivalTime"))
    val duration = flight.cardString("duration")
    val stops = flight.cardInt("stops")
    val travelClass = flight.cardString("travelClass", "cabin", "class")
    val airplane = flight.cardString("airplane")
    val price = flight.cardString("priceFormatted", "price")

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AirlineLogo(airlineLogo)
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
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                duration?.let { MetaText(it) }
                FlightRouteLine()
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
        if (travelClass != null || airplane != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                travelClass?.let { CardChip(it) }
                airplane?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = Icons.Outlined.Flight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AirlineLogo(url: String?) {
    CardRemoteImage(
        url = url,
        modifier = Modifier.size(22.dp),
        corner = 5,
    ) {
        Icon(
            imageVector = Icons.Outlined.Flight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.Center).size(14.dp),
        )
    }
}

@Composable
private fun FlightRouteLine() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp),
    ) {
        val centerY = size.height / 2f
        val startX = 4.dp.toPx()
        val endX = size.width - 4.dp.toPx()
        drawLine(
            color = color.copy(alpha = 0.28f),
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = color.copy(alpha = 0.72f), radius = 2.3.dp.toPx(), center = Offset(startX, centerY))
        drawCircle(color = color.copy(alpha = 0.72f), radius = 2.3.dp.toPx(), center = Offset(endX, centerY))
        drawCircle(color = color, radius = 3.dp.toPx(), center = Offset((startX + endX) / 2f, centerY))
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

@Composable
private fun stopsLabel(stops: Int?): String = when {
    stops == null -> ""
    stops == 0 -> stringResource(R.string.screen_room_timeline_tool_card_direct)
    else -> pluralStringResource(R.plurals.screen_room_timeline_tool_card_stop_count, stops, stops)
}

// MARK: - Hotels (hotelBooking)

@Composable
private fun HotelBookingCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("hotels")
    if (all.isEmpty()) return
    val hotels = all.take(MAX_ITEMS)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        DividedList(hotels) { HotelRow(it, onLinkClick) }
    }
}

@Composable
private fun HotelRow(hotel: JSONObject, onLinkClick: () -> Unit) {
    val name = hotel.cardString("name") ?: stringResource(R.string.screen_room_timeline_tool_card_unknown_hotel)
    val address = hotel.cardString("area", "address")
    val price = hotel.cardString("price")
    val totalPrice = hotel.cardString("total", "totalPrice")
    val rating = hotel.cardDouble("rating")
    val reviewCount = hotel.cardInt("reviews", "reviewCount")
    val stars = hotel.cardInt("stars")
    val thumbnailUrls = hotel.cardImageUrls("thumbnail", "imageUrl", "image")
    val galleryImageUrls = (
        hotel.cardImageUrls("imageUrls", "images", "photos") +
            hotel.cardImageUrls("gallery", "photo_images")
        )
        .normalizedHotelImageUrls()
    val primaryImageUrl = thumbnailUrls.normalizedHotelImageUrls().firstOrNull() ?: galleryImageUrls.firstOrNull()
    val amenities = hotel.cardStrings("amenities")
    val url = hotel.cardString("url")
    val mapsUrl = hotel.cardString("mapUrl", "mapsUrl")
    val bookAction = openLinkAction(url, onLinkClick)
    val mapAction = openLinkAction(mapsUrl, onLinkClick)
    var showGallery by remember(galleryImageUrls.joinToString("|"), primaryImageUrl) { mutableStateOf(false) }
    val galleryUrls = galleryImageUrls.ifEmpty { listOfNotNull(primaryImageUrl) }
    val hasHotelDetails = listOf(address, price, totalPrice, url, mapsUrl).any { !it.isNullOrBlank() } ||
        rating != null ||
        reviewCount != null ||
        stars != null ||
        amenities.isNotEmpty() ||
        galleryUrls.isNotEmpty()

    if (!hasHotelDetails) {
        SparseHotelRow(name = name)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (!primaryImageUrl.isNullOrBlank()) {
                Box(
                    modifier = if (galleryUrls.isNotEmpty()) {
                        Modifier.clickable { showGallery = true }
                    } else {
                        Modifier
                    },
                ) {
                    CardRemoteImage(
                        url = primaryImageUrl,
                        modifier = Modifier
                            .size(50.dp),
                        corner = 8,
                    ) {
                        HotelImagePlaceholder()
                    }
                    if (galleryImageUrls.size > 1) {
                        Text(
                            text = galleryImageUrls.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (!address.isNullOrBlank()) {
                    Text(
                        text = address,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                stars?.let { HotelStarRating(count = it) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    rating?.let { HotelRatingBadge(rating = it) }
                    reviewCount?.let { MetaText(stringResource(R.string.screen_room_timeline_tool_card_review_count_abbreviated, it.compactCount())) }
                }
            }

            if (price != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.width(58.dp),
                ) {
                    Text(
                        text = price,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = FinanceUpColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    totalPrice?.let {
                        Text(
                            text = stringResource(R.string.screen_room_timeline_tool_card_total_price, it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (amenities.isNotEmpty()) {
            HotelAmenityRow(amenities = amenities)
        }

        if (galleryUrls.size > 1) {
            HotelImageStrip(
                urls = galleryUrls,
                onClick = { showGallery = true },
            )
        }

        if (mapAction != null || bookAction != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                mapAction?.let {
                    HotelActionChip(text = stringResource(R.string.screen_room_timeline_tool_card_map), color = Color(0xFF2F80ED), onClick = it)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                bookAction?.let {
                    HotelActionChip(text = stringResource(R.string.screen_room_timeline_tool_card_book), color = FinanceUpColor, onClick = it)
                }
            }
        }
    }
    if (showGallery) {
        HotelImageDialog(
            urls = galleryUrls,
            title = name,
            onDismiss = { showGallery = false },
        )
    }
}

private fun List<String>.normalizedHotelImageUrls(): List<String> =
    distinctBy { it.substringBefore("?") }
        .sortedWith(
            compareBy<String> {
                when {
                    "gps-cs-s" in it -> 0
                    "googleusercontent.com/proxy/" in it || "/proxy/" in it -> 4
                    else -> 2
                }
            }
        )
        .let { urls ->
            urls.filterNot { "googleusercontent.com/proxy/" in it || "/proxy/" in it }
                .takeIf { it.isNotEmpty() }
                ?: urls
        }

@Composable
private fun SparseHotelRow(name: String) {
    val hotelsFallback = stringResource(R.string.screen_room_timeline_tool_card_hotels)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.24f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            FinanceUpColor.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.02f),
                        ),
                        start = Offset.Zero,
                        end = Offset(520f, 180f),
                    ),
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF2F80ED).copy(alpha = 0.28f),
                                FinanceUpColor.copy(alpha = 0.28f),
                            ),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "H",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = name.ifBlank { hotelsFallback },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.screen_room_timeline_tool_card_hotel_results_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    HotelAmenityChip(text = stringResource(R.string.screen_room_timeline_tool_card_hotel_details_unavailable), index = 1)
                    HotelAmenityChip(text = stringResource(R.string.screen_room_timeline_tool_card_hotel_try_dates_or_city), index = 2)
                }
            }
        }
    }
}

@Composable
private fun HotelImageStrip(
    urls: List<String>,
    onClick: () -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(urls.take(8)) { url ->
            CardRemoteImage(
                url = url,
                modifier = Modifier
                    .size(width = 92.dp, height = 66.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onClick),
                corner = 8,
            ) {
                HotelImagePlaceholder()
            }
        }
    }
}

@Composable
private fun HotelImagePlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HotelImageDialog(
    urls: List<String>,
    title: String,
    onDismiss: () -> Unit,
) {
    if (urls.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { urls.size })
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp),
            shape = RoundedCornerShape(18.dp),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    CardRemoteImage(
                        url = urls.getOrNull(page),
                        modifier = Modifier.fillMaxSize(),
                        corner = 18,
                    ) {
                        HotelImagePlaceholder()
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent),
                            ),
                        )
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (urls.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${urls.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.72f),
                        )
                    }
                }
                if (urls.size > 1) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .background(Color.Black.copy(alpha = 0.34f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        urls.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == pagerState.currentPage) 7.dp else 5.dp)
                                    .background(
                                        Color.White.copy(alpha = if (index == pagerState.currentPage) 0.95f else 0.42f),
                                        RoundedCornerShape(50),
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HotelStarRating(count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(5) { index ->
            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = if (index < count) Color(0xFFE0A800) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f),
                modifier = Modifier.size(8.dp),
            )
        }
    }
}

@Composable
private fun HotelActionChip(text: String, color: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun HotelRatingBadge(rating: Double) {
    val badgeColor = when {
        rating >= 4.5 -> FinanceUpColor
        rating >= 4.0 -> Color(0xFF33A853)
        rating >= 3.5 -> Color(0xFFE0A800)
        else -> Color(0xFFF2994A)
    }
    Text(
        text = String.format("%.1f", rating),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .background(badgeColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

@Composable
private fun HotelAmenityRow(amenities: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val maxAmenities = 5
        amenities.take(maxAmenities).forEachIndexed { index, amenity ->
            HotelAmenityChip(text = amenity, index = index)
        }
        if (amenities.size > maxAmenities) {
            Text(
                text = "+${amenities.size - maxAmenities}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun HotelAmenityChip(text: String, index: Int) {
    val palette = listOf(
        Color(0xFF2F80ED) to Color(0xFF2567BF),
        Color(0xFF33A853) to Color(0xFF25833F),
        Color(0xFFF2994A) to Color(0xFFB86618),
        Color(0xFFD64D78) to Color(0xFFA83258),
        Color(0xFF8E6AD8) to Color(0xFF6A4CB3),
    )
    val (base, content) = palette[index % palette.size]
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .background(base.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

// MARK: - Headlines (headlineList)

@Composable
private fun HeadlineListCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("headlines", "items")
    if (all.isEmpty()) return
    val headlines = all.take(MAX_ITEMS)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        DividedList(headlines) { HeadlineRow(it, onLinkClick) }
    }
}

@Composable
private fun HeadlineRow(item: JSONObject, onLinkClick: () -> Unit) {
    val title = item.cardString("title") ?: stringResource(R.string.screen_room_timeline_tool_card_untitled)
    val rawSnippet = item.cardString("snippet")?.trim()
    val source = item.cardString("source", "label")
    val snippetAsDate = rawSnippet?.takeIf { it.looksLikeHeadlineDate() }?.cleanHeadlineMetaDate()
    val snippet = rawSnippet?.takeUnless { it.looksLikeHeadlineDate() }?.cleanHeadlineMetaDate()
    val publishedAt = (
        item.cardString("publishedAt", "published_at", "publishedDate", "published_date", "date", "meta")
            ?: snippetAsDate
        )?.cleanHeadlineMetaDate()
    val url = item.cardString("url")
    val domain = url?.let(::domainOf)
    val imageUrl = item.cardImageUrls("imageUrl", "thumbnail", "image", "original").firstOrNull()
    val imageFallback = (source ?: domain ?: title).trim().take(1).uppercase(Locale.getDefault()).ifBlank { "W" }
    val action = openLinkAction(url, onLinkClick)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableIfLink(action)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            snippet?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HeadlineMetaRow(
                modifier = Modifier.fillMaxWidth(),
                source = source,
                publishedAt = publishedAt,
                domain = domain,
            )
        }
        HeadlineTrailingPreview(
            imageUrl = imageUrl,
            imageFallback = imageFallback,
            hasLink = url != null,
        )
    }
}

@Composable
private fun HeadlineTrailingPreview(
    imageUrl: String?,
    imageFallback: String,
    hasLink: Boolean,
) {
    Box(
        modifier = Modifier
            .width(68.dp)
            .height(50.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            CardRemoteImage(
                url = imageUrl,
                modifier = Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                corner = 8,
            ) {
                HeadlineImagePlaceholder(label = imageFallback)
            }
        } else if (hasLink) {
            Box(
                modifier = Modifier
                    .size(width = 44.dp, height = 36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun HeadlineImagePlaceholder(label: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.76f),
            maxLines = 1,
        )
    }
}

@Composable
private fun HeadlineMetaRow(
    source: String?,
    publishedAt: String?,
    domain: String?,
    modifier: Modifier = Modifier,
) {
    val metaItems = listOfNotNull(
        publishedAt?.takeIf { it.isNotBlank() },
        domain?.takeIf { it.isNotBlank() },
    )
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        source?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = NewsSourceColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metaItems.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                metaItems.forEachIndexed { index, value ->
                    if (index > 0) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f),
                        )
                    }
                    val metaModifier = if (index == 0) {
                        Modifier
                    } else {
                        Modifier.weight(1f, fill = false)
                    }
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (index == 0) 0.82f else 0.58f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = metaModifier,
                    )
                }
            }
        }
    }
}

private fun String.cleanHeadlineMetaDate(): String {
    return compactToolCardMetaDate()
}

private fun String.looksLikeHeadlineDate(): Boolean {
    val value = trim()
    return Regex("^\\d{4}-\\d{2}-\\d{2}(?:[T\\s].*)?$").matches(value) ||
        Regex("^[A-Z][a-z]{2}\\s+\\d{1,2},\\s+\\d{4}").containsMatchIn(value)
}

// MARK: - Breaking news (breakingNews)

@Composable
private fun BreakingNewsCard(data: JSONObject, onLinkClick: () -> Unit) {
    val sourceItem = data.cardObjects("headlines", "items").firstOrNull() ?: data
    val headline = data.cardString("headline", "title") ?: sourceItem.cardString("headline", "title") ?: return
    val source = data.cardString("source") ?: sourceItem.cardString("source", "label")
    val publishedAt = (
        data.cardString("publishedAt") ?: sourceItem.cardString("publishedAt", "published_at", "date", "meta")
        )?.cleanHeadlineMetaDate()
    val summary = data.cardString("summary") ?: sourceItem.cardString("summary", "snippet", "description", "text")
    val imageUrl = data.cardImageUrls("imageUrl", "image", "thumbnail").firstOrNull()
        ?: sourceItem.cardImageUrls("imageUrl", "image", "thumbnail").firstOrNull()
    val category = data.cardString("category") ?: sourceItem.cardString("category")
    val url = data.cardString("url") ?: sourceItem.cardString("url", "link")
    val action = openLinkAction(url, onLinkClick)

    ToolCardSurface {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.screen_room_timeline_tool_card_breaking_news),
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
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_images), count = all.size)
        if (all.isEmpty()) {
            MetaText(stringResource(R.string.screen_room_timeline_tool_card_no_images_found))
            return@ToolCardSurface
        }
        val images = all.take(MAX_IMAGES)
        images.chunked(3).forEach { rowImages ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowImages.forEach { image ->
                    val thumb = image.cardImageUrls("thumbnail", "original", "original_image", "url", "imageUrl", "image").firstOrNull()
                    val link = image.cardString("original", "url")
                    val action = openLinkAction(link, onLinkClick)
                    val title = image.cardString("title")
                    val source = image.cardString("source")
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickableIfLink(action)) {
                        CardRemoteImage(
                            url = thumb,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                            corner = 10,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.Bottom,
                            ) {
                                Text(
                                    text = title ?: source ?: stringResource(R.string.screen_room_timeline_tool_card_image),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                source?.takeIf { it != title }?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
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
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        if (all.isEmpty()) return
        DividedList(all.take(MAX_ITEMS)) { ProductRow(it, onLinkClick) }
    }
}

@Composable
private fun ProductRow(product: JSONObject, onLinkClick: () -> Unit) {
    val title = product.cardString("title") ?: ""
    val thumb = product.cardImageUrls("thumbnail", "imageUrl", "image").firstOrNull()
    val price = product.cardString("price")
    val source = product.cardString("source")
    val rating = product.cardString("rating")
    val reviews = product.cardInt("reviews")
    val url = product.cardString("url")
    val action = openLinkAction(url, onLinkClick)
    val imageFallback = (source ?: title).trim().take(1).uppercase(Locale.getDefault()).ifBlank { "P" }

    Row(
        modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CardRemoteImage(url = thumb, modifier = Modifier.size(52.dp), corner = 9) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = imageFallback,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        Box(
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(end = if (price != null) 80.dp else 0.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    source?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    rating?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = ShoppingStarColor, modifier = Modifier.size(11.dp))
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                            )
                            if (reviews != null && reviews > 0) {
                                Text(
                                    text = "(${reviews.compactCount()})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Clip,
                                )
                            }
                        }
                    }
                }
            }
            price?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = ShoppingPriceColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
    }
}

// MARK: - Finance (finance)

@Composable
private fun FinanceCard(data: JSONObject, onLinkClick: () -> Unit) {
    val quote = data.optJSONObject("quote")
    val graph = data.cardObjects("graph")
    val markets = data.cardObjects("markets")
    val keyEvents = data.cardObjects("keyEvents")
    val news = data.cardObjects("news")
    val stats = data.cardStrings("stats")
    val financials = data.cardObjects("financials")
    val newsLabel = stringResource(R.string.screen_room_timeline_tool_card_news)
    val financialsLabel = stringResource(R.string.screen_room_timeline_tool_card_financials)
    val eventsLabel = stringResource(R.string.screen_room_timeline_tool_card_events)
    val statsLabel = stringResource(R.string.screen_room_timeline_tool_card_stats)
    val tabs = buildList {
        if (news.isNotEmpty()) add(FinanceTab("News", newsLabel))
        if (financials.isNotEmpty()) add(FinanceTab("Financials", financialsLabel))
        if (keyEvents.isNotEmpty()) add(FinanceTab("Events", eventsLabel))
        if (stats.isNotEmpty()) add(FinanceTab("Stats", statsLabel))
    }
    var selectedTab by remember(tabs.map { it.key }.joinToString("|")) { mutableStateOf(0) }

    if (quote == null && graph.isEmpty() && markets.isEmpty() && tabs.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        quote?.let { QuoteHero(it) }
        if (graph.isNotEmpty()) {
            PriceChart(graph)
        }
        if (markets.isNotEmpty()) {
            FinanceSectionHeader(stringResource(R.string.screen_room_timeline_tool_card_markets))
            DividedList(markets.take(MAX_ITEMS)) { MarketRow(it) }
        }
        if (tabs.isNotEmpty()) {
            val safeSelectedTab = selectedTab.coerceIn(tabs.indices)
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )
            if (tabs.size > 1) {
                FinanceSegmentedTabs(
                    tabs = tabs.map { it.label },
                    selectedIndex = safeSelectedTab,
                    onSelected = { selectedTab = it },
                )
            } else {
                FinanceSectionHeader(tabs.first().label)
            }
            FinanceTabContentSurface(scrollKey = tabs[safeSelectedTab].key) {
                when (tabs[safeSelectedTab].key) {
                    "News" -> DividedList(news.take(MAX_ITEMS)) { NewsRow(it, onLinkClick) }
                    "Financials" -> FinancialsList(financials)
                    "Events" -> DividedList(keyEvents.take(MAX_ITEMS)) { KeyEventRow(it) }
                    "Stats" -> StatsGrid(stats.take(MAX_ITEMS))
                }
            }
        }
    }
}

private data class FinanceTab(
    val key: String,
    val label: String,
)

@Composable
private fun FinanceTabContentSurface(scrollKey: String, content: ColumnContent) {
    val scrollState = remember(scrollKey) { ScrollState(0) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

@Composable
private fun FinanceSegmentedTabs(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            SelectedStatePill(
                selected = selected,
                onClick = { onSelected(index) },
                modifier = Modifier
                    .weight(1f),
                selectedColor = MaterialTheme.colorScheme.secondaryContainer,
                unselectedColor = Color.Transparent,
                selectedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = androidx.compose.material3.LocalContentColor.current,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun PriceChart(graph: List<JSONObject>) {
    val points = graph.mapNotNull { it.cardDouble("price") }
    if (points.size < 2) return
    val up = points.last() >= points.first()
    val color = if (up) FinanceUpColor else FinanceDownColor
    val min = points.minOrNull() ?: return
    val max = points.maxOrNull() ?: return
    val spread = (max - min).takeIf { it > 0.0 } ?: 1.0

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .padding(horizontal = 4.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.05f)),
    ) {
        val horizontalStep = size.width / (points.lastIndex.coerceAtLeast(1))
        val path = Path()
        val areaPath = Path()
        points.forEachIndexed { index, price ->
            val x = index * horizontalStep
            val y = size.height - (((price - min) / spread).toFloat() * size.height)
            if (index == 0) {
                path.moveTo(x, y)
                areaPath.moveTo(x, size.height)
                areaPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                areaPath.lineTo(x, y)
            }
            if (index == points.lastIndex) {
                areaPath.lineTo(x, size.height)
                areaPath.close()
            }
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                startY = 0f,
                endY = size.height,
            ),
        )
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )
        points.lastOrNull()?.let { last ->
            val x = points.lastIndex * horizontalStep
            val y = size.height - (((last - min) / spread).toFloat() * size.height)
            drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(x, y))
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
    val up = quote.cardBool("up") ?: (pct?.cardDoubleValue()?.let { it >= 0 } ?: true)
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
    val pct = percent.cardDoubleValue()
    val label = if (pct != null) "%.2f%%".format(kotlin.math.abs(pct)) else percent
    val color = if (up) FinanceUpColor else FinanceDownColor
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = if (up) "▲" else "▼",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
    }
}

@Composable
private fun MarketRow(market: JSONObject) {
    val name = market.cardString("name") ?: "—"
    val price = market.cardString("price")
    val pct = market.cardString("changePercent")
    val up = market.cardBool("up") ?: (pct?.cardDoubleValue()?.let { it >= 0 } ?: true)
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
    val date = news.cardString("date", "publishedAt", "published_at", "meta")?.cleanHeadlineMetaDate()
    val url = news.cardString("url")
    val action = openLinkAction(url, onLinkClick)
    val meta = listOfNotNull(source, date).joinToString(" · ")

    Row(modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) MetaText(meta)
        }
    }
}

@Composable
private fun KeyEventRow(event: JSONObject) {
    val title = event.cardString("title") ?: ""
    val meta = listOfNotNull(event.cardString("date")?.cleanHeadlineMetaDate(), event.cardString("source")).joinToString(" · ")
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
    val parts = splitStatLabelValue(text)
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (parts.size == 2) {
            MetaText(parts[0])
            Text(parts[1], style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        } else {
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsGrid(stats: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        stats.forEach { stat -> StatRow(stat) }
    }
}

@Composable
private fun StatChip(text: String, modifier: Modifier = Modifier) {
    val parts = splitStatLabelValue(text)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        if (parts.size == 2) {
            Text(
                text = parts[0],
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = parts[1],
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun splitStatLabelValue(text: String): List<String> {
    val index = text.indexOf(':')
    if (index <= 0) return listOf(text)
    val label = text.take(index).trim()
    val value = text.drop(index + 1).trim()
    val labelLooksLikeText = label.any { it.isLetter() } && label.none { it.isDigit() || it == ',' }
    return if (labelLooksLikeText && value.isNotEmpty()) listOf(label, value) else listOf(text)
}

@Composable
private fun FinancialsList(sections: List<JSONObject>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        sections.forEachIndexed { index, section ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = section.cardString("title") ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                section.cardString("period")?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            section.cardObjects("rows").forEach { row ->
                FinancialRow(row)
            }
        }
    }
}

@Composable
private fun FinancialRow(row: JSONObject) {
    val label = row.cardString("label") ?: ""
    val value = row.cardString("value") ?: ""
    val change = row.cardString("change")
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
        change?.let {
            val down = it.trim().startsWith("-")
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = if (down) FinanceDownColor else FinanceUpColor,
                maxLines = 1,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                modifier = Modifier.width(60.dp),
            )
        }
    }
}

// MARK: - Weather (weather)

@Composable
private fun WeatherCard(data: JSONObject) {
    val current = data.optJSONObject("current")
    val forecast = data.cardObjects("forecast", "items", "daily_forecast")
    val city = data.cardString("city", "location") ?: current?.cardString("city", "location") ?: stringResource(R.string.screen_room_timeline_tool_card_weather)
    val country = data.cardString("country")
    if (current == null && forecast.isEmpty()) return

    var selectedDay by remember(forecast.size) { mutableStateOf(0) }
    if (selectedDay >= forecast.size.coerceAtLeast(1)) selectedDay = 0
    val selectedForecast = forecast.getOrNull(selectedDay)
    val selectedCondition = if (selectedDay > 0) {
        selectedForecast?.cardString("condition", "weather", "description").orEmpty()
    } else {
        current?.cardString("condition", "weather", "description") ?: selectedForecast?.cardString("condition", "weather", "description").orEmpty()
    }
    val condition = selectedCondition
    val themeColor = weatherColor(condition)
    val temp = if (selectedDay > 0) {
        selectedForecast?.cardDouble("high", "max", "max_temp", "temperature")
    } else {
        current?.cardDouble("temperature")
    }
    val feelsLike = if (selectedDay > 0) {
        selectedForecast?.cardDouble("high", "max", "max_temp", "temperature")?.minus(2.0)
    } else {
        current?.cardDouble("feelsLike", "feels_like")
    }
    val humidity = current?.cardDouble("humidity")
    val windSpeed = current?.cardDouble("windSpeed", "wind_speed")
    val statPanelColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
    val statBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f)
    val weatherAccent = MaterialTheme.colorScheme.primary

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WeatherLocationHeader(
            city = city,
            country = country.orEmpty(),
            dateText = weatherDateText(selectedDay, selectedForecast),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WeatherHeroIcon(condition = condition, tint = themeColor)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = temp?.roundedInt()?.toString() ?: "—",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Light,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = "°C",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 5.dp, start = 2.dp),
                    )
                }
                Text(
                    text = formatWeatherCondition(condition),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (selectedDay == 0) {
                        stringResource(R.string.screen_room_timeline_tool_card_weather_current)
                    } else {
                        val forecastDay = selectedForecast?.cardString("day", "weekday", "date").orEmpty()
                        if (forecastDay.isBlank()) {
                            stringResource(R.string.screen_room_timeline_tool_card_weather_forecast)
                        } else {
                            stringResource(R.string.screen_room_timeline_tool_card_weather_forecast_day, forecastDay)
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(statPanelColor, RoundedCornerShape(14.dp))
                .border(0.6.dp, statBorderColor, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeatherStat(label = stringResource(R.string.screen_room_timeline_tool_card_weather_feels), value = feelsLike?.let { "${it.roundedInt()}°" } ?: "—", unit = "", icon = Icons.Outlined.DeviceThermostat, tint = weatherAccent, modifier = Modifier.weight(1f))
            WeatherStat(label = stringResource(R.string.screen_room_timeline_tool_card_weather_wind), value = windSpeed?.let { "${it.roundedInt()}" } ?: "—", unit = if (windSpeed == null) "" else "km/h", icon = Icons.Outlined.Air, tint = weatherAccent, modifier = Modifier.weight(1f))
            WeatherStat(label = stringResource(R.string.screen_room_timeline_tool_card_weather_humid), value = humidity?.let { "${it.roundedInt()}%" } ?: "—", unit = "", icon = Icons.Outlined.WaterDrop, tint = weatherAccent, modifier = Modifier.weight(1f))
        }

        if (forecast.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(11.dp))
                    Text(
                        text = stringResource(R.string.screen_room_timeline_tool_card_weather_seven_day_forecast),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    forecast.forEachIndexed { index, item ->
                        ForecastPill(item = item, selected = selectedDay == index, onClick = { selectedDay = index })
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherLocationHeader(city: String, country: String, dateText: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color(0xFFFF4F7A), modifier = Modifier.size(15.dp))
            Text(
                text = listOf(city, country).filter { it.isNotBlank() }.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = dateText,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun WeatherHeroIcon(condition: String, tint: Color) {
    Box(modifier = Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.10f), RoundedCornerShape(50)),
        )
        Icon(weatherIcon(condition), contentDescription = null, tint = tint, modifier = Modifier.size(44.dp))
    }
}

@Composable
private fun WeatherStat(
    label: String,
    value: String,
    unit: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val displayValue = if (unit.isBlank() || value == "—") value else "$value $unit"
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(15.dp))
        Text(text = label.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(text = displayValue, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ForecastPill(item: JSONObject, selected: Boolean, onClick: () -> Unit) {
    val condition = item.cardString("condition", "weather", "description").orEmpty()
    val color = weatherColor(condition)
    val high = item.cardDouble("high", "max", "max_temp", "temperature")
    val low = item.cardDouble("low", "min", "min_temp")
    val precipitation = item.cardDouble("precipitation", "rain_chance", "precipitation_probability") ?: 0.0
    SelectedStatePill(
        selected = selected,
        onClick = onClick,
        modifier = Modifier
            .width(74.dp),
        selectedColor = color.copy(alpha = 0.16f),
        unselectedColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
        selectedContentColor = MaterialTheme.colorScheme.onSurface,
        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedBorder = BorderStroke(0.8.dp, color.copy(alpha = 0.36f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.cardString("day", "weekday", "date")?.take(3) ?: stringResource(R.string.screen_room_timeline_tool_card_day),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.material3.LocalContentColor.current,
                maxLines = 1,
            )
            Icon(weatherIcon(condition), contentDescription = null, tint = if (selected) color else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(high?.let { "${it.roundedInt()}°" } ?: "—", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                low?.let { Text("${it.roundedInt()}°", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
            }
            if (precipitation > 0) {
                Text("${precipitation.roundedInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.72f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun DetailedForecast(item: JSONObject, themeColor: Color) {
    val condition = item.cardString("condition", "weather", "description").orEmpty()
    val high = item.cardDouble("high", "max", "max_temp", "temperature")
    val low = item.cardDouble("low", "min", "min_temp")
    val precipitation = item.cardDouble("precipitation", "rain_chance", "precipitation_probability") ?: 0.0
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .border(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.cardString("day", "weekday") ?: stringResource(R.string.screen_room_timeline_tool_card_day), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            item.cardString("date")?.let { Text(it, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(weatherIcon(condition), contentDescription = null, tint = themeColor, modifier = Modifier.size(36.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(formatWeatherCondition(condition), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
                    if (precipitation > 0) Text(stringResource(R.string.screen_room_timeline_tool_card_precipitation_chance), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(high?.let { "${it.roundedInt()}°" } ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    low?.let { Text("/ ${it.roundedInt()}°", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp)) }
                }
                if (precipitation > 0) Text(stringResource(R.string.screen_room_timeline_tool_card_rain_chance, precipitation.roundedInt()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
            }
        }
        if (precipitation > 0) {
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))) {
                Box(modifier = Modifier.fillMaxWidth((precipitation / 100.0).toFloat().coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)))
            }
        }
    }
}

private fun weatherDateText(selectedDay: Int, selectedForecast: JSONObject?): String {
    if (selectedDay > 0) {
        selectedForecast?.cardString("date")?.let { return it }
    }
    val locale = Locale.getDefault()
    val pattern = AndroidDateFormat.getBestDateTimePattern(locale, "MMMEd")
    return SimpleDateFormat(pattern, locale).format(Date())
}

private fun weatherIcon(condition: String) = when {
    condition.containsAny("storm", "bolt", "thunder") -> Icons.Outlined.Thunderstorm
    condition.containsAny("rain", "shower") -> Icons.Outlined.WaterDrop
    condition.containsAny("wind") -> Icons.Outlined.Air
    condition.containsAny("sun", "clear") -> Icons.Outlined.WbSunny
    else -> Icons.Outlined.Cloud
}

private fun weatherColor(condition: String): Color = when {
    condition.containsAny("storm", "bolt", "thunder") -> Color(0xFFE0A800)
    condition.containsAny("rain", "shower") -> Color(0xFF2F80ED)
    condition.containsAny("wind") -> Color(0xFF009688)
    condition.containsAny("sun", "clear") -> Color(0xFFF2994A)
    else -> Color(0xFF8E8E93)
}

@Composable
private fun formatWeatherCondition(condition: String): String = when {
    condition.containsAny("sun", "clear") -> stringResource(R.string.screen_room_timeline_tool_card_weather_sunny)
    condition.containsAny("rain", "shower") -> stringResource(R.string.screen_room_timeline_tool_card_weather_rainy)
    condition.containsAny("wind") -> stringResource(R.string.screen_room_timeline_tool_card_weather_windy)
    condition.containsAny("storm", "bolt", "thunder") -> stringResource(R.string.screen_room_timeline_tool_card_weather_stormy)
    condition.isBlank() -> stringResource(R.string.screen_room_timeline_tool_card_weather_current_conditions)
    else -> condition.replaceFirstChar { it.uppercase() }
}

private fun String.containsAny(vararg needles: String): Boolean {
    val lower = lowercase()
    return needles.any { lower.contains(it) }
}

private fun Double.roundedInt(): Int = roundToInt()

private fun Int.compactCount(): String = when {
    this >= 1_000_000 -> "%.1fM".format(this / 1_000_000.0).trimTrailingZero()
    this >= 1_000 -> "%.1fK".format(this / 1_000.0).trimTrailingZero()
    else -> toString()
}

private fun String.trimTrailingZero(): String = replace(".0", "")

// MARK: - Events (eventList)

@Composable
private fun EventListCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("events")
    ToolCardSurface {
        ToolCardHeader(title = stringResource(R.string.screen_room_timeline_tool_card_events), count = all.size)
        if (all.isEmpty()) {
            MetaText(stringResource(R.string.screen_room_timeline_tool_card_no_events_found))
            return@ToolCardSurface
        }
        DividedList(all.take(MAX_ITEMS)) { EventRow(it, onLinkClick) }
    }
}

@Composable
private fun EventRow(event: JSONObject, onLinkClick: () -> Unit) {
    val title = event.cardString("title") ?: ""
    val whenText = event.cardString("when")?.cleanEventTimeText()
    val location = event.cardString("venue", "address")
    val imageUrl = event.cardImageUrls("thumbnail", "imageUrl", "image", "images").firstOrNull()
    val action = openLinkAction(event.cardString("url"), onLinkClick)

    Row(
        modifier = Modifier.fillMaxWidth().clickableIfLink(action).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            whenText?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(13.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            location?.let {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        imageUrl?.let {
            CardRemoteImage(
                url = it,
                modifier = Modifier.size(width = 72.dp, height = 54.dp),
                corner = 10,
            )
        }
    }
}

private fun String.cleanEventTimeText(): String {
    val monthNames = mapOf(
        "ene" to "Jan",
        "feb" to "Feb",
        "mar" to "Mar",
        "abr" to "Apr",
        "apr" to "Apr",
        "may" to "May",
        "jun" to "Jun",
        "jul" to "Jul",
        "ago" to "Aug",
        "aug" to "Aug",
        "sep" to "Sep",
        "oct" to "Oct",
        "nov" to "Nov",
        "dic" to "Dec",
        "dec" to "Dec",
    )
    val weekdayPattern = "(mon|tue|wed|thu|fri|sat|sun|lun|mar|mi[eé]|jue|vie|s[aá]b|dom)"
    val compact = replace(Regex("\\s+"), " ")
        .replace("–", "-")
        .trim()
    val withoutWeekday = compact
        .replace(Regex("^$weekdayPattern,?\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("-\\s*$weekdayPattern,?\\s+", RegexOption.IGNORE_CASE), "- ")
    val normalizedMonths = Regex("(\\d{1,2})\\s+de\\s+([a-záé]{3})", RegexOption.IGNORE_CASE)
        .replace(withoutWeekday) { match ->
            val day = match.groupValues[1]
            val month = monthNames[match.groupValues[2].lowercase(Locale.US)] ?: match.groupValues[2]
            "$month $day"
        }
    return normalizedMonths
        .replace("a.m.", "AM")
        .replace("p.m.", "PM")
        .replace(Regex("\\s+-\\s+"), " - ")
}

// MARK: - Places (placeList)

@Composable
private fun PlaceListCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("places")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            if (all.isEmpty()) {
                MetaText(stringResource(R.string.screen_room_timeline_tool_card_no_places_found))
                return@Column
            }
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                all.take(MAX_ITEMS).forEach { PlaceRow(it, onLinkClick) }
            }
    }
}

@Composable
private fun PlaceHeader(count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF31E879), modifier = Modifier.size(22.dp))
        Text(
            text = stringResource(R.string.screen_room_timeline_tool_card_places),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlaceRow(place: JSONObject, onLinkClick: () -> Unit) {
    val name = place.cardString("name") ?: ""
    val type = place.cardString("type")
    val address = place.cardString("address")
    val price = place.cardString("price")
    val thumb = place.cardImageUrls("thumbnail", "imageUrl", "image").firstOrNull()
    val imageUrls = place.cardImageUrls("imageUrls", "images", "photos")
    val galleryUrls = if (imageUrls.isNotEmpty()) imageUrls else listOfNotNull(thumb)
    val rating = place.cardString("rating")
    val reviews = place.cardInt("reviews")
    val openState = place.cardString("openState")
    val url = place.cardString("url")
    val action = openLinkAction(url, onLinkClick)
    val imageFallback = name.trim().take(1).uppercase(Locale.getDefault()).ifBlank { "P" }
    var showGallery by remember(imageUrls.joinToString("|"), thumb) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().clickableIfLink(action),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .then(if (galleryUrls.isNotEmpty()) Modifier.clickable { showGallery = true } else Modifier),
            ) {
                CardRemoteImage(url = thumb, modifier = Modifier.fillMaxSize(), corner = 10) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = imageFallback,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                }
                if (galleryUrls.size > 1) {
                    Text(
                        text = galleryUrls.size.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val tail = listOfNotNull(reviews?.let { pluralStringResource(R.plurals.screen_room_timeline_tool_card_review_count, it, it) }, price, type).joinToString("  ·  ")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rating?.let {
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFFFA142).copy(alpha = 0.18f), RoundedCornerShape(7.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFA142), modifier = Modifier.size(12.dp))
                            Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    if (tail.isNotEmpty()) MetaText(tail)
                }
            }
        }
        openState?.let {
            val closed = it.lowercase().let { s -> s.contains("closed") || s.contains("temporarily") || s.contains("permanently") }
            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = if (closed) PlaceClosedColor else PlaceOpenColor)
        }
        address?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (galleryUrls.size > 1) {
            HotelImageStrip(
                urls = galleryUrls,
                onClick = { showGallery = true },
            )
        }
    }
    if (showGallery) {
        HotelImageDialog(
            urls = galleryUrls,
            title = name,
            onDismiss = { showGallery = false },
        )
    }
}

@Composable
private fun PlaceBanner(
    thumb: String?,
    imageCount: Int,
    onClick: (() -> Unit)?,
) {
    if (thumb.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(PlaceAccent.copy(alpha = 0.32f), PlaceAccent.copy(alpha = 0.12f)),
                        start = Offset.Zero,
                        end = Offset(600f, 280f),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color.White.copy(alpha = 0.82f), modifier = Modifier.size(34.dp))
        }
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(132.dp)
                .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        ) {
            CardRemoteImage(url = thumb, modifier = Modifier.fillMaxWidth().height(132.dp), corner = 16)
            if (imageCount > 1) {
                Text(
                    text = imageCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.50f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// MARK: - URL content (urlContent)

@Composable
private fun UrlContentCard(data: JSONObject, onLinkClick: () -> Unit) {
    val all = data.cardObjects("articles", "results", "items", "data")
    if (all.isEmpty()) {
        MetaText(stringResource(R.string.screen_room_timeline_tool_card_content_not_fetched))
        return
    }
    DividedList(all.take(MAX_ITEMS)) { UrlRow(it, onLinkClick) }
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
internal fun ComposioHotelCardPreview() = ElementPreview {
    composioSearchCard(
        cardType = "hotelBooking",
        data = jsonOf(
            "destination" to "Chengdu",
            "hotels" to previewArray(
                jsonOf(
                    "name" to "Hilton Garden Inn Chengdu Wuhou New City",
                    "address" to "Wuhou District, Chengdu",
                    "price" to "$60",
                    "totalPrice" to "$240",
                    "rating" to 4.7,
                    "reviewCount" to 25,
                    "stars" to 4,
                    "imageUrl" to "https://images.unsplash.com/photo-1566073771259-6a8506099945?w=320",
                    "imageUrls" to JSONArray()
                        .put("https://images.unsplash.com/photo-1566073771259-6a8506099945?w=960")
                        .put("https://images.unsplash.com/photo-1582719508461-905c673771fd?w=960")
                        .put("https://images.unsplash.com/photo-1564501049412-61c2a3083791?w=960"),
                    "amenities" to JSONArray().put("Breakfast").put("Free Wi-Fi").put("Parking").put("Pool").put("Airport shuttle"),
                    "url" to "https://www.google.com/travel/hotels",
                    "mapsUrl" to "https://www.google.com/maps/search/?api=1&query=Hilton%20Garden%20Inn%20Chengdu",
                ),
                jsonOf(
                    "name" to "The Temple House Chengdu",
                    "address" to "Taikoo Li, Jinjiang District",
                    "price" to "$122",
                    "totalPrice" to "$489",
                    "rating" to 4.6,
                    "reviewCount" to 175,
                    "stars" to 5,
                    "imageUrl" to "https://images.unsplash.com/photo-1551882547-ff40c63fe5fa?w=320",
                    "imageUrls" to JSONArray().put("https://images.unsplash.com/photo-1551882547-ff40c63fe5fa?w=960"),
                    "amenities" to JSONArray().put("Spa").put("Gym").put("Pool").put("Restaurant"),
                    "url" to "https://www.google.com/travel/hotels",
                    "mapsUrl" to "https://www.google.com/maps/search/?api=1&query=The%20Temple%20House%20Chengdu",
                ),
            ),
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
internal fun ComposioWeatherCardPreview() = ElementPreview {
    composioSearchCard(
        cardType = "weather",
        data = jsonOf(
            "city" to "Shanghai",
            "country" to "China",
            "current" to jsonOf(
                "temperature" to 28,
                "feelsLike" to 31,
                "humidity" to 74,
                "windSpeed" to 18,
                "condition" to "Clear",
            ),
            "forecast" to previewArray(
                jsonOf("day" to "Today", "date" to "2026-06-18", "high" to 30, "low" to 24, "condition" to "Clear", "precipitation" to 10),
                jsonOf("day" to "Friday", "date" to "2026-06-19", "high" to 27, "low" to 22, "condition" to "Rain", "precipitation" to 65),
                jsonOf("day" to "Saturday", "date" to "2026-06-20", "high" to 29, "low" to 23, "condition" to "Cloudy", "precipitation" to 25),
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
