/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVoiceContentProvider
import io.element.android.libraries.voiceplayer.api.VoiceMessageStateProvider
import io.element.android.tests.testutils.setSafeContent
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
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
    fun `layout keeps the voice message within the fixed timeline content column`() {
        assertThat(TimelineItemVoiceLayoutSpec.maxWidth.value).isEqualTo(360f)
        assertThat(TimelineItemVoiceLayoutSpec.trailingPadding.value).isAtLeast(8f)
    }

    @Test
    fun `voice message respects compact timeline content column width`() = runAndroidComposeUiTest<ComponentActivity> {
        val state = VoiceMessageStateProvider().values.first()
        val content = TimelineItemVoiceContentProvider().values.first()

        setSafeContent {
            Box(modifier = Modifier.width(COMPACT_CONTENT_COLUMN_WIDTH)) {
                TimelineItemVoiceView(
                    state = state,
                    content = content,
                    modifier = Modifier.testTag(VOICE_MESSAGE_TAG),
                )
            }
        }

        onNodeWithTag(VOICE_MESSAGE_TAG).assertWidthIsEqualTo(COMPACT_CONTENT_COLUMN_WIDTH)
    }

    private companion object {
        val COMPACT_CONTENT_COLUMN_WIDTH = 246.dp
        const val VOICE_MESSAGE_TAG = "voice-message"
    }
}
