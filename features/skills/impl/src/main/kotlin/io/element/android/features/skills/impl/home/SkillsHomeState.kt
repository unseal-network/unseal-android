/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

import io.element.android.features.skills.impl.shared.matchesSkillQuery
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

enum class SkillsHomeTab {
    Mine,
    Marketplace,
}

data class SkillsHomeState(
    val skills: ImmutableList<ChatbotUserSkill>,
    val selectedTab: SkillsHomeTab,
    val marketplaceSkills: ImmutableList<ChatbotUserSkill>,
    val marketplaceTotal: Int?,
    val marketplacePage: Int,
    val marketplacePageSize: Int,
    val isLoading: Boolean,
    val isLoadingMarketplace: Boolean,
    val isLoadingMarketplaceNextPage: Boolean,
    val searchQuery: String,
    val error: String?,
    val eventSink: (SkillsHomeEvents) -> Unit,
) {
    val filteredSkills: ImmutableList<ChatbotUserSkill> = skills.filter { it.matchesSkillQuery(searchQuery) }.toImmutableList()
    val marketplaceHasMore: Boolean = marketplaceTotal?.let { marketplaceSkills.size < it } ?: (marketplaceSkills.size >= marketplacePageSize)
}
