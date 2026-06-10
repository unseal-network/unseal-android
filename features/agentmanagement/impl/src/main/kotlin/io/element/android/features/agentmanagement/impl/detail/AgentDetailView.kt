/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.agentmanagement.impl.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.agentmanagement.impl.shared.displayTitle
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AgentDetailView(
    state: AgentDetailState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { state.eventSink(AgentDetailEvents.OnAppear) }
    val uriHandler = LocalUriHandler.current
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.navigationTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(CompoundIcons.ChevronLeft(), "返回") }
                },
                actions = {
                    state.agentProfileUrl?.let { url ->
                        IconButton(onClick = { uriHandler.openUri(url) }) { Icon(CompoundIcons.PopOut(), "智能体资料") }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item { Header(state) }
            item { ActionButtons(state) }
            state.agent?.description?.takeIf { it.isNotBlank() }?.let { description ->
                item {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.agent?.soul?.takeIf { it.isNotBlank() }?.let { soul ->
                item { SoulSection(state, soul) }
            }
            item { SkillsSection(state) }
            item { RoomsSection(state) }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun Header(state: AgentDetailState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(
            avatarData = AvatarData(state.botName, state.agent?.displayTitle() ?: state.botName, state.agent?.avatarUrl, AvatarSize.UserHeader),
            avatarType = AvatarType.Room(),
            forcedAvatarSize = 88.dp,
        )
        Text(state.navigationTitle, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        state.matrixId?.let { id ->
            Row(
                modifier = Modifier.clickable { state.eventSink(AgentDetailEvents.CopyAgentId) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(CompoundIcons.Copy(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.providerModelText?.let { Chip(text = it, icon = CompoundIcons.Computer()) }
            Chip(text = if (state.agent?.isPublic == true) "公开" else "私密", icon = if (state.agent?.isPublic == true) CompoundIcons.Public() else CompoundIcons.Lock())
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
private fun ActionButtons(state: AgentDetailState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(
            modifier = Modifier.weight(1f),
            enabled = state.canStartChat,
            onClick = { state.eventSink(AgentDetailEvents.StartChat) },
        ) {
            Icon(CompoundIcons.Chat(), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("开始聊天")
        }
        OutlinedButton(
            modifier = Modifier.weight(1f),
            onClick = { state.eventSink(AgentDetailEvents.Edit) },
        ) {
            Icon(CompoundIcons.Edit(), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("编辑")
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
private fun SoulSection(state: AgentDetailState, soul: String) {
    Column {
        SectionLabel("角色设定")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = soul,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (state.isSoulExpanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (soul.length > 120) {
                Text(
                    modifier = Modifier.clickable { state.eventSink(AgentDetailEvents.ToggleSoulExpanded) },
                    text = if (state.isSoulExpanded) "收起 ↑" else "展开 ↓",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SkillsSection(state: AgentDetailState) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("拥有的技能")
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { state.eventSink(AgentDetailEvents.ManageSkills) }) { Text("管理") }
            Spacer(Modifier.size(8.dp))
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            onClick = { state.eventSink(AgentDetailEvents.ManageSkills) },
        ) {
            Icon(CompoundIcons.Plus(), null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("为此 Agent 添加技能")
        }
    }
}

@Composable
private fun RoomsSection(state: AgentDetailState) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("已加入的房间")
            Spacer(Modifier.weight(1f))
            if (state.rooms.isNotEmpty()) {
                Text(state.rooms.size.toString(), modifier = Modifier.padding(end = 16.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (state.rooms.isEmpty()) {
            Text(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                text = if (state.isLoading) "正在加载..." else "尚未加入任何房间",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        } else {
            state.rooms.forEachIndexed { index, room ->
                RoomRow(room) { state.eventSink(AgentDetailEvents.OpenRoom(room.roomId)) }
                if (index != state.rooms.lastIndex) HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
        }
    }
}

@Composable
private fun RoomRow(room: ChatbotAgentRoom, onOpen: () -> Unit) {
    val displayName = room.displayName()
    ListItem(
        modifier = Modifier.clickable(onClick = onOpen),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        headlineContent = { Text(displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (displayName != room.roomId) {
            { Text(room.roomId, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        leadingContent = {
            Avatar(
                avatarData = AvatarData(room.roomId, displayName, null, AvatarSize.RoomSelectRoomListItem),
                avatarType = AvatarType.Room(),
                forcedAvatarSize = 40.dp,
            )
        },
        trailingContent = { Icon(CompoundIcons.ChevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) },
    )
}

internal class AgentDetailStateProvider : PreviewParameterProvider<AgentDetailState> {
    override val values: Sequence<AgentDetailState>
        get() = sequenceOf(anAgentDetailState(), anAgentDetailState(rooms = persistentListOf()))
}

private fun anAgentDetailState(
    rooms: kotlinx.collections.immutable.ImmutableList<ChatbotAgentRoom> = aSampleRooms(),
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
