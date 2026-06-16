/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAiContent
import io.element.android.wysiwyg.link.Link

@Composable
internal fun AgentStreamParityPreview(
    content: TimelineItemAiContent,
    modifier: Modifier = Modifier,
) {
    TimelineItemAiView(
        content = content,
        onLinkClick = { _: Link -> },
        onLinkLongClick = { _: Link -> },
        modifier = modifier.width(360.dp),
    )
}
