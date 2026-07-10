/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.agentmanagement.impl.R
import io.element.android.features.agentmanagement.impl.shared.AgentDirectChatService
import io.element.android.features.agentmanagement.impl.shared.agentMatrixUserId
import io.element.android.features.agentmanagement.impl.shared.copyableAgentId
import io.element.android.libraries.androidutils.clipboard.ClipboardHelper
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.chatbot.api.ChatbotApiServiceFactory
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomIdOrAlias
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@AssistedInject
class AgentDetailPresenter(
    @Assisted private val botName: String,
    @Assisted private val initialMatrixUserId: String?,
    @Assisted private val navigator: AgentDetailNavigator,
    private val matrixClient: MatrixClient,
    private val chatbotApiServiceFactory: ChatbotApiServiceFactory,
    private val directChatService: AgentDirectChatService,
    private val clipboardHelper: ClipboardHelper,
) : Presenter<AgentDetailState> {
    @AssistedFactory
    interface Factory {
        fun create(botName: String, initialMatrixUserId: String?, navigator: AgentDetailNavigator): AgentDetailPresenter
    }

    @Composable
    override fun present(): AgentDetailState {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        var agent by remember { mutableStateOf<ChatbotAgent?>(null) }
        var rooms by remember { mutableStateOf(emptyList<ChatbotAgentRoom>()) }
        var agentSkills by remember { mutableStateOf(emptyList<ChatbotUserSkill>()) }
        var channels by remember { mutableStateOf(emptyList<ChatbotChannelSummary>()) }
        var isLoading by remember { mutableStateOf(true) }
        var canEdit by remember { mutableStateOf(false) }
        var isStartingChat by remember { mutableStateOf(false) }
        var isStoppingTasks by remember { mutableStateOf(false) }
        var confirmStopAllTasks by remember { mutableStateOf(false) }
        var isSoulExpanded by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var copiedAgentId by remember { mutableStateOf<String?>(null) }
        var hasLoadedOnce by remember { mutableStateOf(false) }

        fun string(resId: Int): String = context.getString(resId)
        fun Throwable.messageOr(resId: Int): String = message?.takeIf { it.isNotBlank() } ?: string(resId)

        fun loadExtras(isInitial: Boolean) {
            if (isInitial && hasLoadedOnce) return
            coroutineScope.launch {
                isLoading = true
                val api = chatbotApiServiceFactory.createForHomeserver(matrixClient)
                val previousAgent = agent
                api.getAgent(botName)
                    .onSuccess { freshAgent ->
                        agent = freshAgent
                        error = null
                    }
                    .onFailure {
                        if (previousAgent == null) {
                            error = it.messageOr(R.string.agent_detail_error_load)
                        }
                    }
                // Ownership: only agents in the current user's own agent list are editable.
                api.listAgents()
                    .onSuccess { mine -> canEdit = mine.any { it.botName == botName } }
                api.listAgentRooms(botName)
                    .onSuccess { freshRooms ->
                        rooms = freshRooms
                    }
                api.listAgentSkills(botName)
                    .onSuccess { freshSkills ->
                        agentSkills = freshSkills
                    }
                // Channels live on the agent-api and are keyed by the agent's Matrix id.
                agent?.agentMatrixUserId()?.let { agentId ->
                    chatbotApiServiceFactory.createForUnsealApi(matrixClient)
                        .listAgentChannels(agentId)
                        .onSuccess { channels = it }
                }
                isLoading = false
                hasLoadedOnce = true
            }
        }

        fun startChat() {
            val userId = agent?.agentMatrixUserId()
            if (userId == null) {
                error = string(R.string.agent_detail_error_no_matrix_user_id)
                return
            }
            coroutineScope.launch {
                isStartingChat = true
                directChatService.findExistingDirectRoom(userId)
                    .fold(
                        onSuccess = { existingRoomId ->
                            if (existingRoomId != null) {
                                navigator.onOpenRoom(existingRoomId.toRoomIdOrAlias())
                            } else {
                                directChatService.createDirectRoom(userId)
                                    .onSuccess { navigator.onOpenRoom(it.toRoomIdOrAlias()) }
                                    .onFailure { error = it.messageOr(R.string.agent_detail_error_start_chat) }
                            }
                        },
                        onFailure = {
                            directChatService.createDirectRoom(userId)
                                .onSuccess { navigator.onOpenRoom(it.toRoomIdOrAlias()) }
                                .onFailure { createError -> error = createError.messageOr(R.string.agent_detail_error_start_chat) }
                        }
                    )
                isStartingChat = false
            }
        }

        fun leaveRoom(roomId: String) {
            coroutineScope.launch {
                isLoading = true
                chatbotApiServiceFactory.createForHomeserver(matrixClient)
                    .agentLeaveRoom(botName, roomId)
                    .onSuccess { loadExtras(isInitial = false) }
                    .onFailure { error = it.messageOr(R.string.agent_detail_error_leave_room) }
                isLoading = false
            }
        }

        fun stopAllTasks() {
            val agentId = agent?.agentMatrixUserId()
            if (!canEdit || agentId == null) return
            coroutineScope.launch {
                isStoppingTasks = true
                chatbotApiServiceFactory.createForHomeserver(matrixClient)
                    .abortAgent(agentId, "Stopped by owner from Agent details")
                    .onFailure { error = it.messageOr(R.string.error_agent_detail_stop_tasks) }
                isStoppingTasks = false
            }
        }

        fun handleEvent(event: AgentDetailEvents) {
            when (event) {
                AgentDetailEvents.OnAppear -> loadExtras(isInitial = true)
                AgentDetailEvents.Refresh -> loadExtras(isInitial = false)
                AgentDetailEvents.Edit -> navigator.onEdit(agent?.botName ?: botName)
                AgentDetailEvents.CopyAgentId -> {
                    val idToCopy = agent?.copyableAgentId() ?: botName
                    clipboardHelper.copyPlainText(idToCopy)
                    copiedAgentId = idToCopy
                }
                AgentDetailEvents.ToggleSoulExpanded -> isSoulExpanded = !isSoulExpanded
                AgentDetailEvents.ManageSkills -> navigator.onOpenSkills(botName)
                AgentDetailEvents.ManageChannels -> {
                    val agentId = agent?.agentMatrixUserId()
                    if (agentId == null) {
                        error = string(R.string.agent_detail_error_no_matrix_user_id)
                    } else {
                        navigator.onManageChannels(agentId)
                    }
                }
                AgentDetailEvents.StartChat -> startChat()
                is AgentDetailEvents.OpenRoom -> RoomIdOrAlias.from(event.roomId)
                    ?.let(navigator::onOpenRoom)
                    ?: run { error = string(R.string.agent_detail_error_invalid_room_id) }
                is AgentDetailEvents.LeaveRoom -> leaveRoom(event.roomId)
                AgentDetailEvents.RequestStopAllTasks -> if (canEdit) { confirmStopAllTasks = true }
                AgentDetailEvents.ConfirmStopAllTasks -> {
                    confirmStopAllTasks = false
                    stopAllTasks()
                }
                AgentDetailEvents.CancelStopAllTasks -> confirmStopAllTasks = false
                AgentDetailEvents.ClearError -> error = null
            }
        }

        return AgentDetailState(
            botName = botName,
            initialMatrixUserId = initialMatrixUserId,
            agent = agent,
            rooms = rooms.toImmutableList(),
            agentSkills = agentSkills.toImmutableList(),
            channels = channels.toImmutableList(),
            isLoading = isLoading,
            canEdit = canEdit,
            isStoppingTasks = isStoppingTasks,
            confirmStopAllTasks = confirmStopAllTasks,
            isStartingChat = isStartingChat,
            isSoulExpanded = isSoulExpanded,
            error = error,
            copiedAgentId = copiedAgentId,
            eventSink = ::handleEvent,
        )
    }
}
