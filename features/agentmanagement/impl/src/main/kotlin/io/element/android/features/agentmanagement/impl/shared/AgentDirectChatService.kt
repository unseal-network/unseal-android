/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.shared

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UserId

interface AgentDirectChatService {
    suspend fun findExistingDirectRoom(userId: String): Result<RoomId?>
    suspend fun createDirectRoom(userId: String): Result<RoomId>
}

@ContributesBinding(SessionScope::class)
@Inject
class DefaultAgentDirectChatService(
    private val matrixClient: MatrixClient,
) : AgentDirectChatService {
    override suspend fun findExistingDirectRoom(userId: String): Result<RoomId?> {
        return matrixClient.findDM(UserId(userId))
    }

    override suspend fun createDirectRoom(userId: String): Result<RoomId> {
        return matrixClient.createDM(UserId(userId))
    }
}
