/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.list

interface AgentListNavigator {
    fun onCreateAgent()
    fun onOpenAgent(botName: String)
    fun onOpenSkills()
}
