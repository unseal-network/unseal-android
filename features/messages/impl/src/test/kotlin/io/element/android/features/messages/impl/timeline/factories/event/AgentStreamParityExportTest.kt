/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories.event

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.agentstream.api.StreamPart
import io.element.android.libraries.agentstream.api.StreamSnapshot
import io.element.android.libraries.agentstream.api.StreamStatus
import io.element.android.libraries.agentstream.api.ToolPartState
import kotlinx.serialization.json.Json
import org.junit.Test

class AgentStreamParityExportTest {
    @Test
    fun `export includes visible parts and tool root entries`() {
        val content = AiSdkStreamReducer().mapSnapshot(
            snapshot = StreamSnapshot(
                schemaVersion = 1,
                streamId = "compose-email-list",
                status = StreamStatus.Completed,
                parts = listOf(
                    StreamPart.Text(id = "text-1", text = "Done", textState = "done"),
                    StreamPart.Tool(
                        id = "tool-gmail-list",
                        toolState = ToolPartState.OutputAvailable.wireValue,
                        toolName = "GMAIL_FETCH_EMAILS",
                        toolCallId = "tool-gmail-list",
                        output = Json.parseToJsonElement(
                            """
                            {
                              "successful": true,
                              "data": {
                                "messages": [
                                  {
                                    "subject": "Agent stream card parity",
                                    "from": {"name": "Ruihan", "email": "ruihan@unseal.ai"},
                                    "snippet": "Compare both clients.",
                                    "date": "Jun 14, 2026"
                                  }
                                ]
                              }
                            }
                            """.trimIndent()
                        ),
                    )
                ),
                rawEvents = emptyList(),
                updatedAtMs = 1000L,
                completedAtMs = 1000L,
                error = null,
            ),
            isEdited = false,
            sender = "@agent:unseal.ai",
        )

        val json = AgentStreamParityExport.renderJson(content)
        val toolRoot = json.getJSONObject("toolRoot")
        val entries = toolRoot.getJSONArray("entries")
        val firstEntry = entries.getJSONObject(0)

        assertThat(json.getString("streamId")).isEqualTo("compose-email-list")
        assertThat(json.getBoolean("isTerminal")).isTrue()
        assertThat(json.getJSONArray("parts").length()).isEqualTo(2)
        assertThat(toolRoot.getString("title")).isEqualTo("Emails")
        assertThat(firstEntry.getString("cardType")).isEqualTo("composeEmail")
        assertThat(firstEntry.getString("state")).isEqualTo("done")
        assertThat(firstEntry.getJSONArray("propsKeys").strings()).contains("_cardType")
    }

    @Test
    fun `export includes suspended data card summary`() {
        val content = AiSdkStreamReducer().mapSnapshot(
            snapshot = StreamSnapshot(
                schemaVersion = 1,
                streamId = "moltbook-register",
                status = StreamStatus.Completed,
                parts = listOf(
                    StreamPart.Data(
                        id = "suspend-moltbook",
                        type = "data-tool-call-suspended",
                        state = "done",
                        data = Json.parseToJsonElement(
                            """
                            {
                              "toolCallId": "moltbook-register-1",
                              "toolName": "moltbookRegister",
                              "title": "Connect Moltbook",
                              "reason": "Enter your Moltbook credentials.",
                              "suspendPayload": {
                                "kind": "moltbookRegister",
                                "agentId": "agent-mail",
                                "moltyName": "Mail Agent"
                              }
                            }
                            """.trimIndent()
                        ),
                    )
                ),
                rawEvents = emptyList(),
                updatedAtMs = 1000L,
                completedAtMs = 1000L,
                error = null,
            ),
            isEdited = false,
            sender = "@agent:unseal.ai",
        )

        val part = AgentStreamParityExport.renderJson(content)
            .getJSONArray("parts")
            .getJSONObject(0)

        assertThat(part.getString("type")).isEqualTo("data-tool-call-suspended")
        assertThat(part.getString("toolName")).isEqualTo("moltbookRegister")
        assertThat(part.getString("suspendedKind")).isEqualTo("moltbookRegister")
        assertThat(part.getJSONArray("payloadKeys").strings()).contains("agentId")
    }

    private fun org.json.JSONArray.strings(): List<String> {
        return (0 until length()).map { index -> getString(index) }
    }
}
