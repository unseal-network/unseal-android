/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

internal fun String.compactToolCardMetaDate(): String {
    val compact = replace(Regex("\\s+"), " ").trim()
    return Regex("\\d{4}-\\d{2}-\\d{2}").find(compact)?.value ?: compact
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

/** Image URLs under matching keys, accepting strings or objects from search APIs. */
internal fun JSONObject.cardImageUrls(vararg keys: String): List<String> {
    for (key in keys) {
        when (val value = opt(key)) {
            is String -> return listOfNotNull(value.normalizedImageUrl())
            is JSONArray -> {
                val urls = (0 until value.length()).mapNotNull { index ->
                    when (val item = value.opt(index)) {
                        is String -> item.normalizedImageUrl()
                        is JSONObject -> item.cardString(
                            "url",
                            "imageUrl",
                            "image_url",
                            "image",
                            "original",
                            "original_image",
                            "thumbnail",
                            "thumbnailUrl",
                            "thumbnail_url",
                            "source",
                        )?.normalizedImageUrl()
                        else -> null
                    }
                }.distinct()
                if (urls.isNotEmpty()) return urls
            }
        }
    }
    return emptyList()
}

internal fun List<String>.preferredCardImageUrls(): List<String> =
    distinct().sortedWith(compareBy(::imageUrlReliabilityScore))

private fun imageUrlReliabilityScore(url: String): Int = when {
    "/gps-cs-s/" in url -> 0
    "googleusercontent.com/gps-cs-s/" in url -> 0
    "googleusercontent.com/proxy/" in url -> 4
    "/proxy/" in url -> 4
    else -> 2
}

private fun String.normalizedImageUrl(): String? {
    val value = trim()
    if (value.isBlank()) return null
    return when {
        value.startsWith("//") -> "https:$value"
        value.startsWith("http://") || value.startsWith("https://") -> value
        else -> null
    }
}

/** The card's data object: tool output usually wraps the payload in `data`. */
internal fun JSONObject.cardData(): JSONObject = optJSONObject("data") ?: this

internal fun domainOf(url: String): String =
    url.removePrefix("https://www.").removePrefix("http://www.")
        .removePrefix("https://").removePrefix("http://")
        .substringBefore("/")

// MARK: - Shared M3 card components (mirror iOS SharedComponents)

/** Card container (iOS CollapsibleGlassCard, rendered as a plain M3 surface card). */
internal val LocalToolCardEmbeddedInRoot = compositionLocalOf { false }
internal val LocalToolCardRequestScrollToTop = compositionLocalOf<() -> Unit> { {} }

@Composable
internal fun ToolCardSurface(content: ColumnContent) {
    val shape = RoundedCornerShape(16.dp)
    val colors = toolCardSurfaceColors()
    if (LocalToolCardEmbeddedInRoot.current) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
        return
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = colors.container,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (isSystemInDarkTheme()) 2.dp else 2.dp,
        shadowElevation = if (isSystemInDarkTheme()) 4.dp else 3.dp,
        border = BorderStroke(0.6.dp, colors.border),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.accentWash,
                            Color.Transparent,
                        ),
                    )
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
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
internal fun ToolCardRowSurface(
    modifier: Modifier = Modifier,
    content: ColumnContent,
) {
    val shape = RoundedCornerShape(12.dp)
    val colors = toolCardSurfaceColors()
    Surface(
        modifier = modifier
            .fillMaxWidth(),
        shape = shape,
        color = colors.rowContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (isSystemInDarkTheme()) 1.dp else 0.dp,
        shadowElevation = if (isSystemInDarkTheme()) 2.dp else 1.dp,
        border = BorderStroke(0.5.dp, colors.border.copy(alpha = 0.42f)),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.rowHighlight,
                            Color.Transparent,
                        ),
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
internal fun CardRemoteImage(
    url: String?,
    modifier: Modifier = Modifier,
    corner: Int = 6,
    placeholderContent: (@Composable BoxScope.() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(corner.dp)
    val colors = toolCardSurfaceColors()
    val placeholderModifier = modifier
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    colors.imagePlaceholderStart,
                    colors.imagePlaceholderEnd,
                ),
            )
        )
        .border(0.5.dp, colors.border.copy(alpha = 0.55f), shape)
    if (url.isNullOrBlank()) {
        Box(modifier = placeholderModifier, content = { placeholderContent?.invoke(this) })
        return
    }
    val painter = rememberAsyncImagePainter(
        model = url,
        contentScale = ContentScale.Crop,
    )
    val state by painter.state.collectAsState()
    if (state is coil3.compose.AsyncImagePainter.State.Success) {
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(shape),
        )
    } else {
        Box(modifier = placeholderModifier, content = { placeholderContent?.invoke(this) })
    }
}

@Composable
private fun toolCardSurfaceColors(): ToolCardSurfaceColors {
    val dark = isSystemInDarkTheme()
    val scheme = MaterialTheme.colorScheme
    return if (dark) {
        ToolCardSurfaceColors(
            container = Color(0xFF15191F),
            rowContainer = Color(0xFF10151B).copy(alpha = 0.84f),
            border = scheme.primary.copy(alpha = 0.20f),
            accentWash = scheme.primary.copy(alpha = 0.13f),
            rowHighlight = Color.White.copy(alpha = 0.035f),
            imagePlaceholderStart = scheme.surface.copy(alpha = 0.76f),
            imagePlaceholderEnd = scheme.primary.copy(alpha = 0.09f),
        )
    } else {
        ToolCardSurfaceColors(
            container = Color.White,
            rowContainer = Color.White.copy(alpha = 0.92f),
            border = scheme.primary.copy(alpha = 0.18f),
            accentWash = scheme.primary.copy(alpha = 0.065f),
            rowHighlight = scheme.primaryContainer.copy(alpha = 0.16f),
            imagePlaceholderStart = Color.White.copy(alpha = 0.92f),
            imagePlaceholderEnd = scheme.primaryContainer.copy(alpha = 0.34f),
        )
    }
}

private data class ToolCardSurfaceColors(
    val container: Color,
    val rowContainer: Color,
    val border: Color,
    val accentWash: Color,
    val rowHighlight: Color,
    val imagePlaceholderStart: Color,
    val imagePlaceholderEnd: Color,
)

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
