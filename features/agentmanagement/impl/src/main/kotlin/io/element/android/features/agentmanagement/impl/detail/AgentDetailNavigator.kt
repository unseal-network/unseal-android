/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.detail

import io.element.android.libraries.matrix.api.core.RoomIdOrAlias

interface AgentDetailNavigator {
    fun onEdit(botName: String)
    fun onOpenRoom(roomIdOrAlias: RoomIdOrAlias)
    fun onOpenSkills(botName: String)
    fun onManageChannels(agentId: String)
}
