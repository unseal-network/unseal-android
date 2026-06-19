/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.event.toolcards

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.AiToolStreamPart
import org.junit.Test

class ToolCallRootCardAdapterTest {
    @Test
    fun `direct gmail output creates compose email done entry`() {
        val part = AiToolStreamPart(
            id = "tool-1",
            state = "output-available",
            toolName = "GMAIL_FETCH_EMAILS",
            title = null,
            input = """{"query":"from:alice@example.com"}""",
            rawInput = null,
            output = """{"successful":true,"data":{"messages":[{"subject":"Authorization complete","from":"alice@example.com","snippet":"done"}]}}""",
            errorText = null,
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.id).isEqualTo("tool-1")
        assertThat(entry.name).isEqualTo("Emails")
        assertThat(entry.cardType).isEqualTo("composeEmail")
        assertThat(entry.state).isEqualTo("done")
        assertThat(entry.props).contains(""""_cardType":"composeEmail"""")
        assertThat(entry.props).contains("Authorization complete")
    }

    @Test
    fun `multi execute done expands grouped child entries`() {
        val part = AiToolStreamPart(
            id = "multi-1",
            state = "output-available",
            toolName = "COMPOSIO_MULTI_EXECUTE_TOOL",
            title = null,
            input = """{"tools":[{"tool_slug":"GMAIL_FETCH_EMAILS"}]}""",
            rawInput = null,
            output = """{"data":{"results":[{"tool_slug":"GMAIL_FETCH_EMAILS","response":{"successful":true,"data":{"messages":[{"subject":"Invoice"}]}}}]}}""",
            errorText = null,
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.id).isEqualTo("multi-1_GMAIL_FETCH_EMAILS")
        assertThat(entry.name).isEqualTo("Emails")
        assertThat(entry.cardType).isEqualTo("composeEmail")
        assertThat(entry.state).isEqualTo("done")
        assertThat(entry.props).contains(""""_cardType":"composeEmail"""")
        assertThat(entry.props).contains("Invoice")
    }

    @Test
    fun `multi execute terminal fallback entries inherit terminal state`() {
        val part = AiToolStreamPart(
            id = "multi-1",
            state = "output-available",
            toolName = "COMPOSIO_MULTI_EXECUTE_TOOL",
            title = null,
            input = """{"tools":[{"tool_slug":"GMAIL_FETCH_EMAILS"},{"tool_slug":"GOOGLEDRIVE_FIND_FILE"}]}""",
            rawInput = null,
            output = null,
            errorText = null,
        )

        val entries = ToolCallRootCardAdapter.toolCallEntries(listOf(part))

        assertThat(entries.map { it.name }).containsExactly("Emails", "Files").inOrder()
        assertThat(entries.map { it.state }).containsExactly("done", "done")
    }

    @Test
    fun `sub agent calling creates generic calling entry`() {
        val part = AiToolStreamPart(
            id = "agent-1",
            state = "input-available",
            toolName = "agent-mailAgent",
            title = null,
            input = null,
            rawInput = null,
            output = null,
            errorText = null,
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.id).isEqualTo("agent-1")
        assertThat(entry.name).isEqualTo("Mail Agent")
        assertThat(entry.cardType).isEqualTo("generic")
        assertThat(entry.state).isEqualTo("calling")
        assertThat(entry.props).contains(""""_cardType":"generic"""")
    }

    @Test
    fun `denied tool creates error entry with readable error props`() {
        val part = AiToolStreamPart(
            id = "tool-denied",
            state = "output-denied",
            toolName = "GMAIL_FETCH_EMAILS",
            title = null,
            input = """{"query":"newer_than:1d"}""",
            rawInput = null,
            output = null,
            errorText = "User denied Gmail access",
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.name).isEqualTo("Emails")
        assertThat(entry.cardType).isEqualTo("composeEmail")
        assertThat(entry.state).isEqualTo("error")
        assertThat(entry.props).contains(""""_cardType":"composeEmail"""")
        assertThat(entry.props).contains("User denied Gmail access")
    }

    @Test
    fun `completed root model defaults collapsed`() {
        val entry = ToolCallRootCardAdapter.toolCallEntries(
            listOf(
                AiToolStreamPart(
                    id = "hotel-1",
                    state = "output-available",
                    toolName = "COMPOSIO_SEARCH_HOTELS",
                    title = null,
                    input = null,
                    rawInput = null,
                    output = """{"hotels":[{"name":"Felton","price":"$50"}]}""",
                    errorText = null,
                )
            )
        ).single()

        val model = ToolCallRootCardAdapter.rootModel(listOf(entry))

        assertThat(model).isNotNull()
        assertThat(model!!.allFinished).isTrue()
        assertThat(model.expandedByDefault).isFalse()
    }

    @Test
    fun `calling root model defaults expanded`() {
        val entry = ToolCallRootCardAdapter.toolCallEntries(
            listOf(
                AiToolStreamPart(
                    id = "hotel-1",
                    state = "input-available",
                    toolName = "COMPOSIO_SEARCH_HOTELS",
                    title = null,
                    input = """{"q":"Chengdu"}""",
                    rawInput = null,
                    output = null,
                    errorText = null,
                )
            )
        ).single()

        val model = ToolCallRootCardAdapter.rootModel(listOf(entry))

        assertThat(model).isNotNull()
        assertThat(model!!.allFinished).isFalse()
        assertThat(model.expandedByDefault).isTrue()
        assertThat(model.title).isEqualTo("Hotels")
    }
}
