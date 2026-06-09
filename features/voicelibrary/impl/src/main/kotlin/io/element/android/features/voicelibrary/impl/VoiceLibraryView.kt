/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.voicelibrary.impl

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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.libraries.chatbot.api.model.voices.ChatbotProviderVoice
import io.element.android.libraries.chatbot.api.model.voices.ChatbotVoiceProfile

@Composable
fun VoiceLibraryView(
    state: VoiceLibraryState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(VoiceLibraryEvents.OnAppear)
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
                text = "Voice Library",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = { state.eventSink(VoiceLibraryEvents.Dismiss) }) {
                Text("Done")
            }
        }

        TabRow(selectedTabIndex = state.selectedTab.ordinal) {
            Tab(
                selected = state.selectedTab == VoiceLibraryTab.Mine,
                onClick = { state.eventSink(VoiceLibraryEvents.SelectTab(VoiceLibraryTab.Mine)) },
                text = { Text("My Voices") },
            )
            Tab(
                selected = state.selectedTab == VoiceLibraryTab.Public,
                onClick = { state.eventSink(VoiceLibraryEvents.SelectTab(VoiceLibraryTab.Public)) },
                text = { Text("Public") },
            )
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = { state.eventSink(VoiceLibraryEvents.SearchChanged(it)) },
            label = { Text("Search voices") },
            singleLine = true,
        )

        state.error?.let {
            OutlinedButton(onClick = { state.eventSink(VoiceLibraryEvents.ClearError) }) {
                Text(it)
            }
        }
        state.lastShareId?.let {
            OutlinedButton(onClick = { state.eventSink(VoiceLibraryEvents.ClearShareId) }) {
                Text("Share id: $it (tap to dismiss)")
            }
        }

        when {
            state.isLoading -> CircularProgressIndicator()
            state.selectedTab == VoiceLibraryTab.Mine -> MyVoices(state)
            else -> PublicVoices(state)
        }
    }
}

@Composable
private fun MyVoices(state: VoiceLibraryState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                modifier = Modifier.weight(1f),
                value = state.importShareId,
                onValueChange = { state.eventSink(VoiceLibraryEvents.ImportShareChanged(it)) },
                label = { Text("Import share id") },
                singleLine = true,
            )
            Button(
                enabled = state.busyId != "import" && state.importShareId.isNotBlank(),
                onClick = { state.eventSink(VoiceLibraryEvents.ImportShare) },
            ) {
                Text("Import")
            }
        }
        if (state.profiles.isEmpty()) {
            Text("No saved voices")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.profiles, key = { it.id }) { profile ->
                    ProfileRow(state, profile)
                }
            }
        }
    }
}

@Composable
private fun ProfileRow(state: VoiceLibraryState, profile: ChatbotVoiceProfile) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Column {
            Text(profile.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            profile.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        if (state.deleteConfirmationProfileId == profile.id) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { state.eventSink(VoiceLibraryEvents.ConfirmDelete) }) {
                    Text("Delete")
                }
                OutlinedButton(onClick = { state.eventSink(VoiceLibraryEvents.CancelDelete) }) {
                    Text("Cancel")
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = state.busyId != profile.id,
                    onClick = { state.eventSink(VoiceLibraryEvents.ShareVoice(profile.id)) },
                ) {
                    Text("Share")
                }
                OutlinedButton(
                    enabled = state.busyId != profile.id,
                    onClick = { state.eventSink(VoiceLibraryEvents.RequestDelete(profile.id)) },
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun PublicVoices(state: VoiceLibraryState) {
    if (state.catalog.isEmpty()) {
        Text("No public voices")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.catalog, key = { it.providerVoiceId }) { voice ->
                CatalogRow(state, voice)
            }
        }
    }
}

@Composable
private fun CatalogRow(state: VoiceLibraryState, voice: ChatbotProviderVoice) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(voice.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            voice.description?.let { Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2) }
        }
        Button(
            enabled = state.busyId != voice.providerVoiceId,
            onClick = { state.eventSink(VoiceLibraryEvents.SaveVoice(voice)) },
        ) {
            Text(if (state.busyId == voice.providerVoiceId) "Saving…" else "Save")
        }
    }
}
