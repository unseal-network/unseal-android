/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.timeline.components.event.toolcards.CardChip
import io.element.android.features.messages.impl.timeline.components.event.toolcards.CardRemoteImage
import io.element.android.features.messages.impl.timeline.components.event.toolcards.DividedList
import io.element.android.wysiwyg.link.Link
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_JSON_RENDER_DEPTH = 16
private const val MAX_JSON_RENDER_ITEMS = 8
private val JsonAccentGreen = Color(0xFF31D76B)
private val JsonAccentOrange = Color(0xFFFFA142)

@Immutable
private data class JsonRenderElement(
    val id: String,
    val type: String,
    val props: JSONObject,
    val children: List<String>,
)

@Immutable
private data class JsonRenderSpec(
    val root: String,
    val elements: Map<String, JsonRenderElement>,
)

@Composable
internal fun JsonSpecRender(
    payload: String,
    onLinkClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spec = remember(payload) { payload.toJsonRenderSpec() }
    if (spec == null) {
        JsonSpecReadableFallback(payload = payload, onLinkClick = onLinkClick, modifier = modifier)
        return
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            JsonRenderNode(spec = spec, id = spec.root, onLinkClick = onLinkClick, depth = 0)
        }
    }
}

@Composable
private fun JsonRenderNode(
    spec: JsonRenderSpec,
    id: String,
    onLinkClick: (Link) -> Unit,
    depth: Int,
) {
    if (depth > MAX_JSON_RENDER_DEPTH) return
    val element = spec.elements[id] ?: return
    val type = element.type.normalizedJsonType()
    when (type) {
        "stack", "vstack", "hstack", "group", "section", "scroll", "container" -> JsonRenderStack(spec, element, onLinkClick, depth)
        "card" -> JsonRenderCard(spec, element, onLinkClick, depth)
        "text", "paragraph", "span", "label" -> JsonRenderText(element)
        "heading", "title", "headline" -> JsonRenderHeading(element)
        "button", "link" -> JsonRenderButton(element, onLinkClick)
        "image", "avatar" -> JsonRenderImage(element)
        "divider", "separator" -> androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        "badge", "chip" -> JsonRenderBadge(element)
        "progress" -> JsonRenderProgress(element)
        "alert" -> JsonRenderAlert(element)
        "file", "fileattachment", "fileattachmentcard" -> JsonRenderFile(element, onLinkClick)
        "hotel", "hotelcard", "hotelbookingcard" -> JsonRenderHotel(element, onLinkClick)
        "product", "productcard", "shoppingitem", "universalproductcard" -> JsonRenderProduct(element, onLinkClick)
        "news", "headline", "headlineitem", "headlinecard", "urlcontent" -> JsonRenderNews(element, onLinkClick)
        else -> {
            if (element.children.isNotEmpty()) {
                JsonRenderStack(spec, element, onLinkClick, depth)
            } else {
                JsonRenderText(element)
            }
        }
    }
}

@Composable
private fun JsonRenderStack(spec: JsonRenderSpec, element: JsonRenderElement, onLinkClick: (Link) -> Unit, depth: Int) {
    val horizontal = element.type.contains("hstack", ignoreCase = true) || element.props.string("direction") == "horizontal"
    if (horizontal) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            element.children.take(MAX_JSON_RENDER_ITEMS).forEach { child ->
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    JsonRenderNode(spec, child, onLinkClick, depth + 1)
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            element.children.take(MAX_JSON_RENDER_ITEMS).forEach { child ->
                JsonRenderNode(spec, child, onLinkClick, depth + 1)
            }
        }
    }
}

@Composable
private fun JsonRenderCard(spec: JsonRenderSpec, element: JsonRenderElement, onLinkClick: (Link) -> Unit, depth: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.44f),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            element.props.firstString("title", "heading", "name")?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            element.props.firstString("subtitle", "description")?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            element.children.take(MAX_JSON_RENDER_ITEMS).forEach { child ->
                JsonRenderNode(spec, child, onLinkClick, depth + 1)
            }
        }
    }
}

