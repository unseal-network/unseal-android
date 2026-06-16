/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

sealed interface SkillsHomeEvents {
    data object OnAppear : SkillsHomeEvents
    data object Refresh : SkillsHomeEvents
    data object CreateSkill : SkillsHomeEvents
    data class SelectSkill(val id: String) : SkillsHomeEvents
    data class SelectMarketplaceSkill(val id: String) : SkillsHomeEvents
    data class SelectTab(val tab: SkillsHomeTab) : SkillsHomeEvents
    data class SearchQueryChanged(val query: String) : SkillsHomeEvents
    data object LoadNextMarketplacePage : SkillsHomeEvents
    data object ClearError : SkillsHomeEvents
}
