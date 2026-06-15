/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.agentmanagement.impl.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.agentmanagement.impl.detail.AgentDetailEvents
import io.element.android.features.agentmanagement.impl.detail.AgentDetailState
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType

/**
 * Read-only agent profile, mirroring the unseal-webapp agent showcase content (identity / connect
 * link / skills / rooms) in a native single-column Material 3 layout. Data comes from
 * [AgentDetailState] (reused from the agent-management detail screen).
 */
@Composable
fun AgentProfileView(
    state: AgentDetailState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { state.eventSink(AgentDetailEvents.OnAppear) }
    val model = state.renderModel

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = model.navigationTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(CompoundIcons.ChevronLeft(), contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { state.eventSink(AgentDetailEvents.Edit) }) {
                        Icon(CompoundIcons.Edit(), contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { Hero(state) }
            item { ConnectCard(agentProfileUrl = model.agentProfileUrl) }
            if (model.skills.items.isNotEmpty()) {
                item { SkillsSection(state) }
            }
            if (model.rooms.items.isNotEmpty()) {
                item { RoomsSection(state) }
            }
        }
    }
}

@Composable
private fun Hero(state: AgentDetailState) {
    val model = state.renderModel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(
            avatarData = AvatarData(model.botName, model.displayName, model.avatarUrl, AvatarSize.UserHeader),
            avatarType = AvatarType.User,
        )
        Text(
            text = model.displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        model.matrixId?.let { id ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = id,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { state.eventSink(AgentDetailEvents.CopyAgentId) }, modifier = Modifier.size(20.dp)) {
                    Icon(CompoundIcons.Copy(), contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileChip(text = model.visibilityLabel)
            model.providerModelLabel?.let { ProfileChip(text = it) }
        }
        model.description?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { state.eventSink(AgentDetailEvents.StartChat) },
                enabled = model.canStartChat,
                modifier = Modifier.weight(1f),
            ) {
                if (state.isStartingChat) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(model.startChatLabel)
                }
            }
            OutlinedButton(onClick = { state.eventSink(AgentDetailEvents.Edit) }) {
                Text(model.editLabel)
            }
        }
    }
}

@Composable
private fun ProfileChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ConnectCard(agentProfileUrl: String?) {
    if (agentProfileUrl.isNullOrBlank()) return
    // Connect link given to an agent / unseal-cli (mirrors webapp `{origin}/@handle.md`).
    val connectUrl = "$agentProfileUrl.md"
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(CompoundIcons.Link(), contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                Text(text = "连接", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Text(
                text = "把这个链接交给你的 Agent 或 unseal-cli",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth().clickable { clipboard.setText(AnnotatedString(connectUrl)) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = connectUrl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(CompoundIcons.Copy(), contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SkillsSection(state: AgentDetailState) {
    val skills = state.renderModel.skills
    SectionCard(title = "${skills.title} (${skills.items.size})") {
        skills.items.forEachIndexed { index, skill ->
            if (index > 0) HorizontalDivider()
            Column(modifier = Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = skill.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.Medium,
                )
                skill.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomsSection(state: AgentDetailState) {
    val rooms = state.renderModel.rooms
    SectionCard(title = rooms.countLabel ?: rooms.title) {
        rooms.items.forEachIndexed { index, room ->
            if (index > 0) HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.eventSink(AgentDetailEvents.OpenRoom(room.roomId)) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = room.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    room.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Icon(CompoundIcons.ChevronRight(), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp)) {
                Spacer(Modifier.size(2.dp))
                content()
                Spacer(Modifier.size(2.dp))
            }
        }
    }
}
