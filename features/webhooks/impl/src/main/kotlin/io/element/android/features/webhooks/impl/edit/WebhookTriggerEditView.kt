/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.webhooks.impl.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WebhookTriggerEditView(
    state: WebhookTriggerEditState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(WebhookTriggerEditEvents.OnAppear)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (state.isCreateMode) "Create Trigger" else "Edit Trigger",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { state.eventSink(WebhookTriggerEditEvents.Cancel) }) {
                Text("Cancel")
            }
        }
        if (state.isLoading) {
            CircularProgressIndicator()
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.draftPrompt,
            onValueChange = { state.eventSink(WebhookTriggerEditEvents.DraftPromptChanged(it)) },
            label = { Text("Draft prompt") },
        )
        OutlinedButton(
            onClick = { state.eventSink(WebhookTriggerEditEvents.GenerateDraft) },
            enabled = !state.isDrafting,
        ) {
            Text(if (state.isDrafting) "Drafting..." else "Generate draft")
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.name,
            onValueChange = { state.eventSink(WebhookTriggerEditEvents.NameChanged(it)) },
            label = { Text("Name") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.description,
            onValueChange = { state.eventSink(WebhookTriggerEditEvents.DescriptionChanged(it)) },
            label = { Text("Description") },
        )
        ChipGroup(label = "Source") {
            state.eventSources.forEach { source ->
                FilterChip(
                    selected = state.selectedSource?.source == source.source,
                    onClick = { state.eventSink(WebhookTriggerEditEvents.SelectSource(source)) },
                    label = { Text(source.name) },
                )
            }
        }
        state.selectedSource?.let { source ->
            ChipGroup(label = "Events") {
                source.eventTypes.forEach { eventType ->
                    FilterChip(
                        selected = eventType.eventType in state.selectedEventTypes,
                        onClick = { state.eventSink(WebhookTriggerEditEvents.ToggleEventType(eventType.eventType)) },
                        label = { Text(eventType.name) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { state.eventSink(WebhookTriggerEditEvents.ConnectSource) }) {
                    Text("Connect")
                }
                state.selectedConnection?.let {
                    Text(it.name ?: it.connectionId, modifier = Modifier.align(Alignment.CenterVertically))
                }
            }
        }
        ChipGroup(label = "Account") {
            state.connectedAccounts.forEach { account ->
                FilterChip(
                    selected = state.selectedAccount?.id == account.id,
                    onClick = { state.eventSink(WebhookTriggerEditEvents.SelectAccount(account)) },
                    label = { Text(account.alias ?: account.profile?.displayName ?: account.id) },
                )
            }
        }
        ChipGroup(label = "Room") {
            state.availableRooms.forEach { room ->
                FilterChip(
                    selected = state.selectedRoomId == room.roomId.value,
                    onClick = { state.eventSink(WebhookTriggerEditEvents.SelectRoom(room.roomId.value)) },
                    label = { Text(room.info.name ?: room.roomId.value) },
                )
            }
        }
        if (state.isCreateMode) {
            ChipGroup(label = "Agent") {
                state.availableAgents.forEach { agent ->
                    FilterChip(
                        selected = state.selectedAgentId == agent.agentId,
                        onClick = { state.eventSink(WebhookTriggerEditEvents.SelectAgent(agent.agentId)) },
                        label = { Text(agent.displayName ?: agent.agentId) },
                    )
                }
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.actionPrompt,
            onValueChange = { state.eventSink(WebhookTriggerEditEvents.ActionPromptChanged(it)) },
            label = { Text("Action prompt") },
            minLines = 4,
        )
        state.error?.let {
            OutlinedButton(onClick = { state.eventSink(WebhookTriggerEditEvents.ClearError) }) {
                Text(it)
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { state.eventSink(WebhookTriggerEditEvents.Save) },
            enabled = state.canSave && !state.isSaving,
        ) {
            Text(if (state.isSaving) "Saving..." else "Save")
        }
    }
}

@Composable
private fun ChipGroup(
    label: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}
