/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.agentmanagement.impl.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.agentmanagement.impl.R
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelPlatform
import io.element.android.libraries.chatbot.api.model.channels.ChatbotChannelSummary
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.features.agentmanagement.impl.shared.shapeAwareClickable
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AgentDetailView(
    state: AgentDetailState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderModel = state.renderModel
    LaunchedEffect(Unit) { state.eventSink(AgentDetailEvents.OnAppear) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(renderModel.navigationTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(CompoundIcons.ChevronLeft(), stringResource(io.element.android.libraries.ui.strings.CommonStrings.action_go_back)) }
                },
                actions = {
                    if (state.canEdit) {
                        IconButton(onClick = { state.eventSink(AgentDetailEvents.Edit) }) {
                            Icon(CompoundIcons.Edit(), contentDescription = null)
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { Header(state, renderModel) }
            item { ActionButtons(state, renderModel) }
            renderModel.description?.let { description ->
                item {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            renderModel.soul?.let { soul ->
                item { SoulSection(state, soul) }
            }
            item { SkillsSection(state) }
            item { ChannelsSection(state) }
            item { RoomsSection(state) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (state.confirmStopAllTasks) {
        AlertDialog(
            onDismissRequest = { state.eventSink(AgentDetailEvents.CancelStopAllTasks) },
            title = { Text(stringResource(R.string.screen_agent_detail_stop_tasks_title)) },
            text = { Text(stringResource(R.string.screen_agent_detail_stop_tasks_description)) },
            confirmButton = {
                TextButton(onClick = { state.eventSink(AgentDetailEvents.ConfirmStopAllTasks) }) {
                    Text(stringResource(R.string.screen_agent_detail_stop_tasks_action), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(AgentDetailEvents.CancelStopAllTasks) }) {
                    Text(stringResource(io.element.android.libraries.ui.strings.CommonStrings.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun Header(state: AgentDetailState, renderModel: AgentDetailRenderModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            avatarData = AvatarData(renderModel.botName, renderModel.displayName, renderModel.avatarUrl, AvatarSize.UserHeader),
            avatarType = AvatarType.Room(),
            forcedAvatarSize = 88.dp,
        )
        Text(renderModel.displayName, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        renderModel.matrixId?.let { id ->
            Row(
                modifier = Modifier.shapeAwareClickable(RoundedCornerShape(50)) { state.eventSink(AgentDetailEvents.CopyAgentId) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(CompoundIcons.Copy(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            renderModel.providerModelLabel?.let { Chip(text = it, icon = CompoundIcons.Computer()) }
            Chip(
                text = stringResource(if (renderModel.isPublic) R.string.agent_management_visibility_public else R.string.agent_management_visibility_private),
                icon = if (renderModel.isPublic) CompoundIcons.Public() else CompoundIcons.Lock(),
            )
        }
    }
}

@Composable
private fun Chip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(percent = 50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun ActionButtons(state: AgentDetailState, renderModel: AgentDetailRenderModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = renderModel.canStartChat,
            onClick = { state.eventSink(AgentDetailEvents.StartChat) },
        ) {
            Icon(CompoundIcons.Chat(), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.agent_detail_start_chat))
        }
        if (state.canEdit) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isStoppingTasks,
                onClick = { state.eventSink(AgentDetailEvents.RequestStopAllTasks) },
            ) {
                Icon(CompoundIcons.Stop(), null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(
                        if (state.isStoppingTasks) R.string.screen_agent_detail_stopping_tasks else R.string.screen_agent_detail_stop_all_tasks
                    ),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SoulSection(state: AgentDetailState, soul: AgentSoulRenderModel) {
    Column {
        SectionLabel(stringResource(R.string.agent_detail_role))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = soul.text,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (soul.isExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (soul.canToggle) {
                Text(
                    modifier = Modifier.shapeAwareClickable(RoundedCornerShape(50)) { state.eventSink(AgentDetailEvents.ToggleSoulExpanded) },
                    text = stringResource(if (soul.isExpanded) R.string.agent_detail_collapse else R.string.agent_detail_expand),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SkillsSection(state: AgentDetailState) {
    val skills = state.renderModel.skills
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.agent_detail_owned_skills))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { state.eventSink(AgentDetailEvents.ManageSkills) }) { Text(stringResource(R.string.agent_detail_manage)) }
            Spacer(Modifier.size(8.dp))
        }
        if (skills.items.isEmpty()) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = { state.eventSink(AgentDetailEvents.ManageSkills) },
            ) {
                Icon(CompoundIcons.Plus(), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.agent_detail_add_skill))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                skills.items.take(3).forEach { skill ->
                    SkillChip(skill)
                }
            }
        }
    }
}

@Composable
private fun SkillChip(skill: AgentSkillChipRenderModel) {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(7.dp))
                .size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(skill.initial, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column {
            Text(skill.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            skill.description?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ChannelsSection(state: AgentDetailState) {
    val channels = state.channels
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.agent_detail_channels))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { state.eventSink(AgentDetailEvents.ManageChannels) }) { Text(stringResource(R.string.agent_detail_manage)) }
            Spacer(Modifier.size(8.dp))
        }
        if (channels.isEmpty()) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                onClick = { state.eventSink(AgentDetailEvents.ManageChannels) },
            ) {
                Icon(CompoundIcons.Plus(), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.agent_detail_connect_channel))
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                channels.take(3).forEach { channel ->
                    ChannelChip(channel) { state.eventSink(AgentDetailEvents.ManageChannels) }
                }
            }
        }
    }
}

@Composable
private fun ChannelChip(channel: ChatbotChannelSummary, onClick: () -> Unit) {
    val brand = when (channel.platform) {
        ChatbotChannelPlatform.Telegram -> Color(0xFF229ED9)
        ChatbotChannelPlatform.WeCom -> Color(0xFF07C160)
        ChatbotChannelPlatform.Feishu -> Color(0xFF3370FF)
        ChatbotChannelPlatform.Discord -> Color(0xFF5865F2)
    }
    val icon = if (channel.platform == ChatbotChannelPlatform.Telegram) CompoundIcons.Send() else CompoundIcons.Chat()
    val platformName = stringResource(
        when (channel.platform) {
            ChatbotChannelPlatform.Telegram -> R.string.agent_channels_platform_telegram
            ChatbotChannelPlatform.WeCom -> R.string.agent_channels_platform_wecom
            ChatbotChannelPlatform.Feishu -> R.string.agent_channels_platform_feishu
            ChatbotChannelPlatform.Discord -> R.string.agent_channels_platform_discord
        }
    )
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .shapeAwareClickable(RoundedCornerShape(10.dp), onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier.size(26.dp).background(brand, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Column {
            Text(channel.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(platformName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun RoomsSection(state: AgentDetailState) {
    val rooms = state.renderModel.rooms
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(stringResource(R.string.agent_detail_rooms))
            Spacer(Modifier.weight(1f))
            rooms.countLabel?.let { count ->
                Text(count, modifier = Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (rooms.items.isEmpty()) {
            Text(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                text = stringResource(if (state.isLoading) R.string.agent_detail_loading else R.string.agent_detail_no_rooms),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            rooms.items.forEachIndexed { index, room ->
                RoomRow(room) { state.eventSink(AgentDetailEvents.OpenRoom(room.roomId)) }
                if (index != rooms.items.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun RoomRow(room: AgentRoomRenderModel, onOpen: () -> Unit) {
    ListItem(
        modifier = Modifier.shapeAwareClickable(RoundedCornerShape(12.dp), onClick = onOpen),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(room.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = room.subtitle?.let { subtitle ->
            { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Avatar(
                avatarData = AvatarData(room.roomId, room.displayName, null, AvatarSize.RoomSelectRoomListItem),
                avatarType = AvatarType.Room(),
                forcedAvatarSize = 40.dp,
            )
        },
        trailingContent = { Icon(CompoundIcons.ChevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) },
    )
}

internal class AgentDetailStateProvider : PreviewParameterProvider<AgentDetailState> {
    override val values: Sequence<AgentDetailState>
        get() = sequenceOf(
            anAgentDetailState(),
            anAgentDetailState(rooms = persistentListOf()),
            anAgentDetailState(isStoppingTasks = true),
            anAgentDetailState(confirmStopAllTasks = true),
        )
}

private fun anAgentDetailState(
    rooms: kotlinx.collections.immutable.ImmutableList<ChatbotAgentRoom> = aSampleRooms(),
    isStoppingTasks: Boolean = false,
    confirmStopAllTasks: Boolean = false,
) = AgentDetailState(
    botName = "assistant",
    agent = ChatbotAgent(
        botName = "assistant",
        localpart = "assistant",
        serverName = "unseal.network",
        displayName = "Assistant",
        description = "A helpful general-purpose assistant for everyday tasks and questions.",
        provider = "openai",
        model = "gpt-4o",
        isPublic = true,
        soul = "You are a helpful assistant. Be concise, accurate and friendly. Always cite sources when relevant and avoid speculation when unsure.",
    ),
    rooms = rooms,
    agentSkills = persistentListOf(
        ChatbotUserSkill(id = "calendar", name = "Calendar", description = "Reads and creates events."),
        ChatbotUserSkill(id = "research", name = "Research", description = "Finds useful context."),
    ),
    channels = persistentListOf(
        ChatbotChannelSummary(installationId = "i1", platform = ChatbotChannelPlatform.Telegram, status = "active", label = "alpha_bot"),
        ChatbotChannelSummary(installationId = "i2", platform = ChatbotChannelPlatform.WeCom, status = "active", label = "wecom-9da2912f"),
    ),
    canEdit = true,
    isStoppingTasks = isStoppingTasks,
    confirmStopAllTasks = confirmStopAllTasks,
    isLoading = false,
    isStartingChat = false,
    isSoulExpanded = false,
    error = null,
    copiedAgentId = null,
    eventSink = {},
)

private fun aSampleRooms() = persistentListOf(
    ChatbotAgentRoom(roomId = "!abc:unseal.network", roomName = "Product team", joined = true),
    ChatbotAgentRoom(roomId = "!def:unseal.network", alias = "#general:unseal.network", joined = true),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun AgentDetailViewPreview(@PreviewParameter(AgentDetailStateProvider::class) state: AgentDetailState) = ElementPreview {
    AgentDetailView(state = state, onBackClick = {})
}
