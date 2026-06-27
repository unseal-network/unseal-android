/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.detail

import io.element.android.features.agentmanagement.impl.shared.agentMatrixUserId
import io.element.android.features.agentmanagement.impl.shared.copyableAgentId
import io.element.android.features.agentmanagement.impl.shared.displayTitle
import io.element.android.features.agentmanagement.impl.shared.matrixId
import io.element.android.features.agentmanagement.impl.shared.providerModelText
import io.element.android.libraries.chatbot.api.ChatbotConfig
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class AgentDetailRenderModel(
    val navigationTitle: String,
    val botName: String,
    val avatarUrl: String?,
    val displayName: String,
    val matrixId: String?,
    val providerModelLabel: String?,
    val visibilityLabel: String,
    val isPublic: Boolean,
    val description: String?,
    val soul: AgentSoulRenderModel?,
    val skills: AgentSkillsRenderModel,
    val rooms: AgentRoomsRenderModel,
    val canStartChat: Boolean,
    val startChatLabel: String,
    val editLabel: String,
    val copyableAgentId: String,
    val agentProfileUrl: String?,
    val connectTitle: String,
    val connectHint: String,
)

data class AgentSoulRenderModel(
    val title: String,
    val text: String,
    val isExpanded: Boolean,
    val canToggle: Boolean,
    val toggleLabel: String,
)

data class AgentSkillsRenderModel(
    val title: String,
    val manageLabel: String,
    val addFirstLabel: String,
    val items: ImmutableList<AgentSkillChipRenderModel>,
)

data class AgentSkillChipRenderModel(
    val id: String,
    val name: String,
    val description: String?,
    val initial: String,
    val colorKey: String,
)

data class AgentRoomsRenderModel(
    val title: String,
    val countLabel: String?,
    val emptyLabel: String,
    val loadingLabel: String,
    val items: ImmutableList<AgentRoomRenderModel>,
)

data class AgentRoomRenderModel(
    val roomId: String,
    val displayName: String,
    val subtitle: String?,
)

fun AgentDetailState.toRenderModel(): AgentDetailRenderModel {
    val currentAgent = agent
    val displayName = currentAgent?.displayTitle() ?: botName
    val matrixId = currentAgent?.matrixId() ?: initialMatrixUserId
    val agentMatrixUserId = currentAgent?.agentMatrixUserId()
    val isPublic = currentAgent?.isPublic == true
    return AgentDetailRenderModel(
        navigationTitle = displayName,
        botName = botName,
        avatarUrl = currentAgent?.avatarUrl,
        displayName = displayName,
        matrixId = matrixId,
        providerModelLabel = currentAgent?.providerModelText(),
        visibilityLabel = if (isPublic) "Public" else "Private",
        isPublic = isPublic,
        description = currentAgent?.description?.takeIf { it.isNotBlank() },
        soul = currentAgent?.soul?.takeIf { it.isNotBlank() }?.let {
            AgentSoulRenderModel(
                title = "Role prompt",
                text = it,
                isExpanded = isSoulExpanded,
                canToggle = it.length > 120,
                toggleLabel = if (isSoulExpanded) "Collapse ↑" else "Expand ↓",
            )
        },
        skills = AgentSkillsRenderModel(
            title = "Owned skills",
            manageLabel = "Manage",
            addFirstLabel = "Add a skill to this agent",
            items = agentSkills.toSkillChips(),
        ),
        rooms = AgentRoomsRenderModel(
            title = "Joined rooms",
            countLabel = rooms.takeIf { it.isNotEmpty() }?.size?.toString(),
            emptyLabel = "Not joined to any rooms yet",
            loadingLabel = "Loading...",
            items = rooms.toRoomItems(),
        ),
        canStartChat = agentMatrixUserId != null && !isLoading && !isStartingChat,
        startChatLabel = "Start chat",
        editLabel = "Edit",
        copyableAgentId = currentAgent?.copyableAgentId() ?: botName,
        agentProfileUrl = (currentAgent?.localpart ?: botName)
            .takeIf { it.isNotBlank() }
            ?.let { "${ChatbotConfig.WEBSITE_BASE_URL}/@$it" },
        connectTitle = "Connect me",
        connectHint = "Send this to your agent",
    )
}

fun List<ChatbotUserSkill>.toSkillChips(): ImmutableList<AgentSkillChipRenderModel> {
    return mapIndexed { index, skill ->
        AgentSkillChipRenderModel(
            id = skill.id,
            name = skill.name,
            description = skill.description?.takeIf { it.isNotBlank() },
            initial = skill.name.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            colorKey = skill.id.takeIf { it.isNotBlank() } ?: index.toString(),
        )
    }.toImmutableList()
}

fun List<ChatbotAgentRoom>.toRoomItems(): ImmutableList<AgentRoomRenderModel> {
    return map { room ->
        val displayName = room.displayName()
        AgentRoomRenderModel(
            roomId = room.roomId,
            displayName = displayName,
            subtitle = room.roomId.takeIf { displayName != it },
        )
    }.toImmutableList()
}
