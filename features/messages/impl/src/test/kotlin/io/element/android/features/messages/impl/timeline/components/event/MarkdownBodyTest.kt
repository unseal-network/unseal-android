/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event

import android.widget.TextView
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.URLSpan
import com.google.common.truth.Truth.assertThat
import org.junit.runner.RunWith
import org.junit.Test
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MarkdownBodyTest {
    @Test
    fun `streaming markdown safe text hides unclosed code fence`() {
        val source = "before\n```kotlin\nfun main()"

        assertThat(source.streamingMarkdownSafeTextForTest()).isEqualTo("before\n")
    }

    @Test
    fun `streaming markdown safe text keeps closed markdown`() {
        val source = "before\n```kotlin\nfun main() = Unit\n```\nafter"

        assertThat(source.streamingMarkdownSafeTextForTest()).isEqualTo(source)
    }

    @Test
    fun `markdown tables are converted to mobile friendly fields`() {
        val source = """
            Here are results:

            | Date | Text | Likes |
            | --- | --- | ---: |
            | 2026-06-18 | Agent engineer notes | 60 |
            | 2026-06-19 | Streaming renderer | 4 |
        """.trimIndent()

        assertThat(source.mobileFriendlyMarkdownTablesForTest()).isEqualTo(
            """
            Here are results:

            **Item 1**
            - **Date:** 2026-06-18
            - **Text:** Agent engineer notes
            - **Likes:** 60

            **Item 2**
            - **Date:** 2026-06-19
            - **Text:** Streaming renderer
            - **Likes:** 4
            """.trimIndent()
        )
    }

    @Test
    fun `markdown table conversion skips fenced code`() {
        val source = """
            ```markdown
            | Date | Text |
            | --- | --- |
            | 2026 | keep raw |
            ```
        """.trimIndent()

        assertThat(source.mobileFriendlyMarkdownTablesForTest()).isEqualTo(source)
    }

    @Test
    fun `markdown text view supports native text selection`() {
        val textView = TextView(androidx.test.core.app.ApplicationProvider.getApplicationContext())

        configureMarkdownTextViewSelection(textView)

        assertThat(textView.isTextSelectable).isTrue()
        assertThat(textView.hasOnClickListeners()).isFalse()
    }

    @Test
    fun `markdown theme spans replace low contrast foreground and background colors`() {
        val textView = TextView(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val text = SpannableString("cell link code")
        text.setSpan(ForegroundColorSpan(0xFFEFEFEF.toInt()), 0, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(URLSpan("https://example.com"), 5, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(ForegroundColorSpan(0xFFEFEFEF.toInt()), 5, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(BackgroundColorSpan(0x00FFFFFF), 10, 14, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = text

        applyMarkdownThemeSpans(
            textView = textView,
            textColorArgb = 0xFF111111.toInt(),
            linkColorArgb = 0xFF0057D8.toInt(),
            codeBackgroundArgb = 0xFFE8EAED.toInt(),
        )

        val themed = textView.text as Spanned
        val foregrounds = themed.getSpans(0, themed.length, ForegroundColorSpan::class.java)
            .map { themed.getSpanStart(it) to it.foregroundColor }
            .toMap()
        val backgrounds = themed.getSpans(0, themed.length, BackgroundColorSpan::class.java)
            .map { themed.getSpanStart(it) to it.backgroundColor }
            .toMap()

        assertThat(foregrounds[0]).isEqualTo(0xFF111111.toInt())
        assertThat(foregrounds[5]).isEqualTo(0xFF0057D8.toInt())
        assertThat(backgrounds[10]).isEqualTo(0xFFE8EAED.toInt())
    }

    @Test
    fun `markdown theme spans apply base text color when renderer omits foreground spans`() {
        val textView = TextView(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        val text = SpannableString("date status")
        text.setSpan(URLSpan("https://example.com"), 5, 11, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textView.text = text

        applyMarkdownThemeSpans(
            textView = textView,
            textColorArgb = 0xFF111111.toInt(),
            linkColorArgb = 0xFF0057D8.toInt(),
            codeBackgroundArgb = 0xFFE8EAED.toInt(),
        )

        val themed = textView.text as Spanned
        val foregrounds = themed.getSpans(0, themed.length, ForegroundColorSpan::class.java)
        assertThat(foregrounds.map { themed.getSpanStart(it) to themed.getSpanEnd(it) })
            .containsAtLeast(0 to themed.length, 5 to 11)
        assertThat(foregrounds.first { themed.getSpanStart(it) == 0 }.foregroundColor)
            .isEqualTo(0xFF111111.toInt())
        assertThat(foregrounds.first { themed.getSpanStart(it) == 5 }.foregroundColor)
            .isEqualTo(0xFF0057D8.toInt())
    }

    @Test
    fun `spec code blocks render as json spec`() {
        val patchStream = """
            {"op":"add","path":"/root","value":"main"}
            {"op":"add","path":"/elements/main","value":{"type":"Text","props":{"text":"Hello spec"},"children":[]}}
        """.trimIndent()

        assertThat(markdownCodeBlockRenderMode(language = "spec", code = patchStream))
            .isEqualTo(MarkdownCodeBlockRenderMode.JsonSpec)
    }

    @Test
    fun `json code blocks render as json spec only when the payload is renderable`() {
        val renderableJson = """
            {
              "root": "main",
              "elements": {
                "main": {
                  "type": "Text",
                  "props": { "text": "Hello JSON" },
                  "children": []
                }
              }
            }
        """.trimIndent()
        val ordinaryJson = """{"message":"debug payload","count":3}"""

        assertThat(markdownCodeBlockRenderMode(language = "json", code = renderableJson))
            .isEqualTo(MarkdownCodeBlockRenderMode.JsonSpec)
        assertThat(markdownCodeBlockRenderMode(language = "json", code = ordinaryJson))
            .isEqualTo(MarkdownCodeBlockRenderMode.Code)
    }

    @Test
    fun `json code blocks render readable typed ui payloads as json spec`() {
        val typedPayload = """
            {
              "cards": [
                { "tag": "Weather", "status": "dark" },
                { "tag": "Hotel", "status": "pending" }
              ],
              "component": "HeadlineListCard"
            }
        """.trimIndent()

        assertThat(markdownCodeBlockRenderMode(language = "json", code = typedPayload))
            .isEqualTo(MarkdownCodeBlockRenderMode.JsonSpec)
    }

    @Test
    fun `primary json spec fences are extracted before markwon rendering`() {
        val typedPayload = """
            {
              "cards": [
                { "tag": "Weather", "status": "dark" }
              ],
              "component": "HeadlineListCard"
            }
        """.trimIndent()
        val markdown = """
            ```json
            $typedPayload
            ```
        """.trimIndent()

        assertThat(extractPrimaryJsonSpecFenceForTest(markdown)).isEqualTo(typedPayload)
    }

    @Test
    fun `jsonl code blocks render as json spec when patches build elements`() {
        val jsonl = """
            {"op":"add","path":"/root","value":"main"}
            {"op":"add","path":"/elements/main","value":{"type":"Text","props":{"text":{"${"$"}state":"/weather/temp"}},"children":[]}}
            {"op":"add","path":"/state/weather","value":{"temp":"21°C"}}
        """.trimIndent()

        assertThat(markdownCodeBlockRenderMode(language = "jsonl", code = jsonl))
            .isEqualTo(MarkdownCodeBlockRenderMode.JsonSpec)
    }

    @Test
    fun `non spec languages stay as code blocks`() {
        assertThat(markdownCodeBlockRenderMode(language = "kotlin", code = "fun main() = Unit"))
            .isEqualTo(MarkdownCodeBlockRenderMode.Code)
    }
}
