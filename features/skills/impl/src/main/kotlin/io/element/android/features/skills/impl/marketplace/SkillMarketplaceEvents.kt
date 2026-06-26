/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.marketplace

import io.element.android.features.skills.impl.shared.SkillFilterToken
import io.element.android.features.skills.impl.shared.SkillTagMode

sealed interface SkillMarketplaceEvents {
    data object OnAppear : SkillMarketplaceEvents
    data object Refresh : SkillMarketplaceEvents
    data class SearchQueryChanged(val query: String) : SkillMarketplaceEvents
    data object AddFilter : SkillMarketplaceEvents
    data object DismissFilterSheet : SkillMarketplaceEvents
    data class ApplyFilterToken(val token: SkillFilterToken) : SkillMarketplaceEvents
    data class RemoveFilterToken(val token: SkillFilterToken) : SkillMarketplaceEvents
    data object ClearFilters : SkillMarketplaceEvents
    data class TagModeChanged(val tagMode: SkillTagMode) : SkillMarketplaceEvents
    data object LoadNextPage : SkillMarketplaceEvents
    data class SelectSkill(val id: String) : SkillMarketplaceEvents
    data object ClearError : SkillMarketplaceEvents
}
