/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

@Composable
fun SkillsHomeView(
    state: SkillsHomeState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(SkillsHomeEvents.OnAppear)
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
            Text("Skills", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBackClick) {
                Text("Done")
            }
        }
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { state.eventSink(SkillsHomeEvents.CreateSkill) },
        ) {
            Text("Create Skill")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabButton(
                text = "Mine",
                selected = state.selectedTab == SkillsHomeTab.Mine,
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Mine)) },
            )
            TabButton(
                text = "Marketplace",
                selected = state.selectedTab == SkillsHomeTab.Marketplace,
                modifier = Modifier.weight(1f),
                onClick = { state.eventSink(SkillsHomeEvents.SelectTab(SkillsHomeTab.Marketplace)) },
            )
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = { state.eventSink(SkillsHomeEvents.SearchQueryChanged(it)) },
            label = { Text(if (state.selectedTab == SkillsHomeTab.Mine) "Search my skills" else "Search marketplace") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.eventSink(SkillsHomeEvents.Refresh) }) {
                Text("Refresh")
            }
            state.error?.let {
                OutlinedButton(onClick = { state.eventSink(SkillsHomeEvents.ClearError) }) {
                    Text(it)
                }
            }
        }
        when (state.selectedTab) {
            SkillsHomeTab.Mine -> MySkillsContent(state)
            SkillsHomeTab.Marketplace -> MarketplaceContent(state)
        }
    }
}

@Composable
private fun MySkillsContent(state: SkillsHomeState) {
    when {
        state.isLoading -> CircularProgressIndicator()
        state.filteredSkills.isEmpty() -> Text("No skills")
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.filteredSkills, key = { it.id }) { skill ->
                SkillListRow(
                    skill = skill,
                    showVisibility = true,
                    onClick = { state.eventSink(SkillsHomeEvents.SelectSkill(skill.id)) },
                )
            }
        }
    }
}

@Composable
private fun MarketplaceContent(state: SkillsHomeState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        state.marketplaceTotal?.let { Text("Marketplace ($it)") }
        when {
            state.isLoadingMarketplace -> CircularProgressIndicator()
            state.marketplaceSkills.isEmpty() -> Text("No public skills")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.marketplaceSkills, key = { it.id }) { skill ->
                    SkillListRow(
                        skill = skill,
                        showVisibility = false,
                        onClick = { state.eventSink(SkillsHomeEvents.SelectMarketplaceSkill(skill.id)) },
                    )
                }
                item {
                    if (state.marketplaceHasMore) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { state.eventSink(SkillsHomeEvents.LoadNextMarketplacePage) },
                            enabled = !state.isLoadingMarketplaceNextPage,
                        ) {
                            Text(if (state.isLoadingMarketplaceNextPage) "Loading..." else "Load more")
                        }
                    } else {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
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
