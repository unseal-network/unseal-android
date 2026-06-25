/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.element.android.features.skills.impl.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.shared.SkillMetadataChips
import io.element.android.features.skills.impl.shared.displayName
import io.element.android.features.skills.impl.shared.hasDiscoveryMetadata
import io.element.android.libraries.chatbot.api.model.skills.ChatbotGetUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight

@Composable
fun SkillDetailView(
    state: SkillDetailState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(SkillDetailEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(state.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (state.isEditing) {
                        TextButton(onClick = { state.eventSink(SkillDetailEvents.CancelEditing) }, enabled = !state.isSaving) {
                            Text("取消")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    when {
                        state.isEditing -> {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
                            } else {
                                TextButton(onClick = { state.eventSink(SkillDetailEvents.SaveEditing) }) { Text("保存") }
                            }
                        }
                        state.canEdit -> {
                            TextButton(onClick = { state.eventSink(SkillDetailEvents.StartEditing) }) { Text("编辑") }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.isEditing) {
                SkillEditForm(state)
            } else {
                SkillReadOnlyContent(state)
                if (state.canEdit) {
                    OutlinedButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { state.eventSink(SkillDetailEvents.Delete) },
                        enabled = !state.isDeleting,
                    ) {
                        Icon(CompoundIcons.Delete(), null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.isDeleting) "删除中…" else "删除技能")
                    }
                }
            }
            if (state.isLoading) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text("正在加载...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(24.dp))
        }
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

@Composable
private fun InfoRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun SkillReadOnlyContent(state: SkillDetailState) {
    SectionHeader("信息")
    InfoRow("名称", state.title)
    state.skill?.description?.takeIf { it.isNotBlank() }?.let { InfoRow("描述", it) }
    state.visibilityLabel?.let { InfoRow("可见性", it) }
    state.skill?.createdAt?.let { InfoRow("创建时间", it) }

    state.skill?.takeIf { it.hasDiscoveryMetadata() }?.let { skill ->
        SectionHeader("Discovery")
        SkillMetadataChips(skill = skill, maxTags = 20)
        skill.source?.repository?.takeIf { it.isNotBlank() }?.let { InfoRow("Repository", it) }
        skill.source?.path?.takeIf { it.isNotBlank() }?.let { InfoRow("Path", it) }
        skill.source?.ref?.takeIf { it.isNotBlank() }?.let { InfoRow("Ref", it) }
        skill.source?.trustTier?.takeIf { it.isNotBlank() }?.let { InfoRow("Trust", it) }
    }

    val files = state.fileItems
    if (files.isNotEmpty()) {
        FilesSectionHeader(count = files.size)
        files.forEach { file ->
            FileRow(
                name = file.displayPath,
                onClick = { state.eventSink(SkillDetailEvents.OpenFile(file)) },
            )
        }
    }
}

@Composable
private fun FilesSectionHeader(count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = "文件",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun FileRow(name: String, onClick: () -> Unit) {
    val rowShape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
        shape = rowShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = CompoundIcons.Attachment(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                imageVector = CompoundIcons.ChevronRight(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SkillEditForm(state: SkillDetailState) {
    SectionHeader("编辑信息")
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.editName,
        onValueChange = { state.eventSink(SkillDetailEvents.EditNameChanged(it)) },
        label = { Text("名称") },
        singleLine = true,
        enabled = !state.isSaving,
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.editDescription,
        onValueChange = { state.eventSink(SkillDetailEvents.EditDescriptionChanged(it)) },
        label = { Text("描述") },
        placeholder = { Text("可选描述") },
        minLines = 3,
        enabled = !state.isSaving,
    )
    Text("可见性", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatbotSkillVisibility.entries.forEach { visibility ->
            FilterChip(
                selected = state.editVisibility == visibility,
                onClick = { state.eventSink(SkillDetailEvents.EditVisibilityChanged(visibility)) },
                enabled = !state.isSaving,
                label = { Text(visibility.displayName()) },
            )
        }
    }
}

internal class SkillDetailStateProvider : PreviewParameterProvider<SkillDetailState> {
    override val values: Sequence<SkillDetailState>
        get() = sequenceOf(
            aSkillDetailState(),
            aSkillDetailState(isEditing = true),
            aSkillDetailState(isOwner = false),
        )
}

private fun aSkillDetailState(
    isOwner: Boolean = true,
    isEditing: Boolean = false,
) = SkillDetailState(
    id = "weather",
    isOwner = isOwner,
    response = ChatbotGetUserSkillResponse(
        skill = ChatbotUserSkill(
            id = "weather",
            name = "Weather lookup",
            description = "Fetches current weather and forecasts for any location.",
            visibility = ChatbotSkillVisibility.Public,
        ),
    ),
    isLoading = false,
    isSaving = false,
    isDeleting = false,
    isEditing = isEditing,
    editName = "Weather lookup",
    editDescription = "Fetches current weather and forecasts for any location.",
    editVisibility = ChatbotSkillVisibility.Public,
    error = null,
    eventSink = {},
)

@PreviewsDayNight
@Composable
internal fun SkillDetailViewPreview(@PreviewParameter(SkillDetailStateProvider::class) state: SkillDetailState) = ElementPreview {
    SkillDetailView(state = state, onBackClick = {})
}
