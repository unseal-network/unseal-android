/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.messages.reply

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.core.extensions.toSafeLength
import io.element.android.libraries.designsystem.atomic.atoms.PlaceholderAtom
import io.element.android.libraries.designsystem.icons.CompoundDrawables
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import io.element.android.libraries.matrix.ui.components.AttachmentThumbnail
import io.element.android.libraries.matrix.ui.messages.sender.SenderName
import io.element.android.libraries.matrix.ui.messages.sender.SenderNameMode
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * https://www.figma.com/design/G1xy0HDZKJf5TCRFmKb5d5/Compound-Android-Components?node-id=2019-6286
 */
enum class InReplyToViewStyle {
    /** Composer / default: optional media thumbnail + coloured sender name + preview. */
    Default,

    /** Timeline: left accent bar, an "In reply to" label + sender pill, then a 2-line preview.
     *  Mirrors unseal-ios `TimelineReplyView.timelineStyle`. */
    Timeline,
}

@Composable
fun InReplyToView(
    inReplyTo: InReplyToDetails,
    hideImage: Boolean,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    style: InReplyToViewStyle = InReplyToViewStyle.Default,
) {
    when (style) {
        InReplyToViewStyle.Timeline -> TimelineReplyContent(
            inReplyTo = inReplyTo,
            hideImage = hideImage,
            maxLines = maxLines,
            modifier = modifier,
        )
        InReplyToViewStyle.Default -> when (inReplyTo) {
            is InReplyToDetails.Ready -> {
                ReplyToReadyContent(
                    senderId = inReplyTo.senderId,
                    senderProfile = inReplyTo.senderProfile,
                    metadata = inReplyTo.metadata(hideImage),
                    maxLines = maxLines,
                    modifier = modifier,
                )
            }
            is InReplyToDetails.Error ->
                ReplyToErrorContent(data = inReplyTo, maxLines = maxLines, modifier = modifier)
            is InReplyToDetails.Loading ->
                ReplyToLoadingContent(modifier = modifier)
        }
    }
}

/**
 * iOS-parity timeline reply preview: a thin capsule accent bar on the leading edge, an
 * "In reply to" label next to the sender name in a subtle pill, and a two-line content preview.
 */
@Composable
private fun TimelineReplyContent(
    inReplyTo: InReplyToDetails,
    hideImage: Boolean,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    // The leading accent bar is painted via drawBehind using the actual measured height, so we avoid
    // IntrinsicSize.Min here — that would add an extra measurement pass to every reply and make the
    // timeline janky while scrolling.
    val accentColor = ElementTheme.colors.borderAccentSubtle
    Box(
        modifier = modifier
            .drawBehind {
                val barWidth = 3.dp.toPx()
                val vInset = 2.dp.toPx()
                drawRoundRect(
                    color = accentColor,
                    topLeft = Offset(0f, vInset),
                    size = Size(barWidth, (size.height - vInset * 2).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
            .padding(start = 11.dp),
    ) {
        when (inReplyTo) {
            is InReplyToDetails.Ready -> {
                val a11yInReplyToText = stringResource(
                    CommonStrings.common_in_reply_to,
                    inReplyTo.senderProfile.getDisambiguatedDisplayName(inReplyTo.senderId),
                )
                Column(
                    modifier = Modifier.semantics(mergeDescendants = false) { isTraversalGroup = true },
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(CommonStrings.action_reply),
                            style = ElementTheme.typography.fontBodyXsRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                        Text(
                            text = inReplyTo.senderProfile.getDisambiguatedDisplayName(inReplyTo.senderId),
                            style = ElementTheme.typography.fontBodyXsMedium,
                            color = ElementTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ElementTheme.colors.bgSubtleSecondary)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                .semantics { contentDescription = a11yInReplyToText },
                        )
                    }
                    ReplyToContentText(metadata = inReplyTo.metadata(hideImage), maxLines = maxLines)
                }
            }
            is InReplyToDetails.Error ->
                Text(
                    text = inReplyTo.message,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textCriticalPrimary,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis,
                )
            is InReplyToDetails.Loading ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlaceholderAtom(width = 80.dp, height = 12.dp)
                    PlaceholderAtom(width = 140.dp, height = 14.dp)
                }
        }
    }
}

