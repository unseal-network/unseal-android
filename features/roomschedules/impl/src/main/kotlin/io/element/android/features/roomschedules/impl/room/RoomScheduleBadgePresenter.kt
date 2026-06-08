/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.room

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.roomschedules.api.room.RoomScheduleBadgeEvents
import io.element.android.features.roomschedules.api.room.RoomScheduleBadgePresenter
import io.element.android.features.roomschedules.api.room.RoomScheduleBadgeState
import io.element.android.features.roomschedules.impl.model.isEnabled
import io.element.android.features.roomschedules.impl.model.matrixUserId
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.joinedRoomMembers
import io.element.android.libraries.matrix.api.room.roomMembers
import kotlinx.coroutines.launch

@AssistedInject
class DefaultRoomScheduleBadgePresenter(
    @Assisted private val roomId: RoomId,
    @Assisted private val joinedRoom: JoinedRoom,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
) : RoomScheduleBadgePresenter {
    @AssistedFactory
    @ContributesBinding(SessionScope::class)
    interface Factory : RoomScheduleBadgePresenter.Factory {
        override fun create(roomId: RoomId, joinedRoom: JoinedRoom): DefaultRoomScheduleBadgePresenter
    }

    @Composable
    override fun present(): RoomScheduleBadgeState {
        val coroutineScope = rememberCoroutineScope()
        var hasLoadedOnce by remember { mutableStateOf(false) }
        var isLoading by remember { mutableStateOf(false) }
        var isVisible by remember { mutableStateOf(false) }
        var activeScheduleCount by remember { mutableStateOf(0) }
        var error by remember { mutableStateOf<String?>(null) }

        fun errorMessage(throwable: Throwable): String {
            return throwable.message ?: throwable::class.simpleName ?: throwable.toString()
        }

        fun load(isInitial: Boolean) {
            if (isInitial && hasLoadedOnce) return
            coroutineScope.launch {
                isLoading = true
                val service = chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                val agentsResult = service.listAgents()
                val schedulesResult = service.listSchedules(roomId.value)
                if (agentsResult.isFailure || schedulesResult.isFailure) {
                    isVisible = false
                    activeScheduleCount = 0
                    error = errorMessage(agentsResult.exceptionOrNull() ?: schedulesResult.exceptionOrNull() ?: RuntimeException("Failed to load schedules"))
                    isLoading = false
                    hasLoadedOnce = true
                    return@launch
                }

                if (joinedRoom.membersStateFlow.value.roomMembers().isNullOrEmpty()) {
                    joinedRoom.updateMembers()
                }
                val joinedMemberIds = joinedRoom.membersStateFlow.value
                    .joinedRoomMembers()
                    .map { it.userId.value }
                    .toSet()
                val hasAgentInRoom = agentsResult.getOrThrow().any { agent ->
                    agent.matrixUserId() in joinedMemberIds
                }
                isVisible = hasAgentInRoom
                activeScheduleCount = if (hasAgentInRoom) {
                    schedulesResult.getOrThrow().count { it.isEnabled() }
                } else {
                    0
                }
                error = null
                isLoading = false
                hasLoadedOnce = true
            }
        }

        fun handleEvent(event: RoomScheduleBadgeEvents) {
            when (event) {
                RoomScheduleBadgeEvents.OnAppear -> load(isInitial = true)
                RoomScheduleBadgeEvents.Refresh -> load(isInitial = false)
            }
        }

        return RoomScheduleBadgeState(
            isLoading = isLoading,
            isVisible = isVisible,
            activeScheduleCount = activeScheduleCount,
            error = error,
            eventSink = ::handleEvent,
        )
    }
}
