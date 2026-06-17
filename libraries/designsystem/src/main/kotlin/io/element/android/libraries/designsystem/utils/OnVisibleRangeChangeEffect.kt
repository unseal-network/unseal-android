/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

@Composable
fun OnVisibleRangeChangeEffect(
    lazyListState: LazyListState,
    notifyWhileScrolling: Boolean = true,
    onChange: (IntRange) -> Unit,
) {
    val onChangeUpdated by rememberUpdatedState(onChange)
    LaunchedEffect(lazyListState) {
        val visibleRangeFlow = if (notifyWhileScrolling) {
            snapshotFlow { lazyListState.visibleRange() }
        } else {
            snapshotFlow { lazyListState.isScrollInProgress }
                .distinctUntilChanged()
                .filter { isScrollInProgress -> !isScrollInProgress }
                .map { lazyListState.visibleRange() }
        }

        visibleRangeFlow
            .distinctUntilChanged()
            .collectLatest { visibleRange -> onChangeUpdated(visibleRange) }
    }
}

private fun LazyListState.visibleRange(): IntRange {
    val visibleItemsInfo = layoutInfo.visibleItemsInfo
    val firstItemIndex = visibleItemsInfo.firstOrNull()?.index ?: 0
    val size = visibleItemsInfo.size
    return firstItemIndex until firstItemIndex + size
}
