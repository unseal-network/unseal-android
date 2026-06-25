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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.shared.SkillFilterSheet
import io.element.android.features.skills.impl.shared.SkillFilterState
import io.element.android.features.skills.impl.shared.SkillFilterTokensRow
import io.element.android.features.skills.impl.shared.SkillIconTile
import io.element.android.features.skills.impl.shared.SkillListRow
import io.element.android.features.skills.impl.shared.deriveSkillFacets
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillVisibility
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.designsystem.components.management.ManagementListRow
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
                title = { Text("技能") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        val isRefreshing = if (state.selectedTab == SkillsHomeTab.Mine) state.isLoading else state.isLoadingMarketplace
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            isRefreshing = isRefreshing,
            onRefresh = { state.eventSink(SkillsHomeEvents.Refresh) },
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    SkillsTabPicker(
                        selectedTab = state.selectedTab,
                        onSelect = { state.eventSink(SkillsHomeEvents.SelectTab(it)) },
                    )
                }
                item {
                    SearchAndFilterControls(state)
                }

                if (state.isFilterSheetVisible) {
                    item {
                        SkillFilterSheet(
                            facets = state.facets,
                            filterState = state.filterState,
                            onApplyToken = { state.eventSink(SkillsHomeEvents.ApplyFilterToken(it)) },
                            onTagModeChanged = { state.eventSink(SkillsHomeEvents.TagModeChanged(it)) },
                            onDismiss = { state.eventSink(SkillsHomeEvents.DismissFilterSheet) },
                        )
                    }
                }

                if (state.filterState.activeTokenCount > 0) {
                    item {
                        SkillFilterTokensRow(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            filterState = state.filterState,
                            onRemove = { state.eventSink(SkillsHomeEvents.RemoveFilterToken(it)) },
                            onClear = { state.eventSink(SkillsHomeEvents.ClearFilters) },
                        )
                    }
                }

                if (state.selectedTab == SkillsHomeTab.Mine) {
                    item { CreateSkillRow(state) }
                    mineContent(state)
                } else {
                    marketplaceContent(state)
                }
            }
        }
    }
}

@Composable
private fun SearchAndFilterControls(state: SkillsHomeState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = { state.eventSink(SkillsHomeEvents.SearchQueryChanged(it)) },
            placeholder = {
                Text(if (state.selectedTab == SkillsHomeTab.Mine) "搜索技能" else "搜索公开技能")
            },
            leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        FilledTonalButton(
            enabled = state.filtersAvailable,
            onClick = { state.eventSink(SkillsHomeEvents.AddFilter) },
        ) {
            Text(if (state.filterState.activeTokenCount > 0) "筛选 · ${state.filterState.activeTokenCount}" else "筛选")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.mineContent(state: SkillsHomeState) {
    when {
        state.isLoading && state.filteredSkills.isEmpty() -> {
            items(count = 5) { SkillSkeletonRow() }
        }
        state.filteredSkills.isEmpty() -> {
            item {
                EmptyMessage(
                    text = if (state.showClearFiltersForEmptyMine) "没有符合条件的技能" else "暂无技能",
                    actionText = if (state.showClearFiltersForEmptyMine) "清除筛选" else null,
                    onAction = if (state.showClearFiltersForEmptyMine) {
                        { state.eventSink(SkillsHomeEvents.ClearFilters) }
                    } else {
                        null
                    },
                )
            }
        }
        else -> {
            items(items = state.filteredSkills, key = { "mine-${it.id}" }) { skill ->
                SkillListRow(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    skill = skill,
                    showVisibility = true,
                    onClick = { state.eventSink(SkillsHomeEvents.SelectSkill(skill.id)) },
                    onFilterSelected = { state.eventSink(SkillsHomeEvents.ApplyFilterToken(it)) },
                )
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
            item { EmptyMessage("暂无公开技能") }
        }
        else -> {
            state.marketplaceTotal?.let { total ->
                item {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        text = "$total 个公开技能",
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
                    onFilterSelected = { state.eventSink(SkillsHomeEvents.ApplyFilterToken(it)) },
                )
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
                                text = "加载更多",
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
private fun CreateSkillRow(state: SkillsHomeState) {
    ManagementListRow(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        title = "创建技能",
        description = "创建可复用的 Agent 能力并加入技能库",
        leadingContent = { SkillIconTile() },
        trailingContent = {
            FilledTonalButton(onClick = { state.eventSink(SkillsHomeEvents.CreateSkill) }) {
                Text("创建")
            }
        },
    )
}

@Composable
private fun SkillsTabPicker(
    selectedTab: SkillsHomeTab,
    onSelect: (SkillsHomeTab) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        SkillsHomeTab.entries.forEachIndexed { index, tab ->
            SegmentedButton(
                selected = tab == selectedTab,
                onClick = { onSelect(tab) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = SkillsHomeTab.entries.size),
                label = { Text(if (tab == SkillsHomeTab.Mine) "我的" else "公开") },
            )
        }
    }
}

@Composable
private fun EmptyMessage(
    text: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 40.dp),
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    if (actionText != null && onAction != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            FilledTonalButton(onClick = onAction) {
                Text(actionText)
            }
        }
    }
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
    filterState = SkillFilterState(),
    facets = deriveSkillFacets(skills),
    isFilterSheetVisible = false,
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
