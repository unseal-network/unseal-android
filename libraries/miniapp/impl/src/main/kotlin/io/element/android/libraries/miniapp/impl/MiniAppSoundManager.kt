/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.miniapp.impl

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File

/**
 * Per-session audio manager for mini-app bridge sound methods.
 *
 * Mirrors iOS `Webview+AV.swift`:
 * - [preload]  → `soundPreload` — downloads and prepares a MediaPlayer.
 * - [play]     → `sound.play`   — starts playback (with optional loop).
 * - [pause]    → `sound.pause`  — pauses playback.
 * - [stop]     → `sound.stop`   — stops and resets to beginning.
 * - [clear]    → `sound.clear`  — releases all players.
 * - [setVolume]→ `sound.volume` — sets left/right channel volume.
 *
 * Each sound is keyed by a caller-supplied [soundId].  Players are cached in
 * memory for the lifetime of the session; call [release] when the mini-app is
 * closed to free MediaPlayer resources.
 */
internal class MiniAppSoundManager(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    /** soundId → prepared MediaPlayer */
    private val players = mutableMapOf<String, MediaPlayer>()

    /**
     * Download [urlStr] to the sound cache if necessary, then prepare a
     * [MediaPlayer] for [soundId].  Must be called from a coroutine context
     * (network I/O happens on [Dispatchers.IO]).
     */
    suspend fun preload(soundId: String, urlStr: String) {
        if (urlStr.isBlank()) return

        val safeId = soundId.replace("[^a-zA-Z0-9._-]".toRegex(), "_").take(128)
        val cacheDir = File(context.cacheDir, "miniapp/sounds")
        cacheDir.mkdirs()
        val cacheFile = File(cacheDir, safeId)

        // Download only if not yet cached.
        if (!cacheFile.exists()) {
            withContext(Dispatchers.IO) {
                try {
                    val request = Request.Builder().url(urlStr).build()
                    okHttpClient.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            cacheFile.outputStream().use { out ->
                                response.body.byteStream().copyTo(out)
                            }
                        } else {
                            Timber.w("MiniApp: sound preload HTTP %d for %s", response.code, urlStr)
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "MiniApp: sound preload failed for soundId=%s", soundId)
                }
            }
        }

        if (!cacheFile.exists()) return

        withContext(Dispatchers.Main) {
            players[soundId]?.release()
            try {
                players[soundId] = MediaPlayer().apply {
                    setDataSource(cacheFile.absolutePath)
                    prepare()
                }
            } catch (e: Exception) {
                Timber.e(e, "MiniApp: MediaPlayer prepare failed for soundId=%s", soundId)
            }
        }
    }

    /** Start playback for [soundId]. If already playing, seeks to start. */
    fun play(soundId: String, loop: Boolean = false) {
        val player = players[soundId] ?: run {
            Timber.d("MiniApp: soundPlay — no player for soundId=%s", soundId)
            return
        }
        player.isLooping = loop
        if (player.isPlaying) {
            player.seekTo(0)
        } else {
            runCatching { player.start() }.onFailure {
                Timber.e(it, "MiniApp: soundPlay failed for soundId=%s", soundId)
            }
        }
    }

    /** Pause playback for [soundId]. */
    fun pause(soundId: String) {
        players[soundId]?.takeIf { it.isPlaying }?.pause()
    }

    /** Stop playback for [soundId] and reset to the beginning. */
    fun stop(soundId: String) {
        players[soundId]?.let { mp ->
            if (mp.isPlaying) mp.stop()
            runCatching { mp.prepare() }
        }
    }

    /** Stop and release all players. */
    fun clear() {
        players.values.forEach { runCatching { it.release() } }
        players.clear()
    }

    /**
     * Set playback volume for [soundId].
     * [volume] is clamped to `[0.0, 1.0]`.
     */
    fun setVolume(soundId: String, volume: Float) {
        val v = volume.coerceIn(0f, 1f)
        players[soundId]?.setVolume(v, v)
    }

    /** Release all resources — call when the mini-app session ends. */
    fun release() = clear()
}
