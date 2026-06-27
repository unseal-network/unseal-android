/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package io.element.android.features.connectors.impl.list

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.connectors.impl.R
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkit
import io.element.android.libraries.chatbot.api.model.connectors.ChatbotToolkitCategory
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.utils.OnLifecycleEvent
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

@Composable
fun ConnectorListView(
    state: ConnectorListState,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) {
        state.eventSink(ConnectorListEvents.OnAppear)
    }
    // Mirror iOS observeAppForeground()/OAuth callback: reload toolkits whenever the screen
    // returns to the foreground, so a toolkit connected in the external browser shows as
    // connected on return. This is a silent refresh (the skeleton only shows when the list is
    // still empty). Skip the first ON_RESUME so it does not double up with OnAppear's initial load.
    var skipInitialResume by remember { mutableStateOf(true) }
    OnLifecycleEvent { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            if (skipInitialResume) {
                skipInitialResume = false
            } else {
                state.eventSink(ConnectorListEvents.Refresh)
            }
        }
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.connectors_title)) },
                navigationIcon = {
                    IconButton(onClick = { state.eventSink(ConnectorListEvents.Dismiss) }) {
                        Icon(imageVector = CompoundIcons.ChevronLeft(), contentDescription = stringResource(CommonStrings.action_go_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                value = state.searchQuery,
                onValueChange = { state.eventSink(ConnectorListEvents.SearchChanged(it)) },
                placeholder = { Text(stringResource(R.string.connectors_search_placeholder)) },
                leadingIcon = { Icon(imageVector = CompoundIcons.Search(), contentDescription = null) },
                trailingIcon = if (state.searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { state.eventSink(ConnectorListEvents.SearchChanged("")) }) {
                            Icon(imageVector = CompoundIcons.Close(), contentDescription = stringResource(CommonStrings.action_clear))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
            CategoryFilterRow(
                categories = state.categories,
                selectedCategoryId = state.selectedCategoryId,
                onSelect = { state.eventSink(ConnectorListEvents.SelectCategory(it)) },
            )
            state.error?.let { error ->
                ErrorBanner(error = error, onDismiss = { state.eventSink(ConnectorListEvents.ClearError) })
            }
            state.successMessage?.let { message ->
                SuccessBanner(message = message, onDismiss = { state.eventSink(ConnectorListEvents.ClearSuccess) })
            }
            when {
                state.isLoading && state.toolkits.isEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(count = 8) { ToolkitSkeletonRow() }
                    }
                }
                state.toolkits.isEmpty() -> {
                    Text(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp, start = 16.dp, end = 16.dp),
                        text = stringResource(R.string.connectors_empty_toolkits),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.toolkits, key = { it.slug }) { toolkit ->
                            ToolkitRow(state = state, toolkit = toolkit)
                        }
                        if (state.hasMore) {
                            item {
                                // Auto-load the next page when the loader becomes visible.
                                LaunchedEffect(state.toolkits.size) {
                                    if (!state.isLoadingMore) state.eventSink(ConnectorListEvents.LoadMore)
                                }
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Horizontal category-chip row mirroring the iOS ConnectorListScreen categoryFilter:
 * an "All" chip (empty id) followed by one chip per category. Selecting a chip drives
 * [ConnectorListState.selectedCategoryId] and reloads toolkits with that category.
 * Hidden when there are no categories, matching iOS where the row only shows API-provided ones.
 */
@Composable
private fun CategoryFilterRow(
    categories: ImmutableList<ChatbotToolkitCategory>,
    selectedCategoryId: String,
    onSelect: (String) -> Unit,
) {
    if (categories.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedCategoryId.isEmpty(),
                onClick = { onSelect("") },
                label = { Text(stringResource(R.string.connectors_filter_all)) },
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selectedCategoryId == category.id,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
            )
        }
    }
}

@Composable
private fun SuccessBanner(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.CheckCircle(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            modifier = Modifier.weight(1f),
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = stringResource(CommonStrings.action_close),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ErrorBanner(error: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = CompoundIcons.Error(),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.size(18.dp),
        )
        Text(
            modifier = Modifier.weight(1f),
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(
                imageVector = CompoundIcons.Close(),
                contentDescription = stringResource(CommonStrings.action_close),
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ToolkitRow(state: ConnectorListState, toolkit: ChatbotToolkit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RemoteToolkitLogo(
            logoUrl = toolkit.logo,
            slug = toolkit.slug,
            name = toolkit.name,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = toolkit.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            toolkit.description?.takeIf { it.isNotBlank() }?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (toolkit.connected) {
            OutlinedButton(onClick = { state.eventSink(ConnectorListEvents.Manage(toolkit)) }) {
                Icon(imageVector = CompoundIcons.Settings(), contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(stringResource(R.string.connectors_manage))
            }
        } else {
            val connecting = state.connectingSlug == toolkit.slug
            Button(
                enabled = !connecting,
                onClick = { state.eventSink(ConnectorListEvents.Connect(toolkit)) },
            ) {
                if (connecting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(imageVector = CompoundIcons.Link(), contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.size(6.dp))
                Text(if (connecting) stringResource(R.string.connectors_connecting) else stringResource(R.string.connectors_connect))
            }
        }
    }
}

/**
 * Renders the toolkit/connector logo as a remote image (mirrors the iOS RemoteToolkitIcon).
 * Uses the API-provided [logoUrl] when present, otherwise falls back to the composio logo
 * URL scheme `https://logos.composio.dev/api/<slug>`. On load failure or when no URL can be
 * resolved, shows a letter-box fallback with the first character of [name].
 */
@Composable
private fun RemoteToolkitLogo(
    logoUrl: String?,
    slug: String,
    name: String,
) {
    val resolvedUrl = logoUrl?.takeIf { it.isNotBlank() }
        ?: slug.takeIf { it.isNotBlank() }?.let { "https://logos.composio.dev/api/$it" }
    val shape = RoundedCornerShape(6.dp)
    if (resolvedUrl == null) {
        LogoLetterFallback(name = name, shape = shape)
        return
    }
    SubcomposeAsyncImage(
        model = resolvedUrl,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier.size(36.dp).clip(shape),
    ) {
        val painterState by painter.state.collectAsState()
        when (painterState) {
            is AsyncImagePainter.State.Success -> SubcomposeAsyncImageContent()
            else -> LogoLetterFallback(name = name, shape = shape)
        }
    }
}

@Composable
private fun LogoLetterFallback(name: String, shape: androidx.compose.ui.graphics.Shape) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ToolkitSkeletonRow() {
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
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(modifier = Modifier.size(width = 120.dp, height = 12.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp)))
            Box(modifier = Modifier.size(width = 180.dp, height = 10.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(3.dp)))
        }
        Box(modifier = Modifier.size(width = 72.dp, height = 28.dp).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp)))
    }
}

internal class ConnectorListStateProvider : PreviewParameterProvider<ConnectorListState> {
    override val values: Sequence<ConnectorListState>
        get() = sequenceOf(
            aConnectorListState(),
            aConnectorListState(selectedCategoryId = "communication"),
            aConnectorListState(isLoading = true, toolkits = persistentListOf()),
            aConnectorListState(toolkits = persistentListOf()),
            aConnectorListState(error = "Failed to load connectors"),
            aConnectorListState(successMessage = "Slack connected"),
        )
}

private fun aConnectorListState(
    toolkits: ImmutableList<ChatbotToolkit> = aSampleToolkits(),
    categories: ImmutableList<ChatbotToolkitCategory> = aSampleCategories(),
    selectedCategoryId: String = "",
    isLoading: Boolean = false,
    error: String? = null,
    successMessage: String? = null,
) = ConnectorListState(
    toolkits = toolkits,
    categories = categories,
    selectedCategoryId = selectedCategoryId,
    searchQuery = "",
    isLoading = isLoading,
    isLoadingMore = false,
    hasMore = false,
    connectingSlug = null,
    error = error,
    successMessage = successMessage,
    eventSink = {},
)

private fun aSampleToolkits() = persistentListOf(
    ChatbotToolkit(name = "GitHub", slug = "github", description = "Connect repositories, issues and pull requests.", connected = false),
    ChatbotToolkit(name = "Slack", slug = "slack", description = "Send and read messages in your workspace.", connected = true),
    ChatbotToolkit(name = "Google Calendar", slug = "googlecalendar", description = "Manage events and reminders.", connected = false),
).toImmutableList()

private fun aSampleCategories() = persistentListOf(
    ChatbotToolkitCategory(id = "productivity", name = "Productivity"),
    ChatbotToolkitCategory(id = "communication", name = "Communication"),
    ChatbotToolkitCategory(id = "developer-tools", name = "Developer Tools"),
).toImmutableList()

@PreviewsDayNight
@Composable
internal fun ConnectorListViewPreview(@PreviewParameter(ConnectorListStateProvider::class) state: ConnectorListState) = ElementPreview {
    ConnectorListView(state = state)
}
