/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VoiceRecordingWaveformNormalizerTest {
    @Test
    fun `normalize returns empty waveform for silent input`() {
        assertThat(VoiceRecordingWaveformNormalizer.normalize(floatArrayOf(0f, 0f, 0f))).isEmpty()
    }

    @Test
    fun `normalize scales peaks against the loudest bucket`() {
        val waveform = VoiceRecordingWaveformNormalizer.normalize(floatArrayOf(0.1f, 0.5f, 1.0f))

        assertThat(waveform).containsExactly(0.1f, 0.5f, 1.0f).inOrder()
    }

    @Test
    fun `normalize keeps quiet non-zero buckets visible`() {
        val waveform = VoiceRecordingWaveformNormalizer.normalize(floatArrayOf(0.001f, 1.0f))

        assertThat(waveform).containsExactly(0.08f, 1.0f).inOrder()
    }
}
