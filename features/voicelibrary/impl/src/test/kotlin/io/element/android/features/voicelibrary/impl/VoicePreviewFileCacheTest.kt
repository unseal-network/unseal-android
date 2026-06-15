/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.nio.file.Files

class VoicePreviewFileCacheTest {
    @Test
    fun `get - downloads preview once and reuses cached mp3 file`() = runTest {
        val directory = Files.createTempDirectory("voice-preview-cache").toFile()
        var fetchCalls = 0
        val cache = VoicePreviewFileCache(directory) { url ->
            fetchCalls++
            "audio:$url".encodeToByteArray()
        }
        val item = VoiceLibraryPreviewItem(
            id = "catalog:elevenlabs:voice-1",
            title = "Aria",
            previewUrl = "https://example.com/aria",
        )

        val first = cache.get(item)
        val second = cache.get(item)

        assertThat(first).isEqualTo(second)
        assertThat(first.name).endsWith(".mp3")
        assertThat(first.readText()).isEqualTo("audio:https://example.com/aria")
        assertThat(fetchCalls).isEqualTo(1)
    }

    @Test
    fun `get - redownloads when cached file is empty`() = runTest {
        val directory = Files.createTempDirectory("voice-preview-cache-empty").toFile()
        var fetchCalls = 0
        val cache = VoicePreviewFileCache(directory) {
            fetchCalls++
            "audio".encodeToByteArray()
        }
        val item = VoiceLibraryPreviewItem(
            id = "profile:voice-1",
            title = "Aria",
            previewUrl = "https://example.com/aria",
        )

        val first = cache.get(item)
        first.writeBytes(ByteArray(0))
        val second = cache.get(item)

        assertThat(first).isEqualTo(second)
        assertThat(second.length()).isGreaterThan(0)
        assertThat(fetchCalls).isEqualTo(2)
    }
}
