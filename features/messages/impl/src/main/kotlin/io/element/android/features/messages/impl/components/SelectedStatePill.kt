/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Shared iOS-style selected/active state container.
 *
 * Keep selected backgrounds, borders, ripple clipping, and hit target on the same shaped layer.
 * This avoids the rectangular selected blocks that happen when callers use a naked
 * `Modifier.background(color)` or clip a different child than the painted background.
 */
@Composable
internal fun SelectedStatePill(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selectedColor: Color = MaterialTheme.colorScheme.surface,
    unselectedColor: Color = Color.Transparent,
    selectedContentColor: Color = MaterialTheme.colorScheme.onSurface,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    selectedBorder: BorderStroke? = null,
    unselectedBorder: BorderStroke? = null,
    shape: Shape = RoundedCornerShape(50),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = shape,
        color = if (selected) selectedColor else unselectedColor,
        contentColor = if (selected) selectedContentColor else unselectedContentColor,
        border = if (selected) selectedBorder else unselectedBorder,
    ) {
        content()
    }
}
