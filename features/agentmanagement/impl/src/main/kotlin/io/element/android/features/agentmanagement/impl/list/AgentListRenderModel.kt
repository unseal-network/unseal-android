/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.list

import io.element.android.features.agentmanagement.impl.shared.displayTitle
import io.element.android.features.agentmanagement.impl.shared.providerModelText
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.abs

data class AgentListRenderModel(
    val title: String,
    val searchPlaceholder: String,
    val createLabel: String,
    val skillsLabel: String,
    val emptyLabel: String,
    val isLoading: Boolean,
    val query: String,
    val items: ImmutableList<AgentListItemRenderModel>,
)

data class AgentListItemRenderModel(
    val botName: String,
    val title: String,
    val avatarUrl: String?,
    val providerModelLabel: String?,
    val description: String?,
    val isPublic: Boolean,
    val relativeTime: AgentRelativeTime?,
)

fun AgentListState.toRenderModel(nowMillis: Long = System.currentTimeMillis()): AgentListRenderModel {
    return AgentListRenderModel(
        title = "",
        searchPlaceholder = "",
        createLabel = "",
        skillsLabel = "",
        emptyLabel = "",
        isLoading = isLoading,
        query = searchQuery,
        items = filteredAgents.toRenderItems(nowMillis),
    )
}

fun List<ChatbotAgent>.toRenderItems(nowMillis: Long = System.currentTimeMillis()): ImmutableList<AgentListItemRenderModel> {
    return map { agent ->
        val isPublic = agent.isPublic == true
        AgentListItemRenderModel(
            botName = agent.botName,
            title = agent.displayTitle(),
            avatarUrl = agent.avatarUrl,
            providerModelLabel = agent.providerModelText(),
            description = agent.description?.takeIf { it.isNotBlank() },
            isPublic = isPublic,
            relativeTime = agent.relativeTime(nowMillis),
        )
    }.toImmutableList()
}

data class AgentRelativeTime(
    val amount: Long,
    val unit: AgentRelativeTimeUnit,
    val isFuture: Boolean,
)

enum class AgentRelativeTimeUnit {
    Now,
    Minute,
    Hour,
    Day,
}

private fun ChatbotAgent.relativeTime(nowMillis: Long): AgentRelativeTime? {
    val timestamp = updatedAt ?: createdAt ?: return null
    if (timestamp <= 0L) return null
    val diffMillis = nowMillis - timestamp
    val future = diffMillis < 0
    val absMillis = abs(diffMillis)
    val minute = 60_000L
    val hour = 60 * minute
    val day = 24 * hour
    return when {
        absMillis < minute -> AgentRelativeTime(0, AgentRelativeTimeUnit.Now, future)
        absMillis < hour -> AgentRelativeTime(absMillis / minute, AgentRelativeTimeUnit.Minute, future)
        absMillis < day -> AgentRelativeTime(absMillis / hour, AgentRelativeTimeUnit.Hour, future)
        absMillis < 30 * day -> AgentRelativeTime(absMillis / day, AgentRelativeTimeUnit.Day, future)
        else -> null
    }
}
