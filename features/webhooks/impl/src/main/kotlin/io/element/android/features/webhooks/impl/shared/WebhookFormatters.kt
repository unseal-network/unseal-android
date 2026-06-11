/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.shared

import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventCatalogResponse
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookEventSource
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTriggerStatus

fun ChatbotWebhookTrigger.isEnabled(): Boolean = status == ChatbotWebhookTriggerStatus.Enabled

fun ChatbotWebhookTrigger.matchesWebhookQuery(query: String): Boolean {
    val normalized = query.trim().lowercase()
    if (normalized.isEmpty()) return true
    return listOf(
        name,
        source.orEmpty(),
        eventTypes.joinToString(separator = " "),
        agentId,
        actionPrompt,
        roomId,
    ).any { it.lowercase().contains(normalized) }
}

fun ChatbotWebhookTrigger.withStatus(enabled: Boolean): ChatbotWebhookTrigger {
    return copy(status = if (enabled) ChatbotWebhookTriggerStatus.Enabled else ChatbotWebhookTriggerStatus.Disabled)
}

fun ChatbotWebhookEventCatalogResponse.sourceSlugFor(trigger: ChatbotWebhookTrigger): String? {
    return trigger.eventTypes.firstNotNullOfOrNull { eventType ->
        sources.firstOrNull { source ->
            source.eventTypes.any { it.eventType == eventType }
        }?.source
    }
}

/**
 * Mirrors iOS `resolveSourceSlug(for:)`: find the event source whose event types contain one of
 * the trigger's event types and return its `source` slug, used to build the composio logo URL.
 */
fun resolveSourceSlug(
    trigger: ChatbotWebhookTrigger,
    eventSources: List<ChatbotWebhookEventSource>,
): String? {
    return trigger.eventTypes.firstNotNullOfOrNull { eventType ->
        eventSources.firstOrNull { source ->
            source.eventTypes.any { it.eventType == eventType }
        }?.source
    }
}

/** The composio logo URL for a source slug, mirroring iOS `https://logos.composio.dev/api/<slug>`. */
fun composioLogoUrl(slug: String): String = "https://logos.composio.dev/api/$slug"

fun ChatbotWebhookTrigger.displaySource(fallback: String? = null): String {
    return source ?: fallback ?: eventTypes.firstOrNull()?.substringBefore('.') ?: "Webhook"
}
