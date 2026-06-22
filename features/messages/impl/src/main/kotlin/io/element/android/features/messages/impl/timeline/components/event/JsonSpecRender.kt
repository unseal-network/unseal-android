/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.components.SelectedStatePill
import io.element.android.features.messages.impl.components.ShapedClickableSurface
import io.element.android.features.messages.impl.timeline.components.event.toolcards.CardChip
import io.element.android.features.messages.impl.timeline.components.event.toolcards.CardRemoteImage
import io.element.android.features.messages.impl.timeline.components.event.toolcards.DividedList
import io.element.android.features.messages.impl.timeline.components.event.toolcards.cardObjects
import io.element.android.features.messages.impl.timeline.components.event.toolcards.cardString
import io.element.android.features.messages.impl.timeline.components.event.toolcards.cardStrings
import io.element.android.features.messages.impl.timeline.components.event.toolcards.compactToolCardMetaDate
import io.element.android.wysiwyg.link.Link
import org.json.JSONArray
import org.json.JSONObject

private const val MAX_JSON_RENDER_DEPTH = 16
private const val MAX_JSON_RENDER_ITEMS = 8
private val JsonAccentGreen = Color(0xFF31D76B)
private val JsonAccentOrange = Color(0xFFFFA142)

@Immutable
internal data class JsonRenderElement(
    val id: String,
    val type: String,
    val props: JSONObject,
    val children: List<String>,
)

@Immutable
internal data class JsonRenderSpec(
    val root: String,
    val elements: Map<String, JsonRenderElement>,
    val state: JSONObject?,
)

internal data class JsonRenderChild(
    val id: String,
    val state: JSONObject?,
    val element: JsonRenderElement? = null,
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
            JsonRenderNode(spec = spec, id = spec.root, onLinkClick = onLinkClick, depth = 0, state = spec.state)
        }
    }
}

@Composable
private fun JsonRenderNode(
    spec: JsonRenderSpec,
    id: String,
    onLinkClick: (Link) -> Unit,
    depth: Int,
    state: JSONObject?,
) {
    if (depth > MAX_JSON_RENDER_DEPTH) return
    JsonRenderElementNode(spec = spec, element = spec.elements[id] ?: return, onLinkClick = onLinkClick, depth = depth, state = state)
}

@Composable
private fun JsonRenderElementNode(
    spec: JsonRenderSpec,
    element: JsonRenderElement,
    onLinkClick: (Link) -> Unit,
    depth: Int,
    state: JSONObject?,
) {
    if (!element.isVisible(spec, state)) return
    val type = element.type.normalizedJsonType()
    when (type) {
        "stack", "vstack", "hstack", "group", "section", "container", "conditional" -> JsonRenderStack(spec, element, onLinkClick, depth, state)
        "scroll", "scrollview", "scrollarea", "list" -> JsonRenderScroll(spec, element, onLinkClick, depth, state)
        "card" -> JsonRenderCard(spec, element, onLinkClick, depth, state)
        "spacer", "gap", "space" -> JsonRenderSpacer(state, element)
        "text", "paragraph", "span", "label" -> JsonRenderText(state, element)
        "heading", "title", "headline" -> JsonRenderHeading(state, element)
        "button", "link", "action" -> JsonRenderButton(state, element, onLinkClick)
        "input", "textfield", "textarea", "textinput" -> JsonRenderReadOnlyField(state, element)
        "select", "dropdown", "picker" -> JsonRenderSelect(state, element)
        "switch", "toggle" -> JsonRenderSwitch(state, element)
        "checkbox" -> JsonRenderCheckbox(state, element)
        "radio", "radiogroup" -> JsonRenderRadioGroup(state, element)
        "image", "avatar" -> JsonRenderImage(element)
        "divider", "separator" -> androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
        "badge", "chip" -> JsonRenderBadge(state, element)
        "progress" -> JsonRenderProgress(state, element)
        "alert" -> JsonRenderAlert(state, element)
        "file", "fileattachment", "fileattachmentcard" -> JsonRenderFile(element, onLinkClick)
        "hotel", "hotelcard", "hotelbookingcard" -> JsonRenderHotel(element, onLinkClick)
        "product", "productcard", "shoppingitem", "universalproductcard" -> JsonRenderProduct(element, onLinkClick)
        "news", "headline", "headlineitem", "headlinecard", "urlcontent" -> JsonRenderNews(state, element, onLinkClick)
        else -> {
            if (element.children.isNotEmpty()) {
                JsonRenderStack(spec, element, onLinkClick, depth, state)
            } else {
                JsonRenderText(state, element)
            }
        }
    }
}

