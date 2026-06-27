/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimelineItemVoiceViewLayoutTest {
    @Test
    fun `layout uses an inner playback capsule smaller than the outer bubble`() {
        assertThat(TimelineItemVoiceLayoutSpec.outerHeight.value).isEqualTo(64f)
        assertThat(TimelineItemVoiceLayoutSpec.innerHeight.value).isEqualTo(48f)
        assertThat(TimelineItemVoiceLayoutSpec.innerHeight).isLessThan(TimelineItemVoiceLayoutSpec.outerHeight)
        assertThat(TimelineItemVoiceLayoutSpec.outerCornerRadius.value).isEqualTo(18f)
        assertThat(TimelineItemVoiceLayoutSpec.innerCornerRadius.value).isEqualTo(24f)
    }

    @Test
    fun `layout keeps the voice message compact enough for timeline metadata`() {
        assertThat(TimelineItemVoiceLayoutSpec.minWidth.value).isEqualTo(260f)
        assertThat(TimelineItemVoiceLayoutSpec.maxWidth.value).isEqualTo(360f)
        assertThat(TimelineItemVoiceLayoutSpec.trailingPadding.value).isAtLeast(8f)
    }
}
