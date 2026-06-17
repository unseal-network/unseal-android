/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import io.element.android.features.messages.impl.timeline.components.event.toolcards.META_TOOL_NAMES
import io.element.android.features.messages.impl.timeline.components.event.toolcards.TOOL_CARD_REGISTRY
import org.json.JSONArray
import org.json.JSONObject

// MARK: - Card type resolution (mirror iOS ToolCardRegistry.resolve)

/** Resolves a tool name to its card type key, or null if the tool has no card. */
internal fun resolveToolCardType(toolName: String): String? {
    val name = toolName.removePrefix("tool-")
    if (META_TOOL_NAMES.contains(name)) return null
    return TOOL_CARD_REGISTRY[name] ?: when {
        name.startsWith("agent-") -> "subAgent"
        name == "data-spec" || name == "data-ui-spec" || name == "data-json-render" -> "jsonSpec"
        else -> null
    }
}

/** Parses a tool payload into the card's data object (unwrapping `data`, wrapping bare arrays). */
internal fun String.toCardDataJson(): JSONObject? {
    val t = trim()
    return runCatching {
        when {
            t.startsWith("{") -> JSONObject(t).cardData()
            t.startsWith("[") -> JSONObject().put("items", JSONArray(t))
            else -> null
        }
    }.getOrNull()
}

// MARK: - JSON extraction helpers (mirror iOS CardHelpers)

/** First non-blank string under any of [keys] (coerces numbers/bools). */
internal fun JSONObject.cardString(vararg keys: String): String? {
    for (key in keys) {
        if (!has(key) || isNull(key)) continue
        when (val v = opt(key)) {
            is String -> v.takeIf { it.isNotBlank() }?.let { return it }
            is Number, is Boolean -> return v.toString()
        }
    }
    return null
}

internal fun JSONObject.cardInt(vararg keys: String): Int? {
    for (key in keys) {
        val v = opt(key)
        when (v) {
            is Number -> return v.toInt()
            is String -> v.toIntOrNull()?.let { return it }
        }
    }
    return null
}

internal fun JSONObject.cardBool(key: String): Boolean? = if (has(key) && opt(key) is Boolean) getBoolean(key) else null

/** Array of objects under the first matching key. */
internal fun JSONObject.cardObjects(vararg keys: String): List<JSONObject> {
    for (key in keys) {
        (opt(key) as? JSONArray)?.let { arr ->
            return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }
    }
    return emptyList()
}

/** Array of strings under the first matching key. */
internal fun JSONObject.cardStrings(vararg keys: String): List<String> {
    for (key in keys) {
        (opt(key) as? JSONArray)?.let { arr ->
            return (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        }
    }
    return emptyList()
}

/** The card's data object: tool output usually wraps the payload in `data`. */
internal fun JSONObject.cardData(): JSONObject = optJSONObject("data") ?: this

internal fun domainOf(url: String): String =
    url.removePrefix("https://www.").removePrefix("http://www.")
        .removePrefix("https://").removePrefix("http://")
        .substringBefore("/")

// MARK: - Shared M3 card components (mirror iOS SharedComponents)

/** Card container (iOS CollapsibleGlassCard, rendered as a plain M3 surface card). */
@Composable
internal fun ToolCardSurface(content: ColumnContent) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

internal typealias ColumnContent = @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit

@Composable
internal fun ToolCardHeader(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Renders [items] with a divider between rows (iOS DividedList). */
@Composable
internal fun <T> DividedList(items: List<T>, row: @Composable (T) -> Unit) {
    items.forEachIndexed { index, item ->
        row(item)
        if (index < items.lastIndex) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        }
    }
}

@Composable
internal fun CardRemoteImage(url: String?, modifier: Modifier = Modifier, corner: Int = 6) {
    val shape = RoundedCornerShape(corner.dp)
    val placeholderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    if (url.isNullOrBlank()) {
        Box(
            modifier = modifier
                .clip(shape)
                .background(placeholderColor),
        )
        return
    }
    val painter = rememberAsyncImagePainter(
        model = url,
        contentScale = ContentScale.Crop,
    )
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Success) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(
            modifier = modifier
                .clip(shape)
                .background(placeholderColor),
        )
    }
}

@Composable
internal fun CardChip(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer, contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = contentColor,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
