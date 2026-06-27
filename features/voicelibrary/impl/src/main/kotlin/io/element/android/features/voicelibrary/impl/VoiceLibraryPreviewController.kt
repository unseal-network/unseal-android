/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

/**
 * Plays a remote voice preview through a single shared [MediaPlayer]. Like iOS, previews are
 * downloaded to a local .mp3 cache before playback so malformed remote content-types do not break
 * media format detection.
 */
internal class VoicePreviewController {
    private var player: MediaPlayer? = null
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    suspend fun play(
        item: VoiceLibraryPreviewItem,
        fileCache: VoicePreviewFileCache,
        noPreviewMessage: String,
        loadPreviewMessage: String,
        onPlaying: (String) -> Unit,
        onStopped: () -> Unit,
        onFailed: (String, String) -> Unit,
    ) {
        if (!item.hasPreview) {
            onFailed(item.id, noPreviewMessage)
            return
        }
        val localFile = runCatching { fileCache.get(item) }
            .onFailure {
                stop()
                onFailed(item.id, loadPreviewMessage)
            }
            .getOrNull() ?: return
        release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setOnPreparedListener { mp ->
                onPlaying(item.id)
                mp.start()
            }
            setOnCompletionListener {
                stop()
                onStopped()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                onFailed(item.id, loadPreviewMessage)
                true
            }
            runCatching {
                setDataSource(localFile.absolutePath)
                prepareAsync()
            }.onFailure {
                stop()
                onFailed(item.id, loadPreviewMessage)
            }
        }
    }

    fun playLocalFile(
        filePath: String,
        previewUnavailableMessage: String,
        onPlaying: () -> Unit,
        onStopped: () -> Unit,
        onFailed: (String) -> Unit,
        onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    ) {
        val localFile = File(filePath)
        if (!localFile.exists() || localFile.length() == 0L) {
            onFailed(previewUnavailableMessage)
            return
        }
        release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setOnPreparedListener { mp ->
                onPlaying()
                mp.start()
                startProgressUpdates(mp, onProgress)
            }
            setOnCompletionListener {
                stop()
                onStopped()
            }
            setOnErrorListener { _, _, _ ->
                stop()
                onFailed(previewUnavailableMessage)
                true
            }
            runCatching {
                setDataSource(localFile.absolutePath)
                prepareAsync()
            }.onFailure {
                stop()
                onFailed(previewUnavailableMessage)
            }
        }
    }

    fun seekToProgress(progress: Float) {
        val currentPlayer = player ?: return
        val duration = currentPlayer.duration.coerceAtLeast(0)
        val target = (duration * progress.coerceIn(0f, 1f)).toInt()
        runCatching { currentPlayer.seekTo(target) }
    }

    fun stop() {
        release()
    }

    private fun release() {
        progressRunnable?.let(progressHandler::removeCallbacks)
        progressRunnable = null
        runCatching { player?.release() }
        player = null
    }

    private fun startProgressUpdates(
        mediaPlayer: MediaPlayer,
        onProgress: (positionMillis: Long, durationMillis: Long) -> Unit,
    ) {
        progressRunnable?.let(progressHandler::removeCallbacks)
        val runnable = object : Runnable {
            override fun run() {
                if (player !== mediaPlayer) return
                val position = runCatching { mediaPlayer.currentPosition.toLong() }.getOrDefault(0L)
                val duration = runCatching { mediaPlayer.duration.toLong() }.getOrDefault(0L)
                onProgress(position, duration)
                progressHandler.postDelayed(this, 250L)
            }
        }
        progressRunnable = runnable
        progressHandler.post(runnable)
    }
}

internal class VoicePreviewFileCache(
    private val directory: File,
    private val fetchBytes: suspend (String) -> ByteArray = { url ->
        withContext(Dispatchers.IO) { URL(url).readBytes() }
    },
) {
    suspend fun get(item: VoiceLibraryPreviewItem): File {
        val remoteUrl = item.previewUrl?.takeIf { it.isNotBlank() }
            ?: error("Preview URL is missing")
        return withContext(Dispatchers.IO) {
            directory.mkdirs()
            val file = File(directory, "${item.id.hashCode().toUInt()}.mp3")
            if (!file.exists() || file.length() == 0L) {
                file.writeBytes(fetchBytes(remoteUrl))
            }
            file
        }
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
