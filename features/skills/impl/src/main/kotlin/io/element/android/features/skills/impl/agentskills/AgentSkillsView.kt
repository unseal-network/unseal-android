/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.element.android.features.skills.impl.agentskills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton

@Composable
fun AgentSkillsView(
    state: AgentSkillsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(AgentSkillsEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text("Agent Skills")
                        Text(
                            text = state.botName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
                    } else {
                        TextButton(onClick = { state.eventSink(AgentSkillsEvents.Save) }) {
                            Text("Save")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedTab == AgentSkillsTab.Mine,
                        onClick = { state.eventSink(AgentSkillsEvents.SelectTab(AgentSkillsTab.Mine)) },
                        label = { Text("Mine") },
                    )
                    FilterChip(
                        selected = state.selectedTab == AgentSkillsTab.Public,
                        onClick = { state.eventSink(AgentSkillsEvents.SelectTab(AgentSkillsTab.Public)) },
                        label = { Text("Public") },
                    )
                }
            }
            item {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    value = state.searchQuery,
                    onValueChange = { state.eventSink(AgentSkillsEvents.SearchQueryChanged(it)) },
                    placeholder = { Text("Search skills") },
                    leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }
            if (state.deselectedOriginalCount > 0) {
                item {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        text = "Existing attached skills can't be removed yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = state.saveFailures, key = { "fail-${it.skillId}" }) { failure ->
                Text(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    text = "${failure.skillId}: ${failure.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.error?.let { error ->
                item {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            when (state.selectedTab) {
                AgentSkillsTab.Mine -> mineSkills(state)
                AgentSkillsTab.Public -> publicSkills(state)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mineSkills(state: AgentSkillsState) {
    val skills = state.filteredUserSkills
    when {
        state.isLoading && skills.isEmpty() -> item { LoadingRow() }
        skills.isEmpty() -> item { EmptyRow("You haven't created any skills yet.") }
        else -> items(items = skills, key = { "mine-${it.id}" }) { skill ->
            SelectableSkillRow(
                skill = skill,
                selected = skill.id in state.selectedSkillIds,
                onToggle = { state.eventSink(AgentSkillsEvents.ToggleSkill(skill)) },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.publicSkills(state: AgentSkillsState) {
    state.publicTotal?.let { total ->
        item {
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                text = "$total public skills",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    when {
        state.isLoadingPublic && state.publicSkills.isEmpty() -> item { LoadingRow() }
        state.publicSkills.isEmpty() -> item { EmptyRow("No public skills available.") }
        else -> {
            items(items = state.publicSkills, key = { "public-${it.id}" }) { skill ->
                SelectableSkillRow(
                    skill = skill,
                    selected = skill.id in state.selectedSkillIds,
                    onToggle = { state.eventSink(AgentSkillsEvents.ToggleSkill(skill)) },
                )
                HorizontalDivider(modifier = Modifier.padding(start = 72.dp))
            }
            if (state.publicHasMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !state.isLoadingPublicNextPage) {
                                state.eventSink(AgentSkillsEvents.LoadNextPublicPage)
                            }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isLoadingPublicNextPage) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text("Load more", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
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
    ListItem(
        modifier = Modifier.clickable(onClick = onToggle),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = {
            Avatar(
                avatarData = AvatarData(skill.id, skill.name, null, AvatarSize.RoomListItem),
                avatarType = AvatarType.Room(),
                forcedAvatarSize = 40.dp,
            )
        },
        headlineContent = { Text(skill.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = skill.description?.takeIf { it.isNotBlank() }?.let {
            { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        },
    )
}

@Composable
private fun LoadingRow() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

internal class AgentSkillsStateProvider : PreviewParameterProvider<AgentSkillsState> {
    override val values: Sequence<AgentSkillsState>
        get() = sequenceOf(
            anAgentSkillsState(),
            anAgentSkillsState(selectedTab = AgentSkillsTab.Public),
        )
}

private fun anAgentSkillsState(
    selectedTab: AgentSkillsTab = AgentSkillsTab.Mine,
) = AgentSkillsState(
    botName = "assistant",
    attachedSkills = persistentListOf(),
    userSkills = aSampleAgentSkills(),
    selectedSkills = persistentListOf(),
    originalSkillIds = persistentSetOf(),
    selectedSkillIds = persistentSetOf("weather"),
    selectedTab = selectedTab,
    searchQuery = "",
    publicSkills = aSamplePublicSkills(),
    publicTotal = 2,
    publicPage = 1,
    publicPageSize = 20,
    publicHasMore = false,
    isLoading = false,
    isLoadingPublic = false,
    isLoadingPublicNextPage = false,
    isSaving = false,
    saveFailures = persistentListOf(),
    error = null,
    eventSink = {},
)

private fun aSampleAgentSkills() = persistentListOf(
    ChatbotUserSkill(id = "weather", name = "Weather lookup", description = "Fetches current weather.", visibility = ChatbotSkillVisibility.Public),
    ChatbotUserSkill(id = "calendar", name = "Calendar", description = "Reads and creates events.", visibility = ChatbotSkillVisibility.Private),
).toImmutableList()

private fun aSamplePublicSkills() = persistentListOf(
    ChatbotUserSkill(id = "translate", name = "Translator", description = "Translates text between languages."),
    ChatbotUserSkill(id = "summarise", name = "Summariser", description = "Condenses long documents."),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun AgentSkillsViewPreview(@PreviewParameter(AgentSkillsStateProvider::class) state: AgentSkillsState) = ElementPreview {
    AgentSkillsView(state = state, onBackClick = {})
}
