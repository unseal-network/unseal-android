/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme

@Composable
internal fun TimelineCardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    if (onClick == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = TimelineCardShape,
            color = ElementTheme.colors.bgSubtleSecondary,
            border = BorderStroke(1.dp, ElementTheme.colors.borderInteractiveSecondary),
            content = content,
        )
    } else {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = TimelineCardShape,
            color = ElementTheme.colors.bgSubtleSecondary,
            border = BorderStroke(1.dp, ElementTheme.colors.borderInteractiveSecondary),
            content = content,
        )
    }
}

internal val TimelineCardShape = RoundedCornerShape(12.dp)
