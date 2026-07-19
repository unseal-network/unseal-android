/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.call.impl.R
import io.element.android.features.call.impl.audience.AudienceManifest
import io.element.android.features.call.impl.audience.AudiencePlaybackCounts
import io.element.android.features.call.impl.audience.AudiencePlaybackGenerationBarrier
import io.element.android.features.call.impl.audience.AudiencePlaybackSource
import io.element.android.features.call.impl.audience.AudiencePlaybackState
import io.element.android.features.call.impl.audience.AudiencePlaybackTarget
import io.element.android.features.call.impl.audience.AudiencePresentation
import io.element.android.features.call.impl.audience.AudiencePresentationKind
import io.element.android.features.call.impl.audience.toPlaybackTarget
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import kotlinx.coroutines.delay

@Composable
internal fun AudiencePlaybackView(
    playbackState: AudiencePlaybackState,
    isInPictureInPicture: Boolean,
    playbackEnabled: Boolean = true,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val counts = when (playbackState) {
        is AudiencePlaybackState.Live -> playbackState.counts
        is AudiencePlaybackState.Reconnecting -> playbackState.counts
        else -> null
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115)),
    ) {
        when (playbackState) {
            AudiencePlaybackState.Connecting -> AudienceMessage(
                text = stringResource(R.string.screen_call_audience_connecting),
                showProgress = true,
            )
            is AudiencePlaybackState.Live -> AudiencePresentationGrid(
                manifest = playbackState.manifest,
                reconnecting = false,
                isInPictureInPicture = isInPictureInPicture,
                playbackEnabled = playbackEnabled,
            )
            is AudiencePlaybackState.Reconnecting -> AudiencePresentationGrid(
                manifest = playbackState.manifest,
                reconnecting = true,
                isInPictureInPicture = isInPictureInPicture,
                playbackEnabled = playbackEnabled,
            )
            AudiencePlaybackState.Ended -> AudienceMessage(text = stringResource(R.string.screen_call_audience_ended))
            is AudiencePlaybackState.Failed -> AudienceMessage(text = stringResource(R.string.screen_call_audience_unavailable))
        }
        if (!isInPictureInPicture) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color(0xB3000000))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = CompoundIcons.HeadphonesSolid(),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.screen_call_audience_listening),
                            color = Color.White,
                            style = ElementTheme.typography.fontBodyLgMedium,
                        )
                        counts?.let { AudienceCounts(it) }
                    }
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = CompoundIcons.Close(),
                        contentDescription = stringResource(R.string.a11y_leave_listener_mode),
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AudiencePresentationGrid(
    manifest: AudienceManifest,
    reconnecting: Boolean,
    isInPictureInPicture: Boolean,
    playbackEnabled: Boolean,
) {
    if (manifest.presentations.isEmpty()) {
        AudienceMessage(
            text = stringResource(
                if (reconnecting) R.string.screen_call_audience_reconnecting else R.string.screen_call_audience_waiting_for_media
            ),
            showProgress = true,
        )
        return
    }
    val playbackTarget = remember(manifest.generation, manifest.presentations) {
        manifest.toPlaybackTarget()
    }
    var committedTarget by remember { mutableStateOf<AudiencePlaybackTarget?>(null) }
    var displayedPresentations by remember { mutableStateOf(manifest.presentations) }
    LaunchedEffect(playbackTarget, manifest.revision, manifest.presentations) {
        if (committedTarget == playbackTarget) {
            displayedPresentations = manifest.presentations
        }
    }
    val ordered = remember(displayedPresentations) {
        displayedPresentations.sortedWith(
            compareByDescending<AudiencePresentation> { it.kind == AudiencePresentationKind.Screen }
                .thenByDescending { it.activeSpeaker }
                .thenBy { it.presentationId }
        )
    }
    var players by remember { mutableStateOf<Map<String, ExoPlayer>>(emptyMap()) }
    AudienceGenerationPlaybackOwner(playbackTarget, playbackEnabled) { committed, updatedPlayers ->
        if (committed == playbackTarget) {
            committedTarget = committed
            displayedPresentations = manifest.presentations
            players = updatedPlayers
        } else if (committed == committedTarget) {
            players = updatedPlayers
        }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(if (isInPictureInPicture) 320.dp else 180.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 8.dp,
            top = if (isInPictureInPicture) 8.dp else 64.dp,
            end = 8.dp,
            bottom = 8.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = ordered,
            key = AudiencePresentation::presentationId,
            span = { presentation ->
                if (presentation.kind == AudiencePresentationKind.Screen || presentation.activeSpeaker && ordered.size > 1) {
                    GridItemSpan(maxLineSpan)
                } else {
                    GridItemSpan(1)
                }
            },
        ) { presentation ->
            AudiencePresentationTile(presentation, players[presentation.presentationId])
        }
        if (reconnecting) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC252A32), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Text(stringResource(R.string.screen_call_audience_reconnecting), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun AudiencePresentationTile(presentation: AudiencePresentation, player: ExoPlayer?) {
    val shape = RoundedCornerShape(14.dp)
    val border = if (presentation.activeSpeaker) BorderStroke(3.dp, Color(0xFF45E0A8)) else BorderStroke(1.dp, Color(0xFF343A44))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(if (presentation.kind == AudiencePresentationKind.Screen) 16f / 9f else 4f / 3f)
            .clip(shape)
            .border(border, shape)
            .background(Color(0xFF1B1F26)),
    ) {
        AudiencePresentationPlayerView(presentation, player)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
                .background(Color(0xB3000000), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (presentation.kind == AudiencePresentationKind.Screen) {
                Icon(
                    imageVector = CompoundIcons.ShareScreen(),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = presentation.displayName,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ElementTheme.typography.fontBodySmMedium,
            )
        }
    }
}

@Composable
private fun AudienceGenerationPlaybackOwner(
    target: AudiencePlaybackTarget,
    playbackEnabled: Boolean,
    onPlayersChanged: (AudiencePlaybackTarget, Map<String, ExoPlayer>) -> Unit,
) {
    if (LocalInspectionMode.current) return
    val context = LocalContext.current
    val currentOnPlayersChanged by rememberUpdatedState(onPlayersChanged)
    val barrier = remember { AudiencePlaybackGenerationBarrier<ExoPlayer>() }
    var retryAttempt by remember { mutableStateOf(0) }
    LaunchedEffect(playbackEnabled) {
        barrier.allPlayers().forEach { player -> player.playWhenReady = playbackEnabled }
    }
    DisposableEffect(Unit) {
        onDispose {
            currentOnPlayersChanged(target, emptyMap())
            barrier.close().forEach(ExoPlayer::release)
        }
    }
    LaunchedEffect(target, retryAttempt) {
        if (!barrier.needsCandidate(target)) return@LaunchedEffect
        if (retryAttempt > 0) {
            delay((500L * (1L shl (retryAttempt - 1).coerceIn(0, 3))).coerceAtMost(4_000L))
        }
        val candidates = mutableMapOf<String, ExoPlayer>()
        try {
            target.sources.forEach { source ->
                candidates[source.presentationId] = buildAudiencePlayer(context, source, playbackEnabled)
            }
        } catch (failure: Throwable) {
            candidates.values.forEach(ExoPlayer::release)
            retryAttempt++
            return@LaunchedEffect
        }
        barrier.stage(target, candidates).forEach(ExoPlayer::release)
        candidates.forEach { (presentationId, candidate) ->
            candidate.addListener(
                object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState != Player.STATE_READY) return
                        barrier.markReady(target, presentationId, candidate)?.let { commit ->
                            commit.activePlayers.values.forEach { player ->
                                player.volume = 1f
                                player.playWhenReady = playbackEnabled
                            }
                            currentOnPlayersChanged(target, commit.activePlayers)
                            commit.previousPlayers.forEach(ExoPlayer::release)
                            retryAttempt = 0
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        val failedPending = barrier.failPending(target, presentationId, candidate)
                        if (failedPending != null) {
                            failedPending.forEach(ExoPlayer::release)
                            retryAttempt++
                            return
                        }
                        val remainingActive = barrier.removeFailedActive(target, presentationId, candidate)
                        if (remainingActive != null) {
                            candidate.release()
                            currentOnPlayersChanged(target, remainingActive)
                            retryAttempt++
                        }
                    }
                }
            )
            candidate.prepare()
        }
    }
}

