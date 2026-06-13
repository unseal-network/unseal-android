/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.roomdata

import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeRoomUnsealContextStore(
    initialContext: AsyncData<RoomUnsealContext> = AsyncData.Uninitialized,
) : RoomUnsealContextStore {
    private val mutableContext = MutableStateFlow(initialContext)

    override val context: StateFlow<AsyncData<RoomUnsealContext>> = mutableContext
    var refreshCount: Int = 0
        private set

    override suspend fun refresh(force: Boolean) {
        refreshCount++
    }

    fun givenContext(context: RoomUnsealContext) {
        mutableContext.value = AsyncData.Success(context)
    }

    fun givenEmptyContext(roomId: RoomId) {
        givenContext(
            RoomUnsealContext.from(
                roomId = roomId,
                members = emptyList(),
                snapshot = RoomUnsealDataSnapshot(),
            )
        )
    }
}