@Composable
private fun ReplyToReadyContent(
    senderId: UserId,
    senderProfile: ProfileDetails,
    metadata: InReplyToMetadata?,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val paddings = if (metadata is InReplyToMetadata.Thumbnail) {
        PaddingValues(end = 8.dp)
    } else {
        PaddingValues(start = 8.dp, end = 8.dp)
    }
    Row(
        modifier
            .background(ElementTheme.colors.bgCanvasDefault)
            .padding(paddings)
    ) {
        if (metadata is InReplyToMetadata.Thumbnail) {
            AttachmentThumbnail(
                info = metadata.attachmentThumbnailInfo,
                backgroundColor = ElementTheme.colors.bgSubtlePrimary,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        val a11InReplyToText = stringResource(CommonStrings.common_in_reply_to, senderProfile.getDisambiguatedDisplayName(senderId))
        Column(
            modifier = Modifier.semantics(mergeDescendants = false) { isTraversalGroup = true },
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            SenderName(
                senderId = senderId,
                senderProfile = senderProfile,
                senderNameMode = SenderNameMode.Reply,
                modifier = Modifier.semantics {
                    contentDescription = a11InReplyToText
                    isTraversalGroup = true
                    traversalIndex = 1f
                },
            )
            ReplyToContentText(metadata, maxLines)
        }
    }
}

@Composable
private fun ReplyToLoadingContent(
    modifier: Modifier = Modifier,
) {
    val paddings = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    Row(
        modifier
            .background(ElementTheme.colors.bgCanvasDefault)
            .padding(paddings)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PlaceholderAtom(width = 80.dp, height = 12.dp)
            PlaceholderAtom(width = 140.dp, height = 14.dp)
        }
    }
}

@Composable
private fun ReplyToErrorContent(
    data: InReplyToDetails.Error,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val paddings = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    Row(
        modifier
            .background(ElementTheme.colors.bgCanvasDefault)
            .padding(paddings)
    ) {
        Text(
            text = data.message,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textCriticalPrimary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReplyToContentText(
    metadata: InReplyToMetadata?,
    maxLines: Int,
) {
    val text = when (metadata) {
        InReplyToMetadata.Redacted -> stringResource(id = CommonStrings.common_message_removed)
        InReplyToMetadata.UnableToDecrypt -> stringResource(id = CommonStrings.common_waiting_for_decryption_key)
        // Add a limit to the text length to avoid a crash in Compose
        is InReplyToMetadata.Text -> metadata.text.toSafeLength()
        // Add a limit to the text length to avoid a crash in Compose
        is InReplyToMetadata.Thumbnail -> metadata.text.toSafeLength()
        null -> ""
    }
    val iconResourceId = when (metadata) {
        InReplyToMetadata.Redacted -> CompoundDrawables.ic_compound_delete
        InReplyToMetadata.UnableToDecrypt -> CompoundDrawables.ic_compound_time
        else -> null
    }
    val fontStyle = when (metadata) {
        is InReplyToMetadata.Informative -> FontStyle.Italic
        else -> FontStyle.Normal
    }
    Row(
        modifier = Modifier.semantics(mergeDescendants = false) {
            isTraversalGroup = true
            traversalIndex = -1f
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconResourceId != null) {
            Icon(
                resourceId = iconResourceId,
                tint = ElementTheme.colors.iconSecondary,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyMdRegular,
            fontStyle = fontStyle,
            textAlign = TextAlign.Start,
            color = ElementTheme.colors.textSecondary,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun InReplyToViewPreview(@PreviewParameter(provider = InReplyToDetailsProvider::class) inReplyTo: InReplyToDetails) = ElementPreview {
    InReplyToView(
        inReplyTo = inReplyTo,
        hideImage = false,
    )
}
