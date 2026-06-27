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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.agentmanagement.impl.R
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.management.ManagementCreateFloatingActionButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentListView(
    state: AgentListState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val renderModel = state.renderModel
    LaunchedEffect(Unit) {
        state.eventSink(AgentListEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.agent_management_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_go_back))
                    }
                },
                actions = {
                    IconButton(onClick = { state.eventSink(AgentListEvents.OpenSkills) }) {
                        Icon(imageVector = CompoundIcons.ListBulleted(), contentDescription = stringResource(R.string.agent_management_skills))
                    }
                },
            )
        },
        floatingActionButton = {
            ManagementCreateFloatingActionButton(
                text = stringResource(R.string.agent_management_create),
                onClick = { state.eventSink(AgentListEvents.CreateAgent) },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                value = renderModel.query,
                onValueChange = { state.eventSink(AgentListEvents.SearchQueryChanged(it)) },
                placeholder = { Text(stringResource(R.string.agent_management_search_placeholder)) },
                leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when {
                    renderModel.isLoading && renderModel.items.isEmpty() -> items(count = 6) {
                        AgentSkeletonCard()
                    }
                    renderModel.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            text = stringResource(R.string.agent_management_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> items(items = renderModel.items, key = { it.botName }) { agent ->
                        AgentListCard(
                            agent = agent,
                            onClick = { state.eventSink(AgentListEvents.SelectAgent(agent.botName)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentListCard(
    agent: AgentListItemRenderModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 172.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Avatar(
                    avatarData = AvatarData(
                        id = agent.botName,
                        name = agent.title,
                        url = agent.avatarUrl,
                        size = AvatarSize.SelectedRoom,
                    ),
                    avatarType = AvatarType.Room(),
                    forcedAvatarSize = 48.dp,
                )
                agent.providerModelLabel?.let { tag ->
                    Text(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        text = tag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = agent.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(if (agent.isPublic) R.string.agent_management_visibility_public else R.string.agent_management_visibility_private),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            agent.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            agent.relativeTime?.localizedLabel()?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AgentRelativeTime.localizedLabel(): String {
    return when (unit) {
        AgentRelativeTimeUnit.Now -> stringResource(R.string.agent_management_just_now)
        AgentRelativeTimeUnit.Minute -> stringResource(
            if (isFuture) R.string.agent_management_minutes_later else R.string.agent_management_minutes_ago,
            amount,
        )
        AgentRelativeTimeUnit.Hour -> stringResource(
            if (isFuture) R.string.agent_management_hours_later else R.string.agent_management_hours_ago,
            amount,
        )
        AgentRelativeTimeUnit.Day -> stringResource(
            if (isFuture) R.string.agent_management_days_later else R.string.agent_management_days_ago,
            amount,
        )
    }
}

@Composable
private fun AgentSkeletonCard(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = 172.dp).alpha(alpha),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
            Box(modifier = Modifier.size(width = 112.dp, height = 16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
            Box(modifier = Modifier.size(width = 86.dp, height = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
            Box(modifier = Modifier.size(width = 132.dp, height = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
            Box(modifier = Modifier.size(width = 96.dp, height = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
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
