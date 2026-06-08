/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.agentskills

import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill

sealed interface AgentSkillsEvents {
    data object OnAppear : AgentSkillsEvents
    data object Refresh : AgentSkillsEvents
    data class SelectTab(val tab: AgentSkillsTab) : AgentSkillsEvents
    data class SearchQueryChanged(val query: String) : AgentSkillsEvents
    data class ToggleSkill(val skill: ChatbotUserSkill) : AgentSkillsEvents
    data object LoadNextPublicPage : AgentSkillsEvents
    data object Save : AgentSkillsEvents
    data object ClearError : AgentSkillsEvents
}
