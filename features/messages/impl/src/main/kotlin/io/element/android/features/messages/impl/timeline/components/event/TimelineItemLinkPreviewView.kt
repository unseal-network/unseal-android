/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.cache.BoundedTimelineCache
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.wysiwyg.link.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI

@Composable
fun TimelineItemLinkPreviewView(
    url: String,
    onClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    var metadata by remember(url) {
        mutableStateOf(LinkPreviewMetadataProvider.cached(url) ?: LinkPreviewMetadata.fallback(url))
    }
    val isInspectionMode = LocalInspectionMode.current

    LaunchedEffect(url, isInspectionMode) {
        if (!isInspectionMode) {
            metadata = LinkPreviewMetadataProvider.fetch(url)
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val title = when (metadata.style) {
        LinkPreviewStyle.GooglePlayInternalTest -> stringResource(R.string.screen_room_timeline_link_preview_sign_in)
        else -> metadata.title?.takeIf { it.isNotBlank() } ?: metadata.host
    }
    val subtitle = metadata.description?.takeIf { it.isNotBlank() } ?: metadata.host
    val isBranded = metadata.style != LinkPreviewStyle.Default
    val cardBackground = metadata.style.backgroundColor() ?: ElementTheme.colors.bgSubtleSecondaryLevel0
    val titleColor = metadata.style.titleColor() ?: ElementTheme.colors.textPrimary
    val subtitleColor = metadata.style.subtitleColor() ?: ElementTheme.colors.textSecondary
    val cardModifier = modifier
        .fillMaxWidth()
        .heightIn(min = 78.dp)
        .clip(shape)
        .background(cardBackground)
    val decoratedModifier = if (isBranded) {
        cardModifier
    } else {
        cardModifier.border(1.dp, ElementTheme.colors.borderInteractiveSecondary, shape)
    }
    Row(
        modifier = decoratedModifier
            .clickable { onClick(Link(url = url, text = url)) }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = "$title, $subtitle" },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = titleColor,
                maxLines = if (isBranded) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = subtitleColor,
                maxLines = if (isBranded) 1 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LinkPreviewVisual(metadata)
    }
}

@Composable
private fun LinkPreviewVisual(
    metadata: LinkPreviewMetadata,
    modifier: Modifier = Modifier,
) {
    when (metadata.style) {
        LinkPreviewStyle.GooglePlayInternalTest -> GooglePlayLogo(
            modifier = modifier.size(48.dp)
        )
        LinkPreviewStyle.AppStore,
        LinkPreviewStyle.GitHub,
        LinkPreviewStyle.GoogleMaps,
        LinkPreviewStyle.YouTube -> BrandBadge(
            label = metadata.style.badgeLabel(),
            background = metadata.style.badgeBackground(),
            foreground = metadata.style.badgeForeground(),
            modifier = modifier.size(52.dp),
        )
        LinkPreviewStyle.Default -> {
            val imageUrl = metadata.imageUrl ?: metadata.iconUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElementTheme.colors.bgSubtleSecondary),
                )
            } else {
                HostMonogram(
                    host = metadata.host,
                    modifier = modifier.size(52.dp)
                )
            }
        }
    }
}

@Composable
private fun BrandBadge(
    label: String,
    background: Color,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = foreground,
        )
    }
}

@Composable
private fun HostMonogram(
    host: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ElementTheme.colors.bgSubtleSecondary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = host.firstOrNull()?.uppercase().orEmpty(),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun GooglePlayLogo(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height

        fun pathOf(vararg points: Pair<Float, Float>) = Path().apply {
            points.forEachIndexed { index, point ->
                val x = point.first * width
                val y = point.second * height
                if (index == 0) {
                    moveTo(x, y)
                } else {
                    lineTo(x, y)
                }
            }
            close()
        }

        drawPath(
            path = pathOf(0.18f to 0.08f, 0.58f to 0.50f, 0.18f to 0.92f),
            color = GooglePlayGreen,
        )
        drawPath(
            path = pathOf(0.18f to 0.92f, 0.58f to 0.50f, 0.73f to 0.66f, 0.32f to 0.96f),
            color = GooglePlayBlueIcon,
        )
        drawPath(
            path = pathOf(0.58f to 0.50f, 0.73f to 0.34f, 0.90f to 0.44f, 0.96f to 0.50f, 0.90f to 0.56f, 0.73f to 0.66f),
            color = GooglePlayYellow,
        )
        drawPath(
            path = pathOf(0.58f to 0.50f, 0.73f to 0.66f, 0.32f to 0.96f, 0.18f to 0.92f),
            color = GooglePlayRed,
        )
    }
}

