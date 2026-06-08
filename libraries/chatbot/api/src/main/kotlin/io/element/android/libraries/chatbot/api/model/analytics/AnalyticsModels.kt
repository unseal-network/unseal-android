/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api.model.analytics

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsModelDaily(
    val model: String,
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int,
)

@Serializable
data class AnalyticsDayBucket(
    val date: String,
    val models: List<AnalyticsModelDaily> = emptyList(),
)

@Serializable
data class AnalyticsModelSummary(
    val model: String,
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int,
    val pct: Double,
)

@Serializable
data class AnalyticsAgentSummary(
    @SerialName("agent_id")
    val agentId: String,
    @SerialName("display_name")
    val displayName: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("input_tokens")
    val inputTokens: Int,
    @SerialName("output_tokens")
    val outputTokens: Int,
    @SerialName("cache_read_tokens")
    val cacheReadTokens: Int? = null,
    @SerialName("cache_creation_tokens")
    val cacheCreationTokens: Int? = null,
    @SerialName("call_count")
    val callCount: Int,
    val pct: Double,
    val models: List<AnalyticsModelDaily>? = null,
)

@Serializable
data class AnalyticsTokensResponse(
    val period: String,
    val daily: List<AnalyticsDayBucket> = emptyList(),
    @SerialName("summary_by_model")
    val summaryByModel: List<AnalyticsModelSummary> = emptyList(),
    @SerialName("summary_by_agent")
    val summaryByAgent: List<AnalyticsAgentSummary> = emptyList(),
)
