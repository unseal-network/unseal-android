/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

/*
 * Pure (Compose-free) helpers for the list-level room swipe gesture, so the hit-test — the part most
 * likely to be off-by-one across banners — is unit-testable without a UI. See
 * docs/perf/room-list-swipe-refactor.md.
 */

/** Vertical bounds of one visible LazyColumn item, extracted from `LazyListLayoutInfo.visibleItemsInfo`. */
internal data class RoomRowBounds(
    /** Absolute item index in the LazyColumn (includes any leading banner items). */
    val index: Int,
    /** Top of the item in viewport pixels (already accounts for contentPadding + scroll). */
    val top: Int,
    /** Item height in pixels. */
    val height: Int,
)

/**
 * Maps a vertical touch position to the index into `state.summaries` of the room under it, or null
 * when the touch is on a leading banner, a gap, or outside the rendered rooms.
 *
 * @param y touch Y in the same coordinate space as [RoomRowBounds.top] (viewport pixels).
 * @param headerItemCount number of leading non-room items (see [roomListHeaderItemCount]).
 * @param summaryCount size of `state.summaries`.
 */
internal fun resolveSwipeTargetIndex(
    y: Int,
    visibleItems: List<RoomRowBounds>,
    headerItemCount: Int,
    summaryCount: Int,
): Int? {
    val item = visibleItems.firstOrNull { y >= it.top && y < it.top + it.height } ?: return null
    val roomIndex = item.index - headerItemCount
    return roomIndex.takeIf { it in 0 until summaryCount }
}

/**
 * Number of leading non-room items in the room-list LazyColumn. The banners render with
 * top-to-bottom precedence and at most one shows at a time (see RoomsViewList), so this is 0 or 1.
 * Kept in lockstep with the banner `item {}` blocks so the hit-test index mapping cannot drift.
 */
internal fun roomListHeaderItemCount(
    securityBannerVisible: Boolean,
    fullScreenIntentBannerVisible: Boolean,
    batteryOptimizationBannerVisible: Boolean,
    newNotificationSoundBannerVisible: Boolean,
): Int = if (
    securityBannerVisible ||
    fullScreenIntentBannerVisible ||
    batteryOptimizationBannerVisible ||
    newNotificationSoundBannerVisible
) {
    1
} else {
    0
}
