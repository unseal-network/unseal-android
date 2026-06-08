/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.list

sealed interface AgentListEvents {
    data object OnAppear : AgentListEvents
    data object Refresh : AgentListEvents
    data class SearchQueryChanged(val query: String) : AgentListEvents
    data object CreateAgent : AgentListEvents
    data class SelectAgent(val botName: String) : AgentListEvents
    data object OpenSkills : AgentListEvents
    data object ClearError : AgentListEvents
}
