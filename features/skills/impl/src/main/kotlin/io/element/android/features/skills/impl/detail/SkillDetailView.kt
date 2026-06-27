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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.R
import io.element.android.features.skills.impl.shared.SkillMetadataChips
import io.element.android.features.skills.impl.shared.displayNameRes
import io.element.android.features.skills.impl.shared.hasDiscoveryMetadata
import io.element.android.libraries.chatbot.api.model.skills.ChatbotGetUserSkillResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

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
                            Text(stringResource(CommonStrings.action_cancel))
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_go_back))
                        }
                    }
                },
                actions = {
                    when {
                        state.isEditing -> {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
                            } else {
                                TextButton(onClick = { state.eventSink(SkillDetailEvents.SaveEditing) }) { Text(stringResource(CommonStrings.action_save)) }
                            }
                        }
                        state.canEdit -> {
                            TextButton(onClick = { state.eventSink(SkillDetailEvents.StartEditing) }) { Text(stringResource(CommonStrings.action_edit)) }
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
                        Text(if (state.isDeleting) stringResource(R.string.skill_detail_deleting) else stringResource(R.string.skill_detail_delete))
                    }
                }
            }
            if (state.isLoading) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.skill_detail_loading), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    SectionHeader(stringResource(R.string.skill_detail_info))
    InfoRow(stringResource(R.string.skill_detail_name), state.title)
    state.skill?.description?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.skill_detail_description), it) }
    state.visibilityLabelRes?.let { InfoRow(stringResource(R.string.skill_detail_visibility), stringResource(it)) }
    state.skill?.createdAt?.let { InfoRow(stringResource(R.string.skill_detail_created_at), it) }

    state.skill?.takeIf { it.hasDiscoveryMetadata() }?.let { skill ->
        SectionHeader(stringResource(R.string.skill_detail_discovery))
        SkillMetadataChips(
            skill = skill,
            maxTags = 20,
            onFilterSelected = if (state.canApplyMetadataFilters) {
                { state.eventSink(SkillDetailEvents.ApplyFilterToken(it)) }
            } else {
                null
            },
        )
        skill.source?.repository?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.skill_detail_repository), it) }
        skill.source?.path?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.skill_detail_path), it) }
        skill.source?.ref?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.skill_detail_ref), it) }
        skill.source?.trustTier?.takeIf { it.isNotBlank() }?.let { InfoRow(stringResource(R.string.skill_detail_trust), it) }
    }

    val presignedUrls = state.response?.presignedUrls.orEmpty()
    val fileFallbacks = presignedUrls.indices.map { index ->
        stringResource(R.string.skill_file_fallback, index + 1)
    }
    val files = buildSkillFileRenderModels(
        skillId = state.id,
        presignedUrls = presignedUrls,
        preuploadUrls = state.response?.preuploadUrls.orEmpty(),
        fallbackForIndex = { index -> fileFallbacks.getOrElse(index) { (index + 1).toString() } },
    )
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
            text = stringResource(R.string.skill_detail_files),
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
    SectionHeader(stringResource(R.string.skill_detail_edit_info))
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.editName,
        onValueChange = { state.eventSink(SkillDetailEvents.EditNameChanged(it)) },
        label = { Text(stringResource(R.string.skill_detail_name)) },
        singleLine = true,
        enabled = !state.isSaving,
    )
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = state.editDescription,
        onValueChange = { state.eventSink(SkillDetailEvents.EditDescriptionChanged(it)) },
        label = { Text(stringResource(R.string.skill_detail_description)) },
        placeholder = { Text(stringResource(R.string.skill_detail_optional_description)) },
        minLines = 3,
        enabled = !state.isSaving,
    )
    Text(stringResource(R.string.skill_detail_visibility), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChatbotSkillVisibility.entries.forEach { visibility ->
            FilterChip(
                selected = state.editVisibility == visibility,
                onClick = { state.eventSink(SkillDetailEvents.EditVisibilityChanged(visibility)) },
                enabled = !state.isSaving,
                label = { Text(stringResource(visibility.displayNameRes())) },
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
    canApplyMetadataFilters = false,
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
