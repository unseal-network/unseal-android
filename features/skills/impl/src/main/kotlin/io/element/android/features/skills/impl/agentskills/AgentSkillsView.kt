/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.agentskills

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.element.android.features.skills.impl.shared.SkillListRow
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill

@Composable
fun AgentSkillsView(
    state: AgentSkillsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(AgentSkillsEvents.OnAppear)
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
            OutlinedButton(onClick = onBackClick) {
                Text("Back")
            }
            Button(
                onClick = { state.eventSink(AgentSkillsEvents.Save) },
                enabled = !state.isSaving,
            ) {
                Text(if (state.isSaving) "Saving..." else "Save")
            }
        }
        Text("Agent Skills", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            text = state.botName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton(
                text = "Mine",
                selected = state.selectedTab == AgentSkillsTab.Mine,
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(AgentSkillsEvents.SelectTab(AgentSkillsTab.Mine)) },
            )
            TabButton(
                text = "Public",
                selected = state.selectedTab == AgentSkillsTab.Public,
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(AgentSkillsEvents.SelectTab(AgentSkillsTab.Public)) },
            )
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = { state.eventSink(AgentSkillsEvents.SearchQueryChanged(it)) },
            label = { Text("Search skills") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.eventSink(AgentSkillsEvents.Refresh) }) {
                Text("Refresh")
            }
            state.error?.let {
                OutlinedButton(onClick = { state.eventSink(AgentSkillsEvents.ClearError) }) {
                    Text(it)
                }
            }
        }
        if (state.isLoading || state.isSaving) {
            CircularProgressIndicator()
        }
        if (state.deselectedOriginalCount > 0) {
            Text(
                text = "Existing attached skills cannot be removed yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        state.saveFailures.forEach {
            Text(
                text = "${it.skillId}: ${it.message}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        when (state.selectedTab) {
            AgentSkillsTab.Mine -> SkillList(
                skills = state.filteredUserSkills,
                selectedSkillIds = state.selectedSkillIds,
                emptyText = "No skills",
                onToggle = { state.eventSink(AgentSkillsEvents.ToggleSkill(it)) },
            )
            AgentSkillsTab.Public -> PublicSkillList(state)
        }
    }
}

@Composable
private fun PublicSkillList(state: AgentSkillsState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.publicTotal?.let {
            Text(
                text = "Public ($it)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            state.isLoadingPublic && state.publicSkills.isEmpty() -> CircularProgressIndicator()
            state.publicSkills.isEmpty() -> Text("No public skills")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.publicSkills, key = { it.id }) { skill ->
                    SelectableSkillRow(
                        skill = skill,
                        selected = skill.id in state.selectedSkillIds,
                        onToggle = { state.eventSink(AgentSkillsEvents.ToggleSkill(skill)) },
                    )
                }
                item {
                    if (state.publicHasMore) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { state.eventSink(AgentSkillsEvents.LoadNextPublicPage) },
                            enabled = !state.isLoadingPublicNextPage,
                        ) {
                            Text(if (state.isLoadingPublicNextPage) "Loading..." else "Load more")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillList(
    skills: List<ChatbotUserSkill>,
    selectedSkillIds: Set<String>,
    emptyText: String,
    onToggle: (ChatbotUserSkill) -> Unit,
) {
    when {
        skills.isEmpty() -> Text(emptyText)
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(skills, key = { it.id }) { skill ->
                SelectableSkillRow(
                    skill = skill,
                    selected = skill.id in selectedSkillIds,
                    onToggle = { onToggle(skill) },
                )
            }
        }
    }
}

@Composable
private fun SelectableSkillRow(
    skill: ChatbotUserSkill,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SkillListRow(
            skill = skill,
            showVisibility = false,
            onClick = onToggle,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                text = "Selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}
