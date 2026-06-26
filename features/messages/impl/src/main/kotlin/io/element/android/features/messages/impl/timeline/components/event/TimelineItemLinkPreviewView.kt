/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.wysiwyg.link.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

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
    val title = metadata.title?.takeIf { it.isNotBlank() } ?: metadata.host
    val subtitle = metadata.description?.takeIf { it.isNotBlank() } ?: metadata.host
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(ElementTheme.colors.bgSubtleSecondaryLevel0)
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, shape)
            .clickable { onClick(Link(url = url, text = url)) }
            .padding(12.dp)
            .semantics { contentDescription = "$title, $subtitle" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = title,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        metadata.imageUrl?.let { imageUrl ->
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElementTheme.colors.bgSubtleSecondary),
            )
        }
    }
}

internal data class LinkPreviewMetadata(
    val url: String,
    val host: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
) {
    companion object {
        fun fallback(url: String): LinkPreviewMetadata {
            val host = url.toHost()
            return LinkPreviewMetadata(
                url = url,
                host = host,
                title = host,
                description = url,
                imageUrl = null,
            )
        }
    }
}

internal object LinkPreviewMetadataProvider {
    private val cache = ConcurrentHashMap<String, LinkPreviewMetadata>()

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
}

private fun Document.toLinkPreviewMetadata(url: String): LinkPreviewMetadata {
    val host = metaContent("og:site_name") ?: url.toHost()
    val title = metaContent("og:title", "twitter:title") ?: title().takeIf { it.isNotBlank() } ?: host
    val description = metaContent("og:description", "description", "twitter:description") ?: host
    val imageUrl = metaContent("og:image", "twitter:image")?.let { rawImageUrl ->
        runCatching { URI(url).resolve(rawImageUrl).toString() }.getOrNull()
    }
    return LinkPreviewMetadata(
        url = url,
        host = host,
        title = title,
        description = description,
        imageUrl = imageUrl,
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

private fun String.toHost(): String {
    return runCatching { URI(this).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?: removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .removePrefix("www.")
}

@Preview
@Composable
internal fun TimelineItemLinkPreviewViewPreview() = ElementPreview {
    TimelineItemLinkPreviewView(
        url = "https://play.google.com/apps/internaltest/4701613975292549758",
        onClick = {},
    )
}
