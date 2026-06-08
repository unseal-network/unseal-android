/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.shared

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgentFormatterTest {
    @Test
    fun `matrix id uses localpart and server name`() {
        val agent = ChatbotAgent(botName = "helper", localpart = "agent-helper", serverName = "unseal.test")

        assertEquals("@agent-helper:unseal.test", agent.matrixId())
    }

    @Test
    fun `direct chat id falls back to valid provider mxid`() {
        val agent = ChatbotAgent(botName = "helper", providerAgentId = "@bot:unseal.test")

        assertEquals("@bot:unseal.test", agent.agentMatrixUserId())
    }

    @Test
    fun `invalid provider id is ignored for direct chat`() {
        val agent = ChatbotAgent(botName = "helper", providerAgentId = "bot")

        assertNull(agent.agentMatrixUserId())
    }

    @Test
    fun `provider model text joins non blank parts`() {
        val agent = ChatbotAgent(botName = "helper", provider = "openai", model = "gpt-4.1")

        assertEquals("openai · gpt-4.1", agent.providerModelText())
    }

    @Test
    fun `copy id fallback matches ios`() {
        assertEquals("plain", ChatbotAgent(botName = "plain").copyableAgentId())
        assertEquals("@local:server", ChatbotAgent(botName = "plain", localpart = "local", serverName = "server").copyableAgentId())
        assertEquals("@pid:server", ChatbotAgent(botName = "plain", providerAgentId = "@pid:server").copyableAgentId())
    }
}
