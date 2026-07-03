/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.aLiveLocationMode
import io.element.android.features.messages.impl.timeline.model.event.aStaticLocationMode
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemLocationContent
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.setSafeContent
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TimelineItemLocationViewTest {
    @Test
    fun `clicking static location card invokes content click`() = runAndroidComposeUiTest<ComponentActivity> {
        var clicks = 0

        setSafeContent {
            TimelineItemLocationView(
                content = aTimelineItemLocationContent(mode = aStaticLocationMode()),
                onStopLiveLocationClick = {},
                onClick = { clicks++ },
                modifier = Modifier.testTag(LOCATION_CARD_TAG),
            )
        }

        onNodeWithTag(LOCATION_CARD_TAG).performClick()

        assertThat(clicks).isEqualTo(1)
    }

    @Test
    fun `clicking live location stop does not invoke content click`() = runAndroidComposeUiTest<ComponentActivity> {
        var contentClicks = 0
        var stopClicks = 0

        setSafeContent {
            TimelineItemLocationView(
                content = aTimelineItemLocationContent(
                    mode = aLiveLocationMode(
                        isActive = true,
                        isOwnUser = true,
                        endTimestamp = Long.MAX_VALUE,
                    )
                ),
                onStopLiveLocationClick = { stopClicks++ },
                onClick = { contentClicks++ },
                modifier = Modifier.testTag(LOCATION_CARD_TAG),
            )
        }

        onNodeWithContentDescription(activity!!.getString(CommonStrings.action_stop)).performClick()

        assertThat(stopClicks).isEqualTo(1)
        assertThat(contentClicks).isEqualTo(0)
    }

    private companion object {
        const val LOCATION_CARD_TAG = "timeline-location-card"
    }
}
