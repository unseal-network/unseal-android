/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.features.agentmanagement.impl.shared.displayTitle
import io.element.android.features.agentmanagement.impl.shared.matrixId
import io.element.android.features.agentmanagement.impl.shared.providerModelText
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent

@Composable
fun AgentListView(
    state: AgentListState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Agents")
            TextButton(onClick = onBackClick) {
                Text("Done")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { state.eventSink(AgentListEvents.CreateAgent) },
        ) {
            Text("Create Agent")
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = { state.eventSink(AgentListEvents.SearchQueryChanged(it)) },
            label = { Text("Search Agent") },
            singleLine = true,
        )
        if (state.error != null) {
            TextButton(onClick = { state.eventSink(AgentListEvents.Refresh) }) {
                Text(state.error)
            }
        }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.filteredAgents.isEmpty() -> Text("No Agents")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.filteredAgents, key = { it.botName }) { agent ->
                    AgentListRow(agent = agent) {
                        state.eventSink(AgentListEvents.SelectAgent(agent.botName))
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentListRow(
    agent: ChatbotAgent,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(agent.displayTitle())
        agent.matrixId()?.let { Text(it) }
        agent.providerModelText()?.let { Text(it) }
        agent.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
        Text(if (agent.isPublic == true) "Public Agent" else "Private Agent")
    }
}
