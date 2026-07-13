/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGameContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient

@Composable
internal fun TimelineItemGameView(
    content: TimelineItemGameContent,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Build a custom Coil ImageLoader that adds the APP-U header required by the
    // homeserver sign proxy.  Re-created only when the homeserver host changes (once per session).
    val context = LocalContext.current
    val iconImageLoader = remember(content.homeserverHost) {
        val host = content.homeserverHost
        val client = OkHttpClient.Builder()
            .apply {
                if (host != null) {
                    addInterceptor { chain ->
                        val request = chain.request()
                        val requestBuilder = request.newBuilder()
                        appUHeaderValueForUrl(host, request.url)?.let { headerValue ->
                            requestBuilder.header("APP-U", headerValue)
                        }
                        chain.proceed(requestBuilder.build())
                    }
                }
            }
            .build()
        ImageLoader.Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { client }
                    )
                )
            }
            .build()
    }

    var clicked by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = ElementTheme.colors.bgSubtleSecondary,
        border = BorderStroke(1.dp, ElementTheme.colors.borderInteractiveSecondary),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                clicked = true
                eventSink(TimelineEvent.OpenGame(
                    gameId = content.gameId,
                    gameRoomId = content.gameRoomId,
                    remoteUrl = content.remoteUrl,
                ))
            },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            // Align icon to top so it doesn't float when brief text spans two lines
            verticalAlignment = Alignment.Top,
        ) {
            // Game icon — 48×48 with rounded corners
            if (content.resolvedIconUrl != null) {
                AsyncImage(
                    model = content.resolvedIconUrl,
                    contentDescription = content.gameName,
                    imageLoader = iconImageLoader,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ElementTheme.colors.textLinkExternal.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CompoundIcons.PlaySolid(),
                        contentDescription = null,
                        tint = ElementTheme.colors.textLinkExternal,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Text column fills remaining width so the bottom Row can use SpaceBetween
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = content.gameName,
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!content.gameBrief.isNullOrBlank()) {
                    Text(
                        text = content.gameBrief,
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = ElementTheme.colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = stringResource(
                        if (clicked) {
                            R.string.screen_room_game_card_opened
                        } else {
                            R.string.screen_room_game_card_start
                        }
                    ),
                    style = ElementTheme.typography.fontBodySmMedium,
                    color = ElementTheme.colors.textLinkExternal,
                )
            }
        }
    }
}

internal fun appUHeaderValueForUrl(
    homeserverHost: String?,
    requestUrl: HttpUrl,
): String? {
    val target = homeserverHost?.toAppUHeaderTarget() ?: return null
    if (requestUrl.host != target.host) {
        return null
    }
    if (target.port != null && requestUrl.port != target.port) {
        return null
    }
    return "s=${target.authority}"
}

private data class AppUHeaderTarget(
    val host: String,
    val port: Int?,
    val authority: String,
)

private fun String.toAppUHeaderTarget(): AppUHeaderTarget? {
    val raw = trim()
    if (raw.isBlank() || raw.containsHeaderUnsafeCharacter()) {
        return null
    }
    val url = raw.withHttpScheme().toHttpUrlOrNull() ?: return null
    val explicitPort = url.port.takeIf { raw.hasExplicitPort() }
    val authority = if (explicitPort == null) {
        url.host
    } else {
        "${url.host}:$explicitPort"
    }
    return AppUHeaderTarget(
        host = url.host,
        port = explicitPort,
        authority = authority,
    )
}

private fun String.withHttpScheme(): String {
    return if (startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)) {
        this
    } else {
        "https://$this"
    }
}

private fun String.hasExplicitPort(): Boolean {
    val authority = withoutHttpScheme()
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .substringAfterLast('@')
    if (authority.startsWith("[")) {
        return authority.substringAfter("]", missingDelimiterValue = "").startsWith(":")
    }
    val colonIndex = authority.lastIndexOf(':')
    return colonIndex >= 0 && authority.substring(colonIndex + 1).toIntOrNull() != null
}

private fun String.withoutHttpScheme(): String {
    return when {
        startsWith("https://", ignoreCase = true) -> drop("https://".length)
        startsWith("http://", ignoreCase = true) -> drop("http://".length)
        else -> this
    }
}

private fun String.containsHeaderUnsafeCharacter(): Boolean {
    return any { it == '\r' || it == '\n' }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@PreviewsDayNight
@Composable
internal fun TimelineItemGameViewPreview() = ElementPreview {
    TimelineItemGameView(
        content = TimelineItemGameContent(
            gameName = "Chess",
            gameBrief = "Classic board game for two players",
            resolvedIconUrl = null,
            homeserverHost = null,
            gameRoomId = "game-room-abc-123",
            gameId = 1,
            remoteUrl = null,
            creatorUserId = "@alice:matrix.example.com",
            fallbackBody = "Invite everyone to start a Chess game",
        ),
        eventSink = {},
    )
}

@PreviewsDayNight
@Composable
internal fun TimelineItemGameViewNoBriefPreview() = ElementPreview {
    TimelineItemGameView(
        content = TimelineItemGameContent(
            gameName = "Poker",
            gameBrief = null,
            resolvedIconUrl = null,
            homeserverHost = null,
            gameRoomId = "game-room-xyz-456",
            gameId = 2,
            remoteUrl = null,
            creatorUserId = "@bob:matrix.example.com",
            fallbackBody = "Invite everyone to start a Poker game",
        ),
        eventSink = {},
    )
}
