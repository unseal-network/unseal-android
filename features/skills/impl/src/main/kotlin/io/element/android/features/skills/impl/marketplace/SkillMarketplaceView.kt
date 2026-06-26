/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.marketplace

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.shared.SkillFilterSheet
import io.element.android.features.skills.impl.shared.SkillFilterState
import io.element.android.features.skills.impl.shared.SkillFilterTokensRow
import io.element.android.features.skills.impl.shared.SkillListRow
import io.element.android.libraries.chatbot.api.model.skills.ChatbotSkillFacetsResponse
import io.element.android.libraries.chatbot.api.model.skills.ChatbotUserSkill
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkillMarketplaceView(
    state: SkillMarketplaceState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(SkillMarketplaceEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Skill 市场") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            isRefreshing = state.isLoading,
            onRefresh = { state.eventSink(SkillMarketplaceEvents.Refresh) },
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                SearchAndFilterControls(state)
            }
            if (state.isFilterSheetVisible) {
                item {
                    SkillFilterSheet(
                        facets = state.facets,
                        filterState = state.filterState,
                        onApplyToken = { state.eventSink(SkillMarketplaceEvents.ApplyFilterToken(it)) },
                        onTagModeChanged = { state.eventSink(SkillMarketplaceEvents.TagModeChanged(it)) },
                        onDismiss = { state.eventSink(SkillMarketplaceEvents.DismissFilterSheet) },
                    )
                }
            }
            if (state.filterState.activeTokenCount > 0) {
                item {
                    SkillFilterTokensRow(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        filterState = state.filterState,
                        onRemove = { state.eventSink(SkillMarketplaceEvents.RemoveFilterToken(it)) },
                        onClear = { state.eventSink(SkillMarketplaceEvents.ClearFilters) },
                    )
                }
            }
            state.total?.let { total ->
                item {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                        text = "$total 个公开技能",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            when {
                state.isLoading && state.skills.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                state.skills.isEmpty() -> {
                    item {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 40.dp),
                            text = if (state.searchQuery.isNotBlank() || state.filterState.activeTokenCount > 0) "没有匹配的技能" else "暂无公开技能",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        if (state.searchQuery.isNotBlank() || state.filterState.activeTokenCount > 0) {
                            Text(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { state.eventSink(SkillMarketplaceEvents.ClearFilters) }
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                                text = "清除筛选",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                else -> {
                    items(items = state.skills, key = { it.id }) { skill ->
                        SkillListRow(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            skill = skill,
                            showVisibility = false,
                            onClick = { state.eventSink(SkillMarketplaceEvents.SelectSkill(skill.id)) },
                            onFilterSelected = { state.eventSink(SkillMarketplaceEvents.ApplyFilterToken(it)) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    if (state.hasMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !state.isLoadingNextPage) {
                                        state.eventSink(SkillMarketplaceEvents.LoadNextPage)
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isLoadingNextPage) {
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
        }
    }
}

@Composable
private fun SearchAndFilterControls(state: SkillMarketplaceState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = { state.eventSink(SkillMarketplaceEvents.SearchQueryChanged(it)) },
            placeholder = { Text("搜索公开技能") },
            leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
        )
        FilledTonalButton(
            enabled = state.filtersAvailable,
            onClick = { state.eventSink(SkillMarketplaceEvents.AddFilter) },
        ) {
            Text(if (state.filterState.activeTokenCount > 0) "筛选 · ${state.filterState.activeTokenCount}" else "筛选")
        }
    }
}

internal class SkillMarketplaceStateProvider : PreviewParameterProvider<SkillMarketplaceState> {
    override val values: Sequence<SkillMarketplaceState>
        get() = sequenceOf(
            aSkillMarketplaceState(),
            aSkillMarketplaceState(skills = persistentListOf(), isLoading = true),
        )
}

private fun aSkillMarketplaceState(
    skills: kotlinx.collections.immutable.ImmutableList<ChatbotUserSkill> = aSampleMarketplaceSkills(),
    isLoading: Boolean = false,
) = SkillMarketplaceState(
    skills = skills,
    total = skills.size.takeIf { it > 0 },
    page = 1,
    pageSize = 20,
    isLoading = isLoading,
    isLoadingNextPage = false,
    searchQuery = "",
    filterState = SkillFilterState(),
    facets = ChatbotSkillFacetsResponse(),
    isFilterSheetVisible = false,
    error = null,
    eventSink = {},
)

private fun aSampleMarketplaceSkills() = persistentListOf(
    ChatbotUserSkill(id = "translate", name = "Translator", description = "Translates text between languages."),
    ChatbotUserSkill(id = "summarise", name = "Summariser", description = "Condenses long documents into key points."),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun SkillMarketplaceViewPreview(@PreviewParameter(SkillMarketplaceStateProvider::class) state: SkillMarketplaceState) = ElementPreview {
    SkillMarketplaceView(state = state, onBackClick = {})
}
