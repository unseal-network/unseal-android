/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AiMessageContentParserTest {
    private val parser = AiMessageContentParser()

    @Test
    fun `parses aisdk protocol message with rich parts`() {
        val json = """
            {
              "type": "m.room.message",
              "content": {
                "msgtype": "m.aisdk.protocol",
                "content": "Here is the answer.",
                "thinking_process": [
                  { "id": "t1", "title": "Plan", "description": "Figure out the steps", "status": "complete" },
                  { "title": "Search" }
                ],
                "tool_calls": [
                  { "id": "c1", "name": "web_search", "display_name": "Web Search", "state": "completed", "output": "3 results" }
                ],
                "sources": [
                  { "id": "s1", "title": "Wikipedia", "url": "https://en.wikipedia.org", "snippet": "..." }
                ],
                "quick_actions": [
                  { "id": "q1", "label": "Tell me more", "action": "expand" }
                ]
              }
            }
        """.trimIndent()

        val result = parser.parse(json, isEdited = false)

        assertThat(result).isNotNull()
        requireNotNull(result)
        assertThat(result.body).isEqualTo("Here is the answer.")
        assertThat(result.thinkingSteps.map { it.title }).containsExactly("Plan", "Search").inOrder()
        assertThat(result.thinkingSteps[1].status).isEqualTo("complete")
        assertThat(result.toolCalls.single().displayName).isEqualTo("Web Search")
        assertThat(result.toolCalls.single().output).isEqualTo("3 results")
        assertThat(result.sources.single().url).isEqualTo("https://en.wikipedia.org")
        assertThat(result.quickActions.single().label).isEqualTo("Tell me more")
        assertThat(result.hasRichParts).isTrue()
    }

    @Test
    fun `parses m_text message with stream object as ai`() {
        val json = """
            { "content": { "msgtype": "m.text", "body": "hi", "content": "streamed body", "stream": { "status": "active" } } }
        """.trimIndent()

        val result = parser.parse(json, isEdited = false)

        assertThat(result).isNotNull()
        assertThat(result!!.isStreaming).isTrue()
        assertThat(result.body).isEqualTo("streamed body")
    }

    @Test
    fun `returns null for normal text message`() {
        val json = """{ "content": { "msgtype": "m.text", "body": "just a normal message" } }"""
        assertThat(parser.parse(json, isEdited = false)).isNull()
    }

    @Test
    fun `returns null for null or invalid json`() {
        assertThat(parser.parse(null, isEdited = false)).isNull()
        assertThat(parser.parse("", isEdited = false)).isNull()
        assertThat(parser.parse("not json", isEdited = false)).isNull()
        assertThat(parser.parse("{}", isEdited = false)).isNull()
    }

    @Test
    fun `aisdk message without rich parts still parses body`() {
        val json = """{ "content": { "msgtype": "m.aisdk.protocol", "content": "plain" } }"""
        val result = parser.parse(json, isEdited = true)
        requireNotNull(result)
        assertThat(result.body).isEqualTo("plain")
        assertThat(result.isEdited).isTrue()
        assertThat(result.hasRichParts).isFalse()
    }
}
