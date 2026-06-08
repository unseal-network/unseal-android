/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.features.webhooks.impl.shared.displaySource
import io.element.android.features.webhooks.impl.shared.isEnabled
import io.element.android.libraries.chatbot.api.model.webhooks.ChatbotWebhookTrigger

@Composable
fun WebhookTriggerListView(
    state: WebhookTriggerListState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(WebhookTriggerListEvents.OnAppear)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when (val mode = state.mode) {
                    WebhookTriggerListMode.Global -> "Webhook Triggers"
                    is WebhookTriggerListMode.Room -> "${mode.roomName} Triggers"
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { state.eventSink(WebhookTriggerListEvents.Dismiss) }) {
                Text("Done")
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(WebhookTriggerListEvents.CreateTrigger) },
            ) {
                Text("Create")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(WebhookTriggerListEvents.Refresh) },
            ) {
                Text("Refresh")
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = { state.eventSink(WebhookTriggerListEvents.SearchChanged(it)) },
            label = { Text("Search triggers") },
            singleLine = true,
        )
        when (state.mode) {
            WebhookTriggerListMode.Global -> RoomFilterRow(state)
            is WebhookTriggerListMode.Room -> AgentFilterRow(state)
        }
        state.error?.let {
            OutlinedButton(onClick = { state.eventSink(WebhookTriggerListEvents.ClearError) }) {
                Text(it)
            }
        }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.filteredTriggers.isEmpty() -> Text("No webhook triggers")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.filteredTriggers, key = { it.triggerId }) { trigger ->
                    TriggerRow(state, trigger)
                }
            }
        }
    }
}

@Composable
private fun RoomFilterRow(state: WebhookTriggerListState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Room filter", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterButton(
                text = "All",
                selected = state.selectedRoomId == null,
                onClick = { state.eventSink(WebhookTriggerListEvents.SelectRoomFilter(null)) },
            )
            state.availableRooms.take(3).forEach { room ->
                FilterButton(
                    text = room.info.name ?: room.roomId.value,
                    selected = state.selectedRoomId == room.roomId.value,
                    onClick = { state.eventSink(WebhookTriggerListEvents.SelectRoomFilter(room.roomId.value)) },
                )
            }
        }
    }
}

@Composable
private fun AgentFilterRow(state: WebhookTriggerListState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Agent filter", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterButton(
                text = "All",
                selected = state.selectedAgentId == null,
                onClick = { state.eventSink(WebhookTriggerListEvents.SelectAgentFilter(null)) },
            )
            state.availableAgents.take(3).forEach { agent ->
                FilterButton(
                    text = agent.displayName ?: agent.agentId,
                    selected = state.selectedAgentId == agent.agentId,
                    onClick = { state.eventSink(WebhookTriggerListEvents.SelectAgentFilter(agent.agentId)) },
                )
            }
        }
    }
}

@Composable
private fun TriggerRow(state: WebhookTriggerListState, trigger: ChatbotWebhookTrigger) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(trigger.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${trigger.displaySource()} • ${trigger.eventTypes.joinToString()} • ${trigger.agentId}")
                Text(trigger.roomId, style = MaterialTheme.typography.bodySmall)
            }
            Switch(
                checked = trigger.isEnabled(),
                onCheckedChange = { state.eventSink(WebhookTriggerListEvents.ToggleStatus(trigger)) },
                enabled = state.togglingTriggerId != trigger.triggerId,
            )
        }
        if (state.deleteConfirmationTriggerId == trigger.triggerId) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { state.eventSink(WebhookTriggerListEvents.ConfirmDelete) }) {
                    Text("Delete")
                }
                OutlinedButton(onClick = { state.eventSink(WebhookTriggerListEvents.CancelDelete) }) {
                    Text("Cancel")
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { state.eventSink(WebhookTriggerListEvents.EditTrigger(trigger)) }) {
                    Text("Edit")
                }
                OutlinedButton(
                    enabled = state.deletingTriggerId != trigger.triggerId,
                    onClick = { state.eventSink(WebhookTriggerListEvents.RequestDelete(trigger)) },
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun FilterButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(text)
        }
    }
}
