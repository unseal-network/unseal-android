/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

/**
 * Verifies the iOS-parity markdown table styling (bordered grid, alternating rows, padded cells,
 * cell text wrapping to fit) rendered by [MarkdownBody] via Markwon's table theme.
 */
@PreviewsDayNight
@Composable
internal fun MarkdownTablePreview() = ElementPreview {
    MarkdownBody(
        modifier = Modifier.padding(12.dp),
        text = """
            ## Overview

            | Time | Speaker | Role | Content |
            |------|---------|------|---------|
            | 15:21:26 | @rayson:topsecret.network | user | The table needs a little more visual hierarchy. |
            | 15:21:27 | @rayson:topsecret.network | user | I added spacing and row contrast. |
            | 15:21:28 | @rayson:topsecret.network | user | The server can notify after the state changes. |

            This meeting mainly discussed delayed message notifications.
        """.trimIndent(),
        renderMode = MarkdownRenderMode.Stable,
        onLinkClick = {},
    )
}
