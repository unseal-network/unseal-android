/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

import io.element.android.features.skills.impl.shared.SkillFilterToken
import io.element.android.features.skills.impl.shared.SkillTagMode

sealed interface SkillsHomeEvents {
    data object OnAppear : SkillsHomeEvents
    data object Refresh : SkillsHomeEvents
    data object CreateSkill : SkillsHomeEvents
    data class SelectSkill(val id: String) : SkillsHomeEvents
    data class SelectMarketplaceSkill(val id: String) : SkillsHomeEvents
    data class SelectTab(val tab: SkillsHomeTab) : SkillsHomeEvents
    data class SearchQueryChanged(val query: String) : SkillsHomeEvents
    data object AddFilter : SkillsHomeEvents
    data object DismissFilterSheet : SkillsHomeEvents
    data class ApplyFilterToken(val token: SkillFilterToken) : SkillsHomeEvents
    data class RemoveFilterToken(val token: SkillFilterToken) : SkillsHomeEvents
    data object ClearFilters : SkillsHomeEvents
    data class TagModeChanged(val tagMode: SkillTagMode) : SkillsHomeEvents
    data object LoadNextMarketplacePage : SkillsHomeEvents
    data object ClearError : SkillsHomeEvents
}
