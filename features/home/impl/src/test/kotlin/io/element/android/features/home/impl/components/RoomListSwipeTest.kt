/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoomListSwipeTest {
    // 3 rooms, each 100px tall, no leading banner: indices 0,1,2 at 0,100,200.
    private val noBanner = listOf(
        RoomRowBounds(index = 0, top = 0, height = 100),
        RoomRowBounds(index = 1, top = 100, height = 100),
        RoomRowBounds(index = 2, top = 200, height = 100),
    )

    // Same 3 rooms but with one leading banner item (index 0), so rooms are absolute indices 1,2,3.
    private val withBanner = listOf(
        RoomRowBounds(index = 0, top = 0, height = 60),
        RoomRowBounds(index = 1, top = 60, height = 100),
        RoomRowBounds(index = 2, top = 160, height = 100),
        RoomRowBounds(index = 3, top = 260, height = 100),
    )

    @Test
    fun `y inside a room maps to that room index - no banner`() {
        assertThat(resolveSwipeTargetIndex(y = 0, visibleItems = noBanner, headerItemCount = 0, summaryCount = 3)).isEqualTo(0)
        assertThat(resolveSwipeTargetIndex(y = 150, visibleItems = noBanner, headerItemCount = 0, summaryCount = 3)).isEqualTo(1)
        assertThat(resolveSwipeTargetIndex(y = 299, visibleItems = noBanner, headerItemCount = 0, summaryCount = 3)).isEqualTo(2)
    }

    @Test
    fun `y inside the leading banner maps to null`() {
        // y=30 is inside the banner item (index 0, 0..60).
        assertThat(resolveSwipeTargetIndex(y = 30, visibleItems = withBanner, headerItemCount = 1, summaryCount = 3)).isNull()
    }

    @Test
    fun `y inside a room maps to the correct room index - with banner offsets by header count`() {
        // y=100 is inside absolute item index 1, which is room 0 once the banner is accounted for.
        assertThat(resolveSwipeTargetIndex(y = 100, visibleItems = withBanner, headerItemCount = 1, summaryCount = 3)).isEqualTo(0)
        assertThat(resolveSwipeTargetIndex(y = 300, visibleItems = withBanner, headerItemCount = 1, summaryCount = 3)).isEqualTo(2)
    }

    @Test
    fun `y in a gap between visible items maps to null`() {
        val gapped = listOf(
            RoomRowBounds(index = 0, top = 0, height = 100),
            RoomRowBounds(index = 1, top = 120, height = 100), // 20px gap at 100..120
        )
        assertThat(resolveSwipeTargetIndex(y = 110, visibleItems = gapped, headerItemCount = 0, summaryCount = 2)).isNull()
    }

    @Test
    fun `y past the last item or negative maps to null`() {
        assertThat(resolveSwipeTargetIndex(y = 1000, visibleItems = noBanner, headerItemCount = 0, summaryCount = 3)).isNull()
        assertThat(resolveSwipeTargetIndex(y = -5, visibleItems = noBanner, headerItemCount = 0, summaryCount = 3)).isNull()
    }

    @Test
    fun `room index beyond summaries is rejected`() {
        // An item exists at the hit position but its mapped room index is out of summaries range.
        assertThat(resolveSwipeTargetIndex(y = 250, visibleItems = noBanner, headerItemCount = 0, summaryCount = 2)).isNull()
    }

    @Test
    fun `empty visible items maps to null`() {
        assertThat(resolveSwipeTargetIndex(y = 50, visibleItems = emptyList(), headerItemCount = 0, summaryCount = 3)).isNull()
    }

    @Test
    fun `header item count is 1 when any banner is visible`() {
        assertThat(roomListHeaderItemCount(securityBannerVisible = true, fullScreenIntentBannerVisible = false, batteryOptimizationBannerVisible = false, newNotificationSoundBannerVisible = false)).isEqualTo(1)
        assertThat(roomListHeaderItemCount(securityBannerVisible = false, fullScreenIntentBannerVisible = true, batteryOptimizationBannerVisible = false, newNotificationSoundBannerVisible = false)).isEqualTo(1)
        assertThat(roomListHeaderItemCount(securityBannerVisible = false, fullScreenIntentBannerVisible = false, batteryOptimizationBannerVisible = true, newNotificationSoundBannerVisible = false)).isEqualTo(1)
        assertThat(roomListHeaderItemCount(securityBannerVisible = false, fullScreenIntentBannerVisible = false, batteryOptimizationBannerVisible = false, newNotificationSoundBannerVisible = true)).isEqualTo(1)
    }

    @Test
    fun `header item count is 0 when no banner is visible`() {
        assertThat(roomListHeaderItemCount(securityBannerVisible = false, fullScreenIntentBannerVisible = false, batteryOptimizationBannerVisible = false, newNotificationSoundBannerVisible = false)).isEqualTo(0)
    }
}
