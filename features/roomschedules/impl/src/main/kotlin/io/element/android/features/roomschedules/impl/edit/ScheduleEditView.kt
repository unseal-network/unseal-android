/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.roomschedules.impl.cron.CronParser
import io.element.android.features.roomschedules.impl.cron.CronPickerMode
import io.element.android.features.roomschedules.impl.cron.CronPickerModel
import io.element.android.features.roomschedules.impl.model.matrixUserId
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent

@Composable
fun ScheduleEditView(
    state: ScheduleEditState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(ScheduleEditEvents.OnAppear)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Header(
            title = state.title,
            onCancel = { state.eventSink(ScheduleEditEvents.Cancel) },
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.isCreate) {
            OutlinedTextField(
                value = state.name,
                onValueChange = { state.eventSink(ScheduleEditEvents.NameChanged(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Schedule name") },
                singleLine = true,
            )
            AgentSelector(state)
        } else {
            ReadOnlyValue(label = "Schedule name", value = state.name)
            ReadOnlyValue(label = "Agent", value = state.selectedAgentBotName)
        }
        if (!state.selectedAgentIsInRoom) {
            Text(
                text = "Selected agent is not in this room.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedTextField(
            value = state.action,
            onValueChange = { state.eventSink(ScheduleEditEvents.ActionChanged(it)) },
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            label = { Text("Action") },
            minLines = 6,
        )
        CronEditor(
            model = state.cronModel,
            onModelChange = { state.eventSink(ScheduleEditEvents.CronModelChanged(it)) },
        )
        Button(
            onClick = { state.eventSink(ScheduleEditEvents.Submit) },
            enabled = !state.isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isSubmitting) "Saving" else "Save")
        }
        if (state.isSubmitting) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun Header(
    title: String,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onCancel) {
            Text("Cancel")
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AgentSelector(state: ScheduleEditState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Agent", style = MaterialTheme.typography.titleMedium)
        if (state.agents.isEmpty()) {
            Text(
                text = "No agents available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.agents.forEach { agent ->
                    FilterChip(
                        selected = state.selectedAgentBotName == agent.botName,
                        onClick = { state.eventSink(ScheduleEditEvents.AgentChanged(agent.botName)) },
                        label = { Text(agent.agentLabel(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value.ifBlank { "-" }, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CronEditor(
    model: CronPickerModel,
    onModelChange: (CronPickerModel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Repeat", style = MaterialTheme.typography.titleMedium)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CronPickerMode.entries.forEach { mode ->
                FilterChip(
                    selected = model.mode == mode,
                    onClick = { onModelChange(model.copy(mode = mode)) },
                    label = { Text(mode.label()) },
                )
            }
        }
        when (model.mode) {
            CronPickerMode.EveryNHours -> IntervalControls(model, onModelChange)
            CronPickerMode.EveryHourAtMinute -> MinuteControl(model, onModelChange)
            CronPickerMode.Weekday -> {
                WeekdayControls(model, onModelChange)
                TimeControls(model, onModelChange)
            }
            CronPickerMode.Workdays,
            CronPickerMode.EveryDay -> TimeControls(model, onModelChange)
        }
        Text(
            text = CronParser.toReadable(CronParser.toCron(model)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeControls(
    model: CronPickerModel,
    onModelChange: (CronPickerModel) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = "Hour",
            value = model.hour,
            onValueChange = { onModelChange(model.copy(hour = it.coerceIn(0, 23))) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = "Minute",
            value = model.minute,
            onValueChange = { onModelChange(model.copy(minute = it.coerceIn(0, 59))) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MinuteControl(
    model: CronPickerModel,
    onModelChange: (CronPickerModel) -> Unit,
) {
    NumberField(
        label = "Minute",
        value = model.minute,
        onValueChange = { onModelChange(model.copy(minute = it.coerceIn(0, 59))) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun IntervalControls(
    model: CronPickerModel,
    onModelChange: (CronPickerModel) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(1, 2, 3, 4, 6, 8, 12).forEach { interval ->
            FilterChip(
                selected = model.intervalHours == interval,
                onClick = { onModelChange(model.copy(intervalHours = interval)) },
                label = { Text("${interval}h") },
            )
        }
    }
}

@Composable
private fun WeekdayControls(
    model: CronPickerModel,
    onModelChange: (CronPickerModel) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (1..7).forEach { weekday ->
            FilterChip(
                selected = model.weekday == weekday,
                onClick = { onModelChange(model.copy(weekday = weekday)) },
                label = { Text(weekday.label()) },
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text -> text.toIntOrNull()?.let(onValueChange) },
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
    )
}

private fun CronPickerMode.label(): String = when (this) {
    CronPickerMode.Workdays -> "Workdays"
    CronPickerMode.EveryDay -> "Every day"
    CronPickerMode.EveryNHours -> "Every N hours"
    CronPickerMode.EveryHourAtMinute -> "Hourly"
    CronPickerMode.Weekday -> "Weekday"
}

private fun Int.label(): String = when (this) {
    1 -> "Sun"
    2 -> "Mon"
    3 -> "Tue"
    4 -> "Wed"
    5 -> "Thu"
    6 -> "Fri"
    7 -> "Sat"
    else -> toString()
}

private fun ChatbotAgent.agentLabel(): String {
    return displayName?.takeIf { it.isNotBlank() }
        ?: matrixUserId()
}
