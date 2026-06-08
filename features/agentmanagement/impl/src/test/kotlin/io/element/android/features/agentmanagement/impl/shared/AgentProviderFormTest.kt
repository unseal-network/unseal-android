/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.shared

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProvider
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentProviderInfo
import io.element.android.libraries.chatbot.api.model.agent.ChatbotProviderModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProviderFormTest {
    @Test
    fun `unseal provider is ordered first`() {
        val providers = listOf(provider("openai"), provider("unseal"), provider("anthropic"))

        assertEquals(listOf("unseal", "openai", "anthropic"), providers.iosOrderedProviderIds())
    }

    @Test
    fun `first provider is selected when none selected`() {
        val providers = listOf(provider("unseal"), provider("openai"))

        assertEquals("unseal", selectProviderId(providers, null))
    }

    @Test
    fun `first model is selected when current model is absent`() {
        val provider = provider("openai", models = listOf("gpt-4.1", "gpt-4.1-mini"))

        assertEquals("gpt-4.1", selectModelId(provider, "missing"))
    }

    @Test
    fun `api key is not needed for unseal provider`() {
        assertFalse(needsApiKey("unseal"))
        assertTrue(needsApiKey("openai"))
    }

    private fun provider(id: String, models: List<String> = emptyList()) = ChatbotAgentProvider(
        id = id,
        displayName = id,
        info = ChatbotAgentProviderInfo(models = models.map { ChatbotProviderModel(id = it) }),
    )
}
