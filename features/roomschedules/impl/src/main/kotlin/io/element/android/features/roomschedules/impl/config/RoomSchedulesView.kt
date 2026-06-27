/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.roomschedules.impl.R
import io.element.android.libraries.designsystem.components.management.ManagementCreateFloatingActionButton
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun RoomSchedulesView(
    state: RoomSchedulesState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(RoomSchedulesEvents.OnAppear)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(
                title = stringResource(R.string.room_schedules_title),
                subtitle = state.roomName,
                onDone = { state.eventSink(RoomSchedulesEvents.Dismiss) },
            )
            TabSelector(state)
            when (state.selectedTab) {
                RoomSchedulesTab.Schedules -> SchedulesTab(state)
                RoomSchedulesTab.WorkingMemory -> WorkingMemoryTab(state)
            }
        }
        if (state.selectedTab == RoomSchedulesTab.Schedules) {
            ManagementCreateFloatingActionButton(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                text = stringResource(R.string.room_schedules_new_schedule),
                onClick = { state.eventSink(RoomSchedulesEvents.CreateSchedule) },
            )
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
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onDone) {
            Text(stringResource(CommonStrings.action_done))
        }
    }
}

@Composable
private fun TabSelector(state: RoomSchedulesState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ScheduleTabButton(
                label = stringResource(R.string.room_schedules_tab_schedules),
                selected = state.selectedTab == RoomSchedulesTab.Schedules,
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(RoomSchedulesEvents.SelectTab(RoomSchedulesTab.Schedules)) },
            )
            ScheduleTabButton(
                label = stringResource(R.string.room_schedules_tab_working_memory),
                selected = state.selectedTab == RoomSchedulesTab.WorkingMemory,
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(RoomSchedulesEvents.SelectTab(RoomSchedulesTab.WorkingMemory)) },
            )
        }
    }
}

@Composable
private fun ScheduleTabButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SchedulesTab(state: RoomSchedulesState) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScheduleToolbar(state)
        state.scheduleError?.let { ErrorBanner(it) }
        if (state.isLoadingSchedules) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        if (!state.isLoadingSchedules && state.scheduleItems.isEmpty()) {
            EmptySchedulesCard()
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(state.scheduleItems, key = { it.id }) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    isConfirmingDelete = state.deleteConfirmationScheduleId == schedule.id,
                    onEdit = { state.eventSink(RoomSchedulesEvents.EditSchedule(schedule.source)) },
                    onToggle = { state.eventSink(RoomSchedulesEvents.ToggleSchedule(schedule.source)) },
                    onRequestDelete = { state.eventSink(RoomSchedulesEvents.RequestDeleteSchedule(schedule.source)) },
                    onConfirmDelete = { state.eventSink(RoomSchedulesEvents.ConfirmDeleteSchedule) },
                    onDismissDelete = { state.eventSink(RoomSchedulesEvents.DismissDeleteConfirmation) },
                )
            }
        }
    }
}

@Composable
private fun ScheduleToolbar(state: RoomSchedulesState) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.room_schedules_tab_schedules), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = stringResource(R.string.room_schedules_summary, state.activeCount, state.scheduleItems.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.room_schedules_mine_only), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = state.showOnlyMine,
                        onCheckedChange = { state.eventSink(RoomSchedulesEvents.ShowOnlyMineChanged(it)) },
                    )
                }
                OutlinedButton(onClick = { state.eventSink(RoomSchedulesEvents.Refresh) }) {
                    Text(stringResource(R.string.room_schedules_refresh))
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: ScheduleRenderModel,
    isConfirmingDelete: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = schedule.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = schedule.agentLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(schedule)
            }
            ScheduleInfoBlock(label = stringResource(R.string.room_schedules_repeat), value = schedule.cronLabel)
            ScheduleInfoBlock(label = stringResource(R.string.room_schedules_action), value = schedule.actionPreview, maxLines = 3)
            if (schedule.isOwner) {
                OwnerActions(
                    schedule = schedule,
                    onEdit = onEdit,
                    onToggle = onToggle,
                    onRequestDelete = onRequestDelete,
                )
            }
            if (isConfirmingDelete) {
                DeleteConfirmation(
                    onConfirmDelete = onConfirmDelete,
                    onDismissDelete = onDismissDelete,
                )
            }
        }
    }
}

@Composable
private fun StatusPill(schedule: ScheduleRenderModel) {
    val container = if (schedule.isEnabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (schedule.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        border = BorderStroke(1.dp, content.copy(alpha = 0.22f)),
    ) {
        Text(
            text = schedule.statusLabel,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = content,
        )
    }
}

@Composable
private fun ScheduleInfoBlock(
    label: String,
    value: String,
    maxLines: Int = 2,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OwnerActions(
    schedule: ScheduleRenderModel,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onRequestDelete: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onEdit,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(stringResource(CommonStrings.action_edit))
        }
        OutlinedButton(
            onClick = onToggle,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(schedule.toggleLabel)
        }
        OutlinedButton(
            onClick = onRequestDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(stringResource(CommonStrings.action_delete))
        }
    }
}

@Composable
private fun DeleteConfirmation(
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.room_schedules_delete_confirmation),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismissDelete, modifier = Modifier.weight(1f)) {
                    Text(stringResource(CommonStrings.action_cancel))
                }
                Button(
                    onClick = onConfirmDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(CommonStrings.action_delete))
                }
            }
        }
    }
}

@Composable
private fun EmptySchedulesCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.room_schedules_empty_title), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = stringResource(R.string.room_schedules_empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.room_schedules_tab_working_memory), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (state.workingMemory.isBlank()) {
                                stringResource(R.string.room_schedules_no_saved_room_context)
                            } else {
                                stringResource(R.string.room_schedules_characters_saved, state.workingMemory.length)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(onClick = { state.eventSink(RoomSchedulesEvents.RefreshMemory) }) {
                        Text(stringResource(R.string.room_schedules_refresh))
                    }
                }
                state.memoryError?.let { ErrorBanner(it) }
                if (state.isLoadingMemory) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (state.isEditingMemory) {
                    OutlinedTextField(
                        value = state.editingMemoryText,
                        onValueChange = { state.eventSink(RoomSchedulesEvents.EditingMemoryChanged(it)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        label = { Text(stringResource(R.string.room_schedules_memory)) },
                        minLines = 8,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { state.eventSink(RoomSchedulesEvents.CancelEditingMemory) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(CommonStrings.action_cancel))
                        }
                        Button(
                            onClick = { state.eventSink(RoomSchedulesEvents.SaveMemory) },
                            enabled = !state.isSavingMemory,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (state.isSavingMemory) stringResource(R.string.room_schedules_saving) else stringResource(CommonStrings.action_save))
                        }
                    }
                } else {
                    MemoryPreview(state)
                    if (state.canEditMemory) {
                        Button(onClick = { state.eventSink(RoomSchedulesEvents.StartEditingMemory) }) {
                            Text(if (state.workingMemory.isBlank()) stringResource(R.string.room_schedules_add_memory) else stringResource(R.string.room_schedules_edit_memory))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryPreview(state: RoomSchedulesState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Text(
            text = state.workingMemory.ifBlank { stringResource(R.string.room_schedules_no_working_memory) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