private fun buildAudiencePlayer(
    context: android.content.Context,
    source: AudiencePlaybackSource,
    playbackEnabled: Boolean,
): ExoPlayer {
    require(source.mediaUrls.isNotEmpty()) { "Audience presentation has no playable media" }
    val mediaSourceFactory = DefaultMediaSourceFactory(context)
    val mediaSources = source.mediaUrls.map { url ->
        mediaSourceFactory.createMediaSource(MediaItem.fromUri(url))
    }
    val mediaSource = if (mediaSources.size == 1) {
        mediaSources.single()
    } else {
        MergingMediaSource(*mediaSources.toTypedArray())
    }
    return ExoPlayer.Builder(context).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build(),
            false,
        )
        repeatMode = Player.REPEAT_MODE_OFF
        playWhenReady = playbackEnabled
        volume = 0f
        setMediaSource(mediaSource)
    }
}

@Composable
private fun AudienceCounts(counts: AudiencePlaybackCounts) {
    Text(
        text = stringResource(
            R.string.screen_call_audience_counts,
            counts.participantCount,
            counts.listenerCount,
        ),
        color = Color.White.copy(alpha = 0.75f),
        style = ElementTheme.typography.fontBodySmRegular,
    )
}

@Composable
private fun AudiencePresentationPlayerView(presentation: AudiencePresentation, player: ExoPlayer?) {
    if (LocalInspectionMode.current || presentation.video == null || player == null) {
        AudienceAvatar(presentation)
    } else {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = false
                    resizeMode = if (presentation.kind == AudiencePresentationKind.Screen) {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                    this.player = player
                }
            },
            update = { it.player = player },
            onRelease = { it.player = null },
        )
    }
}

@Composable
private fun AudienceAvatar(presentation: AudiencePresentation) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Avatar(
            avatarData = AvatarData(
                id = presentation.matrixUserId,
                name = presentation.displayName,
                url = presentation.avatarUrl,
                size = AvatarSize.DmCluster,
            ),
            avatarType = AvatarType.User,
            forcedAvatarSize = 72.dp,
        )
    }
}

@Composable
private fun AudienceMessage(text: String, showProgress: Boolean = false) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showProgress) {
            CircularProgressIndicator(color = Color.White)
        }
        Text(
            text = text,
            modifier = Modifier.padding(top = if (showProgress) 16.dp else 0.dp),
            color = Color.White,
            style = ElementTheme.typography.fontBodyLgRegular,
        )
    }
}

@PreviewsDayNight
@Composable
private fun AudiencePlaybackViewPreview(
    @PreviewParameter(AudiencePlaybackStateProvider::class) playbackState: AudiencePlaybackState,
) = ElementPreview {
    AudiencePlaybackView(
        playbackState = playbackState,
        isInPictureInPicture = false,
        onClose = {},
    )
}
