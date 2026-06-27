/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.skills.impl.agentskills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.R
import io.element.android.features.skills.impl.shared.SkillIconTile
import io.element.android.features.skills.impl.shared.skillCategory
import io.element.android.features.skills.impl.shared.skillSourceLabel
import io.element.android.features.skills.impl.shared.skillTags
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.designsystem.components.management.ManagementListRow
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun AgentSkillsView(
    state: AgentSkillsState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(AgentSkillsEvents.OnAppear)
    }
    val noSkills = stringResource(R.string.skills_empty)
    val noPublicSkills = stringResource(R.string.skills_empty_public)
    val loadMore = stringResource(R.string.skills_load_more)
    val publicCount: @Composable (Int) -> String = { count -> stringResource(R.string.skills_public_count, count) }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.agent_skills_title))
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
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_go_back))
                    }
                },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp).padding(end = 8.dp))
                    } else {
                        TextButton(onClick = { state.eventSink(AgentSkillsEvents.Save) }) {
                            Text(stringResource(CommonStrings.action_save))
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
                AgentSkillsTabPicker(
                    selectedTab = state.selectedTab,
                    onSelect = { state.eventSink(AgentSkillsEvents.SelectTab(it)) },
                )
            }
            item {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    value = state.searchQuery,
                    onValueChange = { state.eventSink(AgentSkillsEvents.SearchQueryChanged(it)) },
                    placeholder = { Text(stringResource(R.string.skills_search_placeholder)) },
                    leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }
            if (state.deselectedOriginalCount > 0) {
                item {
                    Text(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        text = stringResource(R.string.agent_skills_cannot_remove_added),
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
                AgentSkillsTab.Mine -> mineSkills(state, noSkills)
                AgentSkillsTab.Public -> publicSkills(state, noPublicSkills, loadMore, publicCount)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mineSkills(state: AgentSkillsState, noSkills: String) {
    val skills = state.filteredUserSkills
    when {
        state.isLoading && skills.isEmpty() -> item { LoadingRow() }
        skills.isEmpty() -> item { EmptyRow(noSkills) }
        else -> items(items = skills, key = { "mine-${it.id}" }) { skill ->
            SelectableSkillRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                skill = skill,
                selected = skill.id in state.selectedSkillIds,
                onToggle = { state.eventSink(AgentSkillsEvents.ToggleSkill(skill)) },
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.publicSkills(
    state: AgentSkillsState,
    noPublicSkills: String,
    loadMore: String,
    publicCount: @Composable (Int) -> String,
) {
    state.publicTotal?.let { total ->
        item {
            Text(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                text = publicCount(total),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    when {
        state.isLoadingPublic && state.publicSkills.isEmpty() -> item { LoadingRow() }
        state.publicSkills.isEmpty() -> item { EmptyRow(noPublicSkills) }
        else -> {
            items(items = state.publicSkills, key = { "public-${it.id}" }) { skill ->
                SelectableSkillRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    skill = skill,
                    selected = skill.id in state.selectedSkillIds,
                    onToggle = { state.eventSink(AgentSkillsEvents.ToggleSkill(skill)) },
                )
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
                            Text(loadMore, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentSkillsTabPicker(
    selectedTab: AgentSkillsTab,
    onSelect: (AgentSkillsTab) -> Unit,
) {
    val tabs = listOf(
        AgentSkillsTab.Mine to stringResource(R.string.skills_tab_mine),
        AgentSkillsTab.Public to stringResource(R.string.skills_tab_public),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        tabs.forEachIndexed { index, (tab, label) ->
            SegmentedButton(
                selected = selectedTab == tab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun SelectableSkillRow(
    modifier: Modifier = Modifier,
    skill: ChatbotUserSkill,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    ManagementListRow(
        modifier = modifier.padding(vertical = 4.dp),
        title = skill.name,
        description = listOfNotNull(
            skill.description,
            skill.skillMetadataSummary(),
        ).joinToString("\n"),
        onClick = onToggle,
        leadingContent = {
            SkillIconTile()
        },
        trailingContent = {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
        },
    )
}

private fun ChatbotUserSkill.skillMetadataSummary(): String? {
    val parts = buildList {
        skillCategory()?.let { add(it) }
        val skillTags = skillTags()
        skillTags.take(2).forEach { add("#$it") }
        if (skillTags.size > 2) add("+${skillTags.size - 2}")
        skillSourceLabel()?.let { add(it) }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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
