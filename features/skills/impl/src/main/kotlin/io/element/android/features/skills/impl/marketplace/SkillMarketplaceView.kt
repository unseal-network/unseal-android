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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.skills.impl.shared.SkillListRow
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
                title = { Text("Skill Marketplace") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "Back")
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
                OutlinedTextField(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    value = state.searchQuery,
                    onValueChange = { state.eventSink(SkillMarketplaceEvents.SearchQueryChanged(it)) },
                    placeholder = { Text("Search public skills") },
                    leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(28.dp),
                )
            }
            state.total?.let { total ->
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
                            text = "No public skills available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    items(items = state.skills, key = { it.id }) { skill ->
                        SkillListRow(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            skill = skill,
                            showVisibility = false,
                            onClick = { state.eventSink(SkillMarketplaceEvents.SelectSkill(skill.id)) },
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
