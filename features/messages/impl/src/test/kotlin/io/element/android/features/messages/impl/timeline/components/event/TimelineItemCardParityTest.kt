/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.timeline.components.event

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGameContent
import io.element.android.tests.testutils.setSafeContent
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineItemCardParityTest {
    @Test
    fun `game card uses the available timeline width`() = runAndroidComposeUiTest<ComponentActivity> {
        setSafeContent {
            Box(modifier = Modifier.requiredWidth(360.dp)) {
                TimelineItemGameView(
                    content = aGameContent(),
                    eventSink = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(GameCardTag),
                )
            }
        }

        val cardWidthPx = onNodeWithTag(GameCardTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .width
        val minExpectedWidthPx = with(density) { 340.dp.toPx() }

        assertThat(cardWidthPx).isAtLeast(minExpectedWidthPx)
    }

    @Test
    fun `app store links use a branded fallback instead of the raw host`() {
        val metadata = LinkPreviewMetadata.fallback("https://apps.apple.com/us/app/unseal/id123456")

        assertThat(metadata.title).isEqualTo("App Store")
        assertThat(metadata.description).isEqualTo("apps.apple.com")
        assertThat(metadata.style).isNotEqualTo(LinkPreviewStyle.Default)
    }

    private fun aGameContent() = TimelineItemGameContent(
        gameName = "Wolf-New",
        gameBrief = "Wolf-NewWolf-NewWolf-New",
        resolvedIconUrl = null,
        homeserverHost = null,
        gameRoomId = "game-room",
        gameId = 1,
        remoteUrl = null,
        creatorUserId = "@alice:example.com",
        fallbackBody = "Start game",
    )

    private companion object {
        const val GameCardTag = "game-card"
    }
}
