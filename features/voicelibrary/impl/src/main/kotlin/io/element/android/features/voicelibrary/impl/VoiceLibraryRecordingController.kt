/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import android.Manifest
import android.content.Context
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * A narrow bridge for Voice Library clone recordings.
 *
 * The room composer uses the shared opus/ogg [VoiceRecorder], but that recorder is bound to
 * RoomScope. Voice Library is a settings/session page, so this bridge owns only the native
 * recording lifecycle and returns the existing presenter-owned [VoiceLibraryRecordingSample].
 */
class VoiceLibraryRecordingController(
    private val context: Context,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt: TimeMark? = null

    val isRecording: Boolean
        get() = recorder != null

    @Suppress("DEPRECATION")
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        cancel()
        val file = File.createTempFile("voice-library-", ".m4a", context.cacheDir)
        val mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(44_100)
            setAudioEncodingBitRate(128_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
        outputFile = file
        recorder = mediaRecorder
        startedAt = timeSource.markNow()
    }

    suspend fun stop(displayName: String): VoiceLibraryRecordingSample = withContext(Dispatchers.IO) {
        val mediaRecorder = recorder ?: error("No active recording")
        val file = outputFile ?: error("No recording output file")
        val elapsed = startedAt?.elapsedNow()?.inWholeMilliseconds ?: 0
        try {
            mediaRecorder.stop()
        } finally {
            mediaRecorder.release()
            recorder = null
            outputFile = null
            startedAt = null
        }
        val audioBase64 = Base64.getEncoder().encodeToString(file.readBytes())
        val size = file.length()
        val waveform = VoiceRecordingWaveformExtractor.extract(file)
        VoiceLibraryRecordingSample(
            displayName = displayName,
            audioBase64 = audioBase64,
            filename = "voice.m4a",
            mimeType = "audio/m4a",
            durationMillis = elapsed,
            fileSizeBytes = size,
            localFilePath = file.absolutePath,
            waveform = waveform,
        )
    }

    fun cancel() {
        val mediaRecorder = recorder
        recorder = null
        startedAt = null
        runCatching { mediaRecorder?.stop() }
        runCatching { mediaRecorder?.release() }
        outputFile?.delete()
        outputFile = null
    }

    fun deleteSample(sample: VoiceLibraryRecordingSample?) {
        sample?.localFilePath
            ?.let(::File)
            ?.takeIf { it.exists() }
            ?.delete()
    }
}
