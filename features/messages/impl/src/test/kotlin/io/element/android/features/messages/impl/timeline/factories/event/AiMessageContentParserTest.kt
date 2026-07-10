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
    fun `parse - keeps stream initiator for runtime controls`() {
        val content = parser.parse(
            originalJson = """
                {
                  "type": "m.room.message",
                  "content": {
                    "msgtype": "m.text",
                    "body": "Thinking…",
                    "stream": { "id": "stream-1" },
                    "target_user_id": "@alice:example.org"
                  }
                }
            """.trimIndent(),
            isEdited = false,
        )

        assertThat(content?.streamId).isEqualTo("stream-1")
        assertThat(content?.targetUserId).isEqualTo("@alice:example.org")
    }

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
            { "content": { "msgtype": "m.text", "body": "hi", "content": "streamed body", "stream": { "id": "stream-1", "status": "active" } } }
        """.trimIndent()

        val result = parser.parse(json, isEdited = false)

        assertThat(result).isNotNull()
        assertThat(result!!.isStreaming).isTrue()
        assertThat(result.streamId).isEqualTo("stream-1")
        assertThat(result.body).isEqualTo("streamed body")
    }

    @Test
    fun `parses stream object terminal statuses as completed`() {
        val terminalStatuses = listOf("FAILED", "cancelled", "canceled")

        terminalStatuses.forEach { status ->
            val json = """
                { "content": { "msgtype": "m.text", "body": "done", "stream": { "id": "stream-1", "status": "$status" } } }
            """.trimIndent()

            val result = parser.parse(json, isEdited = false)

            assertThat(result).isNotNull()
            requireNotNull(result)
            assertThat(result.isStreaming).isFalse()
        }
    }

    @Test
    fun `parses message with top level stream id and fallback sender as ai`() {
        val json = """
            { "content": { "msgtype": "m.text", "body": "loading", "stream_id": "stream-2", "sender": "" } }
        """.trimIndent()

        val result = parser.parse(json, isEdited = false, fallbackSender = "@agent:keepsecret.io")

        assertThat(result).isNotNull()
        assertThat(result!!.streamId).isEqualTo("stream-2")
        assertThat(result.sender).isEqualTo("@agent:keepsecret.io")
    }

    @Test
    fun `parses ios stream start event with stream id and fallback sender`() {
        val json = """
            {
              "type": "m.stream.start",
              "event_id": "${'$'}event",
              "content": {
                "msgtype": "m.stream.start",
                "body": "stream-3",
                "stream_id": "stream-3",
                "is_streaming": true
              }
            }
        """.trimIndent()

        val result = parser.parse(json, isEdited = false, fallbackSender = "@agent:keepsecret.io")

        assertThat(result).isNotNull()
        requireNotNull(result)
        assertThat(result.streamId).isEqualTo("stream-3")
        assertThat(result.sender).isEqualTo("@agent:keepsecret.io")
        assertThat(result.isStreaming).isTrue()
    }

    @Test
    fun `parses ios stream complete event with completed content`() {
        val json = """
            {
              "type": "m.stream.complete",
              "content": {
                "msgtype": "m.stream.complete",
                "stream_id": "stream-4",
                "content": "done",
                "is_streaming": false
              }
            }
        """.trimIndent()

        val result = parser.parse(json, isEdited = false)

        assertThat(result).isNotNull()
        requireNotNull(result)
        assertThat(result.streamId).isEqualTo("stream-4")
        assertThat(result.body).isEqualTo("done")
        assertThat(result.isStreaming).isFalse()
    }

    @Test
    fun `parses ios stream complete event without streaming flag as completed`() {
        val json = """
            {
              "type": "m.stream.complete",
              "content": {
                "msgtype": "m.stream.complete",
                "stream_id": "stream-5",
                "content": "done"
              }
            }
        """.trimIndent()

        val result = parser.parse(json, isEdited = false)

        assertThat(result).isNotNull()
        requireNotNull(result)
        assertThat(result.streamId).isEqualTo("stream-5")
        assertThat(result.isStreaming).isFalse()
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
