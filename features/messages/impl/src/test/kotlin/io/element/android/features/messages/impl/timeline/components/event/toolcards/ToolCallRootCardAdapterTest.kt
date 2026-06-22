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
    fun `search output with typed cards creates readable headline rows`() {
        val part = AiToolStreamPart(
            id = "search-1",
            state = "output-available",
            toolName = "COMPOSIO_SEARCH_TAVILY",
            title = null,
            input = """{"query":"render json card"}""",
            rawInput = null,
            output = """
                {
                  "cards": [
                    { "tag": "Weather", "status": "dark" },
                    { "tag": "Hotel", "status": "pending" }
                  ],
                  "component": "HeadlineListCard"
                }
            """.trimIndent(),
            errorText = null,
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.name).isEqualTo("Search")
        assertThat(entry.cardType).isEqualTo("headlineList")
        assertThat(entry.state).isEqualTo("done")
        assertThat(entry.props).contains("Weather")
        assertThat(entry.props).contains("dark")
        assertThat(entry.props).contains("Hotel")
        assertThat(entry.props).contains("pending")
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
    fun `multi execute hotel ads results create populated hotel entry`() {
        val part = AiToolStreamPart(
            id = "multi-hotels",
            state = "output-available",
            toolName = "COMPOSIO_MULTI_EXECUTE_TOOL",
            title = null,
            input = """{"tools":[{"tool_slug":"COMPOSIO_SEARCH_HOTELS"}]}""",
            rawInput = null,
            output = """
                {
                  "data": {
                    "results": [
                      {
                        "tool_slug": "COMPOSIO_SEARCH_HOTELS",
                        "response": {
                          "successful": true,
                          "data": {
                            "results": {
                              "ads": [
                                {
                                  "name": "Hilton Chengdu Chenghua",
                                  "price": "$90",
                                  "overall_rating": 4.5,
                                  "images": [{ "thumbnail": "https://example.com/hilton.jpg" }]
                                }
                              ]
                            }
                          }
                        }
                      }
                    ]
                  }
                }
            """.trimIndent(),
            errorText = null,
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.id).isEqualTo("multi-hotels_COMPOSIO_SEARCH_HOTELS")
        assertThat(entry.name).isEqualTo("Hotels")
        assertThat(entry.cardType).isEqualTo("hotelBooking")
        assertThat(entry.state).isEqualTo("done")
        assertThat(entry.props).contains("Hilton Chengdu Chenghua")
        assertThat(entry.props).contains("https://example.com/hilton.jpg")
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
    fun `multi entry root title uses selected tool name`() {
        val entries = listOf(
            ToolCallRootCardAdapter.toolCallEntries(
                listOf(
                    AiToolStreamPart(
                        id = "multi-1",
                        state = "output-available",
                        toolName = "COMPOSIO_MULTI_EXECUTE_TOOL",
                        title = null,
                        input = """{"tools":[{"tool_slug":"GMAIL_FETCH_EMAILS"},{"tool_slug":"COMPOSIO_SEARCH_HOTELS"}]}""",
                        rawInput = null,
                        output = null,
                        errorText = null,
                    )
                )
            )
        ).flatten()

        val root = ToolCallRootCardAdapter.rootModel(entries)

        assertThat(root?.title).isEqualTo("Hotels")
        assertThat(root?.selectedEntry?.name).isEqualTo("Hotels")
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
    fun `failed render ui with social feed spec creates posts entry from input spec`() {
        val part = AiToolStreamPart(
            id = "render-ui-1",
            state = "output-error",
            toolName = "renderUI",
            title = null,
            input = """
                {
                  "spec": {
                    "component": "SocialPostFeedCard",
                    "cards": [
                      {
                        "username": "N/A",
                        "text": "The best agent engineer deletes unused skills.",
                        "engagement": 4
                      }
                    ]
                  }
                }
            """.trimIndent(),
            rawInput = null,
            output = null,
            errorText = "render failed",
        )

        val entry = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(entry.name).isEqualTo("Posts")
        assertThat(entry.cardType).isEqualTo("socialPostFeed")
        assertThat(entry.state).isEqualTo("done")
        assertThat(entry.props).contains(""""_cardType":"socialPostFeed"""")
        assertThat(entry.props).contains("The best agent engineer deletes unused skills.")
        assertThat(entry.props).contains(""""likes":4""")
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

    @Test
    fun `root model id stays stable when more tool entries are appended`() {
        val first = ToolCallRootCardAdapter.toolCallEntries(
            listOf(
                AiToolStreamPart(
                    id = "tool-root",
                    state = "input-available",
                    toolName = "COMPOSIO_SEARCH_FLIGHTS",
                    title = null,
                    input = """{"origin":"CAN"}""",
                    rawInput = null,
                    output = null,
                    errorText = null,
                )
            )
        ).single()
        val second = first.copy(id = "tool-root_hotel", name = "Hotels", cardType = "hotelBooking")

        val initial = ToolCallRootCardAdapter.rootModel(listOf(first))
        val appended = ToolCallRootCardAdapter.rootModel(listOf(first, second))

        assertThat(initial!!.id).isEqualTo("tool-root")
        assertThat(appended!!.id).isEqualTo(initial.id)
    }

    @Test
    fun `root model deduplicates repeated tool tabs but keeps progress counts`() {
        val first = ToolCallRootCardAdapter.toolCallEntries(
            listOf(
                AiToolStreamPart(
                    id = "hotel-1",
                    state = "output-available",
                    toolName = "COMPOSIO_SEARCH_HOTELS",
                    title = null,
                    input = null,
                    rawInput = null,
                    output = """{"hotels":[{"name":"Upper House","price":"$360"}]}""",
                    errorText = null,
                )
            )
        ).single()
        val second = first.copy(
            id = "hotel-2",
            props = first.props.replace("Upper House", "St. Regis"),
        )

        val model = ToolCallRootCardAdapter.rootModel(listOf(first, second))

        assertThat(model).isNotNull()
        assertThat(model!!.entries.map { it.name }).containsExactly("Hotels")
        assertThat(model.doneCount).isEqualTo(2)
        assertThat(model.isSingleTool).isTrue()
        assertThat(model.selectedEntry!!.props).contains("St. Regis")
    }

    @Test
    fun `root model keeps same tool success and failure tabs separate`() {
        val success = ToolCallRootCardAdapter.toolCallEntries(
            listOf(
                AiToolStreamPart(
                    id = "event-1",
                    state = "output-available",
                    toolName = "COMPOSIO_SEARCH_EVENT",
                    title = null,
                    input = null,
                    rawInput = null,
                    output = """{"events":[{"title":"WAIC 2026"}]}""",
                    errorText = null,
                )
            )
        ).single()
        val failure = success.copy(
            id = "event-2",
            state = "error",
            props = errorProps("eventSearch", "Provider timed out").toString(),
        )

        val model = ToolCallRootCardAdapter.rootModel(listOf(success, failure))

        assertThat(model).isNotNull()
        assertThat(model!!.entries.map { it.name }).containsExactly("Events", "Events").inOrder()
        assertThat(model.entries.map { it.state }).containsExactly("done", "error").inOrder()
        assertThat(model.doneCount).isEqualTo(1)
        assertThat(model.errorCount).isEqualTo(1)
        assertThat(model.isSingleTool).isFalse()
        assertThat(model.selectedEntry!!.props).contains("Provider timed out")
    }

    @Test
    fun `entry props are stable for unchanged tool output`() {
        val part = AiToolStreamPart(
            id = "tool-1",
            state = "output-available",
            toolName = "GMAIL_FETCH_EMAILS",
            title = null,
            input = """{"query":"from:alice@example.com"}""",
            rawInput = null,
            output = """{"successful":true,"data":{"messages":[{"subject":"Authorization complete"}]}}""",
            errorText = null,
        )

        val first = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()
        val second = ToolCallRootCardAdapter.toolCallEntries(listOf(part)).single()

        assertThat(first.id).isEqualTo(second.id)
        assertThat(first.props).isEqualTo(second.props)
        assertThat(first.props.hashCode()).isEqualTo(second.props.hashCode())
    }
}
