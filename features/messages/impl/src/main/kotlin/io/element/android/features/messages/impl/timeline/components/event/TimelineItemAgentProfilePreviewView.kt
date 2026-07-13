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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.cache.BoundedTimelineCache
import io.element.android.features.messages.impl.utils.UnsealAgentProfileLink
import io.element.android.wysiwyg.link.Link
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URI

@Composable
internal fun TimelineItemAgentProfilePreviewView(
    profileLink: UnsealAgentProfileLink,
    metadata: AgentProfilePreviewMetadata,
    onClick: (Link) -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboardManager = LocalClipboardManager.current
    val shape = RoundedCornerShape(12.dp)
    val contentDescription = listOfNotNull(
        metadata.title,
        metadata.description?.takeIf { it.isNotBlank() },
    ).joinToString()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 132.dp)
            .clip(shape)
            .background(ElementTheme.colors.bgSubtleSecondaryLevel0)
            .border(1.dp, ElementTheme.colors.borderInteractiveSecondary, shape)
            .clickable { onClick(Link(url = profileLink.profileUrl, text = profileLink.profileUrl)) }
            .padding(14.dp)
            .semantics { this.contentDescription = contentDescription },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            AgentProfileAvatar(metadata, modifier = Modifier.size(52.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = metadata.title,
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!metadata.description.isNullOrBlank()) {
                    Text(
                        text = metadata.description,
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                ChannelIconRow(metadata.channels.take(3))
            }
        }
        if (!metadata.soul.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(ElementTheme.colors.bgSubtleSecondary)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "Soul",
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                )
                Text(
                    text = metadata.soul,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            onClick = {
                clipboardManager.setText(AnnotatedString(buildAgentProfileConnectText(profileLink.mdUrl)))
            },
        ) {
            Text(
                text = "Connect with me",
                style = ElementTheme.typography.fontBodyLgMedium,
            )
        }
    }
}

@Composable
private fun AgentProfileAvatar(
    metadata: AgentProfilePreviewMetadata,
    modifier: Modifier = Modifier,
) {
    if (metadata.avatarUrl != null) {
        AsyncImage(
            model = metadata.avatarUrl,
            contentDescription = null,
            modifier = modifier
                .clip(CircleShape)
                .background(ElementTheme.colors.bgSubtleSecondary),
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(ElementTheme.colors.bgActionPrimaryRest.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = metadata.title.firstOrNull()?.uppercase().orEmpty(),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textActionPrimary,
            )
        }
    }
}

@Composable
private fun ChannelIconRow(channels: List<AgentProfilePreviewChannel>) {
    if (channels.isEmpty()) return
    val uriHandler = LocalUriHandler.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        channels.forEach { channel ->
            ChannelIcon(channel) {
                channel.url?.let(uriHandler::openUri)
            }
        }
    }
}

@Composable
private fun ChannelIcon(channel: AgentProfilePreviewChannel, onClick: () -> Unit) {
    val brand = channel.platformBrand()
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(brand.color)
            .clickable(enabled = channel.url != null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(brand.icon, contentDescription = channel.label ?: brand.name, tint = Color.White, modifier = Modifier.size(17.dp))
    }
}

internal data class AgentProfilePreviewMetadata(
    val title: String,
    val description: String?,
    val avatarUrl: String?,
    val soul: String?,
    val channels: List<AgentProfilePreviewChannel>,
)

internal data class AgentProfilePreviewChannel(
    val platform: String,
    val label: String?,
    val url: String?,
)

internal object AgentProfilePreviewMetadataProvider {
    private val cache = BoundedTimelineCache<String, AgentProfilePreviewMetadata>(MAX_CACHED_METADATA)

    fun cached(profileLink: UnsealAgentProfileLink): AgentProfilePreviewMetadata? = cache[profileLink.profileUrl]

    suspend fun fetch(profileLink: UnsealAgentProfileLink): AgentProfilePreviewMetadata? {
        cached(profileLink)?.let { return it }
        val metadata = runCatching {
            withContext(Dispatchers.IO) {
                val body = Jsoup.connect(profileLink.jsonUrl)
                    .ignoreContentType(true)
                    .timeout(5_000)
                    .userAgent("Mozilla/5.0")
                    .execute()
                    .body()
                JSONObject(body).toAgentProfilePreviewMetadata(profileLink)
            }
        }.getOrNull()
        if (metadata != null) cache[profileLink.profileUrl] = metadata
        return metadata
    }

    private const val MAX_CACHED_METADATA = 128
}

internal fun JSONObject.toAgentProfilePreviewMetadata(profileLink: UnsealAgentProfileLink): AgentProfilePreviewMetadata? {
    val title = optStringOrNull("displayName")
        ?: optStringOrNull("name")
        ?: return null
    val soul = optStringOrNull("soul")
    val channels = optJSONArray("channels").orEmpty().mapNotNull { channelJson ->
        channelJson as? JSONObject
    }.mapNotNull { channelJson ->
        val platform = channelJson.optStringOrNull("platform") ?: return@mapNotNull null
        val url = sanitizeAgentProfilePreviewMetadataUrl(channelJson.optStringOrNull("url"))
            ?: return@mapNotNull null
        AgentProfilePreviewChannel(
            platform = platform,
            label = channelJson.optStringOrNull("label"),
            url = url,
        )
    }
    return AgentProfilePreviewMetadata(
        title = title,
        description = optStringOrNull("description"),
        avatarUrl = sanitizeAgentProfilePreviewMetadataUrl(optStringOrNull("avatarUrl")),
        soul = soul,
        channels = channels,
    )
}

private data class AgentProfileChannelBrand(
    val name: String,
    val color: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
private fun AgentProfilePreviewChannel.platformBrand(): AgentProfileChannelBrand {
    return when (platform.lowercase()) {
        "telegram" -> AgentProfileChannelBrand("Telegram", Color(0xFF229ED9), CompoundIcons.Send())
        "wecom" -> AgentProfileChannelBrand("WeCom", Color(0xFF07C160), CompoundIcons.Chat())
        "feishu" -> AgentProfileChannelBrand("Feishu", Color(0xFF3370FF), CompoundIcons.Chat())
        "discord" -> AgentProfileChannelBrand("Discord", Color(0xFF5865F2), CompoundIcons.Chat())
        "whatsapp" -> AgentProfileChannelBrand("WhatsApp", Color(0xFF25D366), CompoundIcons.Chat())
        "messenger" -> AgentProfileChannelBrand("Messenger", Color(0xFF0084FF), CompoundIcons.Chat())
        else -> AgentProfileChannelBrand(platform, Color(0xFF64748B), CompoundIcons.Chat())
    }
}

private fun buildAgentProfileConnectText(mdUrl: String): String {
    return "Follow this instructions to connect me $mdUrl"
}

internal fun sanitizeAgentProfilePreviewMetadataUrl(rawUrl: String?): String? {
    val trimmedUrl = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    val uri = runCatching { URI(trimmedUrl) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    if (scheme != "http" && scheme != "https") return null
    if (uri.host.isNullOrBlank()) return null
    return uri.toString()
}

private fun JSONObject.optStringOrNull(name: String): String? =
    optString(name).takeIf { it.isNotBlank() }

private fun JSONArray?.orEmpty(): List<Any?> {
    if (this == null) return emptyList()
    return List(length()) { index -> opt(index) }
}
