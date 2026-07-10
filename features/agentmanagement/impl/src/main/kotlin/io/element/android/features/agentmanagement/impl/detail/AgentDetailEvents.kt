/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.detail

sealed interface AgentDetailEvents {
    data object OnAppear : AgentDetailEvents
    data object Refresh : AgentDetailEvents
    data object Edit : AgentDetailEvents
    data object CopyAgentId : AgentDetailEvents
    data object ToggleSoulExpanded : AgentDetailEvents
    data object ManageSkills : AgentDetailEvents
    data object ManageChannels : AgentDetailEvents
    data object StartChat : AgentDetailEvents
    data class OpenRoom(val roomId: String) : AgentDetailEvents
    data class LeaveRoom(val roomId: String) : AgentDetailEvents
    data object RequestStopAllTasks : AgentDetailEvents
    data object ConfirmStopAllTasks : AgentDetailEvents
    data object CancelStopAllTasks : AgentDetailEvents
    data object ClearError : AgentDetailEvents
}
