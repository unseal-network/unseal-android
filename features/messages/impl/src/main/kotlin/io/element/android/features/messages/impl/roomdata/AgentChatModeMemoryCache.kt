/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Process-lifetime room agent chat mode cache. Mirrors iOS: survives navigating in/out of a room
 * but is cleared on app restart.
 */
object AgentChatModeMemoryCache {
    private val targetDeviceIdByRoom = mutableMapOf<String, String>()

    fun targetDeviceIdFor(roomId: RoomId): String? {
        return targetDeviceIdByRoom[roomId.value]
    }

    fun setTargetDeviceId(roomId: RoomId, targetDeviceId: String?) {
        if (targetDeviceId.isNullOrBlank()) {
            targetDeviceIdByRoom.remove(roomId.value)
        } else {
            targetDeviceIdByRoom[roomId.value] = targetDeviceId
        }
    }
}
