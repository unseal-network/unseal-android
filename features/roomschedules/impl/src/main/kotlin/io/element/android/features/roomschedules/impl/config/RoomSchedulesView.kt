/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.roomschedules.impl.cron.CronParser
import io.element.android.features.roomschedules.impl.model.isEnabled
import io.element.android.features.roomschedules.impl.model.stableId
import io.element.android.libraries.chatbot.api.model.schedules.ChatbotSchedule

@Composable
fun RoomSchedulesView(
    state: RoomSchedulesState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(RoomSchedulesEvents.OnAppear)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(
            title = "Room AI Config",
            subtitle = state.roomName,
            onDone = { state.eventSink(RoomSchedulesEvents.Dismiss) },
        )
        TabSelector(state)
        when (state.selectedTab) {
            RoomSchedulesTab.Schedules -> SchedulesTab(state)
            RoomSchedulesTab.WorkingMemory -> WorkingMemoryTab(state)
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onDone) {
            Text("Done")
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TabSelector(state: RoomSchedulesState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.selectedTab == RoomSchedulesTab.Schedules,
            onClick = { state.eventSink(RoomSchedulesEvents.SelectTab(RoomSchedulesTab.Schedules)) },
            label = { Text("Schedules") },
        )
        FilterChip(
            selected = state.selectedTab == RoomSchedulesTab.WorkingMemory,
            onClick = { state.eventSink(RoomSchedulesEvents.SelectTab(RoomSchedulesTab.WorkingMemory)) },
            label = { Text("Working Memory") },
        )
    }
}

@Composable
private fun SchedulesTab(state: RoomSchedulesState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Active schedules: ${state.activeCount}", style = MaterialTheme.typography.titleMedium)
                state.scheduleError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Mine", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = state.showOnlyMine,
                    onCheckedChange = { state.eventSink(RoomSchedulesEvents.ShowOnlyMineChanged(it)) },
                )
            }
            OutlinedButton(onClick = { state.eventSink(RoomSchedulesEvents.Refresh) }) {
                Text("Refresh")
            }
        }
        Button(onClick = { state.eventSink(RoomSchedulesEvents.CreateSchedule) }) {
            Text("New Schedule")
        }
        if (state.isLoadingSchedules) {
            CircularProgressIndicator()
        }
        if (!state.isLoadingSchedules && state.displayedSchedules.isEmpty()) {
            Text(
                text = "No schedules",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.displayedSchedules, key = { it.stableId() }) { schedule ->
                ScheduleRow(
                    schedule = schedule,
                    isOwner = schedule.creatorId == state.currentUserId,
                    isConfirmingDelete = state.deleteConfirmationScheduleId == schedule.stableId(),
                    onEdit = { state.eventSink(RoomSchedulesEvents.EditSchedule(schedule)) },
                    onToggle = { state.eventSink(RoomSchedulesEvents.ToggleSchedule(schedule)) },
                    onRequestDelete = { state.eventSink(RoomSchedulesEvents.RequestDeleteSchedule(schedule)) },
                    onConfirmDelete = { state.eventSink(RoomSchedulesEvents.ConfirmDeleteSchedule) },
                    onDismissDelete = { state.eventSink(RoomSchedulesEvents.DismissDeleteConfirmation) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: ChatbotSchedule,
    isOwner: Boolean,
    isConfirmingDelete: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(schedule.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(schedule.agentId, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(CronParser.toReadable(schedule.cron), style = MaterialTheme.typography.bodyMedium)
                Text(
                    schedule.action,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = if (schedule.isEnabled()) "Enabled" else "Disabled",
                color = if (schedule.isEnabled()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        if (isOwner) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) {
                    Text("Edit")
                }
                OutlinedButton(onClick = onToggle) {
                    Text(if (schedule.isEnabled()) "Disable" else "Enable")
                }
                OutlinedButton(onClick = onRequestDelete) {
                    Text("Delete")
                }
            }
        }
        if (isConfirmingDelete) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Delete this schedule?", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onDismissDelete) {
                    Text("Cancel")
                }
                Button(onClick = onConfirmDelete) {
                    Text("Delete")
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun WorkingMemoryTab(state: RoomSchedulesState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Working Memory", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { state.eventSink(RoomSchedulesEvents.RefreshMemory) }) {
                Text("Refresh")
            }
            if (!state.isEditingMemory && state.canEditMemory) {
                Button(onClick = { state.eventSink(RoomSchedulesEvents.StartEditingMemory) }) {
                    Text(if (state.workingMemory.isBlank()) "Add" else "Edit")
                }
            }
        }
        state.memoryError?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (state.isLoadingMemory) {
            CircularProgressIndicator()
        }
        if (state.isEditingMemory) {
            OutlinedTextField(
                value = state.editingMemoryText,
                onValueChange = { state.eventSink(RoomSchedulesEvents.EditingMemoryChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                label = { Text("Memory") },
                minLines = 8,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { state.eventSink(RoomSchedulesEvents.CancelEditingMemory) }) {
                    Text("Cancel")
                }
                Button(
                    onClick = { state.eventSink(RoomSchedulesEvents.SaveMemory) },
                    enabled = !state.isSavingMemory,
                ) {
                    Text(if (state.isSavingMemory) "Saving" else "Save")
                }
            }
        } else {
            Text(
                text = state.workingMemory.ifBlank { "No working memory" },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
