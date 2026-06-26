/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

import io.element.android.features.skills.impl.shared.SkillFilterState
import io.element.android.features.skills.impl.shared.hasAnyFacet
import io.element.android.features.skills.impl.shared.hasDiscoveryMetadata
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse
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
    val filterState: SkillFilterState,
    val facets: ChatbotSkillFacetsResponse,
    val isFilterSheetVisible: Boolean,
    val error: String?,
    val eventSink: (SkillsHomeEvents) -> Unit,
) {
    val filteredSkills: ImmutableList<ChatbotUserSkill> = skills.filter { filterState.copy(searchQuery = searchQuery).matches(it) }.toImmutableList()
    val filtersAvailable: Boolean = facets.hasAnyFacet() ||
        when (selectedTab) {
            SkillsHomeTab.Mine -> skills.any { it.hasDiscoveryMetadata() }
            SkillsHomeTab.Marketplace -> marketplaceSkills.any { it.hasDiscoveryMetadata() }
        }
    val marketplaceHasMore: Boolean = marketplaceTotal?.let { marketplaceSkills.size < it } ?: (marketplaceSkills.size >= marketplacePageSize)
    val hasActiveFiltersOrSearch: Boolean = searchQuery.isNotBlank() || filterState.activeTokenCount > 0
    val showClearFiltersForEmptyMine: Boolean = selectedTab == SkillsHomeTab.Mine && skills.isNotEmpty() && filteredSkills.isEmpty() && hasActiveFiltersOrSearch
}
