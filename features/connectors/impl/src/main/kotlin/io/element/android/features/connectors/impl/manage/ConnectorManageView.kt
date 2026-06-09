/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.connectors.impl.manage

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotConnectedAccount

@Composable
fun ConnectorManageView(
    state: ConnectorManageState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(ConnectorManageEvents.OnAppear)
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
                text = state.toolkitName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { state.eventSink(ConnectorManageEvents.Dismiss) }) {
                Text("Done")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.connecting,
            onClick = { state.eventSink(ConnectorManageEvents.Connect) },
        ) {
            Text(if (state.connecting) "Connecting…" else "Connect new account")
        }
        state.error?.let {
            OutlinedButton(onClick = { state.eventSink(ConnectorManageEvents.ClearError) }) {
                Text(it)
            }
        }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.accounts.isEmpty() -> Text("No connected accounts.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.accounts, key = { it.id }) { account ->
                    ConnectedAccountRow(state, account)
                }
            }
        }
    }
}

@Composable
private fun ConnectedAccountRow(state: ConnectorManageState, account: ChatbotConnectedAccount) {
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
                Text(
                    text = account.profile?.displayName ?: account.toolkit,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (account.status != "ACTIVE") {
                    Text(account.status, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (state.confirmingDisconnectId == account.id) {
            Text(
                "Disconnect this account? Triggers using it will also be removed.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { state.eventSink(ConnectorManageEvents.Disconnect(account.id)) }) {
                    Text("Disconnect")
                }
                OutlinedButton(onClick = { state.eventSink(ConnectorManageEvents.CancelDisconnect) }) {
                    Text("Cancel")
                }
            }
        } else {
            OutlinedButton(
                enabled = state.disconnectingId != account.id,
                onClick = { state.eventSink(ConnectorManageEvents.ConfirmDisconnect(account.id)) },
            ) {
                Text("Disconnect")
            }
        }
    }
}
