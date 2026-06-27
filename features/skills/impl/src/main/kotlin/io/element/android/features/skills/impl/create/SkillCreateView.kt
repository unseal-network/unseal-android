/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.element.android.features.skills.impl.create

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.R
import io.element.android.features.skills.impl.shared.displayNameRes
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf

@Composable
fun SkillCreateView(
    state: SkillCreateState,
    onBackClick: () -> Unit,
    onPickFile: () -> Unit = {},
    onPickZip: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.skills_create), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_go_back))
                    }
                },
            )
        },
    ) { padding ->
        when (val phase = state.phase) {
            is SkillCreatePhase.Success -> SuccessContent(
                name = phase.name,
                onViewDetail = { state.eventSink(SkillCreateEvents.ViewDetail) },
                onBackToList = { state.eventSink(SkillCreateEvents.BackToList) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            else -> FormContent(
                state = state,
                onPickFile = onPickFile,
                onPickZip = onPickZip,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }

    state.editingFile?.let { file ->
        FileEditorBottomSheet(
            file = file,
            onSave = { path, content -> state.eventSink(SkillCreateEvents.FileEdited(file.id, path, content)) },
            onDismiss = { state.eventSink(SkillCreateEvents.CancelEditingFile) },
        )
    }

    state.pendingFileConflict?.let { conflict ->
        AlertDialog(
            onDismissRequest = { state.eventSink(SkillCreateEvents.DismissFileConflict) },
            title = { Text(stringResource(R.string.skill_create_file_exists_title)) },
            text = { Text(stringResource(R.string.skill_create_file_exists_message, conflict.incomingFile.path)) },
            confirmButton = {
                TextButton(onClick = { state.eventSink(SkillCreateEvents.KeepBothConflictingFile) }) {
                    Text(stringResource(R.string.skill_create_file_exists_keep_both))
                }
            },
            dismissButton = {
                TextButton(onClick = { state.eventSink(SkillCreateEvents.OverwriteConflictingFile) }) {
                    Text(stringResource(R.string.skill_create_file_exists_overwrite))
                }
            },
        )
    }
}

@Composable
private fun FormContent(
    state: SkillCreateState,
    onPickFile: () -> Unit,
    onPickZip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // MARK: Basic info section
        SectionHeader(stringResource(R.string.skill_create_basic_info))
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.name,
            onValueChange = { state.eventSink(SkillCreateEvents.NameChanged(it)) },
            placeholder = { Text(stringResource(R.string.skill_create_name_placeholder)) },
            singleLine = true,
            enabled = !state.isSubmitting,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.description,
            onValueChange = { state.eventSink(SkillCreateEvents.DescriptionChanged(it)) },
            placeholder = { Text(stringResource(R.string.skill_detail_description)) },
            minLines = 2,
            maxLines = 6,
            enabled = !state.isSubmitting,
        )
        Text(stringResource(R.string.skill_create_visibility), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChatbotSkillVisibility.entries.forEach { visibility ->
                FilterChip(
                    selected = state.visibility == visibility,
                    onClick = { state.eventSink(SkillCreateEvents.VisibilityChanged(visibility)) },
                    enabled = !state.isSubmitting,
                    label = { Text(stringResource(visibility.displayNameRes())) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // MARK: Files section
        FilesSectionHeader(
            onPickFile = onPickFile,
            onPickZip = onPickZip,
            enabled = !state.isSubmitting,
        )
        state.manualFiles.forEach { file ->
            FileRow(
                file = file,
                canDelete = state.manualFiles.size > 1,
                onClick = { state.eventSink(SkillCreateEvents.StartEditingFile(file.id)) },
                onDelete = { state.eventSink(SkillCreateEvents.DeleteFile(file.id)) },
            )
        }
        ListItem(
            modifier = Modifier.clip(MaterialTheme.shapes.medium).clickableRow(enabled = !state.isSubmitting) {
                state.eventSink(SkillCreateEvents.AddFile)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(CompoundIcons.Plus(), contentDescription = null) },
            headlineContent = { Text(stringResource(R.string.skill_create_add_file), color = MaterialTheme.colorScheme.primary) },
        )
        Text(
            text = stringResource(R.string.skill_create_text_import_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.error?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(8.dp))

        // MARK: Submit
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { state.eventSink(SkillCreateEvents.Submit) },
            enabled = !state.isSubmitting,
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(stringResource(R.string.skill_create_submit))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FilesSectionHeader(
    onPickFile: () -> Unit,
    onPickZip: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(R.string.skill_create_file),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        TextButton(onClick = onPickFile, enabled = enabled) {
            Icon(CompoundIcons.Attachment(), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.skill_create_upload_file), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onPickZip, enabled = enabled) {
            Icon(CompoundIcons.Files(), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(4.dp))
            Text(stringResource(R.string.skill_create_upload_zip), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun FileRow(
    file: ManualSkillFile,
    canDelete: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickableRow(enabled = true, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(CompoundIcons.Document(), contentDescription = null) },
        headlineContent = {
            Text(
                text = file.path.ifEmpty { stringResource(R.string.skill_create_file_path_placeholder) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = CompoundIcons.Delete(),
                        contentDescription = stringResource(CommonStrings.action_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
    )
}

@Composable
private fun FileEditorBottomSheet(
    file: ManualSkillFile,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var path by rememberSaveable(file.id) { mutableStateOf(file.path) }
    var content by rememberSaveable(file.id) { mutableStateOf(file.content) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.fillMaxWidth().imePadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(CommonStrings.action_cancel)) }
                Text(
                    modifier = Modifier.weight(1f),
                    text = path.ifEmpty { stringResource(R.string.skill_create_file_editor_title) },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                TextButton(onClick = { onSave(path, content) }) { Text(stringResource(CommonStrings.action_done)) }
            }
            HorizontalDivider()
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                value = path,
                onValueChange = { path = it },
                placeholder = { Text(stringResource(R.string.skill_create_file_path_placeholder)) },
                singleLine = true,
            )
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                value = content,
                onValueChange = { content = it },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SuccessContent(
    name: String,
    onViewDetail: () -> Unit,
    onBackToList: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CompoundIcons.CheckCircle(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.skill_create_success_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                Text(name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onViewDetail,
            ) {
                Icon(CompoundIcons.Document(), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.skill_create_view_detail))
            }
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = onBackToList,
            ) {
                Text(stringResource(R.string.skill_create_back_to_list))
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
    )
}

private fun Modifier.clickableRow(enabled: Boolean, onClick: () -> Unit): Modifier =
    this.clickable(enabled = enabled, onClick = onClick)

internal class SkillCreateStateProvider : PreviewParameterProvider<SkillCreateState> {
    override val values: Sequence<SkillCreateState>
        get() = sequenceOf(
            aSkillCreateState(),
            aSkillCreateState(isSubmitting = true),
            aSkillCreateState(
                manualFiles = persistentListOf(
                    ManualSkillFile("1", "SKILL.md", "# Weather\nFetches weather."),
                    ManualSkillFile("2", "scripts/run.py", "print('hi')"),
                ),
                name = "Weather lookup",
                description = "Fetches current weather and forecasts.",
            ),
            aSkillCreateState(phase = SkillCreatePhase.Success(id = "weather", name = "Weather lookup")),
        )
}

private fun aSkillCreateState(
    phase: SkillCreatePhase = SkillCreatePhase.Editing,
    name: String = "",
    description: String = "",
    visibility: ChatbotSkillVisibility = ChatbotSkillVisibility.Private,
    manualFiles: kotlinx.collections.immutable.ImmutableList<ManualSkillFile> =
        persistentListOf(ManualSkillFile("1", "SKILL.md", "")),
    isSubmitting: Boolean = false,
    editingFile: ManualSkillFile? = null,
    pendingFileConflict: SkillCreateFileConflict? = null,
    error: String? = null,
) = SkillCreateState(
    phase = if (isSubmitting) SkillCreatePhase.Submitting else phase,
    name = name,
    description = description,
    skillContent = "",
    visibility = visibility,
    manualFiles = manualFiles,
    editingFile = editingFile,
    pendingFileConflict = pendingFileConflict,
    error = error,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun SkillCreateViewPreview(@PreviewParameter(SkillCreateStateProvider::class) state: SkillCreateState) = ElementPreview {
    SkillCreateView(state = state, onBackClick = {})
}
