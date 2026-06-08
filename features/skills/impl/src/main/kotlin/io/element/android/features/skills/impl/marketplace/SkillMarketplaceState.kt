/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.marketplace

import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import kotlinx.collections.immutable.ImmutableList

data class SkillMarketplaceState(
    val skills: ImmutableList<ChatbotUserSkill>,
    val total: Int?,
    val page: Int,
    val pageSize: Int,
    val isLoading: Boolean,
    val isLoadingNextPage: Boolean,
    val searchQuery: String,
    val error: String?,
    val eventSink: (SkillMarketplaceEvents) -> Unit,
) {
    val hasMore: Boolean = total?.let { skills.size < it } ?: (skills.size >= pageSize)
}