@Composable
private fun JsonRenderText(element: JsonRenderElement) {
    val text = element.props.firstString("text", "content", "value", "label", "title").orEmpty()
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun JsonRenderHeading(element: JsonRenderElement) {
    val text = element.props.firstString("text", "content", "title", "label").orEmpty()
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun JsonRenderButton(element: JsonRenderElement, onLinkClick: (Link) -> Unit) {
    val label = element.props.firstString("label", "text", "title").orEmpty()
    val url = element.props.firstString("url", "href")
    if (label.isBlank() && url.isNullOrBlank()) return
    Text(
        text = label.ifBlank { url.orEmpty() },
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), RoundedCornerShape(50))
            .clickable(enabled = !url.isNullOrBlank()) { url?.let { onLinkClick(Link(it)) } }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    )
}

@Composable
private fun JsonRenderImage(element: JsonRenderElement) {
    val url = element.props.firstString("url", "src", "image", "imageUrl", "thumbnail")
    CardRemoteImage(url = url, modifier = Modifier.fillMaxWidth().height(148.dp), corner = 12)
}

@Composable
private fun JsonRenderBadge(element: JsonRenderElement) {
    val text = element.props.firstString("label", "text", "title", "value").orEmpty()
    if (text.isBlank()) return
    CardChip(text = text)
}

@Composable
private fun JsonRenderProgress(element: JsonRenderElement) {
    val value = element.props.double("value") ?: element.props.double("progress") ?: return
    LinearProgressIndicator(progress = { value.coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun JsonRenderAlert(element: JsonRenderElement) {
    val title = element.props.firstString("title", "heading").orEmpty()
    val text = element.props.firstString("message", "description", "text", "content").orEmpty()
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.70f), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun JsonRenderFile(element: JsonRenderElement, onLinkClick: (Link) -> Unit) {
    val title = element.props.firstString("name", "title", "filename").orEmpty()
    val subtitle = element.props.firstString("size", "subtitle", "mimeType", "type")
    val url = element.props.firstString("url", "href")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !url.isNullOrBlank()) { url?.let { onLinkClick(Link(it)) } }
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title.ifBlank { "File" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun JsonRenderHotel(element: JsonRenderElement, onLinkClick: (Link) -> Unit) {
    val title = element.props.firstString("name", "title").orEmpty()
    val subtitle = element.props.firstString("location", "address", "subtitle")
    val image = element.props.firstString("imageUrl", "image", "thumbnail") ?: element.props.firstObject("images")?.firstString("thumbnail", "url", "original_image")
    val rating = element.props.firstString("rating", "score", "overall_rating")
    val price = element.props.firstString("price", "rate", "totalPrice") ?: element.props.firstObject("rate_per_night")?.firstString("lowest")
    val url = element.props.firstString("url", "link", "mapsUrl")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !url.isNullOrBlank()) { url?.let { onLinkClick(Link(it)) } }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CardRemoteImage(url = image, modifier = Modifier.size(68.dp), corner = 10)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title.ifBlank { "Hotel" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                rating?.let {
                    CardChip(text = it, color = JsonAccentGreen, contentColor = Color.White)
                }
                element.props.stringArray("amenities").take(2).forEach { CardChip(text = it) }
            }
        }
        price?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = JsonAccentGreen, modifier = Modifier.widthIn(max = 96.dp), maxLines = 1)
        }
    }
}

@Composable
private fun JsonRenderProduct(element: JsonRenderElement, onLinkClick: (Link) -> Unit) {
    val title = element.props.firstString("title", "name").orEmpty()
    val brand = element.props.firstString("brand", "source", "store")
    val image = element.props.firstString("imageUrl", "image", "thumbnail")
    val price = element.props.firstString("price", "amount")
    val rating = element.props.firstString("rating")
    val url = element.props.firstString("url", "link", "href")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !url.isNullOrBlank()) { url?.let { onLinkClick(Link(it)) } }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CardRemoteImage(url = image, modifier = Modifier.size(62.dp), corner = 10)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.ifBlank { "Product" }, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            brand?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            rating?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = JsonAccentOrange, modifier = Modifier.size(13.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        price?.let { Text(it, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = JsonAccentGreen, modifier = Modifier.widthIn(max = 106.dp), maxLines = 1) }
    }
}