@Composable
private fun JsonRenderScroll(spec: JsonRenderSpec, element: JsonRenderElement, onLinkClick: (Link) -> Unit, depth: Int, state: JSONObject?) {
    val maxHeight = element.props.dp(state, "maxHeight", "height") ?: 220.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        element.renderChildren(spec, state).forEach { child ->
            child.element?.let {
                JsonRenderElementNode(spec, it, onLinkClick, depth + 1, child.state)
            } ?: JsonRenderNode(spec, child.id, onLinkClick, depth + 1, child.state)
        }
    }
}

@Composable
private fun JsonRenderSpacer(state: JSONObject?, element: JsonRenderElement) {
    val height = element.props.dp(state, "height", "size", "value") ?: 8.dp
    Box(modifier = Modifier.height(height.coerceIn(0.dp, 96.dp)))
}

@Composable
private fun JsonRenderStack(spec: JsonRenderSpec, element: JsonRenderElement, onLinkClick: (Link) -> Unit, depth: Int, state: JSONObject?) {
    val horizontal = element.type.contains("hstack", ignoreCase = true) || element.props.string("direction") == "horizontal"
    if (horizontal) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            element.renderChildren(spec, state).forEach { child ->
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    child.element?.let {
                        JsonRenderElementNode(spec, it, onLinkClick, depth + 1, child.state)
                    } ?: JsonRenderNode(spec, child.id, onLinkClick, depth + 1, child.state)
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            element.renderChildren(spec, state).forEach { child ->
                child.element?.let {
                    JsonRenderElementNode(spec, it, onLinkClick, depth + 1, child.state)
                } ?: JsonRenderNode(spec, child.id, onLinkClick, depth + 1, child.state)
            }
        }
    }
}

@Composable
private fun JsonRenderCard(spec: JsonRenderSpec, element: JsonRenderElement, onLinkClick: (Link) -> Unit, depth: Int, state: JSONObject?) {
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
            element.renderChildren(spec, state).forEach { child ->
                child.element?.let {
                    JsonRenderElementNode(spec, it, onLinkClick, depth + 1, child.state)
                } ?: JsonRenderNode(spec, child.id, onLinkClick, depth + 1, child.state)
            }
        }
    }
}

