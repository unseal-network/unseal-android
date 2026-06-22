/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.text.SpannedString
import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayout
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContentProvider
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemTextContent
import io.element.android.features.messages.impl.utils.containsOnlyEmojis
import io.element.android.libraries.androidutils.text.LinkifyHelper
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.textcomposer.ElementRichTextEditorStyle
import io.element.android.libraries.textcomposer.mentions.LocalMentionSpanUpdater
import io.element.android.wysiwyg.compose.EditorStyledText
import io.element.android.wysiwyg.link.Link

val LocalTimelineTextLayoutMeasurementEnabled = compositionLocalOf { true }

@Composable
fun TimelineItemTextView(
    content: TimelineItemTextBasedContent,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit = {},
) {
    if (content.shouldRenderPlainBodyAsMarkdown()) {
        Box(
            modifier
                .fillMaxWidth()
                .onSizeChanged { size ->
                    onContentLayoutChange(
                        ContentAvoidingLayoutData(
                            contentWidth = size.width,
                            contentHeight = size.height,
                            nonOverlappingContentWidth = size.width,
                            nonOverlappingContentHeight = size.height,
                        )
                    )
                }
                .semantics { contentDescription = content.plainText }
        ) {
            MarkdownBody(
                text = content.body,
                renderMode = MarkdownRenderMode.Stable,
                onLinkClick = onLinkClick,
                onLongClick = onLongClick,
                modifier = Modifier,
            )
        }
        return
    }

    val emojiOnly = content.formattedBody.toString() == content.body &&
        content.body.replace(" ", "").containsOnlyEmojis()
    val textStyle = when {
        emojiOnly -> ElementTheme.typography.fontHeadingXlRegular
        else -> ElementTheme.typography.fontBodyLgRegular
    }
    CompositionLocalProvider(
        LocalContentColor provides ElementTheme.colors.textPrimary,
        LocalTextStyle provides textStyle
    ) {
        val text = getTextWithResolvedMentions(content)
        val measureTextLayout = LocalTimelineTextLayoutMeasurementEnabled.current
        Box(modifier.semantics { contentDescription = content.plainText }) {
            EditorStyledText(
                text = text,
                onLinkClickedListener = onLinkClick,
                onLinkLongClickedListener = onLinkLongClick,
                style = ElementRichTextEditorStyle.textStyle(),
                onTextLayout = if (measureTextLayout) {
                    ContentAvoidingLayout.measureLegacyLastTextLine(onContentLayoutChange = onContentLayoutChange)
                } else {
                    {}
                },
                releaseOnDetach = false,
            )
        }
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun getTextWithResolvedMentions(content: TimelineItemTextBasedContent): CharSequence {
    val mentionSpanUpdater = LocalMentionSpanUpdater.current
    val bodyWithResolvedMentions = mentionSpanUpdater.rememberMentionSpans(content.formattedBody)
    return SpannedString.valueOf(bodyWithResolvedMentions)
}

private fun TimelineItemTextBasedContent.shouldRenderPlainBodyAsMarkdown(): Boolean {
    // HTML wins: when the event has a non-empty formatted (HTML) body it already carries the
    // rendered formatting and mention spans (rendered via EditorStyledText below), so we render
    // that. Only when the HTML body is empty do we fall back to the raw Markdown body — and then
    // only if it actually contains Markdown syntax, which is exactly what users expect to render.
    return htmlBody.isNullOrBlank() && body.hasMarkdownSyntax()
}

private fun String.hasMarkdownSyntax(): Boolean {
    return MARKDOWN_SYNTAX_PATTERNS.any { it.containsMatchIn(this) }
}

private val MARKDOWN_SYNTAX_PATTERNS = listOf(
    Regex("""(?m)^#{1,6}\s+\S"""),
    Regex("""(?m)^\s{0,3}([-*+]|\d+\.)\s+\S"""),
    Regex("""(?m)^\s{0,3}>\s+\S"""),
    Regex("""```"""),
    Regex("""`[^`\n]+`"""),
    Regex("""\*\*[^*\n]+\*\*|__[^_\n]+__"""),
    Regex("""(?<!\*)\*[^*\n]+\*(?!\*)"""),
    Regex("""\[[^]\n]+]\([^) \n]+(?:\s+"[^"]*")?\)"""),
    Regex("""!\[[^]\n]*]\([^) \n]+\)"""),
    Regex("""(?m)^\|.+\|\s*$"""),
)

@PreviewsDayNight
@Composable
internal fun TimelineItemTextViewPreview(
    @PreviewParameter(TimelineItemTextBasedContentProvider::class) content: TimelineItemTextBasedContent
) = ElementPreview {
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
        onLongClick = {},
    )
}

@Preview
@Composable
internal fun TimelineItemTextViewWithLinkifiedUrlPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        formattedBody = LinkifyHelper.linkify("The link should end after the first '?' (url: github.com/element-hq/element-x-android/README?)?.")
    )
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
        onLongClick = {},
    )
}

@Preview
@Composable
internal fun TimelineItemTextViewWithLinkifiedUrlAndNestedParenthesisPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        formattedBody = LinkifyHelper.linkify("The link should end after the '(ME)' ((url: github.com/element-hq/element-x-android/READ(ME)))!")
    )
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
        onLongClick = {},
    )
}
