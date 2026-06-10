/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.shared.SkillListRow
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillsHomeView(
    state: SkillsHomeState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(SkillsHomeEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Skills") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { state.eventSink(SkillsHomeEvents.CreateSkill) }) {
                        Icon(imageVector = CompoundIcons.Plus(), contentDescription = "Create skill")
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
                SkillsTabPicker(
                    selectedTab = state.selectedTab,
                    onSelect = { state.eventSink(SkillsHomeEvents.SelectTab(it)) },
                )
            }
            item {
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    value = state.searchQuery,
                    onValueChange = { state.eventSink(SkillsHomeEvents.SearchQueryChanged(it)) },
                    placeholder = {
                        Text(if (state.selectedTab == SkillsHomeTab.Mine) "Search your skills" else "Search public skills")
                    },
                    leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }

            if (state.selectedTab == SkillsHomeTab.Mine) {
                mineContent(state)
            } else {
                marketplaceContent(state)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mineContent(state: SkillsHomeState) {
    when {
        state.isLoading && state.filteredSkills.isEmpty() -> {
            items(count = 5) { SkillSkeletonRow() }
        }
        state.filteredSkills.isEmpty() -> {
            item { EmptyMessage("You haven't created any skills yet.") }
        }
        else -> {
            items(items = state.filteredSkills, key = { "mine-${it.id}" }) { skill ->
                SkillListRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    skill = skill,
                    showVisibility = true,
                    onClick = { state.eventSink(SkillsHomeEvents.SelectSkill(skill.id)) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.marketplaceContent(state: SkillsHomeState) {
    when {
        state.isLoadingMarketplace && state.marketplaceSkills.isEmpty() -> {
            items(count = 5) { SkillSkeletonRow() }
        }
        state.marketplaceSkills.isEmpty() -> {
            item { EmptyMessage("No public skills available.") }
        }
        else -> {
            state.marketplaceTotal?.let { total ->
                item {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        text = "$total public skills",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(items = state.marketplaceSkills, key = { "market-${it.id}" }) { skill ->
                SkillListRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    skill = skill,
                    showVisibility = false,
                    onClick = { state.eventSink(SkillsHomeEvents.SelectMarketplaceSkill(skill.id)) },
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (state.marketplaceHasMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.eventSink(SkillsHomeEvents.LoadNextMarketplacePage) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isLoadingMarketplaceNextPage) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        } else {
                            Text(
                                text = "Load more",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SkillsTabPicker(
    selectedTab: SkillsHomeTab,
    onSelect: (SkillsHomeTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(percent = 50))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SkillsHomeTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Text(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                        RoundedCornerShape(percent = 50),
                    )
                    .clickable { onSelect(tab) }
                    .padding(vertical = 7.dp),
                text = if (tab == SkillsHomeTab.Mine) "My skills" else "Marketplace",
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyMessage(text: String) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 40.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SkillSkeletonRow() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(alpha),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 140.dp, height = 15.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .size(height = 12.dp, width = 200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
            )
        }
    }
}

internal class SkillsHomeStateProvider : PreviewParameterProvider<SkillsHomeState> {
    override val values: Sequence<SkillsHomeState>
        get() = sequenceOf(
            aSkillsHomeState(),
            aSkillsHomeState(skills = persistentListOf(), isLoading = true),
        )
}

private fun aSkillsHomeState(
    skills: kotlinx.collections.immutable.ImmutableList<ChatbotUserSkill> = aSampleSkills(),
    isLoading: Boolean = false,
) = SkillsHomeState(
    skills = skills,
    selectedTab = SkillsHomeTab.Mine,
    marketplaceSkills = persistentListOf(),
    marketplaceTotal = null,
    marketplacePage = 1,
    marketplacePageSize = 20,
    isLoading = isLoading,
    isLoadingMarketplace = false,
    isLoadingMarketplaceNextPage = false,
    searchQuery = "",
    error = null,
    eventSink = {},
)

private fun aSampleSkills() = persistentListOf(
    ChatbotUserSkill(
        id = "weather",
        name = "Weather lookup",
        description = "Fetches current weather and forecasts for any location.",
        visibility = ChatbotSkillVisibility.Public,
    ),
    ChatbotUserSkill(
        id = "calendar",
        name = "Calendar",
        description = "Reads and creates calendar events.",
        visibility = ChatbotSkillVisibility.Private,
    ),
    ChatbotUserSkill(
        id = "search",
        name = "Web search",
        description = "Searches the web and summarises results.",
        visibility = ChatbotSkillVisibility.Shared,
    ),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun SkillsHomeViewPreview(@PreviewParameter(SkillsHomeStateProvider::class) state: SkillsHomeState) = ElementPreview {
    SkillsHomeView(
        state = state,
        onBackClick = {},
    )
}
