/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.messages.impl.timeline.components.event

import android.text.SpannableString
import android.text.SpannedString
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.core.text.buildSpannedString
import androidx.core.text.inSpans
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemImageContent
import io.element.android.features.messages.impl.utils.FakeMentionSpanFormatter
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.ui.messages.RoomMemberProfilesCache
import io.element.android.libraries.matrix.ui.messages.RoomNamesCache
import io.element.android.libraries.textcomposer.mentions.DefaultMentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.LocalMentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.MentionSpan
import io.element.android.libraries.textcomposer.mentions.MentionSpanTheme
import io.element.android.libraries.textcomposer.mentions.MentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.MentionType
import io.element.android.libraries.textcomposer.mentions.getMentionSpans
import io.element.android.tests.testutils.lambda.assert
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.wysiwyg.view.spans.CustomMentionSpan
import kotlinx.coroutines.CompletableDeferred
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimelineTextViewTest {
    private val mentionSpanTheme = MentionSpanTheme(currentUserId = A_USER_ID)
    private val formatLambda = lambdaRecorder<MentionType, CharSequence> { mentionType -> mentionType.toString() }
    private val mentionSpanFormatter = FakeMentionSpanFormatter(formatLambda)

    @Test
    fun `getTextWithResolvedMentions - does nothing for a non spannable CharSequence`() = runAndroidComposeUiTest {
        val charSequence = "Hello <a href=\"https://matrix.to/#/@alice:example.com\">@alice:example.com</a>"
        val mentionSpanUpdater = aMentionSpanUpdater()
        val result = getText(mentionSpanUpdater, aTextContentWithFormattedBody(charSequence))

        assertThat(result.getMentionSpans()).isEmpty()
        assert(formatLambda).isNeverCalled()
    }

    @Test
    fun `getTextWithResolvedMentions - does nothing if there are no mentions`() = runAndroidComposeUiTest {
        val charSequence = SpannableString("Hello <a href=\"https://matrix.to/#/@alice:example.com\">@alice:example.com</a>")
        val mentionSpanUpdater = aMentionSpanUpdater()
        val result = getText(mentionSpanUpdater, aTextContentWithFormattedBody(charSequence))

        assertThat(result.getMentionSpans()).isEmpty()
        assert(formatLambda).isNeverCalled()
    }

    @Test
    fun `getTextWithResolvedMentions - just returns the body if there is no formattedBody`() = runAndroidComposeUiTest {
        val charSequence = "Hello <a href=\"https://matrix.to/#/@alice:example.com\">@alice:example.com</a>"
        val mentionSpanUpdater = aMentionSpanUpdater()
        val result = getText(mentionSpanUpdater, aTextContentWithFormattedBody(body = charSequence, formattedBody = null))

        assertThat(result.getMentionSpans()).isEmpty()
        assertThat(result.toString()).isEqualTo(charSequence)
        assert(formatLambda).isNeverCalled()
    }

    @Test
    fun `getTextWithResolvedMentions - with Room mention format correctly`() = runAndroidComposeUiTest {
        val mentionType = MentionType.Room(roomIdOrAlias = A_ROOM_ID_2.toRoomIdOrAlias())
        val charSequence = buildSpannedString {
            append("Hello ")
            inSpans(MentionSpan(mentionType)) {
                append(A_ROOM_ID.value)
            }
        }
        val mentionSpanUpdater = aMentionSpanUpdater()
        val result = getText(mentionSpanUpdater, aTextContentWithFormattedBody(charSequence))

        val expectedDisplayText = mentionType.toString()
        assertThat(result.getMentionSpans().firstOrNull()?.displayText.toString()).isEqualTo(expectedDisplayText)
        assertThat(result).isEqualTo(charSequence)
        assert(formatLambda).isCalledOnce()
    }

    @Test
    fun `getTextWithResolvedMentions - replaces MentionSpan's text`() = runAndroidComposeUiTest {
        val mentionType = MentionType.User(userId = A_USER_ID)
        val charSequence = buildSpannedString {
            append("Hello ")
            inSpans(MentionSpan(mentionType)) {
                append("@NotAlice")
            }
        }
        val mentionSpanUpdater = aMentionSpanUpdater()
        val result = getText(mentionSpanUpdater, aTextContentWithFormattedBody(charSequence))

        val expectedDisplayText = mentionType.toString()
        assertThat(result.getMentionSpans().firstOrNull()?.displayText.toString()).isEqualTo(expectedDisplayText)
        assert(formatLambda).isCalledOnce()
    }

    @Test
    fun `getTextWithResolvedMentions - replaces MentionSpan's text inside CustomMentionSpan`() = runAndroidComposeUiTest {
        val mentionType = MentionType.User(userId = A_USER_ID)
        val charSequence = buildSpannedString {
            append("Hello ")
            inSpans(CustomMentionSpan(MentionSpan(mentionType))) {
                append("@NotAlice")
            }
        }
        val mentionSpanUpdater = aMentionSpanUpdater()
        val expectedDisplayText = mentionType.toString()
        val result = getText(mentionSpanUpdater, aTextContentWithFormattedBody(charSequence))
        assertThat(result.getMentionSpans().firstOrNull()?.displayText.toString()).isEqualTo(expectedDisplayText)
        assert(formatLambda).isCalledOnce()
    }

    @Test
    fun `shouldRenderBodyAsMarkdown - html body uses html renderer even when body contains markdown table`() {
        val content = aTextContentWithFormattedBody(
            body = "| Name | Value |\n| --- | --- |\n| A | B |",
            formattedBody = SpannedString("Name Value A B"),
            htmlBody = "<table><thead><tr><th>Name</th><th>Value</th></tr></thead><tbody><tr><td>A</td><td>B</td></tr></tbody></table>",
        )

        assertThat(content.shouldRenderBodyAsMarkdown()).isFalse()
    }

    @Test
    fun `shouldRenderBodyAsMarkdown - non markdown formatted body keeps html renderer`() {
        val content = aTextContentWithFormattedBody(
            body = "bold item",
            formattedBody = SpannedString("bold item"),
            htmlBody = "<strong>bold</strong> item",
        )

        assertThat(content.shouldRenderBodyAsMarkdown()).isFalse()
    }

    @Test
    fun `shouldRenderBodyAsMarkdown - markdown body without html uses markdown renderer`() {
        val content = aTextContentWithFormattedBody(
            body = "**bold**\n\n- item",
            formattedBody = SpannedString("**bold**\n\n- item"),
        )

        assertThat(content.shouldRenderBodyAsMarkdown()).isTrue()
    }

    @Test
    fun `shouldRenderCaptionAsMarkdown - formatted caption uses html renderer even when caption contains markdown table`() {
        val content = aTimelineItemImageContent(
            caption = "| Name | Value |\n| --- | --- |\n| A | B |",
        ).copy(formattedCaption = SpannedString("Name Value A B"))

        assertThat(content.shouldRenderCaptionAsMarkdown(isInspectionMode = false)).isFalse()
    }

    @Test
    fun `shouldRenderCaptionAsMarkdown - markdown caption without formatted caption uses markdown renderer`() {
        val content = aTimelineItemImageContent(
            caption = "**bold**\n\n- item",
        )

        assertThat(content.shouldRenderCaptionAsMarkdown(isInspectionMode = false)).isTrue()
    }

    @Test
    fun `extractHtmlTables - preserves table rows and header cells`() {
        val document = org.jsoup.Jsoup.parse(
            """
            <table>
              <thead><tr><th>Time</th><th>Speaker</th></tr></thead>
              <tbody><tr><td>15:21:26</td><td>user</td></tr></tbody>
            </table>
            """.trimIndent()
        )

        val table = document.extractHtmlTables().single()

        assertThat(table.rows).hasSize(2)
        assertThat(table.rows[0].cells.map { it.text }).containsExactly("Time", "Speaker").inOrder()
        assertThat(table.rows[0].cells.all { it.isHeader }).isTrue()
        assertThat(table.rows[1].cells.map { it.text }).containsExactly("15:21:26", "user").inOrder()
        assertThat(table.rows[1].cells.any { it.isHeader }).isFalse()
    }

    private suspend fun AndroidComposeUiTest<ComponentActivity>.getText(
        mentionSpanUpdater: MentionSpanUpdater,
        content: TimelineItemTextBasedContent,
    ): CharSequence {
        val completable = CompletableDeferred<CharSequence>()
        setContent {
            CompositionLocalProvider(
                LocalMentionSpanUpdater provides mentionSpanUpdater
            ) {
                completable.complete(getTextWithResolvedMentions(content = content))
            }
        }
        return completable.await()
    }

    private fun aMentionSpanUpdater(): MentionSpanUpdater {
        return DefaultMentionSpanUpdater(
            formatter = mentionSpanFormatter,
            theme = mentionSpanTheme,
            roomMemberProfilesCache = RoomMemberProfilesCache(),
            roomNamesCache = RoomNamesCache(),
        )
    }

    private fun aTextContentWithFormattedBody(
        formattedBody: CharSequence?,
        body: String = "",
        htmlBody: String? = null,
    ) =
        TimelineItemTextContent(
            body = body,
            htmlDocument = htmlBody?.let { org.jsoup.Jsoup.parse(it) },
            formattedBody = formattedBody ?: SpannedString(body),
            isEdited = false
        )
}
