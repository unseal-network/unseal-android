/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.roomschedules.impl.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.features.roomschedules.impl.R
import io.element.android.features.roomschedules.impl.cron.CronPickerMode
import io.element.android.features.roomschedules.impl.cron.CronPickerModel
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun ScheduleEditView(
    state: ScheduleEditState,
    modifier: Modifier = Modifier,
) {
    val renderModel = state.renderModel
    LaunchedEffect(Unit) {
        state.eventSink(ScheduleEditEvents.OnAppear)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Header(
            title = renderModel.title,
            onCancel = { state.eventSink(ScheduleEditEvents.Cancel) },
        )
        state.error?.let { ErrorBanner(it) }
        ScheduleSection(title = stringResource(R.string.schedule_edit_basics)) {
            if (state.isCreate) {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = { state.eventSink(ScheduleEditEvents.NameChanged(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(renderModel.nameLabel) },
                    singleLine = true,
                )
                AgentSelector(renderModel, state.eventSink)
            } else {
                ReadOnlyValue(label = renderModel.nameLabel, value = state.name)
                ReadOnlyValue(label = renderModel.agentLabel, value = renderModel.selectedAgentLabel)
            }
            renderModel.outOfRoomAgentWarning?.let {
                ErrorBanner(it)
            }
        }
        ScheduleSection(title = stringResource(R.string.schedule_edit_action)) {
            OutlinedTextField(
                value = state.action,
                onValueChange = { state.eventSink(ScheduleEditEvents.ActionChanged(it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                label = { Text(renderModel.actionLabel) },
                minLines = 6,
            )
        }
        ScheduleSection(title = renderModel.repeatLabel) {
            CronEditor(
                model = state.cronModel,
                renderModel = renderModel,
                onModelChange = { state.eventSink(ScheduleEditEvents.CronModelChanged(it)) },
            )
        }
        Button(
            onClick = { state.eventSink(ScheduleEditEvents.Submit) },
            enabled = renderModel.canSubmit,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 12.dp),
        ) {
            Text(renderModel.submitLabel)
        }
        if (state.isSubmitting) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.schedule_edit_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onCancel) {
            Text(stringResource(CommonStrings.action_cancel))
        }
    }
}

@Composable
private fun ScheduleSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
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
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun AgentSelector(
    renderModel: ScheduleEditRenderModel,
    eventSink: (ScheduleEditEvents) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(renderModel.agentLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (renderModel.agentOptions.isEmpty()) {
            Text(
                text = stringResource(R.string.schedule_edit_no_agents),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                renderModel.agentOptions.forEach { agent ->
                    FilterChip(
                        selected = agent.isSelected,
                        onClick = { eventSink(ScheduleEditEvents.AgentChanged(agent.botName)) },
                        label = { Text(agent.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
    renderModel: ScheduleEditRenderModel,
    onModelChange: (CronPickerModel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            renderModel.cronModeOptions.forEach { option ->
                FilterChip(
                    selected = option.isSelected,
                    onClick = { onModelChange(model.withMode(option.mode)) },
                    label = { Text(option.label) },
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
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        ) {
            Text(
                text = renderModel.cronSummary,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TimeControls(
    model: CronPickerModel,
    onModelChange: (CronPickerModel) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        NumberField(
            label = stringResource(R.string.schedule_edit_hour),
            value = model.hour,
            onValueChange = { onModelChange(model.copy(hour = it.coerceIn(0, 23))) },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = stringResource(R.string.schedule_edit_minute),
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
        label = stringResource(R.string.schedule_edit_minute),
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
                label = { Text(weekdayDisplayLabel(weekday)) },
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
