/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.timeline.components.receipt

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.setSafeContent
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineItemReadReceiptViewTest {
    @Test
    fun `hidden receipts collapse the row while scrolling`() = runAndroidComposeUiTest<ComponentActivity> {
        setSafeContent {
            Box(modifier = Modifier.testTag(ReceiptContainerTag)) {
                TimelineItemReadReceiptView(
                    state = aReadReceiptViewState(
                        receipts = List(2) { aReadReceiptData(it) },
                    ),
                    renderReadReceipts = false,
                    onReadReceiptsClick = {},
                )
            }
        }

        val actualHeightPx = onNodeWithTag(ReceiptContainerTag)
            .fetchSemanticsNode()
            .boundsInRoot
            .height

        assertThat(actualHeightPx).isEqualTo(0f)
    }

    private companion object {
        const val ReceiptContainerTag = "receipt-container"
    }
}
