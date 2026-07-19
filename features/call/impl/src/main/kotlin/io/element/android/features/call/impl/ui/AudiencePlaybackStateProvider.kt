/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.impl.ui

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.call.impl.audience.AudienceManifest
import io.element.android.features.call.impl.audience.AudiencePlaybackCounts
import io.element.android.features.call.impl.audience.AudiencePlaybackState
import io.element.android.features.call.impl.audience.AudiencePresentation
import io.element.android.features.call.impl.audience.AudiencePresentationKind
import io.element.android.features.call.impl.audience.AudienceRendition

internal class AudiencePlaybackStateProvider : PreviewParameterProvider<AudiencePlaybackState> {
    private val manifest = AudienceManifest(
        broadcastId = "bcast_demo",
        meetingInstanceId = "4d1c64a7-6d0a-4fac-91f8-5bcbf2fc6a9d",
        generation = 2,
        revision = 4,
        presentations = listOf(
            AudiencePresentation(
                presentationId = "user:alice",
                kind = AudiencePresentationKind.User,
                matrixUserId = "@alice:keepsecret.io",
                matrixDeviceId = "ALICE1",
                displayName = "Alice",
                avatarUrl = null,
                sourceId = null,
                audio = AudienceRendition("https://example.test/alice-audio.m3u8", "audio/mp4", null, null),
                video = AudienceRendition("https://example.test/alice-video.m3u8", "video/mp4", 1280, 720),
                activeSpeaker = true,
            ),
            AudiencePresentation(
                presentationId = "screen:alice",
                kind = AudiencePresentationKind.Screen,
                matrixUserId = "@alice:keepsecret.io",
                matrixDeviceId = "ALICE1",
                displayName = "Alice's screen",
                avatarUrl = null,
                sourceId = "screen",
                audio = AudienceRendition("https://example.test/screen.m3u8", "audio/mp4", null, null),
                video = AudienceRendition("https://example.test/screen.m3u8", "video/mp4", 1920, 1080),
                activeSpeaker = false,
            ),
        ),
    )
    private val counts = AudiencePlaybackCounts(participantCount = 6, listenerCount = 24)

    override val values: Sequence<AudiencePlaybackState>
        get() = sequenceOf(
            AudiencePlaybackState.Connecting,
            AudiencePlaybackState.Live(manifest, counts),
            AudiencePlaybackState.Reconnecting(manifest, counts),
            AudiencePlaybackState.Ended,
            AudiencePlaybackState.Failed("audience_access_denied"),
        )
}
