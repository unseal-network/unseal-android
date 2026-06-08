/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.test

import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkit
import io.element.android.libraries.chatbot.api.model.credits.CreditBalance
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerStatus

fun aChatbotAgent(
    botName: String = "agent",
    displayName: String? = "Agent",
) = ChatbotAgent(
    botName = botName,
    displayName = displayName,
)

fun aChatbotUserSkill(
    id: String = "skill",
    name: String = "Skill",
) = ChatbotUserSkill(
    id = id,
    name = name,
)

fun aChatbotSchedule(
    scheduleId: String = "schedule",
) = ChatbotSchedule(
    scheduleId = scheduleId,
    name = "Schedule",
    cron = "0 9 * * *",
    action = "ping",
    agentId = "agent",
    roomId = "!room:example",
)

fun aChatbotWebhookTrigger(
    triggerId: String = "trigger",
) = ChatbotWebhookTrigger(
    triggerId = triggerId,
    agentId = "agent",
    name = "Trigger",
    eventTypes = listOf("event"),
    actionPrompt = "ping",
    roomId = "!room:example",
    status = ChatbotWebhookTriggerStatus.Enabled,
)

fun aCreditBalance(
    balanceMicros: String = "0",
) = CreditBalance(
    userId = "@alice:server.org",
    balanceMicros = balanceMicros,
    balanceUsd = "0.00",
)

fun aChatbotToolkit(
    slug: String = "toolkit",
    name: String = "Toolkit",
) = ChatbotToolkit(
    name = name,
    slug = slug,
)
