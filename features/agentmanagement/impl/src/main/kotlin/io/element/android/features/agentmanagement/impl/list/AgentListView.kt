/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.agentmanagement.impl.list

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.agentmanagement.impl.shared.displayTitle
import io.element.android.features.agentmanagement.impl.shared.providerModelText
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListView(
    state: AgentListState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(AgentListEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Agents") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { state.eventSink(AgentListEvents.OpenSkills) }) {
                        Icon(imageVector = CompoundIcons.ListBulleted(), contentDescription = "Skills")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Create agent") },
                icon = { Icon(imageVector = CompoundIcons.Plus(), contentDescription = null) },
                onClick = { state.eventSink(AgentListEvents.CreateAgent) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                value = state.searchQuery,
                onValueChange = { state.eventSink(AgentListEvents.SearchQueryChanged(it)) },
                placeholder = { Text("Search agents") },
                leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    state.isLoading && state.filteredAgents.isEmpty() -> items(count = 6) { AgentSkeletonCard() }
                    state.filteredAgents.isEmpty() -> fullSpanItem {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            text = "No agents yet. Tap “Create agent” to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> items(items = state.filteredAgents, key = { it.botName }) { agent ->
                        AgentGridCard(
                            agent = agent,
                            onClick = { state.eventSink(AgentListEvents.SelectAgent(agent.botName)) },
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.grid.LazyGridScope.fullSpanItem(content: @Composable () -> Unit) {
    item(span = { GridItemSpan(maxLineSpan) }) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentGridCard(agent: ChatbotAgent, onClick: () -> Unit) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Avatar(
                avatarData = AvatarData(
                    id = agent.botName,
                    name = agent.displayTitle(),
                    url = agent.avatarUrl,
                    size = AvatarSize.SelectedRoom,
                ),
                avatarType = AvatarType.Room(),
                forcedAvatarSize = 56.dp,
            )
            Text(
                text = agent.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            agent.providerModelText()?.let { tag ->
                Text(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            agent.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (agent.isPublic == true) CompoundIcons.Public() else CompoundIcons.Lock(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = if (agent.isPublic == true) "Public" else "Private",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AgentSkeletonCard() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    ElevatedCard(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).alpha(alpha)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            Box(modifier = Modifier.size(width = 90.dp, height = 14.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
            Box(modifier = Modifier.size(width = 60.dp, height = 10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
        }
    }
}

internal class AgentListStateProvider : PreviewParameterProvider<AgentListState> {
    override val values: Sequence<AgentListState>
        get() = sequenceOf(
            anAgentListState(),
            anAgentListState(isLoading = true, agents = persistentListOf()),
            anAgentListState(agents = persistentListOf()),
        )
}

private fun anAgentListState(
    agents: kotlinx.collections.immutable.ImmutableList<ChatbotAgent> = aSampleAgents(),
    isLoading: Boolean = false,
) = AgentListState(
    agents = agents,
    filteredAgents = agents,
    searchQuery = "",
    isLoading = isLoading,
    error = null,
    eventSink = {},
)

private fun aSampleAgents() = persistentListOf(
    ChatbotAgent(botName = "assistant", displayName = "Assistant", description = "A helpful general-purpose assistant.", provider = "openai", model = "gpt-4o", isPublic = true),
    ChatbotAgent(botName = "coder", displayName = "Code Helper", description = "Writes and reviews code.", provider = "anthropic", model = "claude", isPublic = false),
    ChatbotAgent(botName = "researcher", displayName = "Researcher", provider = "google", isPublic = true),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun AgentListViewPreview(@PreviewParameter(AgentListStateProvider::class) state: AgentListState) = ElementPreview {
    AgentListView(state = state, onBackClick = {})
}
