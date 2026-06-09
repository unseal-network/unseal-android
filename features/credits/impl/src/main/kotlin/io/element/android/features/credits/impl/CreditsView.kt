/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.credits.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.features.credits.api.CreditsEntryPoint

@Composable
fun CreditsView(
    state: CreditsState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(CreditsEvents.OnAppear)
    }
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Credits & Billing", style = MaterialTheme.typography.headlineSmall)
        TextButton(onClick = { state.eventSink(CreditsEvents.Dismiss) }) {
            Text("Done")
        }
        Text("Selected tab: ${state.selectedTab.name}")
        Button(onClick = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Balance)) }) {
            Text("Balance")
        }
        Button(onClick = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.DailyUsage)) }) {
            Text("Daily Usage")
        }
        Button(onClick = { state.eventSink(CreditsEvents.SelectTab(CreditsEntryPoint.CreditsTab.Usage)) }) {
            Text("Usage")
        }
        Button(onClick = { state.eventSink(CreditsEvents.RequestTopUp) }) {
            Text("Recharge")
        }
    }
}
