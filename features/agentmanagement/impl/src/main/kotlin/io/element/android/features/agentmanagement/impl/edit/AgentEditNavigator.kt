/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.edit

import io.element.android.libraries.matrix.api.core.RoomId

interface AgentEditNavigator {
    fun onCreated(botName: String, directRoomId: RoomId?)
    fun onUpdated(botName: String)
}
