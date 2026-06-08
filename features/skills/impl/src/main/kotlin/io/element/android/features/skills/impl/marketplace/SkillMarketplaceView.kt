/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.skills.impl.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
fun SkillMarketplaceView(
    state: SkillMarketplaceState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(SkillMarketplaceEvents.OnAppear)
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
            Text("Skill Marketplace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = onBackClick) {
                Text("Back")
            }
        }
        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.searchQuery,
            onValueChange = { state.eventSink(SkillMarketplaceEvents.SearchQueryChanged(it)) },
            label = { Text("Search marketplace") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { state.eventSink(SkillMarketplaceEvents.Refresh) }) {
                Text("Refresh")
            }
            state.total?.let { Text("Total $it", modifier = Modifier.align(Alignment.CenterVertically)) }
        }
        state.error?.let {
            OutlinedButton(onClick = { state.eventSink(SkillMarketplaceEvents.ClearError) }) {
                Text(it)
            }
        }
        when {
            state.isLoading -> CircularProgressIndicator()
            state.skills.isEmpty() -> Text("No public skills")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.skills, key = { it.id }) { skill ->
                    SkillListRow(
                        skill = skill,
                        showVisibility = false,
                        onClick = { state.eventSink(SkillMarketplaceEvents.SelectSkill(skill.id)) },
                    )
                }
                item {
                    if (state.hasMore) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { state.eventSink(SkillMarketplaceEvents.LoadNextPage) },
                            enabled = !state.isLoadingNextPage,
                        ) {
                            Text(if (state.isLoadingNextPage) "Loading..." else "Load more")
                        }
                    }
                }
            }
        }
    }
}
