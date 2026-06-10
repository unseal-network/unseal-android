/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomkey

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

@SingleIn(RoomScope::class)
@Inject
class RoomAgentResolver(
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) {
    private var cache: Cache? = null

    suspend fun roomAgentUserIds(roomId: RoomId, activeMemberIds: Set<UserId>): Set<UserId> {
        val memberSignature = activeMemberIds
            .map { it.value }
            .sorted()
            .joinToString("|")
        cache?.takeIf { it.roomId == roomId && it.memberSignature == memberSignature }?.let { return it.userIds }

        val service = chatbotApiServiceFactory.createForHomeserver(matrixClient)
        val userIds = service.getRoomAgents(roomId.value)
            .getOrElse { return emptySet() }
            .agents
            .mapNotNull { it.mxid }
            .map(::UserId)
            .toSet()
        cache = Cache(roomId = roomId, memberSignature = memberSignature, userIds = userIds)
        return userIds
    }

    private data class Cache(
        val roomId: RoomId,
        val memberSignature: String,
        val userIds: Set<UserId>,
    )
}
