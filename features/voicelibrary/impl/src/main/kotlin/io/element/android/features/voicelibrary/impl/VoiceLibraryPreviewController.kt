/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Streams a remote voice-preview URL with a single shared [MediaPlayer], mirroring iOS
 * `togglePreview`/`stopPreview`. Tapping a playing/loading row stops it; tapping another switches.
 */
internal class VoicePreviewController {
    var playingUrl by mutableStateOf<String?>(null)
        private set
    var loadingUrl by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null

    fun toggle(url: String) {
        if (url == playingUrl || url == loadingUrl) {
            stop()
            return
        }
        release()
        loadingUrl = url
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setOnPreparedListener { mp ->
                if (loadingUrl == url) {
                    loadingUrl = null
                    playingUrl = url
                    mp.start()
                } else {
                    mp.release()
                }
            }
            setOnCompletionListener { stop() }
            setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            runCatching {
                setDataSource(url)
                prepareAsync()
            }.onFailure { stop() }
        }
    }

    fun stop() {
        release()
        playingUrl = null
        loadingUrl = null
    }

    private fun release() {
        runCatching { player?.release() }
        player = null
    }
}

internal val LocalVoicePreviewController = staticCompositionLocalOf<VoicePreviewController?> { null }

@Composable
internal fun rememberVoicePreviewController(): VoicePreviewController {
    val controller = remember { VoicePreviewController() }
    DisposableEffect(Unit) {
        onDispose { controller.stop() }
    }
    return controller
}
