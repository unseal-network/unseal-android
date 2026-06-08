/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.features.skills.impl.shared.displayName
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility

@Composable
fun SkillDetailView(
    state: SkillDetailState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(SkillDetailEvents.OnAppear)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onBackClick) {
                    Text("Back")
                }
                OutlinedButton(onClick = { state.eventSink(SkillDetailEvents.Refresh) }) {
                    Text("Refresh")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = state.id,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.visibilityLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (state.isEditing) {
            item {
                SkillEditForm(state)
            }
        } else {
            item {
                SkillReadOnlyContent(state)
            }
            if (state.canEdit) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = { state.eventSink(SkillDetailEvents.StartEditing) },
                        ) {
                            Text("Edit")
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = { state.eventSink(SkillDetailEvents.Delete) },
                            enabled = !state.isDeleting,
                        ) {
                            Text(if (state.isDeleting) "Deleting..." else "Delete")
                        }
                    }
                }
            }
        }
        item {
            if (state.isLoading || state.isSaving) {
                CircularProgressIndicator()
            }
            state.error?.let {
                OutlinedButton(onClick = { state.eventSink(SkillDetailEvents.ClearError) }) {
                    Text(it)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SkillReadOnlyContent(state: SkillDetailState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val description = state.skill?.description?.takeIf { it.isNotBlank() }
        Text(
            text = description ?: "No description",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.isOwner) {
            Text(
                text = "Public marketplace skill",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SkillEditForm(state: SkillDetailState) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.editName,
            onValueChange = { state.eventSink(SkillDetailEvents.EditNameChanged(it)) },
            label = { Text("Name") },
            singleLine = true,
        )
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.editDescription,
            onValueChange = { state.eventSink(SkillDetailEvents.EditDescriptionChanged(it)) },
            label = { Text("Description") },
            minLines = 3,
        )
        Text("Visibility", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VisibilityButton(state, ChatbotSkillVisibility.Private, Modifier.weight(1f))
            VisibilityButton(state, ChatbotSkillVisibility.Public, Modifier.weight(1f))
            VisibilityButton(state, ChatbotSkillVisibility.Shared, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(SkillDetailEvents.SaveEditing) },
                enabled = !state.isSaving,
            ) {
                Text(if (state.isSaving) "Saving..." else "Save")
            }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(SkillDetailEvents.CancelEditing) },
                enabled = !state.isSaving,
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun VisibilityButton(
    state: SkillDetailState,
    visibility: ChatbotSkillVisibility,
    modifier: Modifier = Modifier,
) {
    if (state.editVisibility == visibility) {
        Button(
            modifier = modifier,
            onClick = { state.eventSink(SkillDetailEvents.EditVisibilityChanged(visibility)) },
        ) {
            Text(visibility.displayName())
        }
    } else {
        OutlinedButton(
            modifier = modifier,
            onClick = { state.eventSink(SkillDetailEvents.EditVisibilityChanged(visibility)) },
        ) {
            Text(visibility.displayName())
        }
    }
}