internal data class LinkPreviewMetadata(
    val url: String,
    val host: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val iconUrl: String?,
    val style: LinkPreviewStyle = LinkPreviewStyle.Default,
) {
    companion object {
        fun fallback(url: String): LinkPreviewMetadata {
            val host = url.toHost()
            val style = url.toLinkPreviewStyle()
            val branded = style.fallbackText(host)
            return LinkPreviewMetadata(
                url = url,
                host = host,
                title = branded?.title ?: host,
                description = branded?.description ?: url,
                imageUrl = null,
                iconUrl = url.toFaviconUrl(),
                style = style,
            )
        }
    }
}

internal enum class LinkPreviewStyle {
    Default,
    GooglePlayInternalTest,
    AppStore,
    GitHub,
    GoogleMaps,
    YouTube,
}

private fun LinkPreviewStyle.backgroundColor(): Color? = when (this) {
    LinkPreviewStyle.Default -> null
    LinkPreviewStyle.GooglePlayInternalTest -> GooglePlayBlue
    LinkPreviewStyle.AppStore -> Color(0xFF0A84FF)
    LinkPreviewStyle.GitHub -> Color(0xFF24292F)
    LinkPreviewStyle.GoogleMaps -> Color(0xFF1A73E8)
    LinkPreviewStyle.YouTube -> Color(0xFFFF0033)
}

private fun LinkPreviewStyle.titleColor(): Color? = when (this) {
    LinkPreviewStyle.Default -> null
    LinkPreviewStyle.GooglePlayInternalTest,
    LinkPreviewStyle.AppStore,
    LinkPreviewStyle.GitHub,
    LinkPreviewStyle.GoogleMaps,
    LinkPreviewStyle.YouTube -> Color.White
}

private fun LinkPreviewStyle.subtitleColor(): Color? = when (this) {
    LinkPreviewStyle.Default -> null
    LinkPreviewStyle.GooglePlayInternalTest,
    LinkPreviewStyle.AppStore,
    LinkPreviewStyle.GitHub,
    LinkPreviewStyle.GoogleMaps,
    LinkPreviewStyle.YouTube -> Color.White.copy(alpha = 0.68f)
}

private data class BrandedFallbackText(
    val title: String,
    val description: String,
)

private fun LinkPreviewStyle.fallbackText(host: String): BrandedFallbackText? = when (this) {
    LinkPreviewStyle.Default -> null
    LinkPreviewStyle.GooglePlayInternalTest -> BrandedFallbackText("Sign in", host)
    LinkPreviewStyle.AppStore -> BrandedFallbackText("App Store", host)
    LinkPreviewStyle.GitHub -> BrandedFallbackText("GitHub", host)
    LinkPreviewStyle.GoogleMaps -> BrandedFallbackText("Google Maps", host)
    LinkPreviewStyle.YouTube -> BrandedFallbackText("YouTube", host)
}

private fun LinkPreviewStyle.badgeLabel(): String = when (this) {
    LinkPreviewStyle.Default -> ""
    LinkPreviewStyle.GooglePlayInternalTest -> ""
    LinkPreviewStyle.AppStore -> "A"
    LinkPreviewStyle.GitHub -> "GH"
    LinkPreviewStyle.GoogleMaps -> "M"
    LinkPreviewStyle.YouTube -> "YT"
}

private fun LinkPreviewStyle.badgeBackground(): Color = when (this) {
    LinkPreviewStyle.Default -> Color.Transparent
    LinkPreviewStyle.GooglePlayInternalTest -> Color.Transparent
    LinkPreviewStyle.AppStore -> Color.White.copy(alpha = 0.18f)
    LinkPreviewStyle.GitHub -> Color.White.copy(alpha = 0.14f)
    LinkPreviewStyle.GoogleMaps -> Color.White.copy(alpha = 0.18f)
    LinkPreviewStyle.YouTube -> Color.White.copy(alpha = 0.18f)
}

private fun LinkPreviewStyle.badgeForeground(): Color = when (this) {
    LinkPreviewStyle.Default -> Color.Transparent
    LinkPreviewStyle.GooglePlayInternalTest -> Color.Transparent
    LinkPreviewStyle.AppStore,
    LinkPreviewStyle.GitHub,
    LinkPreviewStyle.GoogleMaps,
    LinkPreviewStyle.YouTube -> Color.White
}