@Composable
private fun JsonRenderNews(element: JsonRenderElement, onLinkClick: (Link) -> Unit) {
    val title = element.props.firstString("title", "headline").orEmpty()
    val snippet = element.props.firstString("snippet", "description", "summary")
    val source = element.props.firstString("source", "domain", "publisher")
    val url = element.props.firstString("url", "link", "href")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !url.isNullOrBlank()) { url?.let { onLinkClick(Link(it)) } }
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title.ifBlank { "News" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        snippet?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        source?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = JsonAccentGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun JsonSpecReadableFallback(payload: String, onLinkClick: (Link) -> Unit, modifier: Modifier = Modifier) {
    val json = remember(payload) { payload.jsonObjectOrNull() }
    val items = remember(payload) { json?.readableItems().orEmpty() }
    if (items.isEmpty()) return
    Surface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.66f)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            DividedList(items.take(MAX_JSON_RENDER_ITEMS)) { item ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.first, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        item.second?.let { value ->
                            if (value.startsWith("http")) {
                                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onLinkClick(Link(value)) })
                            } else {
                                Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun String.toJsonRenderSpec(): JsonRenderSpec? {
    val json = jsonObjectOrNull()?.let { root ->
        root.optJSONObject("data") ?: root.optJSONObject("spec") ?: root
    } ?: return null
    val rootId = json.optString("root").takeIf { it.isNotBlank() } ?: json.optString("id").takeIf { it.isNotBlank() } ?: "root"
    val elementsObject = json.optJSONObject("elements")
    return if (elementsObject != null) {
        val elements = mutableMapOf<String, JsonRenderElement>()
        elementsObject.keys().forEach { key ->
            elementsObject.optJSONObject(key)?.toJsonRenderElement(key)?.let { elements[key] = it }
        }
        if (elements[rootId] == null) null else JsonRenderSpec(root = rootId, elements = elements)
    } else {
        val element = json.toJsonRenderElement(rootId)
        JsonRenderSpec(root = rootId, elements = mapOf(rootId to element))
    }
}

private fun JSONObject.toJsonRenderElement(id: String): JsonRenderElement {
    val props = optJSONObject("props") ?: this
    val children = optJSONArray("children").toStringList()
    return JsonRenderElement(
        id = id,
        type = optString("type").takeIf { it.isNotBlank() } ?: optString("component").takeIf { it.isNotBlank() } ?: "card",
        props = props,
        children = children,
    )
}

private fun String.normalizedJsonType(): String = lowercase().replace("-", "").replace("_", "")

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index ->
        when (val value = opt(index)) {
            is String -> value.takeIf { it.isNotBlank() }
            is JSONObject -> value.optString("id").takeIf { it.isNotBlank() }
            else -> null
        }
    }
}

private fun JSONObject.firstString(vararg keys: String): String? {
    keys.forEach { key ->
        val value = opt(key)
        when (value) {
            is String -> value.takeIf { it.isNotBlank() }?.let { return it }
            is Number, is Boolean -> return value.toString()
            is JSONObject -> value.firstString("label", "text", "value")?.let { return it }
        }
    }
    return null
}

private fun JSONObject.string(key: String): String? = firstString(key)

private fun JSONObject.double(key: String): Double? {
    return when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

private fun JSONObject.firstObject(key: String): JSONObject? {
    return when (val value = opt(key)) {
        is JSONObject -> value
        is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { value.optJSONObject(it) }
        else -> null
    }
}

private fun JSONObject.stringArray(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optString(it).takeIf { value -> value.isNotBlank() } }
}

private fun JSONObject.readableItems(): List<Pair<String, String?>> {
    val labels = listOf("title", "heading", "name", "label", "description", "summary", "message", "url", "link")
    val direct = labels.mapNotNull { key ->
        firstString(key)?.let { key.replaceFirstChar { c -> c.uppercase() } to it }
    }
    if (direct.isNotEmpty()) return direct
    return keys().asSequence()
        .mapNotNull { key ->
            val value = opt(key)
            when (value) {
                is String -> key to value.takeIf { it.isNotBlank() }
                is Number, is Boolean -> key to value.toString()
                else -> null
            }
        }
        .toList()
}
