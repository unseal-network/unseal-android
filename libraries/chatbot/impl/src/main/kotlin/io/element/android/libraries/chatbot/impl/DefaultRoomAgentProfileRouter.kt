/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.chatbot.impl

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.RoomAgentProfileRouter
import io.element.android.libraries.chatbot.api.model.rooms.ChatbotRoomAgent
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RoomAgentProfileRouter>())
@Inject
class DefaultRoomAgentProfileRouter(
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : RoomAgentProfileRouter {
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, List<ChatbotRoomAgent>>()

    override suspend fun agentBotNameFor(roomId: RoomId, userId: UserId): String? {
        return roomAgents(roomId)
            .firstOrNull { it.agentId == userId.value || it.mxid == userId.value }
            ?.botName()
    }

    override suspend fun directRoomAgentBotName(roomId: RoomId): String? {
        // Only a DM-like room (1:1 / small) should open the agent profile from the header; larger
        // rooms keep the normal room-details page.
        val info = runCatching { matrixClient.getRoom(roomId)?.info() }.getOrNull() ?: return null
        if (!info.isDm && info.joinedMembersCount > 2) return null
        return roomAgents(roomId).firstOrNull()?.botName()
    }

    private suspend fun roomAgents(roomId: RoomId): List<ChatbotRoomAgent> = mutex.withLock {
        cache[roomId.value]?.let { return it }
        val agents = runCatching {
            chatbotApiServiceFactory.createForHomeserver(matrixClient)
                .getRoomAgents(roomId.value)
                .getOrNull()
                ?.agents
        }.getOrNull().orEmpty()
        cache[roomId.value] = agents
        agents
    }

    private fun ChatbotRoomAgent.botName(): String? {
        val matrixId = mxid?.takeIf { it.isNotBlank() } ?: agentId
        return matrixId.substringAfter("@").substringBefore(":").takeIf { it.isNotBlank() }
    }
}
