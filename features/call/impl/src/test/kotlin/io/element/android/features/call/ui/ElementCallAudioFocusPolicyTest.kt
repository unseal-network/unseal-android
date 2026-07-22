/*
 * Copyright (c) 2026 Unseal
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.call.ui

import com.google.common.truth.Truth.assertThat
import io.element.android.features.call.impl.ui.shouldRequestNativeCallAudioFocus
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
}
