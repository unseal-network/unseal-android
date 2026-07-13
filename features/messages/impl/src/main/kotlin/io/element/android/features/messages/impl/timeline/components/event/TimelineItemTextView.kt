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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.model.event.AgentProfilePreviewDisplayMode
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

@Composable
fun TimelineItemTextView(
    content: TimelineItemTextBasedContent,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    renderLinkPreviews: Boolean = true,
) {
    val htmlTables = remember(content.htmlDocument) {
        content.htmlDocument?.extractHtmlTables().orEmpty()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val shouldRenderStandaloneAgentProfiles = renderLinkPreviews &&
            content.agentProfilePreviewPlan.displayMode == AgentProfilePreviewDisplayMode.Standalone &&
            content.agentProfilePreviewPlan.profiles.isNotEmpty()
        if (!shouldRenderStandaloneAgentProfiles) {
            when {
                htmlTables.isNotEmpty() -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = content.plainText }
                    ) {
                        HtmlTableBody(tables = htmlTables)
                    }
                }
                content.shouldRenderBodyAsMarkdown() -> {
                    Box(
                        Modifier
                            .fillMaxWidth()
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
                }
                else -> {
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
                        Box(Modifier.semantics { contentDescription = content.plainText }) {
                            EditorStyledText(
                                text = text,
                                onLinkClickedListener = onLinkClick,
                                onLinkLongClickedListener = onLinkLongClick,
                                style = ElementRichTextEditorStyle.textStyle(),
                                releaseOnDetach = false,
                            )
                        }
                    }
                }
            }
        }
        if (renderLinkPreviews) {
            TimelineItemAgentProfilePreviewCarousel(content.agentProfilePreviewPlan.profiles, onLinkClick)
            TimelineLinkPreviews(content.linkPreviewUrls, onLinkClick)
        }
    }
}

@Composable
private fun TimelineLinkPreviews(
    urls: List<String>,
    onLinkClick: (Link) -> Unit,
) {
    urls.forEach { url ->
        TimelineItemLinkPreviewView(
            url = url,
            onClick = onLinkClick,
        )
    }
}

@VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
@Composable
internal fun getTextWithResolvedMentions(content: TimelineItemTextBasedContent): CharSequence {
    val mentionSpanUpdater = LocalMentionSpanUpdater.current
    val bodyWithResolvedMentions = mentionSpanUpdater.rememberMentionSpans(content.formattedBody)
    return SpannedString.valueOf(bodyWithResolvedMentions)
}

internal fun TimelineItemTextBasedContent.shouldRenderBodyAsMarkdown(): Boolean {
    return htmlBody.isNullOrBlank() && body.hasMarkdownSyntax()
}

internal fun String.hasMarkdownSyntax(): Boolean {
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

@Preview
@Composable
internal fun TimelineItemTextViewWithLinkPreviewPreview() = ElementPreview {
    val content = aTimelineItemTextContent(
        body = "https://play.google.com/apps/internaltest/4701613975292549758",
        formattedBody = LinkifyHelper.linkify("https://play.google.com/apps/internaltest/4701613975292549758"),
        linkPreviewUrls = listOf("https://play.google.com/apps/internaltest/4701613975292549758"),
    )
    TimelineItemTextView(
        content = content,
        onLinkClick = {},
        onLinkLongClick = {},
        onLongClick = {},
    )
}
