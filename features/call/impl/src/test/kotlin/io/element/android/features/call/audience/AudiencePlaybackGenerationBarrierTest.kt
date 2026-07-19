/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.audience

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.audience.AudiencePlaybackGenerationBarrier
import io.element.android.features.call.impl.audience.AudiencePlaybackSource
import io.element.android.features.call.impl.audience.AudiencePlaybackTarget
import org.junit.Test

class AudiencePlaybackGenerationBarrierTest {
    @Test
    fun `replacement commits only after every presentation is ready`() {
        val barrier = AudiencePlaybackGenerationBarrier<String>()
        val first = target(generation = 1)
        val second = target(generation = 2)
        barrier.stage(first, mapOf("camera" to "old-camera", "screen" to "old-screen"))
        assertThat(barrier.markReady(first, "camera", "old-camera")).isNull()
        assertThat(barrier.markReady(first, "screen", "old-screen")?.activePlayers)
            .containsExactly("camera", "old-camera", "screen", "old-screen")

        barrier.stage(second, mapOf("camera" to "new-camera", "screen" to "new-screen"))
        assertThat(barrier.markReady(second, "camera", "new-camera")).isNull()
        val commit = barrier.markReady(second, "screen", "new-screen")

        assertThat(commit?.activePlayers)
            .containsExactly("camera", "new-camera", "screen", "new-screen")
        assertThat(commit?.previousPlayers).containsExactly("old-camera", "old-screen")
    }

    @Test
    fun `failed replacement is discarded while the active generation is retained`() {
        val barrier = AudiencePlaybackGenerationBarrier<String>()
        val first = target(generation = 1)
        val second = target(generation = 2)
        barrier.stage(first, mapOf("camera" to "old-camera", "screen" to "old-screen"))
        barrier.markReady(first, "camera", "old-camera")
        barrier.markReady(first, "screen", "old-screen")
        barrier.stage(second, mapOf("camera" to "new-camera", "screen" to "new-screen"))

        val discarded = barrier.failPending(second, "screen", "new-screen")

        assertThat(discarded).containsExactly("new-camera", "new-screen")
        assertThat(barrier.needsCandidate(first)).isFalse()
        assertThat(barrier.needsCandidate(second)).isTrue()
    }

    @Test
    fun `media URLs retain distinct audio and video and deduplicate a shared playlist`() {
        val distinct = AudiencePlaybackSource("camera", "video.m3u8", "audio.m3u8")
        val shared = AudiencePlaybackSource("screen", "muxed.m3u8", "muxed.m3u8")

        assertThat(distinct.mediaUrls).containsExactly("video.m3u8", "audio.m3u8").inOrder()
        assertThat(shared.mediaUrls).containsExactly("muxed.m3u8")
    }

    private fun target(generation: Int) = AudiencePlaybackTarget(
        generation = generation,
        sources = listOf(
            AudiencePlaybackSource("camera", "camera-$generation.m3u8", "camera-audio-$generation.m3u8"),
            AudiencePlaybackSource("screen", "screen-$generation.m3u8", null),
        ),
    )
}
