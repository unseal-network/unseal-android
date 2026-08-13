/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.ui

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.ui.shouldRequestNativeCallAudioFocus
import io.element.android.features.call.impl.ui.shouldStartCallForegroundService
import io.element.android.features.call.impl.ui.shouldStopCallForegroundService
import org.junit.Test

class ElementCallAudioFocusPolicyTest {
    @Test
    fun `participant call keeps native in-call audio focus`() {
        assertThat(shouldRequestNativeCallAudioFocus(isAudience = false)).isTrue()
    }

    @Test
    fun `audience playback leaves media focus to Chromium`() {
        assertThat(shouldRequestNativeCallAudioFocus(isAudience = true)).isFalse()
    }

    @Test
    fun `audience playback does not start a foreground service`() {
        assertThat(shouldStartCallForegroundService(isAudience = true)).isFalse()
    }

    @Test
    fun `participant call starts the microphone foreground service`() {
        assertThat(shouldStartCallForegroundService(isAudience = false)).isTrue()
    }

    @Test
    fun `active audience call stops any participant foreground service`() {
        assertThat(shouldStopCallForegroundService(isActive = true, isAudience = true)).isTrue()
    }
}
