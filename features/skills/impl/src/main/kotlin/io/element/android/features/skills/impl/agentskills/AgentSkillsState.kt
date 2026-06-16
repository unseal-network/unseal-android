/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.agentskills

import io.element.android.features.skills.impl.shared.matchesSkillQuery
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableList

enum class AgentSkillsTab {
    Mine,
    Public,
}

data class AgentSkillSaveFailure(
    val skillId: String,
    val message: String,
)

data class AgentSkillsState(
    val botName: String,
    val attachedSkills: ImmutableList<ChatbotUserSkill>,
    val userSkills: ImmutableList<ChatbotUserSkill>,
    val selectedSkills: ImmutableList<ChatbotUserSkill>,
    val originalSkillIds: ImmutableSet<String>,
    val selectedSkillIds: ImmutableSet<String>,
    val selectedTab: AgentSkillsTab,
    val searchQuery: String,
    val publicSkills: ImmutableList<ChatbotUserSkill>,
    val publicTotal: Int?,
    val publicPage: Int,
    val publicPageSize: Int,
    val publicHasMore: Boolean,
    val isLoading: Boolean,
    val isLoadingPublic: Boolean,
    val isLoadingPublicNextPage: Boolean,
    val isSaving: Boolean,
    val saveFailures: ImmutableList<AgentSkillSaveFailure>,
    val error: String?,
    val eventSink: (AgentSkillsEvents) -> Unit,
) {
    val filteredUserSkills: ImmutableList<ChatbotUserSkill> = userSkills.filter { it.matchesSkillQuery(searchQuery) }.toImmutableList()
    val newSelectedSkillIds: Set<String> = selectedSkillIds - originalSkillIds
    val deselectedOriginalCount: Int = originalSkillIds.count { it !in selectedSkillIds }
}
