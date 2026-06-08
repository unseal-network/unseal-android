/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AgentEditView(
    state: AgentEditState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(AgentEditEvents.OnAppear)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBackClick) {
                Text("Back")
            }
            OutlinedButton(onClick = { state.eventSink(AgentEditEvents.RefreshProviders) }) {
                Text("Refresh providers")
            }
        }

        Text(
            text = if (state.isCreate) "Create agent" else "Edit agent",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        when (val phase = state.phase) {
            AgentEditPhase.Editing -> AgentForm(state)
            is AgentEditPhase.Submitting -> AgentSubmitting(step = phase.step)
            is AgentEditPhase.Success -> AgentCreateSuccess(state = state, summary = phase.summary)
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.isLoading) {
            CircularProgressIndicator()
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AgentForm(state: AgentEditState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.isCreate) {
            OutlinedTextField(
                value = state.form.botName,
                onValueChange = { state.eventSink(AgentEditEvents.BotNameChanged(it)) },
                label = { Text("Bot name") },
                supportingText = { Text(state.nameAvailability.displayText()) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        } else {
            Text(
                text = state.form.botName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        OutlinedTextField(
            value = state.form.displayName,
            onValueChange = { state.eventSink(AgentEditEvents.DisplayNameChanged(it)) },
            label = { Text("Display name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        OutlinedTextField(
            value = state.form.description,
            onValueChange = { state.eventSink(AgentEditEvents.DescriptionChanged(it)) },
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )
        OutlinedTextField(
            value = state.form.avatarUrl,
            onValueChange = { state.eventSink(AgentEditEvents.AvatarUrlChanged(it)) },
            label = { Text("Avatar URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        ToggleRow(
            label = "Public",
            checked = state.form.isPublic,
            onCheckedChange = { state.eventSink(AgentEditEvents.IsPublicChanged(it)) },
        )
        ToggleRow(
            label = "Auto join",
            checked = state.form.autoJoin,
            onCheckedChange = { state.eventSink(AgentEditEvents.AutoJoinChanged(it)) },
        )
        ProviderSection(state)
        OutlinedTextField(
            value = state.form.soul,
            onValueChange = { state.eventSink(AgentEditEvents.SoulChanged(it)) },
            label = { Text("Soul") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )
        Button(
            onClick = { state.eventSink(AgentEditEvents.Submit) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isCreate) "Create" else "Save")
        }
    }
}

@Composable
private fun ProviderSection(state: AgentEditState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Provider",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (state.providers.isEmpty()) {
            Text(
                text = "No providers loaded",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.providers.forEach { provider ->
                    OutlinedButton(
                        onClick = { state.eventSink(AgentEditEvents.ProviderChanged(provider.id)) },
                        enabled = provider.id != state.form.providerId,
                    ) {
                        Text(provider.displayName ?: provider.info?.displayName ?: provider.id)
                    }
                }
            }
        }
        OutlinedTextField(
            value = state.form.model,
            onValueChange = { state.eventSink(AgentEditEvents.ModelChanged(it)) },
            label = { Text("Model") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (state.availableModels.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.availableModels.forEach { model ->
                    OutlinedButton(
                        onClick = { state.eventSink(AgentEditEvents.ModelChanged(model.id)) },
                        enabled = model.id != state.form.model,
                    ) {
                        Text(model.displayName ?: model.id)
                    }
                }
            }
        }
        if (state.needsApiKey) {
            OutlinedTextField(
                value = state.form.apiKey,
                onValueChange = { state.eventSink(AgentEditEvents.ApiKeyChanged(it)) },
                label = { Text("API key") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        if (state.supportsBaseUrl) {
            OutlinedTextField(
                value = state.form.baseUrl,
                onValueChange = { state.eventSink(AgentEditEvents.BaseUrlChanged(it)) },
                label = { Text("Base URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AgentSubmitting(step: AgentEditSubmittingStep) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CircularProgressIndicator()
        Text(
            text = when (step) {
                AgentEditSubmittingStep.CreateAgent -> "Saving agent..."
                AgentEditSubmittingStep.CreateDM -> "Creating direct chat..."
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AgentCreateSuccess(
    state: AgentEditState,
    summary: AgentCreateSuccessSummary,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = summary.displayName ?: summary.botName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = listOfNotNull(summary.provider, summary.model).joinToString(" · ").ifBlank { "Agent created" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(
            onClick = { state.eventSink(AgentEditEvents.GoToChat) },
            enabled = summary.directRoomId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Go to chat")
        }
        OutlinedButton(
            onClick = { state.eventSink(AgentEditEvents.CreateAnother) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Create another")
        }
    }
}

private fun AgentNameAvailability.displayText(): String {
    return when (this) {
        AgentNameAvailability.Unknown -> ""
        AgentNameAvailability.Checking -> "Checking..."
        AgentNameAvailability.Available -> "Available"
        AgentNameAvailability.Taken -> "Already taken"
    }
}