@Composable
private fun JsonRenderText(state: JSONObject?, element: JsonRenderElement) {
    val text = element.props.firstString(state, "text", "content", "value", "label", "title").orEmpty().compactJsonMetaText()
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun JsonRenderHeading(state: JSONObject?, element: JsonRenderElement) {
    val text = element.props.firstString(state, "text", "content", "title", "label").orEmpty().compactJsonMetaText()
    if (text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun JsonRenderButton(state: JSONObject?, element: JsonRenderElement, onLinkClick: (Link) -> Unit) {
    val actionLabel = element.props.clickActionLabel(state)
    val label = element.props.firstString(state, "label", "text", "title") ?: actionLabel.orEmpty()
    val url = element.props.firstString(state, "url", "href") ?: element.props.clickUrl(state)
    if (label.isBlank() && url.isNullOrBlank()) return
    val enabled = !url.isNullOrBlank()
    ShapedClickableSurface(
        onClick = { url?.let { onLinkClick(Link(it)) } },
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color = if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
        contentColor = if (enabled) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
    ) {
        Text(
            text = label.ifBlank { url.orEmpty() },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = androidx.compose.material3.LocalContentColor.current,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun JsonRenderReadOnlyField(state: JSONObject?, element: JsonRenderElement) {
    val label = element.props.firstString(state, "label", "title", "name")
    val value = element.props.firstString(state, "value", "text", "placeholder").orEmpty()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            label?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                text = value.ifBlank { " " },
                style = MaterialTheme.typography.bodyMedium,
                color = if (value.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                maxLines = if (element.type.normalizedJsonType() == "textarea") 4 else 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun JsonRenderSelect(state: JSONObject?, element: JsonRenderElement) {
    val label = element.props.firstString(state, "label", "title", "name")
    val selectedValue = element.props.firstString(state, "value", "selected", "selectedValue")
    val options = element.props.optionLabels(state)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        label?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            options.take(4).forEach { option ->
                val selected = selectedValue != null && (option == selectedValue || option.contains(selectedValue, ignoreCase = true))
                if (selected) {
                    SelectedStatePill(
                        selected = true,
                        onClick = {},
                        selectedColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) {
                        Text(
                            text = option,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = androidx.compose.material3.LocalContentColor.current,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                } else {
                    CardChip(
                        text = option,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (options.size > 4) {
                CardChip(text = "+${options.size - 4}")
            }
        }
    }
}

@Composable
private fun JsonRenderSwitch(state: JSONObject?, element: JsonRenderElement) {
    JsonRenderBooleanRow(
        label = element.props.firstString(state, "label", "title", "name") ?: "Switch",
        checked = element.props.boolean(state, "checked") ?: element.props.boolean(state, "value") ?: false,
        checkedText = "On",
        uncheckedText = "Off",
        checkedIcon = Icons.Filled.CheckCircle,
        uncheckedIcon = Icons.Outlined.RadioButtonUnchecked,
    )
}

@Composable
private fun JsonRenderCheckbox(state: JSONObject?, element: JsonRenderElement) {
    JsonRenderBooleanRow(
        label = element.props.firstString(state, "label", "title", "name") ?: "Option",
        checked = element.props.boolean(state, "checked") ?: element.props.boolean(state, "value") ?: false,
        checkedText = "Checked",
        uncheckedText = "Unchecked",
        checkedIcon = Icons.Outlined.CheckBox,
        uncheckedIcon = Icons.Outlined.CheckBoxOutlineBlank,
    )
}

@Composable
private fun JsonRenderRadioGroup(state: JSONObject?, element: JsonRenderElement) {
    val label = element.props.firstString(state, "label", "title", "name")
    val selectedValue = element.props.firstString(state, "value", "selected", "selectedValue")
    val options = element.props.optionLabels(state)
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        label?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
        options.take(MAX_JSON_RENDER_ITEMS).forEach { option ->
            val selected = selectedValue != null && (option == selectedValue || option.contains(selectedValue, ignoreCase = true))
            SelectedStatePill(
                selected = selected,
                onClick = {},
                selectedColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                unselectedColor = Color.Transparent,
                selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = RoundedCornerShape(10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                Icon(
                    imageVector = if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = androidx.compose.material3.LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
                    Text(option, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun JsonRenderBooleanRow(
    label: String,
    checked: Boolean,
    checkedText: String,
    uncheckedText: String,
    checkedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    uncheckedIcon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (checked) checkedIcon else uncheckedIcon,
            contentDescription = null,
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            text = if (checked) checkedText else uncheckedText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun JsonRenderImage(element: JsonRenderElement) {
    val url = element.props.firstString("url", "src", "image", "imageUrl", "thumbnail")
    CardRemoteImage(url = url, modifier = Modifier.fillMaxWidth().height(148.dp), corner = 12)
}

@Composable
private fun JsonRenderBadge(state: JSONObject?, element: JsonRenderElement) {
    val text = element.props.firstString(state, "label", "text", "title", "value").orEmpty()
    if (text.isBlank()) return
    CardChip(text = text)
}

@Composable
private fun JsonRenderProgress(state: JSONObject?, element: JsonRenderElement) {
    val value = element.props.double(state, "value") ?: element.props.double(state, "progress") ?: return
    LinearProgressIndicator(progress = { value.coerceIn(0.0, 1.0).toFloat() }, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun JsonRenderAlert(state: JSONObject?, element: JsonRenderElement) {
    val title = element.props.firstString(state, "title", "heading").orEmpty()
    val text = element.props.firstString(state, "message", "description", "text", "content").orEmpty()
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
            .clickableJsonUrl(url = url, shape = RoundedCornerShape(10.dp), onLinkClick = onLinkClick)
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
    val items = element.props.cardObjects("hotels", "cards")
    if (items.isNotEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
            border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                DividedList(items.take(MAX_JSON_RENDER_ITEMS)) { item ->
                    JsonRenderHotelRow(props = item, onLinkClick = onLinkClick)
                }
            }
        }
        return
    }
    JsonRenderHotelRow(props = element.props, onLinkClick = onLinkClick)
}

@Composable
private fun JsonRenderHotelRow(props: JSONObject, onLinkClick: (Link) -> Unit) {
    val title = props.cardString("name", "hotel_name", "hotelName", "title").orEmpty()
    val subtitle = props.cardString("location", "address", "area", "subtitle")
    val image = props.cardString("imageUrl", "image_url", "image", "thumbnail") ?: props.firstObject("images")?.firstString("thumbnail", "url", "original_image")
    val rating = props.cardString("rating", "score", "overall_rating")
    val stars = props.cardString("stars", "hotelClass", "hotel_class")
    val price = props.cardString("price", "rate", "totalPrice") ?: props.firstObject("rate_per_night")?.firstString("lowest")
    val url = props.cardString("url", "link", "mapsUrl")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickableJsonUrl(url = url, shape = RoundedCornerShape(12.dp), onLinkClick = onLinkClick)
            .padding(vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        val tags = buildList {
            rating?.let { add(it) }
            stars?.let { add("$it star") }
            addAll(props.cardStrings("amenities").take(4))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            JsonHotelImageTile(url = image, title = title)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = title.ifBlank { "Hotel" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        tags.forEachIndexed { index, tag ->
                            if (index == 0 && tag == rating) {
                                CardChip(text = tag, color = JsonAccentGreen, contentColor = Color.White)
                            } else {
                                CardChip(text = tag)
                            }
                        }
                    }
                }
            }
            price?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = JsonAccentGreen,
                    modifier = Modifier.widthIn(max = 124.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun JsonHotelImageTile(url: String?, title: String) {
    Box(contentAlignment = Alignment.Center) {
        CardRemoteImage(url = url, modifier = Modifier.size(56.dp), corner = 10)
        Text(
            text = title.firstOrNull()?.uppercase().orEmpty().ifBlank { "H" },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.62f),
        )
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
            .clickableJsonUrl(url = url, shape = RoundedCornerShape(12.dp), onLinkClick = onLinkClick)
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
private fun JsonRenderNews(state: JSONObject?, element: JsonRenderElement, onLinkClick: (Link) -> Unit) {
    val title = element.props.firstString(state, "title", "headline").orEmpty()
    val snippet = element.props.firstString(state, "snippet", "description", "summary")?.compactJsonMetaText()
    val source = element.props.firstString(state, "source", "domain", "publisher")
    val date = element.props.firstString(state, "date", "publishedAt", "published_at", "meta")?.compactToolCardMetaDate()
    val url = element.props.firstString(state, "url", "link", "href")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickableJsonUrl(url = url, shape = RoundedCornerShape(10.dp), onLinkClick = onLinkClick)
            .padding(vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title.ifBlank { "News" }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        snippet?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        date?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        source?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = JsonAccentGreen, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

private fun String.compactJsonMetaText(): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    if (!Regex("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}").containsMatchIn(compact)) return compact
    if (compact.length > 40) return compact
    return compact.compactToolCardMetaDate()
}

@Composable
private fun JsonSpecReadableFallback(payload: String, onLinkClick: (Link) -> Unit, modifier: Modifier = Modifier) {
    val json = remember(payload) { payload.jsonRenderObjectOrNull() }
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
                                Text(
                                    text = value,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickableJsonUrl(url = value, shape = RoundedCornerShape(6.dp), onLinkClick = onLinkClick)
                                        .padding(horizontal = 3.dp, vertical = 2.dp),
                                )
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

internal fun String.canRenderAsJsonSpec(): Boolean {
    toJsonRenderSpecFromPatchStream()?.let { return true }
    val rootObject = jsonRenderObjectOrNull() ?: return false
    if (rootObject.toJsonRenderSpecFromSpecPayload() != null) return true
    if (rootObject.toJsonRenderSpecFromTypedData() != null) return true
    val json = rootObject.optJSONObject("data") ?: rootObject.optJSONObject("spec") ?: rootObject
    if (json.toJsonRenderSpecFromSpecPayload() != null) return true
    if (json.toJsonRenderSpecFromPatchContainer() != null) return true
    if (json.toJsonRenderSpecFromTypedData() != null) return true
    val rootId = json.optString("root").takeIf { it.isNotBlank() } ?: return false
    return json.optJSONObject("elements")?.optJSONObject(rootId) != null
}

internal fun String.toJsonRenderSpec(): JsonRenderSpec? {
    toJsonRenderSpecFromPatchStream()?.let { return it }
    val rootObject = jsonRenderObjectOrNull() ?: return null
    rootObject.toJsonRenderSpecFromSpecPayload()?.let { return it }
    rootObject.toJsonRenderSpecFromTypedData()?.let { return it }
    val json = rootObject.optJSONObject("data") ?: rootObject.optJSONObject("spec") ?: rootObject
    json.toJsonRenderSpecFromSpecPayload()?.let { return it }
    json.toJsonRenderSpecFromPatchContainer()?.let { return it }
    json.toJsonRenderSpecFromTypedData()?.let { return it }
    return json.toJsonRenderSpecFromFlatObject()
}

private fun String.toJsonRenderSpecFromPatchStream(): JsonRenderSpec? {
    jsonRenderArrayOrNull()?.let { array ->
        val patches = (0 until array.length()).mapNotNull { index -> array.optJSONObject(index).toSpecPatchOrNull() }
        if (patches.size == array.length() && patches.isNotEmpty()) {
            return patches.toJsonRenderSpec()
        }
    }

    jsonRenderObjectOrNull()?.toSpecPatchOrNull()?.let { patch ->
        listOf(patch).toJsonRenderSpec()?.let { return it }
    }

    val lines = lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .toList()
    if (lines.isEmpty()) return null
    val patches = lines.mapNotNull { line -> line.jsonRenderObjectOrNull().toSpecPatchOrNull() }
    return if (patches.size == lines.size && patches.isNotEmpty()) patches.toJsonRenderSpec() else null
}

private fun JSONObject.toJsonRenderSpecFromPatchContainer(): JsonRenderSpec? {
    toSpecPatchOrNull()?.let { return listOf(it).toJsonRenderSpec() }
    return null
}

private fun JSONObject.toJsonRenderSpecFromTypedData(): JsonRenderSpec? {
    val type = optString("type").takeIf { it.isNotBlank() } ?: return null
    val data = optJSONObject("data") ?: return null
    val element = JsonRenderElement(id = "root", type = type, props = data, children = emptyList())
    return JsonRenderSpec(root = "root", elements = mapOf("root" to element), state = null)
}

private fun JSONObject.toJsonRenderSpecFromSpecPayload(): JsonRenderSpec? {
    return when (optString("type").lowercase()) {
        "flat" -> optJSONObject("spec")?.toJsonRenderSpecFromFlatObject()
        "nested" -> optJSONObject("spec")?.toJsonRenderSpecFromNestedObject()
        "patch" -> toSpecPatchOrNull()?.let { listOf(it).toJsonRenderSpec() }
        else -> null
    }
}

private fun JSONObject.toJsonRenderSpecFromFlatObject(): JsonRenderSpec? {
    val rootId = optString("root").takeIf { it.isNotBlank() } ?: optString("id").takeIf { it.isNotBlank() } ?: "root"
    val elementsObject = optJSONObject("elements")
    return if (elementsObject != null) {
        val elements = mutableMapOf<String, JsonRenderElement>()
        elementsObject.keys().forEach { key ->
            elementsObject.optJSONObject(key)?.toJsonRenderElement(key)?.let { elements[key] = it }
        }
        if (elements[rootId] == null) null else JsonRenderSpec(root = rootId, elements = elements, state = optJSONObject("state"))
    } else {
        val element = toJsonRenderElement(rootId)
        JsonRenderSpec(root = rootId, elements = mapOf(rootId to element), state = optJSONObject("state"))
    }
}

private fun JSONObject.toJsonRenderSpecFromNestedObject(): JsonRenderSpec? {
    val nestedRoot = optJSONObject("root") ?: this
    val elements = mutableMapOf<String, JsonRenderElement>()
    val rootId = nestedRoot.flattenNestedJsonElement(idHint = nestedRoot.optString("id").takeIf { it.isNotBlank() } ?: "root", elements = elements)
    return JsonRenderSpec(root = rootId, elements = elements, state = optJSONObject("state"))
}

private data class JsonSpecPatch(
    val op: String,
    val path: String,
    val value: Any?,
    val from: String?,
)

private fun JSONObject?.toSpecPatchOrNull(): JsonSpecPatch? {
    if (this == null) return null
    val patchObject = optJSONObject("patch") ?: this
    val op = patchObject.optString("op").takeIf { it.isNotBlank() } ?: return null
    if (!patchObject.has("path")) return null
    return JsonSpecPatch(
        op = op,
        path = patchObject.optString("path"),
        value = patchObject.opt("value"),
        from = patchObject.optString("from").takeIf { it.isNotBlank() },
    )
}

private fun List<JsonSpecPatch>.toJsonRenderSpec(): JsonRenderSpec? {
    val json = JSONObject()
        .put("root", "")
        .put("elements", JSONObject())
    forEach { json.applySpecPatch(it) }
    val rootId = json.optString("root").takeIf { it.isNotBlank() } ?: return null
    val elementsObject = json.optJSONObject("elements") ?: return null
    val elements = mutableMapOf<String, JsonRenderElement>()
    elementsObject.keys().forEach { key ->
        elementsObject.optJSONObject(key)?.toJsonRenderElement(key)?.let { elements[key] = it }
    }
    return if (elements[rootId] == null) {
        null
    } else {
        JsonRenderSpec(root = rootId, elements = elements, state = json.optJSONObject("state"))
    }
}

private fun JSONObject.applySpecPatch(patch: JsonSpecPatch) {
    when (patch.op.lowercase()) {
        "add", "replace" -> setJsonPointer(patch.path, patch.value)
        "remove" -> removeJsonPointer(patch.path)
        "copy" -> patch.from?.let { from -> setJsonPointer(patch.path, getJsonPointer(from)) }
        "move" -> patch.from?.let { from ->
            val value = getJsonPointer(from)
            removeJsonPointer(from)
            setJsonPointer(patch.path, value)
        }
    }
}

private fun JSONObject.setJsonPointer(path: String, value: Any?) {
    val segments = path.jsonPointerSegments()
    if (segments.isEmpty()) return
    var current = this
    segments.dropLast(1).forEach { segment ->
        val next = current.optJSONObject(segment) ?: JSONObject().also { current.put(segment, it) }
        current = next
    }
    current.put(segments.last(), value)
}

private fun JSONObject.removeJsonPointer(path: String) {
    val segments = path.jsonPointerSegments()
    if (segments.isEmpty()) return
    var current = this
    segments.dropLast(1).forEach { segment ->
        current = current.optJSONObject(segment) ?: return
    }
    current.remove(segments.last())
}

private fun JSONObject.getJsonPointer(path: String): Any? {
    val segments = path.jsonPointerSegments()
    if (segments.isEmpty()) return this
    var current: Any? = this
    segments.forEach { segment ->
        current = (current as? JSONObject)?.opt(segment) ?: return null
    }
    return current
}

private fun String.jsonPointerSegments(): List<String> {
    if (isBlank() || this == "/") return emptyList()
    val raw = if (startsWith("/")) drop(1) else this
    if (raw.isBlank()) return emptyList()
    return raw.split("/").map { it.replace("~1", "/").replace("~0", "~") }
}

private fun JSONObject.toJsonRenderElement(id: String): JsonRenderElement {
    val props = mergedJsonRenderProps()
    val children = optJSONArray("children").toStringList()
    return JsonRenderElement(
        id = id,
        type = optString("type").takeIf { it.isNotBlank() } ?: optString("component").takeIf { it.isNotBlank() } ?: "card",
        props = props,
        children = children,
    )
}

private fun JSONObject.flattenNestedJsonElement(
    idHint: String,
    elements: MutableMap<String, JsonRenderElement>,
): String {
    val id = optString("id").takeIf { it.isNotBlank() } ?: idHint
    val childIds = mutableListOf<String>()
    optJSONArray("children")?.let { children ->
        repeat(children.length()) { index ->
            when (val child = children.opt(index)) {
                is String -> child.takeIf { it.isNotBlank() }?.let { childIds += it }
                is JSONObject -> {
                    childIds += child.flattenNestedJsonElement(
                        idHint = "${id}_child_$index",
                        elements = elements,
                    )
                }
            }
        }
    }
    elements[id] = JsonRenderElement(
        id = id,
        type = optString("type").takeIf { it.isNotBlank() } ?: optString("component").takeIf { it.isNotBlank() } ?: "card",
        props = mergedJsonRenderProps(),
        children = childIds,
    )
    return id
}

private fun JSONObject.mergedJsonRenderProps(): JSONObject {
    val merged = optJSONObject("props")?.let { JSONObject(it.toString()) } ?: JSONObject(this.toString())
    listOf("visible", "on", "actions", "repeat", "watch", "template", "$" + "template").forEach { key ->
        if (has(key) && !merged.has(key)) {
            merged.put(key, opt(key))
        }
    }
    return merged
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

private fun JSONObject.firstString(vararg keys: String): String? = firstString(state = null, *keys)

internal fun JSONObject.firstString(state: JSONObject?, vararg keys: String): String? {
    keys.forEach { key ->
        val value = opt(key)
        when (value) {
            is String -> value.takeIf { it.isNotBlank() }?.let { return it }
            is Number, is Boolean -> return value.toString()
            is JSONObject -> {
                value.optString("$" + "state").takeIf { it.isNotBlank() }?.let { path ->
                    state?.getJsonPointer(path)?.let { return it.toString() }
                }
                value.firstString(state, "label", "text", "value")?.let { return it }
            }
        }
    }
    return null
}

internal fun JsonRenderElement.isVisible(spec: JsonRenderSpec, state: JSONObject? = spec.state): Boolean {
    if (!props.has("visible")) return true
    return props.opt("visible").toJsonRenderBoolean(state) != false
}

internal fun JsonRenderElement.renderChildren(spec: JsonRenderSpec, state: JSONObject? = spec.state): List<JsonRenderChild> {
    val repeatItems = props.opt("repeat").toJsonRenderArray(state)
    if (repeatItems.isEmpty()) {
        return children.take(MAX_JSON_RENDER_ITEMS).map { childId -> JsonRenderChild(childId, state) }
    }
    val template = props.optJSONObject("$" + "template") ?: props.optJSONObject("template")
    if (template != null) {
        return repeatItems
            .take(MAX_JSON_RENDER_ITEMS)
            .mapIndexed { index, item ->
                val itemState = when (item) {
                    is JSONObject -> item
                    else -> JSONObject().put("value", item)
                }
                JsonRenderChild(
                    id = "${id}_template_$index",
                    state = itemState,
                    element = template.toJsonRenderElement("${id}_template_$index"),
                )
            }
    }
    return repeatItems
        .flatMap { item ->
            val itemState = when (item) {
                is JSONObject -> item
                else -> JSONObject().put("value", item)
            }
            children.map { childId -> JsonRenderChild(childId, itemState) }
        }
        .take(MAX_JSON_RENDER_ITEMS)
}

private fun Any?.toJsonRenderBoolean(state: JSONObject?): Boolean? {
    return when (this) {
        null, JSONObject.NULL -> null
        is Boolean -> this
        is Number -> this.toInt() != 0
        is String -> when (lowercase()) {
            "true", "1", "yes", "on" -> true
            "false", "0", "no", "off" -> false
            else -> null
        }
        is JSONObject -> {
            optString("$" + "state").takeIf { it.isNotBlank() }?.let { path ->
                return state?.getJsonPointer(path).toJsonRenderBoolean(state)
            }
            firstString("value", "visible", "enabled").toJsonRenderBoolean(state)
        }
        else -> null
    }
}

private fun Any?.toJsonRenderArray(state: JSONObject?): List<Any?> {
    return when (this) {
        null, JSONObject.NULL -> emptyList()
        is JSONArray -> (0 until length()).map { index -> opt(index) }
        is JSONObject -> {
            optString("$" + "state").takeIf { it.isNotBlank() }?.let { path ->
                return state?.getJsonPointer(path).toJsonRenderArray(state)
            }
            optJSONArray("items")?.let { return it.toJsonRenderArray(state) }
            emptyList()
        }
        else -> emptyList()
    }
}

private fun JSONObject.string(key: String): String? = firstString(key)

private fun JSONObject.double(key: String): Double? {
    return when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}

private fun JSONObject.double(state: JSONObject?, key: String): Double? {
    return when (val value = opt(key)) {
        is JSONObject -> value.optString("$" + "state").takeIf { it.isNotBlank() }?.let { path ->
            when (val resolved = state?.getJsonPointer(path)) {
                is Number -> resolved.toDouble()
                is String -> resolved.toDoubleOrNull()
                else -> null
            }
        }
        else -> double(key)
    }
}

private fun JSONObject.dp(state: JSONObject?, vararg keys: String) = keys
    .firstNotNullOfOrNull { key -> double(state, key) }
    ?.coerceIn(0.0, 640.0)
    ?.dp

private fun JSONObject.boolean(state: JSONObject?, key: String): Boolean? = opt(key).toJsonRenderBoolean(state)

private fun JSONObject.clickUrl(state: JSONObject?): String? {
    val on = optJSONObject("on") ?: optJSONObject("actions") ?: return null
    val click = on.optJSONObject("click") ?: on.optJSONObject("tap") ?: on.optJSONObject("press") ?: on
    return click.firstString(state, "url", "href", "link")
}

private fun Modifier.clickableJsonUrl(
    url: String?,
    shape: Shape,
    onLinkClick: (Link) -> Unit,
): Modifier {
    if (url.isNullOrBlank()) return this
    return clip(shape)
        .clickable { onLinkClick(Link(url)) }
}

internal fun JSONObject.clickActionLabel(state: JSONObject?): String? {
    val on = optJSONObject("on") ?: optJSONObject("actions") ?: return null
    val click = on.optJSONObject("click") ?: on.optJSONObject("tap") ?: on.optJSONObject("press") ?: on
    return click.firstString(state, "label", "text", "title", "name", "action", "type", "id")
        ?.replace("_", " ")
        ?.replace("-", " ")
        ?.trim()
        ?.takeIf { it.isNotBlank() }
}

private fun JSONObject.optionLabels(state: JSONObject?): List<String> {
    fun Any?.label(): String? {
        return when (this) {
            is String -> takeIf { it.isNotBlank() }
            is Number, is Boolean -> toString()
            is JSONObject -> firstString(state, "label", "text", "title", "name", "value")
            else -> null
        }
    }

    val raw = opt("options").toJsonRenderArray(state)
        .ifEmpty { opt("items").toJsonRenderArray(state) }
        .ifEmpty { optJSONArray("choices").toJsonRenderArray(state) }
    return raw.mapNotNull { it.label() }
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

private fun String.jsonRenderObjectOrNull(): JSONObject? = runCatching { JSONObject(this) }.getOrNull()

private fun String.jsonRenderArrayOrNull(): JSONArray? = runCatching { JSONArray(this) }.getOrNull()
