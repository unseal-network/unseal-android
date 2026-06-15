/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.max

internal object VoiceRecordingWaveformNormalizer {
    private const val MIN_VISIBLE_AMPLITUDE = 0.08f

    fun normalize(peaks: FloatArray): ImmutableList<Float> {
        val maxPeak = peaks.maxOrNull()?.takeIf { it > 0f } ?: return persistentListOf()
        return peaks.map { peak ->
            if (peak <= 0f) {
                0f
            } else {
                max(MIN_VISIBLE_AMPLITUDE, (peak / maxPeak).coerceIn(0f, 1f))
            }
        }.toImmutableList()
    }
}