internal object LinkPreviewMetadataProvider {
    private val cache = BoundedTimelineCache<String, LinkPreviewMetadata>(MAX_CACHED_METADATA)

    fun cached(url: String): LinkPreviewMetadata? = cache[url]

    suspend fun fetch(url: String): LinkPreviewMetadata {
        cache[url]?.let { return it }
        val metadata = runCatching {
            withContext(Dispatchers.IO) {
                Jsoup.connect(url)
                    .timeout(5_000)
                    .userAgent("Mozilla/5.0")
                    .get()
                    .toLinkPreviewMetadata(url)
            }
        }.getOrElse {
            LinkPreviewMetadata.fallback(url)
        }
        cache[url] = metadata
        return metadata
    }

    private const val MAX_CACHED_METADATA = 256
}

private fun Document.toLinkPreviewMetadata(url: String): LinkPreviewMetadata {
    val style = url.toLinkPreviewStyle()
    val urlHost = url.toHost()
    val branded = style.fallbackText(urlHost)
    val host = branded?.description ?: metaContent("og:site_name") ?: urlHost
    val title = branded?.title ?: metaContent("og:title", "twitter:title") ?: title().takeIf { it.isNotBlank() } ?: host
    val description = branded?.description ?: metaContent("og:description", "description", "twitter:description") ?: host
    val imageUrl = metaContent("og:image", "twitter:image")?.let { rawImageUrl ->
        runCatching { URI(url).resolve(rawImageUrl).toString() }.getOrNull()
    }
    val iconUrl = linkHref("apple-touch-icon", "apple-touch-icon-precomposed", "icon", "shortcut icon")
        ?.let { rawIconUrl -> runCatching { URI(url).resolve(rawIconUrl).toString() }.getOrNull() }
        ?: url.toFaviconUrl()
    return LinkPreviewMetadata(
        url = url,
        host = host,
        title = title,
        description = description,
        imageUrl = imageUrl,
        iconUrl = iconUrl,
        style = style,
    )
}

private fun Document.metaContent(vararg names: String): String? {
    for (name in names) {
        val content = selectFirst("""meta[property="$name"], meta[name="$name"]""")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
        if (content != null) return content
    }
    return null
}

private fun Document.linkHref(vararg rels: String): String? {
    for (rel in rels) {
        val href = selectFirst("""link[rel~=(?i)\b${Regex.escape(rel)}\b]""")
            ?.attr("href")
            ?.takeIf { it.isNotBlank() }
        if (href != null) return href
    }
    return null
}

private fun String.toHost(): String {
    return runCatching { URI(this).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?: removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .removePrefix("www.")
}

private fun String.toFaviconUrl(): String? {
    return runCatching {
        val uri = URI(this)
        "${uri.scheme}://${uri.host}/favicon.ico"
    }.getOrNull()
}

private fun String.toLinkPreviewStyle(): LinkPreviewStyle {
    val uri = runCatching { URI(this) }.getOrNull() ?: return LinkPreviewStyle.Default
    val host = uri.host?.removePrefix("www.").orEmpty()
    val path = uri.path.orEmpty()
    return when {
        host == "play.google.com" && path.startsWith("/apps/internaltest") -> LinkPreviewStyle.GooglePlayInternalTest
        host == "apps.apple.com" -> LinkPreviewStyle.AppStore
        host == "github.com" || host.endsWith(".github.com") -> LinkPreviewStyle.GitHub
        host == "maps.google.com" || host == "google.com" && path.startsWith("/maps") -> LinkPreviewStyle.GoogleMaps
        host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com") -> LinkPreviewStyle.YouTube
        else -> LinkPreviewStyle.Default
    }
}

private val GooglePlayBlue = Color(0xFF3F73E8)
private val GooglePlayGreen = Color(0xFF3BCC72)
private val GooglePlayBlueIcon = Color(0xFF4285F4)
private val GooglePlayYellow = Color(0xFFFABB05)
private val GooglePlayRed = Color(0xFFEA4335)

@Preview
@Composable
internal fun TimelineItemLinkPreviewViewPreview() = ElementPreview {
    TimelineItemLinkPreviewView(
        url = "https://play.google.com/apps/internaltest/4701613975292549758",
        onClick = {},
    )
}
