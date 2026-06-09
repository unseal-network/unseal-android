/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.list

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkit

@Composable
fun ConnectorListView(
    state: ConnectorListState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(ConnectorListEvents.OnAppear)
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
                text = "Connectors",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { state.eventSink(ConnectorListEvents.Dismiss) }) {
                Text("Done")
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = { state.eventSink(ConnectorListEvents.SearchChanged(it)) },
            label = { Text("Search connectors") },
            singleLine = true,
        )
        state.error?.let {
            OutlinedButton(onClick = { state.eventSink(ConnectorListEvents.ClearError) }) {
                Text(it)
            }
        }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.toolkits.isEmpty() -> Text("No connectors")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.toolkits, key = { it.slug }) { toolkit ->
                    ToolkitRow(state, toolkit)
                }
                if (state.hasMore) {
                    item {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !state.isLoadingMore,
                            onClick = { state.eventSink(ConnectorListEvents.LoadMore) },
                        ) {
                            Text(if (state.isLoadingMore) "Loading…" else "Load more")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolkitRow(state: ConnectorListState, toolkit: ChatbotToolkit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(toolkit.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            toolkit.description?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
        }
        if (toolkit.connected) {
            OutlinedButton(onClick = { state.eventSink(ConnectorListEvents.Manage(toolkit)) }) {
                Text("Manage")
            }
        } else {
            Button(
                enabled = state.connectingSlug != toolkit.slug,
                onClick = { state.eventSink(ConnectorListEvents.Connect(toolkit)) },
            ) {
                Text(if (state.connectingSlug == toolkit.slug) "Connecting…" else "Connect")
            }
        }
    }
}
