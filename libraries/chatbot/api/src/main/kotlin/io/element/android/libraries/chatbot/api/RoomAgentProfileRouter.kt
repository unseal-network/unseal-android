/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.api

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

/**
 * Single decision point for "open profile" navigation. Every entry that could open a member/room
 * profile (timeline avatar tap, room header tap, member list tap) must route through this so the
 * agent-vs-user choice lives in one place and no entry point is missed.
 */
interface RoomAgentProfileRouter {
    /**
     * Returns the agent botName when [userId] is an agent in [roomId], or null for a normal Matrix
     * user (caller then opens the standard user profile).
     */
    suspend fun agentBotNameFor(roomId: RoomId, userId: UserId): String?

    /**
     * Returns the room's agent botName when [roomId] is a DM-like room whose counterpart is an
     * agent (so the room header opens the agent profile first), or null otherwise (open room details).
     */
    suspend fun directRoomAgentBotName(roomId: RoomId): String?
}
