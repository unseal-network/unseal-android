/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

private const val WAVEFORM_BUCKET_COUNT = 96
private const val CODEC_TIMEOUT_US = 10_000L

internal object VoiceRecordingWaveformExtractor {
    fun extract(file: File, bucketCount: Int = WAVEFORM_BUCKET_COUNT): ImmutableList<Float> {
        if (!file.exists() || file.length() <= 0 || bucketCount <= 0) return persistentListOf()
        return runCatching {
            decode(file = file, bucketCount = bucketCount)
        }.getOrDefault(persistentListOf())
    }

    private fun decode(file: File, bucketCount: Int): ImmutableList<Float> {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            val trackIndex = (0 until extractor.trackCount).firstOrNull { index ->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return persistentListOf()
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: return persistentListOf()
            val durationUs = inputFormat.safeLong(MediaFormat.KEY_DURATION).takeIf { it > 0L }
            extractor.selectTrack(trackIndex)

            val codec = MediaCodec.createDecoderByType(mime)
            val peaks = FloatArray(bucketCount)
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var outputFormat = inputFormat
                var inputDone = false
                var outputDone = false
                var sequentialOutputIndex = 0

                while (!outputDone) {
                    if (!inputDone) {
                        val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                        if (inputIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIndex)
                            inputBuffer?.clear()
                            val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    when (val outputIndex = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        else -> if (outputIndex >= 0) {
                            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                outputDone = true
                            }
                            if (bufferInfo.size > 0) {
                                val bucketIndex = durationUs
                                    ?.let { ((bufferInfo.presentationTimeUs.toDouble() / it.toDouble()) * bucketCount).toInt() }
                                    ?: sequentialOutputIndex++
                                codec.getOutputBuffer(outputIndex)?.let { buffer ->
                                    peaks[bucketIndex.coerceIn(0, bucketCount - 1)] = max(
                                        peaks[bucketIndex.coerceIn(0, bucketCount - 1)],
                                        buffer.peakAmplitude(bufferInfo, outputFormat),
                                    )
                                }
                            }
                            codec.releaseOutputBuffer(outputIndex, false)
                        }
                    }
                }
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            VoiceRecordingWaveformNormalizer.normalize(peaks)
        } finally {
            extractor.release()
        }
    }
}

private fun ByteBuffer.peakAmplitude(info: MediaCodec.BufferInfo, format: MediaFormat): Float {
    val buffer = duplicate().order(ByteOrder.LITTLE_ENDIAN)
    buffer.position(info.offset)
    buffer.limit(info.offset + info.size)
    return when (format.safeInt(MediaFormat.KEY_PCM_ENCODING).takeIf { it != 0 } ?: AudioFormat.ENCODING_PCM_16BIT) {
        AudioFormat.ENCODING_PCM_FLOAT -> buffer.floatPeak()
        AudioFormat.ENCODING_PCM_8BIT -> buffer.bytePeak()
        else -> buffer.shortPeak()
    }
}

private fun ByteBuffer.shortPeak(): Float {
    var peak = 0f
    while (remaining() >= Short.SIZE_BYTES) {
        peak = max(peak, abs(short.toInt()).toFloat() / Short.MAX_VALUE)
    }
    return peak.coerceIn(0f, 1f)
}

private fun ByteBuffer.floatPeak(): Float {
    var peak = 0f
    while (remaining() >= Float.SIZE_BYTES) {
        peak = max(peak, abs(float).coerceIn(0f, 1f))
    }
    return peak.coerceIn(0f, 1f)
}

private fun ByteBuffer.bytePeak(): Float {
    var peak = 0f
    while (hasRemaining()) {
        val unsignedSample = get().toInt() and 0xFF
        peak = max(peak, abs(unsignedSample - 128).toFloat() / 128f)
    }
    return peak.coerceIn(0f, 1f)
}

private fun MediaFormat.safeLong(key: String): Long = runCatching { getLong(key) }.getOrDefault(0L)
private fun MediaFormat.safeInt(key: String): Int = runCatching { getInteger(key) }.getOrDefault(0)
