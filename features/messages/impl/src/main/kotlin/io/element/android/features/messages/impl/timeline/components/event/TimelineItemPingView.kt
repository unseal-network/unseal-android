/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemPingContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text

@Composable
fun TimelineItemPingView(
    content: TimelineItemPingContent,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = if (content.isOutgoing) {
        stringResource(R.string.screen_room_ping_sent)
    } else {
        stringResource(R.string.screen_room_ping_received, content.senderDisplayName)
    }
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp)
            .onSizeChanged { size ->
                onContentLayoutChange(
                    ContentAvoidingLayoutData(
                        contentWidth = size.width,
                        contentHeight = size.height,
                        nonOverlappingContentWidth = size.width,
                        nonOverlappingContentHeight = size.height,
                    )
                )
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.NotificationsSolid(),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (content.isOutgoing) {
                ElementTheme.colors.iconAccentPrimary
            } else {
                ElementTheme.colors.iconSecondary
            },
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
    }
}

@Composable
internal fun TimelineItemPingViewPreview() = ElementPreview {
    TimelineItemPingView(
        content = TimelineItemPingContent(
            body = "Ping",
            senderDisplayName = "Alice",
            isOutgoing = false,
        ),
        onContentLayoutChange = {},
    )
}
