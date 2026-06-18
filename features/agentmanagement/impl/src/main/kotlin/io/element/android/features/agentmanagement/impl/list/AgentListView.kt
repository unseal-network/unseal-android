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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.chatbot.api.model.agent.ChatbotAgent
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.management.ManagementListRow
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
    val renderModel = state.renderModel
    LaunchedEffect(Unit) {
        state.eventSink(AgentListEvents.OnAppear)
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(renderModel.title) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { state.eventSink(AgentListEvents.OpenSkills) }) {
                        Icon(imageVector = CompoundIcons.ListBulleted(), contentDescription = renderModel.skillsLabel)
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(renderModel.createLabel) },
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
                value = renderModel.query,
                onValueChange = { state.eventSink(AgentListEvents.SearchQueryChanged(it)) },
                placeholder = { Text(renderModel.searchPlaceholder) },
                leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                when {
                    renderModel.isLoading && renderModel.items.isEmpty() -> items(count = 6) {
                        AgentSkeletonRow(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    renderModel.items.isEmpty() -> item {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            text = renderModel.emptyLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    else -> items(items = renderModel.items, key = { it.botName }) { agent ->
                        AgentListRow(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            agent = agent,
                            onClick = { state.eventSink(AgentListEvents.SelectAgent(agent.botName)) },
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentListRow(
    agent: AgentListItemRenderModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ManagementListRow(
        modifier = modifier.padding(vertical = 4.dp),
        title = agent.title,
        subtitle = agent.visibilityLabel,
        description = agent.description,
        meta = agent.relativeTimeLabel,
        onClick = onClick,
        leadingContent = {
            Avatar(
                avatarData = AvatarData(
                    id = agent.botName,
                    name = agent.title,
                    url = agent.avatarUrl,
                    size = AvatarSize.SelectedRoom,
                ),
                avatarType = AvatarType.Room(),
                forcedAvatarSize = 56.dp,
            )
        },
        titleTrailingContent = {
            agent.providerModelLabel?.let { tag ->
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
        },
        trailingContent = {
            Icon(CompoundIcons.ChevronRight(), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        },
    )
}

@Composable
private fun AgentSkeletonRow(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp)
            .alpha(alpha),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(width = 140.dp, height = 16.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
            Box(modifier = Modifier.size(width = 220.dp, height = 12.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp)))
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
