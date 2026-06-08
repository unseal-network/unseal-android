/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun AgentDetailView(
    state: AgentDetailState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(AgentDetailEvents.OnAppear)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackClick) {
                    Text("Back")
                }
                OutlinedButton(onClick = { state.eventSink(AgentDetailEvents.Refresh) }) {
                    Text("Refresh")
                }
            }
        }
        item {
            AgentProfileHeader(state)
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = { state.eventSink(AgentDetailEvents.StartChat) },
                    enabled = state.canStartChat,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.isStartingChat) "Opening..." else "Message")
                }
                OutlinedButton(
                    onClick = { state.eventSink(AgentDetailEvents.Edit) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Edit")
                }
            }
        }
        state.agent?.description?.takeIf { it.isNotBlank() }?.let { description ->
            item {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.agent?.soul?.takeIf { it.isNotBlank() }?.let { soul ->
            item {
                AgentSoulSection(state = state, soul = soul)
            }
        }
        item {
            AgentSkillsSection(state)
        }
        item {
            Text(
                text = "Rooms${if (state.rooms.isNotEmpty()) " (${state.rooms.size})" else ""}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (state.rooms.isEmpty()) {
            item {
                Text(
                    text = if (state.isLoading) "Loading rooms..." else "No rooms yet",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(state.rooms, key = { it.roomId }) { room ->
                AgentRoomRow(
                    room = room,
                    onOpen = { state.eventSink(AgentDetailEvents.OpenRoom(room.roomId)) },
                    onLeave = { state.eventSink(AgentDetailEvents.LeaveRoom(room.roomId)) },
                )
            }
        }
        item {
            if (state.isLoading) {
                CircularProgressIndicator()
            }
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.copiedAgentId?.let { copied ->
                Text(
                    text = "Copied $copied",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AgentProfileHeader(state: AgentDetailState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = state.navigationTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        state.matrixId?.let { matrixId ->
            OutlinedButton(onClick = { state.eventSink(AgentDetailEvents.CopyAgentId) }) {
                Text(matrixId, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.providerModelText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = if (state.agent?.isPublic == true) "Public" else "Private",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AgentSoulSection(
    state: AgentDetailState,
    soul: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Soul",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = soul,
            maxLines = if (state.isSoulExpanded) Int.MAX_VALUE else 4,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (soul.length > 120) {
            OutlinedButton(onClick = { state.eventSink(AgentDetailEvents.ToggleSoulExpanded) }) {
                Text(if (state.isSoulExpanded) "Show less" else "Show more")
            }
        }
    }
}

@Composable
private fun AgentSkillsSection(state: AgentDetailState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Skills",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            OutlinedButton(onClick = { state.eventSink(AgentDetailEvents.ManageSkills) }) {
                Text("Manage")
            }
        }
        Text(
            text = "No skills configured",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AgentRoomRow(
    room: io.element.android.libraries.chatbot.api.model.agent.ChatbotAgentRoom,
    onOpen: () -> Unit,
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onOpen,
            modifier = Modifier.weight(1f),
        ) {
            Column {
                Text(room.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (room.displayName() != room.roomId) {
                    Text(
                        text = room.roomId,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        OutlinedButton(onClick = onLeave) {
            Text("Leave")
        }
    }
}
